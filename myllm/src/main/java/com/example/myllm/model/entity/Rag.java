package com.example.myllm.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

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

    @Column(name = "file_path", columnDefinition = "TEXT")
    private String filePath;

    @Column(name = "file_size")
    private Long fileSize = 0L;

    @Column(name = "file_type", length = 100)
    private String fileType;

    @Column(name = "collection_name", nullable = false, length = 255)
    private String collectionName;

    @Column(name = "chunk_count")
    private Integer chunkCount = 0;

    @Column(name = "chunk_size")
    private Integer chunkSize = 500;

    @Column(name = "chunk_overlap")
    private Integer chunkOverlap = 50;

    @Column(name = "chunk_method", length = 20)
    private String chunkMethod = "fixed_size";

    @Column(columnDefinition = "MEDIUMTEXT")
    private String content;

    @Column(length = 20)
    private String status = "processing";

    @Column(length = 500)
    private String description;

    @Column(name = "is_enabled")
    private Integer isEnabled = 1;

    /** 使用的嵌入模型 ID（NULL=未向量化，关联 ModelConfig 表） */
    @Column(name = "embedding_model_id")
    private Long embeddingModelId;

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
