package com.example.myllm.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "user")
@Data
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(length = 100, unique = true)
    private String email;

    @Column(length = 20, unique = true)
    private String phone;

    @Column(length = 100)
    private String nickname;

    @Column(name = "avatar_path", length = 500)
    private String avatarPath;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    private Integer status = 1;

    @Column(length = 20)
    private String role = "user";

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "last_login_ip", length = 45)
    private String lastLoginIp;

    @Column(name = "login_count")
    private Integer loginCount = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (loginCount == null) loginCount = 0;
        if (status == null) status = 1;
        if (role == null) role = "user";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
