package com.example.myllm.service;

import com.example.myllm.model.dto.ChatResponse;
import com.example.myllm.model.entity.*;
import com.example.myllm.repository.*;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.ConnectException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * 核心聊天服务 + 历史记录管理
 * -----------
 * 每次请求的完整流水线：
 *   1. 从 DB 加载启用的模型/RAG/记忆配置
 *   2. 构建 System Prompt + RAG 上下文 + 历史裁剪
 *   3. 调用 LLM API 获取回复
 *   4. 新会话：二次调 LLM 用 AI 自动生成会话标题（≤20 字）
 *   5. 每轮对话实时持久化到 MySQL（Session + Message）
 *   6. 实时更新会话的 updated_at 时间戳（确保历史列表排序准确）
 *   7. 错误分类处理（连接失败/超时/认证失败等）
 *
 * 标题生成：
 *   - 用户新建会话并发第一条消息 → LLM 回复后 → 再调一次 LLM 用简短 prompt 生成 ≤20 字标题
 *   - 标题异步保存到 Session.title，前端历史面板实时显示
 *
 * 实时记录：
 *   - 每条用户消息 + AI 回复立刻保存为一条 Message 记录
 *   - 每次保存同时更新 Session.updatedAt，确保历史列表按最近活跃排序
 */
@Service
public class ChatService {

    private final ModelConfigRepository modelConfigRepo;
    private final RagRepository ragRepo;
    private final MemoryConfigRepository memoryRepo;
    private final SessionRepository sessionRepo;
    private final MessageRepository messageRepo;
    private final RagService ragService;
    private final SessionPersistenceService persistenceService;  // 独立事务管理

    private final Map<String, List<Map<String, String>>> sessions = new ConcurrentHashMap<>();

    public ChatService(ModelConfigRepository modelConfigRepo,
                       RagRepository ragRepo,
                       MemoryConfigRepository memoryRepo,
                       SessionRepository sessionRepo,
                       MessageRepository messageRepo,
                       RagService ragService,
                       SessionPersistenceService persistenceService) {
        this.modelConfigRepo = modelConfigRepo;
        this.ragRepo = ragRepo;
        this.memoryRepo = memoryRepo;
        this.sessionRepo = sessionRepo;
        this.messageRepo = messageRepo;
        this.ragService = ragService;
        this.persistenceService = persistenceService;
    }

    /**
     * 处理一轮对话
     * 注意：不标注 @Transactional — 外部 HTTP 调用（LLM）不应在事务中运行，
     * 耗时可达数十秒，持有事务会导致连接池耗尽。DB 写操作由 persistSessionAndMessage
     * 和 updateSessionTitle 内部自管理事务。
     */
    public ChatResponse chat(String userMessage, String sessionId) {
        // 1. 获取或创建会话 ID — 以 DB 是否存在为准（不信任前端的 isNew 标记）
        boolean dbSessionExists = false;
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString().substring(0, 8);
        } else {
            // 检查这个 sessionId 在 DB 中是否已有 Session 记录
            dbSessionExists = (sessionRepo.findBySessionName(sessionId) != null);
        }

        // 2. 加载启用的模型配置
        List<ModelConfig> models = modelConfigRepo.findAllByOrderByUpdatedAtDesc();
        ModelConfig activeModel = models.stream()
                .filter(m -> m.getIsEnabled() != null && m.getIsEnabled() == 1)
                .findFirst().orElse(null);
        if (activeModel == null) {
            return ChatResponse.error("没有启用的模型配置", sessionId);
        }

        // 3. 加载启用的 RAG 文档列表（用于持久化关联）
        List<Rag> enabledRags = ragRepo.findAllByOrderByCreatedAtDesc().stream()
                .filter(r -> r.getIsEnabled() != null && r.getIsEnabled() == 1)
                .collect(Collectors.toList());

        // 4. RAG 向量检索: 用用户问题在 Chroma 中搜索最相关的文段
        List<String> sources = new ArrayList<>();
        StringBuilder ragContext = new StringBuilder();

        try {
            // 调 Chroma 相似度搜索, Top-5 最相关文段
            List<Map<String, Object>> ragResults = ragService.searchRelevant(userMessage, 5);
            if (!ragResults.isEmpty()) {
                // 去重来源列表
                sources = ragResults.stream()
                        .map(r -> (String) r.get("source"))
                        .distinct().collect(Collectors.toList());

                ragContext.append("【参考知识库内容（向量检索）】\n");
                for (int i = 0; i < ragResults.size(); i++) {
                    Map<String, Object> item = ragResults.get(i);
                    ragContext.append("--- 文段 ").append(i + 1)
                              .append(" (来源: ").append(item.get("source"))
                              .append(", 相似度: ").append(String.format("%.2f", item.get("similarity")))
                              .append(") ---\n")
                              .append(item.get("content")).append("\n\n");
                }
            }
        } catch (Exception e) {
            System.err.println("[RAG] 向量检索异常，回退到描述注入: " + e.getMessage());
            // 回退: 用已启用的 RAG 文档全文作为上下文
            sources = enabledRags.stream().map(Rag::getFilename).collect(Collectors.toList());
            for (Rag rag : enabledRags) {
                if (rag.getContent() != null && !rag.getContent().isBlank()) {
                    // 注入全文（无检索时用全文，取前 2000 字）
                    String snippet = rag.getContent().length() > 2000
                            ? rag.getContent().substring(0, 2000) + "..."
                            : rag.getContent();
                    ragContext.append("【").append(rag.getFilename()).append("】\n")
                              .append(snippet).append("\n");
                }
            }
        }

        // 4. 加载启用的记忆策略
        MemoryConfig activeMemory = memoryRepo.findAllByOrderByUpdatedAtDesc().stream()
                .filter(m -> m.getIsEnabled() != null && m.getIsEnabled() == 1)
                .findFirst().orElse(null);

        // 5. 获取内存中的历史消息并裁剪（对话记忆）
        List<Map<String, String>> history = getOrCreateHistory(sessionId);
        List<Map<String, String>> contextMessages = applyMemoryStrategy(history, activeMemory);

        // 6. 拼装对话历史文本（注入 LLM 调用实现记忆）
        StringBuilder historyText = new StringBuilder();
        if (!contextMessages.isEmpty()) {
            for (Map<String, String> msg : contextMessages) {
                historyText.append(msg.get("role")).append(": ").append(msg.get("content")).append("\n");
            }
            historyText.append("\n"); // 空行分隔历史和当前问题
        }

        // 7. 构建完整 System Prompt = 模型 prompt + RAG 上下文
        String systemPrompt = buildSystemPrompt(activeModel, ragContext.toString());

        // 8. 构建 LLM 实例
        String baseUrl = activeModel.getBaseUrl() != null && !activeModel.getBaseUrl().isBlank()
                ? activeModel.getBaseUrl() : "https://api.deepseek.com";
        String apiKey = activeModel.getApiKeyEncrypted();
        if (apiKey == null || apiKey.isBlank())
            return ChatResponse.error("模型「" + activeModel.getModelName() + "」未配置 API Key", sessionId);
        String modelName = activeModel.getModelName() != null && !activeModel.getModelName().isBlank()
                ? activeModel.getModelName() : "deepseek-v4-flash";
        int maxTokens = activeModel.getMaxTokens() != null ? activeModel.getMaxTokens() : 4096;

        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl(baseUrl).apiKey(apiKey).modelName(modelName)
                .temperature(0.7).maxTokens(maxTokens)
                .timeout(Duration.ofSeconds(120)).build();

        // 9. 调用 LLM — 传入历史上下文实现对话记忆
        //    当前消息 = 历史对话文本 + 用户新消息
        String fullPrompt = historyText.length() > 0
                ? historyText + "user: " + userMessage
                : userMessage;

        String aiReply;
        try {
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                aiReply = model.generate(
                    dev.langchain4j.data.message.SystemMessage.from(systemPrompt),
                    dev.langchain4j.data.message.UserMessage.from(fullPrompt)
                ).content().text();
            } else {
                aiReply = model.generate(fullPrompt);
            }
            System.out.println("[" + sessionId + "] 模型: " + modelName
                    + " | 历史轮数: " + (contextMessages.size() / 2)
                    + " | 来源: " + sources + " | 回复长度: " + (aiReply != null ? aiReply.length() : 0));
        } catch (Exception e) {
            String errorMsg = classifyError(e);
            System.err.println("[" + sessionId + "] LLM 调用失败: " + errorMsg);
            return ChatResponse.error(errorMsg, sessionId);
        }

        // 10. 保存到内存历史（实现后续对话记忆的关键）
        history.add(Map.of("role", "user", "content", userMessage));
        history.add(Map.of("role", "assistant", "content", aiReply != null ? aiReply : ""));

        // 11. 持久化到 MySQL — 异常不阻断聊天回复
        Rag firstRag = enabledRags.isEmpty() ? null : enabledRags.get(0);
        Long sessionDbId = null;
        try {
            sessionDbId = persistenceService.saveSessionAndMessage(
                    sessionId, !dbSessionExists, userMessage, aiReply,
                    activeModel, activeMemory, firstRag);
        } catch (Exception e) {
            System.err.println("[DB] 持久化异常: " + e.getMessage());
            e.printStackTrace();
        }

        // 12. 新会话：AI 自动生成标题
        if (!dbSessionExists && sessionDbId != null) {
            try {
                String aiTitle = generateSessionTitle(model, userMessage, aiReply);
                if (aiTitle != null) {
                    persistenceService.updateTitle(sessionDbId, aiTitle);
                }
            } catch (Exception e) {
                System.err.println("[标题] 生成/更新异常: " + e.getMessage());
            }
        }

        return ChatResponse.ok(aiReply, sessionId, sources, activeModel.getModelName());
    }

    // ==================== 历史记录 API ====================

    /** 获取所有会话列表 */
    public List<Map<String, Object>> listSessions() {
        List<Session> all = sessionRepo.findAllByOrderByUpdatedAtDesc();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Session s : all) {
            int count = messageRepo.countBySessionId(s.getId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", s.getId());
            item.put("sessionId", s.getSessionName());
            item.put("title", s.getTitle() != null ? s.getTitle() : s.getSessionName());
            item.put("messageCount", count);
            item.put("updatedAt", s.getUpdatedAt() != null ? s.getUpdatedAt().toString() : null);
            result.add(item);
        }
        return result;
    }

    /** 删除会话及其全部消息 */
    @Transactional
    public void deleteSession(Long dbSessionId) {
        messageRepo.deleteBySessionId(dbSessionId);
        sessionRepo.deleteById(dbSessionId);
    }

    // ==================== 私有方法 ====================

    /** 构建 System Prompt */
    private String buildSystemPrompt(ModelConfig model, String ragContext) {
        StringBuilder sb = new StringBuilder();
        if (model.getPrompt() != null && !model.getPrompt().isBlank()) sb.append(model.getPrompt());
        if (!ragContext.isEmpty()) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append("【参考知识库内容】\n").append(ragContext);
        }
        return sb.toString();
    }

    private List<Map<String, String>> getOrCreateHistory(String sessionId) {
        return sessions.computeIfAbsent(sessionId, k -> new ArrayList<>());
    }

    private List<Map<String, String>> applyMemoryStrategy(List<Map<String, String>> history, MemoryConfig mem) {
        int windowSize = (mem != null && mem.getWindowSize() != null) ? mem.getWindowSize() : 10;
        int maxMessages = windowSize * 2;
        if (history.size() <= maxMessages) return new ArrayList<>(history);
        return new ArrayList<>(history.subList(history.size() - maxMessages, history.size()));
    }

    /**
     * AI 自动生成会话标题（新会话首次对话后调用）
     * @return 生成的标题，失败返回 null
     */
    private String generateSessionTitle(OpenAiChatModel model, String userMsg, String aiReply) {
        try {
            // 截取首轮对话的关键内容（限制长度以降低 token 消耗）
            String shortUser = userMsg.length() > 200 ? userMsg.substring(0, 200) : userMsg;
            String shortAi = aiReply != null && aiReply.length() > 200 ? aiReply.substring(0, 200) : aiReply;
            String titlePrompt = "请用不超过20个字简短总结以下对话的主题，只输出标题本身，不要引号、不要解释。\n\n"
                    + "用户: " + shortUser + "\nAI: " + (shortAi != null ? shortAi : "");

            String title = model.generate(titlePrompt);
            if (title != null) {
                title = title.trim().replaceAll("^[\"'「]|[\"'」]$", "");  // 去掉可能的引号
                if (title.length() > 30) title = title.substring(0, 30);   // 硬截断保底
                System.out.println("[标题生成] 用户首条 → AI 标题: " + title);
                return title;
            }
        } catch (Exception e) {
            System.err.println("[标题生成] 失败: " + e.getMessage());
        }
        return null;
    }

    private String classifyError(Exception e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof ConnectException
                    || (cause.getMessage() != null && cause.getMessage().contains("Connection refused")))
                return "连接失败：无法连接到模型服务器";
            if (cause instanceof TimeoutException
                    || (cause.getMessage() != null && cause.getMessage().contains("timeout")))
                return "服务器超时：模型响应时间过长";
            if (cause.getMessage() != null && cause.getMessage().contains("401"))
                return "认证失败：API Key 无效或已过期";
            if (cause.getMessage() != null && cause.getMessage().contains("429"))
                return "请求频率过高：API 配额用尽，请稍后重试";
            cause = cause.getCause();
        }
        return "大模型调用失败: " + e.getMessage();
    }

    public void clearSession(String sessionId) {
        sessions.remove(sessionId);
    }
}
