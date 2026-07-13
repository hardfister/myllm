package com.example.myllm.service;

import com.example.myllm.model.dto.ChatResponse;
import com.example.myllm.model.entity.*;
import com.example.myllm.repository.*;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.Response;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.ConnectException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
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
     * 处理一轮对话 — 支持多模型顺序接力
     *
     * 多模型模式：所有 isEnabled=1 的模型按 sortOrder 排序，
     * 依次调用，每个模型看到之前所有模型的回复。
     * 例如 Model A → B → C, B 的 prompt 包含 A 的回复, C 包含 A+B 的。
     *
     * 回复格式中每条都标注 displayName（用户给模型取的名）。
     */
    public ChatResponse chat(String userMessage, String sessionId) {
        // 1. 会话 ID
        boolean dbSessionExists = false;
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString().substring(0, 8);
        } else {
            dbSessionExists = (sessionRepo.findBySessionName(sessionId) != null);
        }

        // 2. 加载所有启用的模型，按 sortOrder 排序
        List<ModelConfig> enabledModels = modelConfigRepo.findAllByOrderByUpdatedAtDesc().stream()
                .filter(m -> m.getIsEnabled() != null && m.getIsEnabled() == 1)
                .sorted((a, b) -> {
                    int sa = a.getSortOrder() != null ? a.getSortOrder() : 0;
                    int sb = b.getSortOrder() != null ? b.getSortOrder() : 0;
                    return Integer.compare(sa, sb);
                })
                .collect(Collectors.toList());

        if (enabledModels.isEmpty()) {
            return ChatResponse.error("没有启用的模型配置", sessionId);
        }

        // 3. RAG 上下文（所有模型共享同一份知识库检索结果）
        List<Rag> enabledRags = ragRepo.findAllByOrderByCreatedAtDesc().stream()
                .filter(r -> r.getIsEnabled() != null && r.getIsEnabled() == 1)
                .collect(Collectors.toList());
        List<String> sources = new ArrayList<>();
        StringBuilder ragContext = buildRagContext(userMessage, enabledRags, sources);

        // 4. 记忆策略
        MemoryConfig activeMemory = memoryRepo.findAllByOrderByUpdatedAtDesc().stream()
                .filter(m -> m.getIsEnabled() != null && m.getIsEnabled() == 1)
                .findFirst().orElse(null);

        // 5. 历史记忆（从 DB 恢复 + 内存裁剪）
        List<Map<String, String>> history = getOrCreateHistory(sessionId);
        if (dbSessionExists && history.isEmpty()) {
            loadHistoryFromDb(sessionId, history);
        }
        List<Map<String, String>> contextMessages = applyMemoryStrategy(history, activeMemory);
        StringBuilder historyText = new StringBuilder();
        if (!contextMessages.isEmpty()) {
            for (Map<String, String> msg : contextMessages) {
                historyText.append(msg.get("role")).append(": ").append(msg.get("content")).append("\n");
            }
            historyText.append("\n");
        }

        // 6. 保存用户消息到历史
        history.add(Map.of("role", "user", "content", userMessage));

        // 7. 依次调用每个启用的模型，每个模型看到之前所有的对话
        List<Map<String, String>> replies = new ArrayList<>();
        // 对话记录：用户消息作为起点，后续每个模型的回复依次追加
        StringBuilder conversationLog = new StringBuilder();
        if (historyText.length() > 0) {
            conversationLog.append(historyText);
        }
        conversationLog.append("user: ").append(userMessage).append("\n");

        for (ModelConfig mc : enabledModels) {
            String displayName = mc.getDisplayName() != null && !mc.getDisplayName().isBlank()
                    ? mc.getDisplayName() : mc.getModelName();

            // 构建 System Prompt: 模型自带 prompt + RAG + 多角色声明
            String systemPrompt = buildSystemPrompt(mc, ragContext.toString(),
                    enabledModels);

            System.out.println("[" + sessionId + "] → 调用「" + displayName
                    + "」(上下文长度: " + conversationLog.length() + " 字)");

            // 将当前完整对话记录传给模型
            String aiReply = callSingleModel(mc, conversationLog.toString(), systemPrompt, sessionId,
                    enabledModels.size() > 1 ? displayName : null);

            if (aiReply != null) {
                Map<String, String> reply = new LinkedHashMap<>();
                reply.put("model", mc.getModelName());
                reply.put("displayName", displayName);
                reply.put("content", aiReply);
                replies.add(reply);

                // 关键：将模型回复以标注身份的方式追加到对话记录
                String roleTag = enabledModels.size() > 1 ? displayName : "assistant";
                conversationLog.append(roleTag).append(": ").append(aiReply).append("\n");
                history.add(Map.of("role", roleTag, "content", aiReply));
            } else {
                // 调用失败也记录在对话中，让后续模型知道
                conversationLog.append(displayName).append(": [调用失败，无回复]\n");
            }
        }

        if (replies.isEmpty()) {
            return ChatResponse.error("启用的模型均未返回结果，请检查 API Key、Base URL 和模型名称配置", sessionId);
        }

        // 8. 异步持久化
        final String finalSessionId = sessionId;
        final boolean finalIsNew = !dbSessionExists;
        final String finalUserMsg = userMessage;
        final String finalAiReply = replies.isEmpty() ? "" : replies.get(replies.size() - 1).get("content");
        final MemoryConfig finalMem = activeMemory;
        final Rag finalRag = enabledRags.isEmpty() ? null : enabledRags.get(0);
        final ModelConfig finalMc = enabledModels.get(0);
        final String finalCombinedAi = replies.stream()
                .map(r -> "[" + r.get("displayName") + "] " + r.get("content"))
                .collect(Collectors.joining("\n\n"));

        new Thread(() -> {
            try {
                Long dbId = persistenceService.saveSessionAndMessage(
                        finalSessionId, finalIsNew, finalUserMsg, finalCombinedAi,
                        finalMc, finalMem, finalRag);
                if (finalIsNew && dbId != null) {
                    String title = generateTitleFromModel(finalMc, finalUserMsg, finalCombinedAi);
                    if (title != null) persistenceService.updateTitle(dbId, title);
                }
            } catch (Exception e) {
                System.err.println("[DB] 异步持久化异常: " + e.getMessage());
            }
        }, "db-persist-" + finalSessionId).start();

        // 9. 返回
        if (enabledModels.size() == 1) {
            return ChatResponse.ok(replies.get(0).get("content"), sessionId, sources,
                    replies.get(0).get("model"));
        }
        return ChatResponse.okMulti(replies, sessionId, sources);
    }

    // ==================== 单模型调用 ====================

    /** 调用单个模型，失败返回 null */
    private String callSingleModel(ModelConfig mc, String cumulativePrompt,
                                    String systemPrompt, String sessionId, String multiLabel) {
        String baseUrl = mc.getBaseUrl() != null && !mc.getBaseUrl().isBlank()
                ? mc.getBaseUrl() : "https://api.deepseek.com/v1";
        String apiKey = mc.getApiKeyEncrypted();
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("[多模型] 「" + mc.getModelName() + "」未配置 API Key, 跳过");
            return null;
        }
        String modelName = mc.getModelName() != null && !mc.getModelName().isBlank()
                ? mc.getModelName() : "deepseek-chat";
        int maxTokens = mc.getMaxTokens() != null ? mc.getMaxTokens() : 4096;

        try {
            OpenAiChatModel model = OpenAiChatModel.builder()
                    .baseUrl(baseUrl).apiKey(apiKey).modelName(modelName)
                    .temperature(0.7).maxTokens(maxTokens)
                    .timeout(Duration.ofSeconds(120)).build();

            String label = multiLabel != null ? multiLabel : mc.getModelName();
            String reply;
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                reply = model.generate(
                    dev.langchain4j.data.message.SystemMessage.from(systemPrompt),
                    dev.langchain4j.data.message.UserMessage.from(cumulativePrompt)
                ).content().text();
            } else {
                reply = model.generate(cumulativePrompt);
            }
            System.out.println("[" + sessionId + "] 「" + label + "」(" + modelName + ")"
                    + " 回复长度: " + (reply != null ? reply.length() : 0));

            return reply;
        } catch (Exception e) {
            String errorMsg = classifyError(e);
            System.err.println("[" + sessionId + "] 「" + mc.getModelName() + "」调用失败: " + errorMsg);
            return "[" + mc.getModelName() + " 调用失败: " + errorMsg + "]";
        }
    }

    /** 流式调用单个模型 — 每收到一个 token 回调 onToken */
    private String callSingleModelStream(ModelConfig mc, String prompt, String systemPrompt,
                                          Consumer<String> onToken, String sessionId, String label) {
        String baseUrl = mc.getBaseUrl() != null && !mc.getBaseUrl().isBlank()
                ? mc.getBaseUrl() : "https://api.deepseek.com/v1";
        String apiKey = mc.getApiKeyEncrypted();
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("[流式] 「" + mc.getModelName() + "」未配置 API Key");
            return null;
        }
        String modelName = mc.getModelName() != null && !mc.getModelName().isBlank()
                ? mc.getModelName() : "deepseek-chat";
        int maxTokens = mc.getMaxTokens() != null ? mc.getMaxTokens() : 4096;

        try {
            OpenAiStreamingChatModel model = OpenAiStreamingChatModel.builder()
                    .baseUrl(baseUrl).apiKey(apiKey).modelName(modelName)
                    .temperature(0.7).maxTokens(maxTokens)
                    .timeout(Duration.ofSeconds(120)).build();

            CompletableFuture<String> future = new CompletableFuture<>();
            StringBuilder fullReply = new StringBuilder();

            StreamingResponseHandler<AiMessage> handler = new StreamingResponseHandler<>() {
                @Override
                public void onNext(String token) {
                    fullReply.append(token);
                    onToken.accept(token);
                }
                @Override
                public void onComplete(Response<AiMessage> response) {
                    future.complete(fullReply.toString());
                }
                @Override
                public void onError(Throwable error) {
                    future.completeExceptionally(error);
                }
            };

            if (systemPrompt != null && !systemPrompt.isBlank()) {
                model.generate(
                    List.of(dev.langchain4j.data.message.SystemMessage.from(systemPrompt),
                            dev.langchain4j.data.message.UserMessage.from(prompt)),
                    handler);
            } else {
                model.generate(prompt, handler);
            }

            String result = future.get(120, TimeUnit.SECONDS);
            System.out.println("[" + sessionId + "] 「流式」" + label + " 回复长度: " + result.length());
            return result;

        } catch (Exception e) {
            System.err.println("[" + sessionId + "] 「流式」" + label + " 失败: " + e.getMessage());
            return "[" + mc.getModelName() + " 调用失败: " + e.getMessage() + "]";
        }
    }

    /**
     * 流式多模型对话 — 每个模型的回复逐 token 输出。
     *
     * SSE 格式回调: onEvent(type, data)
     *    type: "start_model" / "token" / "end_model" / "done" / "error"
     *    data: displayName 或 token 文本 或 error 消息
     */
    public void chatStream(String userMessage, String sessionId,
                            java.util.function.BiConsumer<String, String> onEvent) {
        boolean dbSessionExists = false;
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString().substring(0, 8);
        } else {
            dbSessionExists = (sessionRepo.findBySessionName(sessionId) != null);
        }

        List<ModelConfig> enabledModels = modelConfigRepo.findAllByOrderByUpdatedAtDesc().stream()
                .filter(m -> m.getIsEnabled() != null && m.getIsEnabled() == 1)
                .sorted(Comparator.comparingInt(m -> m.getSortOrder() != null ? m.getSortOrder() : 0))
                .collect(Collectors.toList());

        if (enabledModels.isEmpty()) {
            onEvent.accept("error", "没有启用的模型配置");
            return;
        }

        // RAG
        List<Rag> enabledRags = ragRepo.findAllByOrderByCreatedAtDesc().stream()
                .filter(r -> r.getIsEnabled() != null && r.getIsEnabled() == 1)
                .collect(Collectors.toList());
        List<String> sources = new ArrayList<>();
        StringBuilder ragContext = buildRagContext(userMessage, enabledRags, sources);

        // 记忆
        MemoryConfig activeMemory = memoryRepo.findAllByOrderByUpdatedAtDesc().stream()
                .filter(m -> m.getIsEnabled() != null && m.getIsEnabled() == 1)
                .findFirst().orElse(null);
        List<Map<String, String>> history = getOrCreateHistory(sessionId);
        if (dbSessionExists && history.isEmpty()) loadHistoryFromDb(sessionId, history);
        List<Map<String, String>> ctxMsgs = applyMemoryStrategy(history, activeMemory);
        StringBuilder historyText = new StringBuilder();
        for (Map<String, String> msg : ctxMsgs) {
            historyText.append(msg.get("role")).append(": ").append(msg.get("content")).append("\n");
        }
        if (!historyText.isEmpty()) historyText.append("\n");

        history.add(Map.of("role", "user", "content", userMessage));

        // 对话记录
        StringBuilder conversationLog = new StringBuilder(historyText);
        conversationLog.append("user: ").append(userMessage).append("\n");

        // 依次流式调用每个模型
        final String finalSessionId = sessionId;
        for (int idx = 0; idx < enabledModels.size(); idx++) {
            ModelConfig mc = enabledModels.get(idx);
            String displayName = mc.getDisplayName() != null && !mc.getDisplayName().isBlank()
                    ? mc.getDisplayName() : mc.getModelName();

            onEvent.accept("start_model", displayName);
            System.out.println("[" + finalSessionId + "] 流式 → 「" + displayName + "」");

            String aiReply = callSingleModelStream(mc, conversationLog.toString(),
                    buildSystemPrompt(mc, ragContext.toString(), enabledModels),
                    token -> onEvent.accept("token", token),
                    finalSessionId, displayName);

            if (aiReply != null) {
                conversationLog.append(displayName).append(": ").append(aiReply).append("\n");
                history.add(Map.of("role", displayName, "content", aiReply));
            } else {
                conversationLog.append(displayName).append(": [调用失败，无回复]\n");
            }
            onEvent.accept("end_model", displayName);
        }

        onEvent.accept("done", finalSessionId);
        saveChatAsync(userMessage, sessionId, !dbSessionExists, history, activeMemory, enabledRags, enabledModels.get(0));
    }

    /** 异步持久化（与 chat 共享） */
    private void saveChatAsync(String userMessage, String sessionId, boolean isNew,
                                List<Map<String, String>> history, MemoryConfig mem,
                                List<Rag> rags, ModelConfig mc) {
        List<Map<String, String>> aiMsgs = history.stream()
                .filter(m -> !"user".equals(m.get("role"))).collect(Collectors.toList());
        String combinedAi = aiMsgs.stream()
                .map(m -> "[" + m.get("role") + "] " + m.get("content"))
                .collect(Collectors.joining("\n\n"));
        Rag firstRag = rags.isEmpty() ? null : rags.get(0);
        new Thread(() -> {
            try {
                Long dbId = persistenceService.saveSessionAndMessage(
                        sessionId, isNew, userMessage, combinedAi, mc, mem, firstRag);
                if (isNew && dbId != null) {
                    String title = generateTitleFromModel(mc, userMessage, combinedAi);
                    if (title != null) persistenceService.updateTitle(dbId, title);
                }
            } catch (Exception e) {
                System.err.println("[DB] 异步持久化异常: " + e.getMessage());
            }
        }, "db-persist-stream-" + sessionId).start();
    }

    // ==================== 历史记录 API ====================

    /** 获取所有会话列表 — 缓存 30s，写操作自动失效 */
    @Cacheable(value = "history_sessions", unless = "#result.isEmpty()")
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

    /**
     * 获取某会话的完整消息列表 — 缓存 30min
     */
    @Cacheable(value = "session_msgs", key = "#sessionId", unless = "#result.isEmpty()")
    public List<Map<String, Object>> getSessionMessages(String sessionId) {
        Session s = sessionRepo.findBySessionName(sessionId);
        if (s == null) return List.of();

        List<Message> msgs = messageRepo.findBySessionIdOrderByCreatedAt(s.getId());
        List<Map<String, Object>> result = new ArrayList<>();
        for (Message m : msgs) {
            Map<String, Object> user = new LinkedHashMap<>();
            user.put("role", "user");
            user.put("content", m.getUserMessage());
            user.put("timestamp", m.getCreatedAt() != null ? m.getCreatedAt().toString() : null);
            result.add(user);

            if (m.getAiResponse() != null && !m.getAiResponse().isEmpty()) {
                Map<String, Object> ai = new LinkedHashMap<>();
                ai.put("role", "assistant");
                ai.put("content", m.getAiResponse());
                ai.put("timestamp", m.getCreatedAt() != null ? m.getCreatedAt().toString() : null);
                result.add(ai);
            }
        }
        return result;
    }

    /** 删除会话及其全部消息 — 同时清除相关缓存 */
    @CacheEvict(value = {"history_sessions", "session_msgs"}, allEntries = true)
    @Transactional
    public void deleteSession(Long dbSessionId) {
        messageRepo.deleteBySessionId(dbSessionId);
        sessionRepo.deleteById(dbSessionId);
    }

    // ==================== 私有方法 ====================

    /** 构建 RAG 上下文 */
    private StringBuilder buildRagContext(String query, List<Rag> enabledRags, List<String> sources) {
        StringBuilder ragContext = new StringBuilder();
        if (enabledRags == null || enabledRags.isEmpty()) {
            return ragContext;
        }
        try {
            Set<Long> enabledRagIds = enabledRags.stream()
                    .map(Rag::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            List<Map<String, Object>> ragResults = ragService.searchRelevant(query, 5, enabledRagIds);
            if (!ragResults.isEmpty()) {
                sources.addAll(ragResults.stream()
                        .map(r -> (String) r.get("source")).distinct().collect(Collectors.toList()));
                ragContext.append("【参考知识库内容（向量检索）】\n");
                for (int i = 0; i < ragResults.size(); i++) {
                    Map<String, Object> item = ragResults.get(i);
                    ragContext.append("--- 文段 ").append(i + 1)
                              .append(" (来源: ").append(item.get("source"))
                              .append(", 相似度: ").append(String.format("%.2f", item.get("similarity")))
                              .append(") ---\n").append(item.get("content")).append("\n\n");
                }
            }
        } catch (Exception e) {
            System.err.println("[RAG] 检索异常, 回退: " + e.getMessage());
            sources.addAll(enabledRags.stream().map(Rag::getFilename).collect(Collectors.toList()));
            for (Rag rag : enabledRags) {
                if (rag.getContent() != null && !rag.getContent().isBlank()) {
                    String snip = rag.getContent().length() > 2000 ? rag.getContent().substring(0, 2000) + "..." : rag.getContent();
                    ragContext.append("【").append(rag.getFilename()).append("】\n").append(snip).append("\n");
                }
            }
        }
        return ragContext;
    }

    /** 从 DB 加载历史消息到内存 */
    private void loadHistoryFromDb(String sessionId, List<Map<String, String>> history) {
        Session dbSession = sessionRepo.findBySessionName(sessionId);
        if (dbSession != null) {
            List<Message> pastMsgs = messageRepo.findBySessionIdOrderByCreatedAt(dbSession.getId());
            for (Message m : pastMsgs) {
                history.add(Map.of("role", "user", "content", m.getUserMessage()));
                if (m.getAiResponse() != null && !m.getAiResponse().isEmpty()) {
                    history.add(Map.of("role", "assistant", "content", m.getAiResponse()));
                }
            }
            System.out.println("[记忆] 从 DB 加载 " + pastMsgs.size() + " 条消息");
        }
    }

    /** AI 生成标题（取第一个模型的实例来调 LLM） */
    private String generateTitleFromModel(ModelConfig mc, String userMsg, String aiReply) {
        try {
            String baseUrl = mc.getBaseUrl() != null && !mc.getBaseUrl().isBlank()
                    ? mc.getBaseUrl() : "https://api.deepseek.com/v1";
            String apiKey = mc.getApiKeyEncrypted();
            if (apiKey == null || apiKey.isBlank()) return null;

            OpenAiChatModel m = OpenAiChatModel.builder()
                    .baseUrl(baseUrl).apiKey(apiKey).modelName(
                        mc.getModelName() != null ? mc.getModelName() : "deepseek-chat")
                    .temperature(0.3).maxTokens(50).timeout(Duration.ofSeconds(30)).build();

            String su = userMsg.length() > 200 ? userMsg.substring(0, 200) : userMsg;
            String sa = aiReply != null && aiReply.length() > 200 ? aiReply.substring(0, 200) : aiReply;
            String prompt = "用不超过20字简短总结以下对话主题，只输出标题，不要引号:\n用户: " + su + "\n回复: " + sa;
            String title = m.generate(prompt);
            if (title != null) {
                title = title.trim().replaceAll("^[\"'「]|[\"'」]$", "");
                if (title.length() > 30) title = title.substring(0, 30);
                System.out.println("[标题] → " + title);
                return title;
            }
        } catch (Exception e) { System.err.println("[标题] 失败: " + e.getMessage()); }
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

    private String buildSystemPrompt(ModelConfig model, String ragContext,
                                       List<ModelConfig> allModels) {
        String displayName = model.getDisplayName() != null && !model.getDisplayName().isBlank()
                ? model.getDisplayName() : model.getModelName();
        StringBuilder sb = new StringBuilder();

        // 1. 模型自带的 Prompt
        if (model.getPrompt() != null && !model.getPrompt().isBlank()) sb.append(model.getPrompt());

        // 2. RAG 知识库上下文
        if (!ragContext.isEmpty()) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append("【参考知识库内容】\n").append(ragContext);
        }

        // 3. 多模型（≥2）时追加角色声明
        if (allModels != null && allModels.size() >= 2) {
            if (sb.length() > 0) sb.append("\n\n");

            // 列出所有参与者：用户 + 各个模型
            List<String> names = new ArrayList<>();
            names.add("用户");
            for (ModelConfig m : allModels) {
                String n = m.getDisplayName() != null && !m.getDisplayName().isBlank()
                        ? m.getDisplayName() : m.getModelName();
                names.add(n);
            }
            String participants = String.join("、", names);

            // 排序说明
            List<String> ordered = new ArrayList<>();
            for (int i = 0; i < allModels.size(); i++) {
                ModelConfig m = allModels.get(i);
                String n = m.getDisplayName() != null && !m.getDisplayName().isBlank()
                        ? m.getDisplayName() : m.getModelName();
                ordered.add((i + 1) + "." + n);
            }

            sb.append("【多角色对话声明】\n");
            sb.append("现在是多角色对话状态，共 ").append(1 + allModels.size())
              .append(" 个角色参与：").append(participants).append("。\n");
            sb.append("发言次序：用户提问 → ")
              .append(String.join(" → ", ordered)).append("。\n");
            sb.append("你就是「").append(displayName).append("」，只代表你自己一个人。\n");
            sb.append("不允许：模拟/替其他角色说话、替用户说话、替其他模型说话。\n");
            sb.append("仅允许：以「").append(displayName).append(": 内容」格式回复你自己的观点。\n");
            sb.append("请先阅读全部对话记录（包括其他模型已说的话），然后给出独立判断回复。");
        }

        return sb.toString();
    }

    private List<Map<String, String>> getOrCreateHistory(String sessionId) {
        return sessions.computeIfAbsent(sessionId, k -> new ArrayList<>());
    }

    private List<Map<String, String>> applyMemoryStrategy(List<Map<String, String>> history, MemoryConfig mem) {
        int ws = (mem != null && mem.getWindowSize() != null) ? mem.getWindowSize() : 10;
        int maxMsgs = ws * 2;
        if (history.size() <= maxMsgs) return new ArrayList<>(history);
        return new ArrayList<>(history.subList(history.size() - maxMsgs, history.size()));
    }
}
