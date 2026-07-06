package com.example.myllm.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 聊天消息 JPA 实体 — 映射 myllm_db.sql 中的 Message 表
 * -------------------------------------------------
 * 每轮对话保存两条：一条 user_message（用户输入），一条 ai_response（AI 回复）。
 */
@Entity
@Table(name = "Message")
@Data
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属会话 ID（对应 Session 表的 id 字段） */
    @Column(name = "SessionId", nullable = false)
    private Long sessionId;

    /** 用户发送的消息文本 */
    @Column(name = "user_message", nullable = false, columnDefinition = "TEXT")
    private String userMessage;

    /** AI 返回的回复文本 */
    @Column(name = "ai_response", columnDefinition = "TEXT")
    private String aiResponse;

    /** 本次交互消耗的 Token 数（估算值） */
    @Column(name = "tokens_used")
    private Integer tokensUsed = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
