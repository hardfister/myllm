package com.example.myllm.service;

import com.example.myllm.model.entity.Rag;
import com.example.myllm.repository.RagRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG 知识库服务 — Ollama embedding + Chroma 向量存储
 * ---------------
 * 不依赖 Spring AI starter，直接调 REST API：
 *   Ollama:  POST /api/embeddings  (model: nomic-embed-text)
 *   Chroma:  REST API v2           (collections CRUD, query, upsert)
 *
 * 流水线：
 *   上传 → 提取文本 → 切片 → Ollama 向量化 → Chroma 存储
 *   检索 → 用户 query → Ollama 向量化 → Chroma 相似度搜索 → Top-K 文段
 */
@Service
public class RagService {

    private final RagRepository ragRepository;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // 配置
    private static final String OLLAMA_URL = "http://localhost:11434";
    private static final String EMBEDDING_MODEL = "nomic-embed-text";
    private static final String CHROMA_URL = "http://127.0.0.1:8000";
    private static final String COLLECTION_NAME = "my_knowledge_base";
    private static final String UPLOAD_DIR = "./uploads/";

    private boolean chromaReady = false;  // Chroma 集合是否已创建

    public RagService(RagRepository ragRepository) {
        this.ragRepository = ragRepository;
        ensureChromaCollection();
    }

    // ===================== CRUD =====================

    public List<Rag> getAllRags() {
        return ragRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * 上传文档 → 提取文本 → 切片 → Ollama embedding → Chroma 存储
     */
    @Transactional
    public Rag createRag(MultipartFile file, String collectionName,
                          Integer chunkSize, Integer chunkOverlap, String chunkMethod) throws IOException {
        // 1. 保存到磁盘
        Path uploadPath = Paths.get(UPLOAD_DIR);
        if (!Files.exists(uploadPath)) Files.createDirectories(uploadPath);
        String originalFilename = file.getOriginalFilename();
        String stored = UUID.randomUUID().toString().substring(0, 8)
                + "_" + (originalFilename != null ? originalFilename : "unknown");
        Path target = uploadPath.resolve(stored);
        file.transferTo(target.toFile());

        // 2. 提取文本 + 切片
        String text = extractText(target);
        int cs = (chunkSize != null && chunkSize > 0) ? chunkSize : 500;
        int co = (chunkOverlap != null && chunkOverlap >= 0) ? chunkOverlap : 50;
        String cm = (chunkMethod != null && !chunkMethod.isBlank()) ? chunkMethod : "fixed_size";
        List<String> chunks = chunkText(text, cs, co, cm);

        String collName = (collectionName != null && !collectionName.isBlank())
                ? collectionName : COLLECTION_NAME;
        String docId = stored; // 用存储文件名作唯一 ID

        // 3. 保存 MySQL 记录
        Rag rag = new Rag();
        rag.setFilename(originalFilename != null ? originalFilename : "unknown");
        rag.setFilePath(target.toAbsolutePath().toString());
        rag.setFileSize(file.getSize());
        rag.setFileType(file.getContentType());
        rag.setCollectionName(collName);
        rag.setChunkSize(cs); rag.setChunkOverlap(co); rag.setChunkMethod(cm);
        rag.setChunkCount(chunks.size());
        rag.setContent(String.join("\n---CHUNK---\n", chunks));
        rag.setStatus("embedding");
        rag = ragRepository.save(rag);

        // 4. 逐个切片: Ollama embedding → Chroma upsert
        try {
            for (int i = 0; i < chunks.size(); i++) {
                float[] vector = embed(chunks.get(i));
                if (vector != null) {
                    Map<String, Object> meta = Map.of(
                            "source", rag.getFilename(),
                            "chunk_index", i,
                            "rag_id", rag.getId(),
                            "text", chunks.get(i)
                    );
                    chromaUpsert(docId + "_chunk_" + i, vector, meta);
                }
            }
            rag.setStatus("completed");
            System.out.println("[RAG] 文档 " + docId + " 完成: " + chunks.size() + " 个切片 → Chroma");
        } catch (Exception e) {
            rag.setStatus("failed");
            System.err.println("[RAG] Chroma 写入失败: " + e.getMessage());
        }
        ragRepository.save(rag);
        return rag;
    }

    /** 更新文档元数据 + 切片配置（变更时重新向量化） */
    @Transactional
    public Rag updateRag(Long id, Rag updated) {
        Rag ex = ragRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("知识库文档不存在: " + id));

        if (updated.getFilename() != null) ex.setFilename(updated.getFilename());
        if (updated.getDescription() != null) ex.setDescription(updated.getDescription());
        if (updated.getCollectionName() != null) ex.setCollectionName(updated.getCollectionName());
        if (updated.getStatus() != null) ex.setStatus(updated.getStatus());
        if (updated.getIsEnabled() != null) ex.setIsEnabled(updated.getIsEnabled());
        if (updated.getChunkSize() != null) ex.setChunkSize(updated.getChunkSize());
        if (updated.getChunkOverlap() != null) ex.setChunkOverlap(updated.getChunkOverlap());
        if (updated.getChunkMethod() != null) ex.setChunkMethod(updated.getChunkMethod());

        boolean needRechunk = (updated.getChunkSize() != null || updated.getChunkMethod() != null);
        if (needRechunk && ex.getContent() != null && !ex.getContent().isBlank()) {
            String raw = ex.getContent().replace("---CHUNK---", "");
            int cs = ex.getChunkSize() != null ? ex.getChunkSize() : 500;
            int co = ex.getChunkOverlap() != null ? ex.getChunkOverlap() : 50;
            String cm = ex.getChunkMethod() != null ? ex.getChunkMethod() : "fixed_size";
            List<String> chunks = chunkText(raw, cs, co, cm);
            ex.setChunkCount(chunks.size());
            ex.setContent(String.join("\n---CHUNK---\n", chunks));
            // 重新向量化: 删除旧向量 + 写入新向量
            for (int i = 0; i < chunks.size(); i++) {
                float[] vec = embed(chunks.get(i));
                if (vec != null) {
                    Map<String, Object> meta = Map.of(
                            "source", ex.getFilename(), "chunk_index", i,
                            "rag_id", ex.getId(), "text", chunks.get(i));
                    chromaUpsert("rag_" + ex.getId() + "_chunk_" + i, vec, meta);
                }
            }
            ex.setStatus("completed");
        }
        return ragRepository.save(ex);
    }

    @Transactional
    public Rag toggleRag(Long id) {
        Rag t = ragRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("不存在: " + id));
        t.setIsEnabled(t.getIsEnabled() != null && t.getIsEnabled() == 1 ? 0 : 1);
        return ragRepository.save(t);
    }

    @Transactional
    public void deleteRag(Long id) {
        Rag rag = ragRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("不存在: " + id));
        if (rag.getFilePath() != null) {
            try { Files.deleteIfExists(Paths.get(rag.getFilePath())); }
            catch (IOException e) { /* ignore */ }
        }
        // Chroma 删除
        for (int i = 0; i < Math.max(rag.getChunkCount(), 100); i++) {
            chromaDelete("rag_" + id + "_chunk_" + i);
        }
        ragRepository.deleteById(id);
    }

    // ===================== RAG 检索（核心） =====================

    /**
     * 向量相似度检索 — 聊天时调用
     * 1. 用户 query → Ollama embedding
     * 2. 向量 → Chroma 相似度搜索
     * 3. 返回 Top-K 文段 + 来源
     */
    public List<Map<String, Object>> searchRelevant(String query, int topK) {
        if (query == null || query.isBlank()) return List.of();

        try {
            float[] queryVec = embed(query);
            if (queryVec == null) return List.of();

            List<Map<String, Object>> results = chromaQuery(queryVec, topK);
            return results.stream()
                    .filter(r -> r.get("content") != null)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("[RAG] 检索失败: " + e.getMessage());
            return List.of();
        }
    }

    // ===================== Ollama Embedding =====================

    /** 调用 Ollama /api/embeddings → 返回 768 维向量 */
    private float[] embed(String text) {
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "model", EMBEDDING_MODEL,
                    "prompt", text
            ));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(OLLAMA_URL + "/api/embeddings"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(30))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = mapper.readTree(resp.body());
                JsonNode arr = root.get("embedding");
                if (arr != null && arr.isArray()) {
                    float[] vec = new float[arr.size()];
                    for (int i = 0; i < arr.size(); i++) vec[i] = arr.get(i).floatValue();
                    return vec;
                }
            } else {
                System.err.println("[Ollama] embedding 返回 HTTP " + resp.statusCode());
            }
        } catch (Exception e) {
            System.err.println("[Ollama] embedding 失败: " + e.getMessage());
        }
        return null;
    }

    // ===================== Chroma REST API =====================

    /** 确保 Chroma 集合存在 */
    private void ensureChromaCollection() {
        try {
            // 尝试获取集合
            HttpRequest get = HttpRequest.newBuilder()
                    .uri(URI.create(CHROMA_URL + "/api/v2/collections/" + COLLECTION_NAME))
                    .GET().build();
            HttpResponse<String> resp = http.send(get, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                chromaReady = true;
                System.out.println("[Chroma] 集合 " + COLLECTION_NAME + " 已存在");
                return;
            }
        } catch (Exception ignored) {}

        // 不存在则创建
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "name", COLLECTION_NAME,
                    "metadata", Map.of("description", "MyLLM RAG collection")
            ));
            HttpRequest post = HttpRequest.newBuilder()
                    .uri(URI.create(CHROMA_URL + "/api/v2/collections"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> resp = http.send(post, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 || resp.statusCode() == 201) {
                chromaReady = true;
                System.out.println("[Chroma] 集合 " + COLLECTION_NAME + " 已创建");
            }
        } catch (Exception e) {
            System.err.println("[Chroma] 创建集合失败: " + e.getMessage());
        }
    }

    /** Chroma upsert: 插入或更新一条向量记录 */
    private void chromaUpsert(String id, float[] vector, Map<String, Object> metadata) {
        try {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("id", id);
            record.put("vector", vector);
            record.put("metadata", metadata);

            String body = mapper.writeValueAsString(Map.of("documents", List.of(record)));

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(CHROMA_URL + "/api/v2/collections/"
                            + COLLECTION_NAME + "/documents"))
                    .header("Content-Type", "application/json")
                    .method("POST", HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(10))
                    .build();
            http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            // 单个切片失败不阻断其他切片
        }
    }

    /** Chroma 查询: 向量相似度搜索 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> chromaQuery(float[] vector, int topK) {
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "query_embeddings", List.of(vector),
                    "n_results", topK,
                    "include", List.of("metadatas", "documents", "distances")
            ));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(CHROMA_URL + "/api/v2/collections/"
                            + COLLECTION_NAME + "/query"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(15))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 200) {
                JsonNode root = mapper.readTree(resp.body());
                JsonNode docs = root.get("documents");
                JsonNode metas = root.get("metadatas");
                JsonNode dists = root.get("distances");

                List<Map<String, Object>> results = new ArrayList<>();
                if (docs != null && docs.isArray() && !docs.isEmpty()) {
                    JsonNode arr = docs.get(0); // 第一个 query 的结果
                    for (int i = 0; i < arr.size(); i++) {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("content", arr.get(i).asText());
                        double dist = (dists != null && dists.get(0).size() > i)
                                ? dists.get(0).get(i).asDouble() : 0.0;
                        // Chroma distance → 相似度转换 (cosine)
                        item.put("similarity", Math.max(0, 1.0 - dist / 2.0));
                        if (metas != null && metas.get(0).size() > i) {
                            JsonNode meta = metas.get(0).get(i);
                            item.put("source", meta.has("source") ? meta.get("source").asText() : "unknown");
                        }
                        results.add(item);
                    }
                }
                return results;
            }
        } catch (Exception e) {
            System.err.println("[Chroma] 查询失败: " + e.getMessage());
        }
        return List.of();
    }

    /** Chroma 删除: 按 ID 删除向量 */
    private void chromaDelete(String id) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(CHROMA_URL + "/api/v2/collections/"
                            + COLLECTION_NAME + "/documents/" + id))
                    .DELETE()
                    .timeout(Duration.ofSeconds(5))
                    .build();
            http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception ignored) {}
    }

    // ===================== 文本提取 + 切片 =====================

    private String extractText(Path filePath) {
        try { return Files.readString(filePath, StandardCharsets.UTF_8); }
        catch (Exception e) {
            try { return new String(Files.readAllBytes(filePath), "GBK"); }
            catch (Exception e2) { return "[无法读取]"; }
        }
    }

    public static List<String> chunkText(String text, int size, int overlap, String method) {
        if (text == null || text.isBlank()) return List.of();
        return switch (method) {
            case "paragraph" -> chunkByParagraph(text, size);
            case "sentence"  -> chunkBySentence(text, size, overlap);
            default          -> chunkByFixedSize(text, size, overlap);
        };
    }

    private static List<String> chunkByFixedSize(String text, int size, int overlap) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + size, text.length());
            chunks.add(text.substring(start, end));
            start += (size - overlap);
            if (start >= text.length()) break;
        }
        return chunks;
    }

    private static List<String> chunkByParagraph(String text, int size) {
        List<String> chunks = new ArrayList<>();
        for (String p : text.split("\\n\\s*\\n")) {
            String t = p.trim(); if (t.isEmpty()) continue;
            if (t.length() <= size) chunks.add(t);
            else chunks.addAll(chunkByFixedSize(t, size, 0));
        }
        return chunks;
    }

    private static List<String> chunkBySentence(String text, int size, int overlap) {
        List<String> sentences = new ArrayList<>();
        String[] parts = text.split("(?<=[。！？.!?])\\s*");
        StringBuilder cur = new StringBuilder();
        for (String s : parts) {
            String t = s.trim(); if (t.isEmpty()) continue;
            if (cur.length() + t.length() > size && cur.length() > 0) {
                sentences.add(cur.toString().trim());
                cur = overlap > 0 && cur.length() > overlap
                        ? new StringBuilder(cur.substring(cur.length() - overlap))
                        : new StringBuilder();
            }
            cur.append(t);
        }
        if (!cur.isEmpty()) sentences.add(cur.toString().trim());
        return sentences.isEmpty() ? List.of(text) : sentences;
    }
}
