package com.example.myllm.model.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "character_persona")
@Data // Lombok 自动生成 getter/setter
public class CharacterPersona {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name; // 角色名称，例如 "法律顾问"、"文案大师"

    @Column(columnDefinition = "TEXT")
    private String systemPrompt; // 核心人设 Prompt

    private Double temperature; // 创造力参数 (0.0 - 1.0)

    private String targetCollection; // 该角色绑定的 Chroma 知识库集合名称
}