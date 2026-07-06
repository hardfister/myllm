package com.example.myllm.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 聊天响应 DTO
 *   reply      — AI 回复文本
 *   sessionId  — 本次对话的会话 ID（前端后续请求需带回）
 *   error      — 错误信息（连接失败 / 服务器超时等，成功时为 null）
 *   sources    — 本次使用的 RAG 知识库来源列表（文件名）
 *   modelUsed  — 实际调用的模型名称
 */
@Data
@AllArgsConstructor
public class ChatResponse {
    private String reply;
    private String sessionId;
    private String error;
    private List<String> sources;
    private String modelUsed;

    /** 成功响应 */
    public static ChatResponse ok(String reply, String sessionId, List<String> sources, String modelUsed) {
        return new ChatResponse(reply, sessionId, null, sources, modelUsed);
    }

    /** 错误响应 */
    public static ChatResponse error(String error, String sessionId) {
        return new ChatResponse(null, sessionId, error, null, null);
    }
}
