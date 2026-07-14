package com.example.myllm.service;

import com.example.myllm.model.entity.ModelConfig;
import com.example.myllm.model.entity.Rag;
import com.example.myllm.repository.ModelConfigRepository;
import com.example.myllm.repository.RagRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
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
 * RAG 知识库服务 — 上传 + 按需向量化 + Chroma 检索
 * -------------------------------------------------
 * 上传：文件 → 磁盘 + 提取文本 + 切片 → MySQL (status=pending)
 * 向量化：用户选择嵌入模型 → 调 OpenAI 兼容 API 生成向量 → Chroma upsert (status=completed)
 * 检索：用文档关联的嵌入模型生成 query 向量 → Chroma 相似度搜索
 *
 * 嵌入模型由用户在知识库页面下半部分自行配置（复用 ModelList 逻辑）。
 */
@Service
public class RagService {

    private final RagRepository ragRepository;
    private final ModelConfigRepository modelConfigRepo;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    private static final String CHROMA_URL = "http://127.0.0.1:8000";
    private static final String COLLECTION_NAME = "my_knowledge_base";
    private static final String UPLOAD_DIR = "./uploads/";

    public RagService(RagRepository ragRepository,
                      ModelConfigRepository modelConfigRepo) {
        this.ragRepository = ragRepository;
        this.modelConfigRepo = modelConfigRepo;
        ensureChromaCollection();
    }

    // ===================== CRUD =====================

    @Cacheable(value = "rag_list", unless = "#result.isEmpty()")
    public List<Rag> getAllRags() {
        return ragRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * 上传文档 → 保存文件到磁盘 + MySQL（status=uploaded），不做任何文本处理。
     * 切片和向量化由后续的 embed 步骤完成。
     */
    @CacheEvict(value = {"rag_list", "rag_search"}, allEntries = true)
    @Transactional
    public Rag createRag(MultipartFile file, String collectionName,
                          Integer chunkSize, Integer chunkOverlap, String chunkMethod) throws IOException {
        Path uploadPath = Paths.get(UPLOAD_DIR);
        if (!Files.exists(uploadPath)) Files.createDirectories(uploadPath);
        String originalFilename = file.getOriginalFilename();
        String stored = UUID.randomUUID().toString().substring(0, 8)
                + "_" + (originalFilename != null ? originalFilename : "unknown");
        Path target = uploadPath.resolve(stored);
        file.transferTo(target.toFile());

        String collName = (collectionName != null && !collectionName.isBlank())
                ? collectionName : COLLECTION_NAME;
        int cs = (chunkSize != null && chunkSize > 0) ? chunkSize : 500;
        int co = (chunkOverlap != null && chunkOverlap >= 0) ? chunkOverlap : 50;
        String cm = (chunkMethod != null && !chunkMethod.isBlank()) ? chunkMethod : "fixed_size";

        Rag rag = new Rag();
        rag.setFilename(originalFilename != null ? originalFilename : "unknown");
        rag.setFilePath(target.toAbsolutePath().toString());
        rag.setFileSize(file.getSize());
        rag.setFileType(file.getContentType());
        rag.setCollectionName(collName);
        rag.setChunkSize(cs); rag.setChunkOverlap(co); rag.setChunkMethod(cm);
        rag.setChunkCount(0);
        rag.setContent(null);
        rag.setStatus("uploaded");  // 仅保存，待后续切片+向量化

        System.out.println("[RAG] 文件已保存: " + originalFilename + " (" + rag.getFileSize() + " bytes)");
        return ragRepository.save(rag);
    }

    /**
     * 向量化指定文档 — 现在包括提取文本+切片+向量化三步
     * @param ragId   文档 ID
     * @param modelId 嵌入模型 ID
     */
    @CacheEvict(value = {"rag_list", "rag_search"}, allEntries = true)
    @Transactional
    public Rag embedRag(Long ragId, Long modelId) {
        Rag rag = ragRepository.findById(ragId)
                .orElseThrow(() -> new RuntimeException("文档不存在: " + ragId));
        ModelConfig embeddingModel = modelConfigRepo.findById(modelId)
                .orElseThrow(() -> new RuntimeException("嵌入模型不存在: " + modelId));

        // 1. 如果还没切片，从磁盘文件提取文本并切片
        if (rag.getContent() == null || rag.getContent().isBlank()) {
            if (rag.getFilePath() == null) {
                rag.setStatus("failed");
                return ragRepository.save(rag);
            }
            Path filePath = Paths.get(rag.getFilePath());
            if (!Files.exists(filePath)) {
                rag.setStatus("failed");
                return ragRepository.save(rag);
            }
            rag.setStatus("chunking");
            try {
                String text = extractText(filePath);
                int cs = rag.getChunkSize() != null ? rag.getChunkSize() : 500;
                int co = rag.getChunkOverlap() != null ? rag.getChunkOverlap() : 50;
                String cm = rag.getChunkMethod() != null ? rag.getChunkMethod() : "fixed_size";
                List<String> chunks = chunkText(text, cs, co, cm);
                rag.setChunkCount(chunks.size());
                rag.setContent(String.join("---CHUNK---", chunks));
                System.out.println("[RAG] 切片完成: " + rag.getFilename() + " → " + chunks.size() + " 片");
            } catch (Exception e) {
                rag.setStatus("failed");
                System.err.println("[RAG] 切片失败: " + e.getMessage());
                return ragRepository.save(rag);
            }
        }

        // 2. 向量化
        rag.setStatus("embedding");
        rag.setEmbeddingModelId(modelId);
        ragRepository.save(rag);

        try {
            String[] chunks = rag.getContent().split("---CHUNK---");
            int successCount = 0;
            for (int i = 0; i < chunks.length; i++) {
                String chunk = chunks[i].trim();
                if (chunk.isEmpty()) continue;
                float[] vector = embedWithModel(chunk, embeddingModel);
                if (vector != null) {
                    chromaUpsert("rag_" + ragId + "_chunk_" + i, vector, Map.of(
                            "source", rag.getFilename(), "chunk_index", i,
                            "rag_id", ragId, "text", chunk));
                    successCount++;
                }
            }
            rag.setStatus("completed");
            System.out.println("[RAG] 向量化完成: doc=" + rag.getFilename()
                    + " 切片=" + successCount + "/" + chunks.length
                    + " 模型=" + embeddingModel.getModelName());
        } catch (Exception e) {
            rag.setStatus("failed");
            System.err.println("[RAG] 向量化失败: " + e.getMessage());
        }
        return ragRepository.save(rag);
    }

    /** 更新文档元数据 + 切片配置 */
    @CacheEvict(value = {"rag_list", "rag_search"}, allEntries = true)
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
        if (needRechunk) {
            // 切片参数变更 → 清除旧切片数据 → 下次向量化时重新从文件提取
            ex.setContent(null);
            ex.setChunkCount(0);
            ex.setStatus("uploaded");  // 回退到未切片状态，需重新向量化
        }
        return ragRepository.save(ex);
    }

    @CacheEvict(value = {"rag_list", "rag_search"}, allEntries = true)
    @Transactional
    public Rag toggleRag(Long id) {
        Rag t = ragRepository.findById(id).orElseThrow(() -> new RuntimeException("不存在: " + id));
        t.setIsEnabled(t.getIsEnabled() != null && t.getIsEnabled() == 1 ? 0 : 1);
        return ragRepository.save(t);
    }

    @CacheEvict(value = {"rag_list", "rag_search"}, allEntries = true)
    @Transactional
    public void deleteRag(Long id) {
        Rag rag = ragRepository.findById(id).orElseThrow(() -> new RuntimeException("不存在: " + id));
        if (rag.getFilePath() != null) {
            try { Files.deleteIfExists(Paths.get(rag.getFilePath())); }
            catch (IOException e) { /* ignore */ }
        }
        for (int i = 0; i < Math.max(rag.getChunkCount(), 100); i++) {
            chromaDelete("rag_" + id + "_chunk_" + i);
        }
        ragRepository.deleteById(id);
    }

    // ===================== 向量检索 =====================

    /**
     * 检索相关文段。用该文档关联的嵌入模型来生成 query 向量。
     * 如果没有文档被向量化过（都没有 embeddingModelId），回退到查所有 ModelConfig
     * 中第一个可用的来生成向量。
     */
    public List<Map<String, Object>> searchRelevant(String query, int topK) {
        return searchRelevant(query, topK, Set.of());
    }

    public List<Map<String, Object>> searchRelevant(String query, int topK, Set<Long> allowedRagIds) {
        if (query == null || query.isBlank()) return List.of();
        try {
            // 找任意一个已向量化的文档，用它的 embedding model
            ModelConfig embModel = findEmbeddingModel();
            if (embModel == null) {
                System.err.println("[RAG] 没有可用的嵌入模型配置，跳过检索");
                return List.of();
            }
            float[] queryVec = embedWithModel(query, embModel);
            if (queryVec == null) return List.of();
            return chromaQuery(queryVec, topK).stream()
                    .filter(r -> r.get("content") != null)
                    .filter(r -> allowedRagIds == null || allowedRagIds.isEmpty()
                            || (r.get("ragId") instanceof Long ragId && allowedRagIds.contains(ragId)))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("[RAG] 检索失败: " + e.getMessage());
            return List.of();
        }
    }

    /** 查找嵌入模型：优先取已完成向量化的文档关联的模型，否则取第一个 ModelConfig */
    private ModelConfig findEmbeddingModel() {
        List<Rag> all = ragRepository.findAll();
        for (Rag r : all) {
            if (r.getEmbeddingModelId() != null && r.getStatus() != null
                    && r.getStatus().equals("completed")) {
                return modelConfigRepo.findById(r.getEmbeddingModelId()).orElse(null);
            }
        }
        // 兜底：取第一个已配置 apiKey 的模型
        List<ModelConfig> models = modelConfigRepo.findAll();
        for (ModelConfig m : models) {
            if (m.getApiKeyEncrypted() != null && !m.getApiKeyEncrypted().isBlank()) {
                return m;
            }
        }
        return null;
    }

    // ===================== 通用 Embedding（OpenAI 兼容 API） =====================

    /**
     * 用指定模型配置调用嵌入 API（OpenAI 兼容格式）。
     * POST {baseUrl}/embeddings  body: { model, input }
     */
    private float[] embedWithModel(String text, ModelConfig mc) {
        try {
            String url = mc.getBaseUrl();
            if (url == null || url.isBlank()) url = "https://api.openai.com/v1";
            if (!url.endsWith("/")) url += "/";
            url += "embeddings";

            String body = mapper.writeValueAsString(Map.of(
                    "model", mc.getModelName() != null ? mc.getModelName() : "text-embedding-ada-002",
                    "input", text
            ));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + mc.getApiKeyEncrypted())
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(30))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = mapper.readTree(resp.body());
                // OpenAI 格式: { data: [{ embedding: [...] }] }
                // Ollama 格式: { embedding: [...] }
                JsonNode arr;
                if (root.has("data")) {
                    arr = root.get("data").get(0).get("embedding");
                } else {
                    arr = root.get("embedding");
                }
                if (arr != null && arr.isArray()) {
                    float[] vec = new float[arr.size()];
                    for (int i = 0; i < arr.size(); i++) vec[i] = arr.get(i).floatValue();
                    return vec;
                }
            } else {
                System.err.println("[Embed] HTTP " + resp.statusCode() + ": " + resp.body().substring(0, Math.min(200, resp.body().length())));
            }
        } catch (Exception e) {
            System.err.println("[Embed] 失败: " + e.getMessage());
        }
        return null;
    }

    // ===================== Chroma REST API =====================

    private void ensureChromaCollection() {
        try {
            HttpRequest get = HttpRequest.newBuilder()
                    .uri(URI.create(CHROMA_URL + "/api/v2/collections/" + COLLECTION_NAME)).GET().build();
            HttpResponse<String> resp = http.send(get, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                System.out.println("[Chroma] 集合 " + COLLECTION_NAME + " 已存在");
                return;
            }
        } catch (Exception ignored) {}
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "name", COLLECTION_NAME, "metadata", Map.of("description", "MyLLM RAG")));
            HttpRequest post = HttpRequest.newBuilder()
                    .uri(URI.create(CHROMA_URL + "/api/v2/collections"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(10)).build();
            http.send(post, HttpResponse.BodyHandlers.ofString());
            System.out.println("[Chroma] 集合 " + COLLECTION_NAME + " 已创建");
        } catch (Exception e) {
            System.err.println("[Chroma] 创建集合失败: " + e.getMessage());
        }
    }

    private void chromaUpsert(String id, float[] vector, Map<String, Object> metadata) {
        try {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("id", id); record.put("vector", vector); record.put("metadata", metadata);
            String body = mapper.writeValueAsString(Map.of("documents", List.of(record)));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(CHROMA_URL + "/api/v2/collections/" + COLLECTION_NAME + "/documents"))
                    .header("Content-Type", "application/json")
                    .method("POST", HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(10)).build();
            http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception ignored) {}
    }

    private List<Map<String, Object>> chromaQuery(float[] vector, int topK) {
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "query_embeddings", List.of(vector), "n_results", topK,
                    "include", List.of("metadatas", "documents", "distances")));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(CHROMA_URL + "/api/v2/collections/" + COLLECTION_NAME + "/query"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(15)).build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = mapper.readTree(resp.body());
                JsonNode docs = root.get("documents"), metas = root.get("metadatas"), dists = root.get("distances");
                List<Map<String, Object>> results = new ArrayList<>();
                if (docs != null && docs.isArray() && !docs.isEmpty()) {
                    JsonNode arr = docs.get(0);
                    for (int i = 0; i < arr.size(); i++) {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("content", arr.get(i).asText());
                        double dist = (dists != null && dists.get(0).size() > i) ? dists.get(0).get(i).asDouble() : 0;
                        item.put("similarity", Math.max(0, 1.0 - dist / 2.0));
                        if (metas != null && metas.get(0).size() > i) {
                            JsonNode meta = metas.get(0).get(i);
                            if (meta.has("source")) item.put("source", meta.get("source").asText());
                            if (meta.has("rag_id")) item.put("ragId", meta.get("rag_id").asLong());
                        }
                        results.add(item);
                    }
                }
                return results;
            }
        } catch (Exception e) { System.err.println("[Chroma] 查询失败: " + e.getMessage()); }
        return List.of();
    }

    private void chromaDelete(String id) {
        try {
            http.send(HttpRequest.newBuilder()
                    .uri(URI.create(CHROMA_URL + "/api/v2/collections/" + COLLECTION_NAME + "/documents/" + id))
                    .DELETE().timeout(Duration.ofSeconds(5)).build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (Exception ignored) {}
    }

    // ===================== 文本提取 + 切片 =====================

    private String extractText(Path filePath) {
        try { return Files.readString(filePath, StandardCharsets.UTF_8); }
        catch (Exception e) { try { return new String(Files.readAllBytes(filePath), "GBK"); } catch (Exception e2) { return "[无法读取]"; } }
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
        List<String> c = new ArrayList<>(); int s = 0;
        int step = Math.max(1, size - Math.max(0, Math.min(overlap, size - 1)));
        while (s < text.length()) { c.add(text.substring(s, Math.min(s + size, text.length()))); s += step; if (s >= text.length()) break; }
        return c;
    }
    private static List<String> chunkByParagraph(String text, int size) {
        List<String> c = new ArrayList<>();
        for (String p : text.split("\\n\\s*\\n")) { String t = p.trim(); if (t.isEmpty()) continue; if (t.length() <= size) c.add(t); else c.addAll(chunkByFixedSize(t, size, 0)); }
        return c;
    }
    private static List<String> chunkBySentence(String text, int size, int overlap) {
        List<String> sents = new ArrayList<>(); StringBuilder cur = new StringBuilder();
        for (String s : text.split("(?<=[。！？.!?])\\s*")) { String t = s.trim(); if (t.isEmpty()) continue;
            if (cur.length() + t.length() > size && cur.length() > 0) { sents.add(cur.toString().trim()); cur = overlap > 0 && cur.length() > overlap ? new StringBuilder(cur.substring(cur.length() - overlap)) : new StringBuilder(); }
            cur.append(t); }
        if (!cur.isEmpty()) sents.add(cur.toString().trim());
        return sents.isEmpty() ? List.of(text) : sents;
    }
}
