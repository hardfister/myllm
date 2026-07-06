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
 *   3. 调用 LLM API
 *   4. 错误分类处理（连接失败/超时/认证失败等）
 *   5. 将用户消息和 AI 回复持久化到 MySQL（Message 表）
 *   6. 首次发消息时自动创建 Session 记录（会话名称取首条消息前 50 字）
 *
 * 历史记录：
 *   - 判定条件：用户发出第一条消息，后端立刻在 DB 创建 Session + Message
 *   - 会话列表按 updated_at 倒序排列（最近活跃的会话排最前）
 *   - 删除会话时级联删除其下的所有 Message 记录
 */
@Service
public class ChatService {

    private final ModelConfigRepository modelConfigRepo;
    private final RagRepository ragRepo;
    private final MemoryConfigRepository memoryRepo;
    private final SessionRepository sessionRepo;      // 新增：会话持久化
    private final MessageRepository messageRepo;      // 新增：消息持久化

    /** 内存会话存储：sessionId → 历史消息列表（用于运行时快速访问和记忆裁剪） */
    private final Map<String, List<Map<String, String>>> sessions = new ConcurrentHashMap<>();

    public ChatService(ModelConfigRepository modelConfigRepo,
                       RagRepository ragRepo,
                       MemoryConfigRepository memoryRepo,
                       SessionRepository sessionRepo,
                       MessageRepository messageRepo) {
        this.modelConfigRepo = modelConfigRepo;
        this.ragRepo = ragRepo;
        this.memoryRepo = memoryRepo;
        this.sessionRepo = sessionRepo;
        this.messageRepo = messageRepo;
    }

    /**
     * 处理一轮对话
     * @param userMessage 用户输入
     * @param sessionId   会话 ID（null 则新建）
     * @return ChatResponse（包含 AI 回复 + sessionId + 错误信息 + 来源）
     */
    @Transactional
    public ChatResponse chat(String userMessage, String sessionId) {
        // 1. 获取或创建会话 ID
        boolean isNewSession = (sessionId == null || sessionId.isBlank());
        if (isNewSession) {
            sessionId = UUID.randomUUID().toString().substring(0, 8);
        }

        // 2. 加载启用的模型配置
        List<ModelConfig> models = modelConfigRepo.findAllByOrderByUpdatedAtDesc();
        ModelConfig activeModel = models.stream()
                .filter(m -> m.getIsEnabled() != null && m.getIsEnabled() == 1)
                .findFirst().orElse(null);

        if (activeModel == null) {
            return ChatResponse.error("没有启用的模型配置，请先在「自定义模型」中配置并激活一个模型", sessionId);
        }

        // 3. 加载启用的 RAG 知识库文档，拼接上下文
        List<Rag> enabledRags = ragRepo.findAllByOrderByCreatedAtDesc().stream()
                .filter(r -> r.getIsEnabled() != null && r.getIsEnabled() == 1)
                .collect(Collectors.toList());
        List<String> sources = enabledRags.stream()
                .map(Rag::getFilename).collect(Collectors.toList());

        StringBuilder ragContext = new StringBuilder();
        for (Rag rag : enabledRags) {
            if (rag.getDescription() != null && !rag.getDescription().isBlank()) {
                ragContext.append("【").append(rag.getFilename()).append("】")
                          .append(rag.getDescription()).append("\n");
            }
        }

        // 4. 加载启用的记忆策略
        MemoryConfig activeMemory = memoryRepo.findAllByOrderByUpdatedAtDesc().stream()
                .filter(m -> m.getIsEnabled() != null && m.getIsEnabled() == 1)
                .findFirst().orElse(null);

        // 5. 构建 System Prompt
        String systemPrompt = buildSystemPrompt(activeModel, ragContext.toString());

        // 6. 获取历史消息并裁剪
        List<Map<String, String>> history = getOrCreateHistory(sessionId);
        List<Map<String, String>> contextMessages = applyMemoryStrategy(history, activeMemory);

        // 7. 构建 LLM 实例
        OpenAiChatModel model;
        try {
            String baseUrl = activeModel.getBaseUrl();
            if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://api.deepseek.com/v1";
            String apiKey = activeModel.getApiKeyEncrypted();
            if (apiKey == null || apiKey.isBlank())
                return ChatResponse.error("模型「" + activeModel.getModelName() + "」未配置 API Key", sessionId);
            String modelName = activeModel.getModelName();
            if (modelName == null || modelName.isBlank()) modelName = "deepseek-chat";
            int maxTokens = activeModel.getMaxTokens() != null ? activeModel.getMaxTokens() : 4096;

            model = OpenAiChatModel.builder()
                    .baseUrl(baseUrl).apiKey(apiKey).modelName(modelName)
                    .temperature(0.7).maxTokens(maxTokens)
                    .timeout(Duration.ofSeconds(120)).build();
        } catch (Exception e) {
            return ChatResponse.error("模型初始化失败: " + e.getMessage(), sessionId);
        }

        // 8. 调用 LLM
        String aiReply;
        try {
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                aiReply = model.generate(
                    dev.langchain4j.data.message.SystemMessage.from(systemPrompt),
                    dev.langchain4j.data.message.UserMessage.from(userMessage)
                ).content().text();
            } else {
                aiReply = model.generate(userMessage);
            }

            System.out.println("[" + sessionId + "] 模型: " + activeModel.getModelName()
                    + " | 来源: " + sources + " | 回复长度: " + (aiReply != null ? aiReply.length() : 0));

        } catch (Exception e) {
            String errorMsg = classifyError(e);
            System.err.println("[" + sessionId + "] LLM 调用失败: " + errorMsg + " — " + e.getMessage());
            return ChatResponse.error(errorMsg, sessionId);
        }

        // 9. 保存到内存历史
        history.add(Map.of("role", "user", "content", userMessage));
        history.add(Map.of("role", "assistant", "content", aiReply != null ? aiReply : ""));

        // 10. 持久化会话和消息到 MySQL
        persistSessionAndMessage(sessionId, isNewSession, userMessage, aiReply,
                activeModel, activeMemory, enabledRags.isEmpty() ? null : enabledRags.get(0));

        return ChatResponse.ok(aiReply, sessionId, sources, activeModel.getModelName());
    }


    // ==================== 历史记录 API ====================

    /**
     * 获取所有会话列表（用于前端侧边栏历史记录面板）
     * 返回格式适合前端渲染：sessionId, sessionName, messageCount, updatedAt
     */
    public List<Map<String, Object>> listSessions() {
        List<Session> sessions = sessionRepo.findAllByOrderByUpdatedAtDesc();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Session s : sessions) {
            int count = messageRepo.countBySessionId(s.getId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", s.getId());
            item.put("sessionId", s.getSessionName());  // 短 UUID，前端用于关联消息
            item.put("title", s.getTitle() != null ? s.getTitle() : s.getSessionName());  // 会话显示标题
            item.put("messageCount", count);
            item.put("updatedAt", s.getUpdatedAt() != null ? s.getUpdatedAt().toString() : null);
            result.add(item);
        }
        return result;
    }

    /**
     * 删除会话及其全部消息
     * @param dbSessionId Session 表的主键 id
     */
    @Transactional
    public void deleteSession(Long dbSessionId) {
        // 级联删除消息
        messageRepo.deleteBySessionId(dbSessionId);
        // 删除会话
        sessionRepo.deleteById(dbSessionId);
    }

    // ==================== 私有方法 ====================

    /** 构建 System Prompt */
    private String buildSystemPrompt(ModelConfig model, String ragContext) {
        StringBuilder sb = new StringBuilder();
        if (model.getPrompt() != null && !model.getPrompt().isBlank()) {
            sb.append(model.getPrompt());
        }
        if (!ragContext.isEmpty()) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append("【参考知识库内容】\n").append(ragContext);
        }
        return sb.toString();
    }

    /** 获取或创建内存中的会话历史 */
    private List<Map<String, String>> getOrCreateHistory(String sessionId) {
        return sessions.computeIfAbsent(sessionId, k -> new ArrayList<>());
    }

    /** 记忆策略裁剪 */
    private List<Map<String, String>> applyMemoryStrategy(
            List<Map<String, String>> history, MemoryConfig mem) {
        int windowSize = (mem != null && mem.getWindowSize() != null) ? mem.getWindowSize() : 10;
        int maxMessages = windowSize * 2;
        if (history.size() <= maxMessages) return new ArrayList<>(history);
        return new ArrayList<>(history.subList(history.size() - maxMessages, history.size()));
    }

    /**
     * 将用户消息和 AI 回复持久化到 MySQL
     * - 新建会话时创建 Session 记录（sessionName 取自首条消息前 50 字）
     * - 每次回复创建一条 Message 记录（user_message + ai_response）
     */
    private void persistSessionAndMessage(String sessionId, boolean isNew,
                                          String userMsg, String aiReply,
                                          ModelConfig model, MemoryConfig mem, Rag rag) {
        try {
            // 查找或创建 Session 记录
            Session session = sessionRepo.findBySessionName(sessionId);
            if (session == null && isNew) {
                session = new Session();
                session.setSessionName(sessionId);
                // 会话标题取用户首条消息的前 50 个字符
                String title = userMsg.length() > 50 ? userMsg.substring(0, 50) + "..." : userMsg;
                session.setTitle(title);
                session.setModelId(model != null ? model.getId() : null);
                session.setMemoryId(mem != null ? mem.getId() : null);
                session.setRagId(rag != null ? rag.getId() : null);
                session = sessionRepo.save(session);
            } else if (session != null) {
                // 已有会话：更新 updated_at 时间戳
                session.setUpdatedAt(null); // 触发 @PreUpdate
                sessionRepo.save(session);
            }

            if (session != null) {
                // 保存消息
                Message msg = new Message();
                msg.setSessionId(session.getId());
                msg.setUserMessage(userMsg);
                msg.setAiResponse(aiReply);
                msg.setTokensUsed(estimateTokens(userMsg, aiReply));
                messageRepo.save(msg);
            }
        } catch (Exception e) {
            System.err.println("[DB] 持久化消息失败: " + e.getMessage());
        }
    }

    /** 估算 Token 消耗（粗略：中英文混合，约 2 字符 = 1 token） */
    private int estimateTokens(String user, String ai) {
        int total = 0;
        if (user != null) total += user.length();
        if (ai != null) total += ai.length();
        return Math.max(1, total / 2);
    }

    /** 错误分类 */
    private String classifyError(Exception e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof ConnectException
                    || (cause.getMessage() != null && cause.getMessage().contains("Connection refused")))
                return "连接失败：无法连接到模型服务器，请检查 Base URL 和网络";
            if (cause instanceof TimeoutException
                    || (cause.getMessage() != null && cause.getMessage().contains("timeout")))
                return "服务器超时：模型响应时间过长，请稍后重试或增大超时限制";
            if (cause.getMessage() != null && cause.getMessage().contains("401"))
                return "认证失败：API Key 无效或已过期";
            if (cause.getMessage() != null && cause.getMessage().contains("429"))
                return "请求频率过高：API 配额用尽，请稍后重试";
            cause = cause.getCause();
        }
        return "大模型调用失败: " + e.getMessage();
    }

    /** 清除内存中的会话 */
    public void clearSession(String sessionId) {
        sessions.remove(sessionId);
    }
}
