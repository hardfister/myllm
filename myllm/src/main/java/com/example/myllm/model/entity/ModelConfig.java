package com.example.myllm.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "ModelConfig")
@Data
public class ModelConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "UserId")
    private Long userId;

    @Column(nullable = false, length = 100)
    private String modelName;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(columnDefinition = "TEXT")
    private String apiKeyEncrypted;

    @Column(length = 500)
    private String baseUrl;

    private Integer maxTokens = 4096;

    @Column(columnDefinition = "TEXT")
    private String prompt;

    private Integer isEnabled = 1;

    /** 用户给模型取的显示名，用于对话中标注"谁在说话" */
    @Column(length = 100)
    private String displayName;

    /** 排序序号，多模型对话时按此顺序依次调用 */
    private Integer sortOrder = 0;

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
