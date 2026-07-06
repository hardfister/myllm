package com.example.myllm.model.dto;

import lombok.Data;

/**
 * 聊天请求 DTO
 *   content   — 用户输入的消息文本
 *   sessionId — 会话 ID（可选，首次为空则由后端创建新会话）
 */
@Data
public class ChatRequest {
    private String content;
    private String sessionId;

    public ChatRequest() {}
}
