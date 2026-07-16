package com.example.myllm.controller;

import com.example.myllm.model.dto.ChatRequest;
import com.example.myllm.model.dto.ChatResponse;
import com.example.myllm.service.ChatService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

/**
 * 聊天 + 历史记录控制器
 * -----------
 * POST   /api/chat               — 发送消息（可选 sessionId，首次发送自动创建会话并持久化）
 * POST   /api/chat/new           — 新建空会话，返回 sessionId
 * GET    /api/sessions           — 获取全部历史会话列表
 * DELETE /api/chat/{sessionId}   — 清除内存中的会话历史
 * DELETE /api/sessions/{dbId}    — 从数据库删除会话及其全部消息（二次确认在客户端）
 */
@RestController
@CrossOrigin(origins = "*")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    // ===== 聊天接口 =====

    /** 发送消息，自动创建/复用会话，并持久化到 DB */
    @PostMapping("/api/chat")
    public ChatResponse handleChat(@RequestBody ChatRequest request) {
        String userMessage = request.getContent();
        if (userMessage == null || userMessage.isBlank()) {
            return ChatResponse.error("消息内容不能为空", request.getSessionId());
        }
        System.out.println("[CHAT] 收到消息 | session=" + request.getSessionId()
                + " | 内容=" + userMessage.substring(0, Math.min(50, userMessage.length())));
        return chatService.chat(userMessage, request.getSessionId(), request.getGlobalPrompt());
    }

    /** 流式聊天 — SSE 格式，逐 token 推送 */
    @PostMapping("/api/chat/stream")
    public void handleChatStream(@RequestBody ChatRequest request, HttpServletResponse response) throws Exception {
        String userMessage = request.getContent();
        if (userMessage == null || userMessage.isBlank()) {
            response.setStatus(400);
            response.getWriter().write("data: {\"error\":\"消息内容不能为空\"}\n\n");
            return;
        }

        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        PrintWriter out = response.getWriter();

        chatService.chatStream(userMessage, request.getSessionId(), request.getGlobalPrompt(),
                (type, data) -> {
                    try {
                        out.write("event: " + type + "\ndata: " + data.replace("\n", "\\n") + "\n\n");
                        out.flush();
                    } catch (Exception e) {
                        // 客户端断开连接
                    }
                });

        out.close();
    }

    /** 新建空会话，返回新 sessionId */
    @PostMapping("/api/chat/new")
    public ChatResponse newSession() {
        String sessionId = java.util.UUID.randomUUID().toString().substring(0, 8);
        return ChatResponse.ok("会话已创建", sessionId, null, null);
    }

    /** 清除内存中的会话 */
    @DeleteMapping("/api/chat/{sessionId}")
    public String clearSession(@PathVariable String sessionId) {
        chatService.clearSession(sessionId);
        return "会话已清除";
    }

    /** 手动重命名会话 */
    @PutMapping("/api/sessions/{sessionId}/rename")
    public Map<String, String> renameSession(@PathVariable String sessionId, @RequestBody Map<String, String> body) {
        String newTitle = body.get("title");
        chatService.renameSession(sessionId, newTitle);
        return Map.of("message", "ok");
    }

    /** AI 自动起名 — 把完整对话发给第一个启用的模型，返回 ≤20 字标题 */
    @PostMapping("/api/sessions/{sessionId}/ai-title")
    public Map<String, String> aiTitle(@PathVariable String sessionId) {
        String title = chatService.generateAiTitle(sessionId);
        return Map.of("title", title);
    }

    // ===== 历史记录接口 =====

    /** 获取全部历史会话（按最近活跃排序） */
    @GetMapping("/api/sessions")
    public List<Map<String, Object>> listSessions() {
        return chatService.listSessions();
    }

    /** 获取指定会话的全部消息（用于点击历史会话后加载对话记录） */
    @GetMapping("/api/sessions/{sessionId}/messages")
    public List<Map<String, Object>> getSessionMessages(@PathVariable String sessionId) {
        return chatService.getSessionMessages(sessionId);
    }

    /** 删除指定会话（含其下的全部消息），id 为 Session 表的自增主键 */
    @DeleteMapping("/api/sessions/{dbSessionId}")
    public Map<String, String> deleteSession(@PathVariable Long dbSessionId) {
        chatService.deleteSession(dbSessionId);
        return Map.of("message", "会话已删除");
    }
}
