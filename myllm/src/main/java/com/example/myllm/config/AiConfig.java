package com.example.myllm.config;

/**
 * AI 配置（占位）
 * ---------------
 * RAG 通过直接调 Ollama REST API (embedding) + Chroma REST API (向量存储) 实现，
 * 不依赖 Spring AI starter。详见 RagService.java。
 */
// 使用 Spring AI starter 时取消注释:
// import org.springframework.ai.embedding.EmbeddingModel;
// import org.springframework.ai.vectorstore.VectorStore;
// import org.springframework.context.annotation.Bean;
// import org.springframework.context.annotation.Configuration;
// @Configuration
// public class AiConfig { ... }
