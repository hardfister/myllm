package com.example.myllm.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 知识库文档 JPA 实体
 * ---------------
 * 新增自定义切片字段：
 *   chunkSize    — 切片大小（字符数，默认 500）
 *   chunkOverlap — 切片重叠（字符数，默认 50）
 *   chunkMethod  — 切片方式：fixed_size / paragraph / sentence
 *   content      — 提取的纯文本内容（TEXT 列，用于聊天时注入上下文）
 *   collectionName — 向量集合名（用户自定义，用于区分不同知识库）
 */
@Entity
@Table(name = "Rag")
@Data
public class Rag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "UserId")
    private Long userId;

    @Column(nullable = false, length = 500)
    private String filename;

    @Column(columnDefinition = "TEXT")
    private String filePath;

    private Long fileSize = 0L;

    @Column(length = 100)
    private String fileType;

    @Column(nullable = false, length = 255)
    private String collectionName;

    private Integer chunkCount = 0;

    /** 切片大小（字符数），默认 500 */
    private Integer chunkSize = 500;

    /** 切片重叠（字符数），默认 50 */
    private Integer chunkOverlap = 50;

    /** 切片方式：fixed_size / paragraph / sentence */
    @Column(length = 20)
    private String chunkMethod = "fixed_size";

    /** 提取的文档纯文本内容，注入 LLM 上下文 */
    @Column(columnDefinition = "MEDIUMTEXT")
    private String content;

    @Column(length = 20)
    private String status = "processing";

    @Column(length = 500)
    private String description;

    private Integer isEnabled = 1;

    private LocalDateTime createdAt;

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
