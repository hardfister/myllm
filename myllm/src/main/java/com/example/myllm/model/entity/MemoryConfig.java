package com.example.myllm.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "memory_config")
@Data
public class MemoryConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "strategy_type", length = 20)
    private String strategyType = "sliding_window";

    @Column(name = "window_size")
    private Integer windowSize = 10;

    @Column(name = "summary_trigger_tokens")
    private Integer summaryTriggerTokens = 2048;

    @Column(name = "summary_max_length")
    private Integer summaryMaxLength = 300;

    @Column(name = "enable_rag")
    private Integer enableRag = 0;

    @Column(name = "rag_collection_name", length = 255)
    private String ragCollectionName;

    @Column(name = "rag_top_k")
    private Integer ragTopK = 3;

    @Column(name = "max_history_messages")
    private Integer maxHistoryMessages = 50;

    @Column(name = "enable_long_term_memory")
    private Integer enableLongTermMemory = 0;

    @Column(name = "compression_interval")
    private Integer compressionInterval = 10;

    @Column(name = "reserve_system_prompt")
    private Integer reserveSystemPrompt = 1;

    @Column(name = "is_enabled")
    private Integer isEnabled = 1;

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
