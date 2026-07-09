package com.example.myllm.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "MemoryConfig")
@Data
public class MemoryConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(length = 20)
    private String strategyType = "sliding_window";

    private Integer windowSize = 10;

    private Integer summaryTriggerTokens = 2048;

    private Integer summaryMaxLength = 300;

    private Integer enableRag = 0;

    @Column(length = 255)
    private String ragCollectionName;

    private Integer ragTopK = 3;

    private Integer maxHistoryMessages = 50;

    private Integer enableLongTermMemory = 0;

    private Integer compressionInterval = 10;

    private Integer reserveSystemPrompt = 1;

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
