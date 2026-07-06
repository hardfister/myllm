package com.example.myllm.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 聊天会话 JPA 实体 — 映射 myllm_db.sql 中的 Session 表
 * -------------------------------------------------
 * 一个会话包含多轮对话消息（Message），关联启用的模型/记忆/RAG 配置。
 * sessionName 取自用户发送的第一条消息的前 50 个字符。
 */
@Entity
@Table(name = "Session")
@Data
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 会话唯一标识（短 UUID），前端用此 ID 关联消息和路由 */
    @Column(name = "session_name", nullable = false, length = 255)
    private String sessionName;

    /** 会话显示标题（首条消息截取前 50 字），用于历史记录面板展示 */
    @Column(length = 100)
    private String title;

    @Column(name = "UserId")
    private Long userId;

    @Column(name = "ModelId")
    private Long modelId;

    @Column(name = "MemoryId")
    private Long memoryId;

    @Column(name = "RagId")
    private Long ragId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
