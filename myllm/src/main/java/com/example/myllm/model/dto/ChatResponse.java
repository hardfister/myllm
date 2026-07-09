package com.example.myllm.model.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 聊天响应 DTO — 支持单模型和多模型（顺序接力）
 *
 * 单模型: reply + modelUsed
 * 多模型: replies = [{model: "GPT-4", displayName: "翻译官", content: "..."}, ...]
 */
@Data
@NoArgsConstructor
public class ChatResponse {
    private String reply;
    private String sessionId;
    private String error;
    private List<String> sources;
    private String modelUsed;
    /** 多模型模式下每条模型的独立回复 */
    private List<Map<String, String>> replies;

    public static ChatResponse ok(String reply, String sessionId, List<String> sources, String modelUsed) {
        ChatResponse r = new ChatResponse();
        r.reply = reply;
        r.sessionId = sessionId;
        r.sources = sources;
        r.modelUsed = modelUsed;
        return r;
    }

    public static ChatResponse okMulti(List<Map<String, String>> replies, String sessionId, List<String> sources) {
        ChatResponse r = new ChatResponse();
        r.replies = replies;
        r.sessionId = sessionId;
        r.sources = sources;
        if (!replies.isEmpty()) r.reply = replies.get(replies.size() - 1).get("content");
        return r;
    }

    public static ChatResponse error(String error, String sessionId) {
        ChatResponse r = new ChatResponse();
        r.error = error;
        r.sessionId = sessionId;
        return r;
    }
}
