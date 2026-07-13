package com.example.myllm.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "model_config")
@Data
public class ModelConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(name = "api_key_encrypted", columnDefinition = "TEXT")
    private String apiKeyEncrypted;

    @Column(name = "base_url", length = 500)
    private String baseUrl;

    @Column(name = "max_tokens")
    private Integer maxTokens = 4096;

    @Column(columnDefinition = "TEXT")
    private String prompt;

    @Column(name = "is_enabled")
    private Integer isEnabled = 1;

    /** 用户给模型取的显示名，用于对话中标注"谁在说话" */
    @Column(name = "display_name", length = 100)
    private String displayName;

    /** 排序序号，多模型对话时按此顺序依次调用 */
    @Column(name = "sort_order")
    private Integer sortOrder = 0;

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
