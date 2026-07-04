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

    @Column(columnDefinition = "TEXT")
    private String filePath;

    private Long fileSize = 0L;

    @Column(length = 100)
    private String fileType;

    @Column(nullable = false, length = 255)
    private String collectionName;

    private Integer chunkCount = 0;

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
