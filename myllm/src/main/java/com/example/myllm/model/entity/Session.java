package com.example.myllm.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "session")
@Data
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_name", nullable = false, length = 255)
    private String sessionName;

    private String title;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "model_id")
    private Long modelId;

    @Column(name = "memory_id")
    private Long memoryId;

    @Column(name = "rag_id")
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
