package com.example.myllm.service;

import com.example.myllm.model.dto.RagErrorDetail;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class RagService {

    private final RagRepository ragRepository;
    private final ModelConfigRepository modelConfigRepo;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    private static final String CHROMA_URL = "http://127.0.0.1:8000";
    private static final String CHROMA_API = "/api/v2/tenants/default_tenant/databases/default_database";
    private static final String DEFAULT_COLLECTION = "myllm_rag_v2";

    /** 缓存 collectionName → UUID */
    private final Map<String, String> collectionIdCache = new ConcurrentHashMap<>();

    public RagService(RagRepository ragRepository, ModelConfigRepository modelConfigRepo) {
        this.ragRepository = ragRepository;
        this.modelConfigRepo = modelConfigRepo;
        // 启动时诊断 Chroma 连接
        new Thread(() -> {
            try {
                Thread.sleep(3000);
                String testColl = DEFAULT_COLLECTION;
                System.out.println("[Chroma DIAG] 跳过诊断测试 (避免锁定 collection 维度)");
            } catch (Exception e) {
                System.err.println("[Chroma DIAG] ❌ 读写测试失败: " + e.getMessage());
            }
        }, "chroma-diag").start();
    }

    // ===================== 每个用户的独立 Chroma Collection =====================

    private String userCollection(Long userId) {
        // 统一使用单个集合，简化管理
        return DEFAULT_COLLECTION;
    }

    private String chromaPath(String suffix) {
        return CHROMA_URL + CHROMA_API + suffix;
    }

    /** 通过名称获取集合的 UUID（v2 写操作需要 UUID 而不是名称） */
    private String getCollectionId(String collectionName) {
        if (collectionIdCache.containsKey(collectionName)) return collectionIdCache.get(collectionName);
        // v2: GET /api/v2/collections/{name} 返回 {id, name, ...}
        try {
            HttpResponse<String> resp = http.send(HttpRequest.newBuilder()
                .uri(URI.create(chromaPath("/collections/" + collectionName))).GET().build(),
                HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = mapper.readTree(resp.body());
                if (root.has("id")) {
                    String uuid = root.get("id").asText();
                    collectionIdCache.put(collectionName, uuid);
                    System.out.println("[Chroma] " + collectionName + " UUID=" + uuid);
                    return uuid;
                }
            }
        } catch (Exception ignored) {}
        // 不存在则创建
        try {
            String body = mapper.writeValueAsString(Map.of("name", collectionName));
            HttpResponse<String> resp = http.send(HttpRequest.newBuilder()
                .uri(URI.create(chromaPath("/collections")))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 || resp.statusCode() == 201) {
                JsonNode root = mapper.readTree(resp.body());
                String uuid = root.get("id").asText();
                collectionIdCache.put(collectionName, uuid);
                System.out.println("[Chroma] " + collectionName + " created UUID=" + uuid);
                return uuid;
            }
        } catch (Exception e) {
            System.err.println("[Chroma] create failed: " + e.getMessage());
        }
        // Fallback: use name as ID (won't work for writes but allows reads to proceed)
        System.out.println("[Chroma] WARN: could not resolve UUID for " + collectionName + ", using name");
        collectionIdCache.put(collectionName, collectionName);
        return collectionName;
    }

    // ===================== CRUD =====================

    @Cacheable(value = "rag_list", unless = "#result.isEmpty()")
    public List<Rag> getAllRags() {
        return ragRepository.findAllByOrderByCreatedAtDesc();
    }

    /** 上传 → 只保存文件 */
    @CacheEvict(value = {"rag_list", "rag_search"}, allEntries = true)
    @Transactional
    public Rag createRag(MultipartFile file, String collectionName,
                          Integer chunkSize, Integer chunkOverlap, String chunkMethod) throws IOException {
        Path uploadPath = Paths.get(System.getProperty("user.dir"), "uploads");
        if (!Files.exists(uploadPath)) Files.createDirectories(uploadPath);

        String originalFilename = file.getOriginalFilename();
        String stored = UUID.randomUUID().toString().substring(0, 8)
                + "_" + (originalFilename != null ? originalFilename : "unknown");
        Path target = uploadPath.resolve(stored);
        Files.copy(file.getInputStream(), target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        Rag rag = new Rag();
        rag.setFilename(originalFilename != null ? originalFilename : "unknown");
        rag.setFilePath(target.toAbsolutePath().toString());
        rag.setFileSize(file.getSize());
        rag.setFileType(file.getContentType());
        rag.setCollectionName(collectionName != null ? collectionName : "default");
        rag.setChunkSize(chunkSize != null && chunkSize > 0 ? chunkSize : 500);
        rag.setChunkOverlap(chunkOverlap != null ? chunkOverlap : 50);
        rag.setChunkMethod(chunkMethod != null ? chunkMethod : "fixed_size");
        rag.setChunkCount(0);
        rag.setContent(null);
        rag.setStatus("uploaded");

        System.out.println("[RAG] " + originalFilename + " 已保存 → " + target);
        return ragRepository.save(rag);
    }

    // ===================== 核心：切片 + 向量化（详细错误汇报） =====================

    /**
     * 完整流水线：读取文件 → 提取文本 → 切片 → Embedding → Chroma upsert
     * 每一步的错误独立汇报，存入 Rag.errorDetail JSON 字段。
     */
    @CacheEvict(value = {"rag_list", "rag_search"}, allEntries = true)
    @Transactional
    public Rag embedRag(Long ragId, Long modelId) {
        Rag rag = ragRepository.findById(ragId)
                .orElseThrow(() -> new RuntimeException("文档不存在: " + ragId));
        ModelConfig embModel = modelConfigRepo.findById(modelId)
                .orElseThrow(() -> new RuntimeException("嵌入模型不存在: " + modelId));

        RagErrorDetail errorDetail = new RagErrorDetail();
        String collName = userCollection(rag.getUserId());
        getCollectionId(collName);

        // ─── 步骤 1: 读文件 ───
        errorDetail.step = "read_file";
        if (rag.getFilePath() == null) {
            rag.setStatus("failed");
            errorDetail.message = "文件路径为空";
            rag.setErrorDetail(mapper.valueToTree(errorDetail).toString());
            return ragRepository.save(rag);
        }
        Path filePath = Paths.get(rag.getFilePath());
        if (!Files.exists(filePath)) {
            rag.setStatus("failed");
            errorDetail.message = "文件不存在: " + rag.getFilePath();
            rag.setErrorDetail(mapper.valueToTree(errorDetail).toString());
            return ragRepository.save(rag);
        }

        // ─── 步骤 2: 提取文本 ───
        errorDetail.step = "extract_text";
        rag.setStatus("chunking");
        String text;
        try {
            text = Files.readString(filePath, StandardCharsets.UTF_8);
        } catch (Exception e1) {
            try {
                text = new String(Files.readAllBytes(filePath), "GBK");
            } catch (Exception e2) {
                rag.setStatus("failed");
                errorDetail.message = "文本提取失败(UTF-8/GBK均无法解析): " + e2.getMessage();
                errorDetail.code = "ENCODING_ERROR";
                rag.setErrorDetail(mapper.valueToTree(errorDetail).toString());
                System.err.println("[RAG] " + errorDetail.message);
                return ragRepository.save(rag);
            }
        }
        if (text == null || text.isBlank()) {
            rag.setStatus("failed");
            errorDetail.message = "文件内容为空";
            errorDetail.code = "EMPTY_FILE";
            rag.setErrorDetail(mapper.valueToTree(errorDetail).toString());
            return ragRepository.save(rag);
        }

        // ─── 步骤 3: 切片 ───
        errorDetail.step = "chunk";
        int cs = rag.getChunkSize() != null ? rag.getChunkSize() : 500;
        int co = rag.getChunkOverlap() != null ? rag.getChunkOverlap() : 50;
        String cm = rag.getChunkMethod() != null ? rag.getChunkMethod() : "fixed_size";
        List<String> chunks;
        try {
            chunks = chunkText(text, cs, co, cm);
        } catch (Exception e) {
            rag.setStatus("failed");
            errorDetail.message = "切片异常: " + e.getMessage();
            errorDetail.code = "CHUNK_ERROR";
            rag.setErrorDetail(mapper.valueToTree(errorDetail).toString());
            return ragRepository.save(rag);
        }
        if (chunks.isEmpty()) {
            rag.setStatus("failed");
            errorDetail.message = "切片后为空（文本长度 < 切片大小？）";
            errorDetail.code = "NO_CHUNKS";
            rag.setErrorDetail(mapper.valueToTree(errorDetail).toString());
            return ragRepository.save(rag);
        }
        rag.setChunkCount(chunks.size());
        rag.setContent(String.join("---CHUNK---", chunks));

        // ─── 步骤 4: Embedding + Chroma upsert ───
        errorDetail.step = "embedding";
        rag.setStatus("embedding");
        rag.setEmbeddingModelId(modelId);
        ragRepository.save(rag);

        int successCount = 0;
        List<RagErrorDetail.ChunkLog> chunkLogs = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i).trim();
            if (chunk.isEmpty()) continue;

            RagErrorDetail.ChunkLog clog = new RagErrorDetail.ChunkLog(i, cs);
            try {
                System.out.println("[RAG] 切片 " + i + "/" + chunks.size()
                        + " (长度=" + chunk.length() + ") 开始向量化...");
                float[] vector = embedWithModel(chunk, embModel);
                if (vector != null && vector.length > 0) {
                    System.out.println("[RAG] 切片 " + i + " 向量化成功 (维数=" + vector.length + "), 写入 Chroma...");
                    chromaUpsert(collName, "rag_" + ragId + "_chunk_" + i, vector,
                            Map.of("source", rag.getFilename(), "chunk_index", i,
                                   "rag_id", ragId, "text", chunk));
                    successCount++;
                    clog.success = true;
                } else {
                    System.err.println("[RAG] 切片 " + i + " 向量化失败: embedding 返回空/null");
                    clog.error = "embedding 返回空/null";
                }
            } catch (Exception e) {
                System.err.println("[RAG] 切片 " + i + " 向量化异常: " + e.getMessage());
                clog.error = e.getMessage();
            }
            chunkLogs.add(clog);
        }

        // ─── 结果 ───
        if (successCount == 0) {
            rag.setStatus("failed");
            errorDetail.message = "所有切片向量化均失败（共 " + chunks.size() + " 片）";
            errorDetail.code = "ALL_EMBED_FAILED";
        } else if (successCount < chunks.size()) {
            rag.setStatus("completed");
            errorDetail.message = "部分切片失败（成功 " + successCount + "/" + chunks.size() + "）";
            errorDetail.code = "PARTIAL_SUCCESS";
        } else {
            rag.setStatus("completed");
            errorDetail.message = "全部切片向量化成功";
        }
        errorDetail.chunkTotal = chunks.size();
        errorDetail.chunkSuccess = successCount;
        errorDetail.chunkLogs = chunkLogs;
        errorDetail.collectionName = collName;
        errorDetail.embeddingModelName = embModel.getModelName();
        rag.setErrorDetail(mapper.valueToTree(errorDetail).toString());

        System.out.println("[RAG] " + rag.getFilename() + " 完成: " + successCount + "/" + chunks.size()
                + " 片 → " + collName + " | " + errorDetail.message);
        return ragRepository.save(rag);
    }

    /** 更新元数据 */
    @CacheEvict(value = {"rag_list", "rag_search"}, allEntries = true)
    @Transactional
    public Rag updateRag(Long id, Rag updated) {
        Rag ex = ragRepository.findById(id).orElseThrow(() -> new RuntimeException("不存在: " + id));
        if (updated.getFilename() != null) ex.setFilename(updated.getFilename());
        if (updated.getDescription() != null) ex.setDescription(updated.getDescription());
        if (updated.getCollectionName() != null) ex.setCollectionName(updated.getCollectionName());
        if (updated.getStatus() != null) ex.setStatus(updated.getStatus());
        if (updated.getIsEnabled() != null) ex.setIsEnabled(updated.getIsEnabled());
        if (updated.getChunkSize() != null) ex.setChunkSize(updated.getChunkSize());
        if (updated.getChunkOverlap() != null) ex.setChunkOverlap(updated.getChunkOverlap());
        if (updated.getChunkMethod() != null) ex.setChunkMethod(updated.getChunkMethod());

        if (updated.getChunkSize() != null || updated.getChunkMethod() != null) {
            ex.setContent(null);
            ex.setChunkCount(0);
            ex.setStatus("uploaded");
        }
        return ragRepository.save(ex);
    }

    /** 启用/禁用 — 禁用时删除 Chroma 向量，启用时不自动加回 */
    @CacheEvict(value = {"rag_list", "rag_search"}, allEntries = true)
    @Transactional
    public Rag toggleRag(Long id) {
        Rag t = ragRepository.findById(id).orElseThrow(() -> new RuntimeException("不存在: " + id));
        int was = t.getIsEnabled() != null ? t.getIsEnabled() : 0;
        t.setIsEnabled(was == 1 ? 0 : 1);
        if (was == 1 && t.getChunkCount() > 0) {
            // 禁用 → 删除 Chroma 向量
            deleteVectors(t);
            System.out.println("[RAG] " + t.getFilename() + " 已禁用，向量已删除");
        }
        return ragRepository.save(t);
    }

    /** 删除文档 → 清理磁盘 + Chroma + MySQL */
    @CacheEvict(value = {"rag_list", "rag_search"}, allEntries = true)
    @Transactional
    public void deleteRag(Long id) {
        Rag rag = ragRepository.findById(id).orElseThrow(() -> new RuntimeException("不存在: " + id));
        if (rag.getFilePath() != null) {
            try { Files.deleteIfExists(Paths.get(rag.getFilePath())); } catch (IOException ignored) {}
        }
        deleteVectors(rag);
        ragRepository.deleteById(id);
    }

    // ===================== 向量数据查询 =====================

    /** 获取当前用户 Chroma 集合中所有向量（分页） */
    public List<Map<String, Object>> listVectors(Long userId) {
        String collName = DEFAULT_COLLECTION;
        String uuid = getCollectionId(collName);
        System.out.println("[Chroma] listVectors userId=" + userId + " coll=" + collName + " uuid=" + uuid);
        try {
            HttpResponse<String> resp = http.send(HttpRequest.newBuilder()
                    .uri(URI.create(chromaPath("/collections/" + uuid + "/get")))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                        mapper.writeValueAsString(Map.of("include", List.of("metadatas")))))
                    .timeout(Duration.ofSeconds(10)).build(),
                    HttpResponse.BodyHandlers.ofString());
            String body = resp.body();
            System.out.println("[Chroma] /get HTTP " + resp.statusCode() + " len=" + body.length());
            if (body.length() > 0) System.out.println("[Chroma] /get body预览: " + body.substring(0, Math.min(500, body.length())));
            if (resp.statusCode() == 200) {
                JsonNode root = mapper.readTree(body);
                JsonNode ids = root.get("ids");
                JsonNode metas = root.get("metadatas");
                List<Map<String, Object>> result = new ArrayList<>();
                if (ids != null && ids.isArray()) {
                    for (int i = 0; i < ids.size(); i++) {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("id", ids.get(i).asText());
                        if (metas != null && metas.size() > i && metas.get(i) != null) {
                            JsonNode meta = metas.get(i);
                            if (meta.has("source")) item.put("source", meta.get("source").asText());
                            if (meta.has("chunk_index")) item.put("chunkIndex", meta.get("chunk_index").asInt());
                            if (meta.has("rag_id")) item.put("ragId", meta.get("rag_id").asLong());
                            if (meta.has("text")) {
                                String txt = meta.get("text").asText();
                                item.put("text", txt.length() > 200 ? txt.substring(0, 200) + "..." : txt);
                            }
                        }
                        result.add(item);
                    }
                }
                return result;
            }
        } catch (Exception e) {
            System.err.println("[Chroma] 列出向量失败: " + e.getMessage());
        }
        return List.of();
    }

    /** 删除某文档的所有 Chroma 向量 */
    private void deleteVectors(Rag rag) {
        int cnt = rag.getChunkCount() != null ? rag.getChunkCount() : 0;
        if (cnt <= 0) return;
        String collName = userCollection(rag.getUserId());
        for (int i = 0; i < Math.max(cnt + 50, 100); i++) {
            chromaDelete(collName, "rag_" + rag.getId() + "_chunk_" + i);
        }
    }

    // ===================== 向量检索 =====================

    public List<Map<String, Object>> searchRelevant(String query, int topK) {
        return searchRelevant(query, topK, Set.of());
    }

    public List<Map<String, Object>> searchRelevant(String query, int topK, Set<Long> allowedRagIds) {
        if (query == null || query.isBlank()) return List.of();
        try {
            ModelConfig embModel = findEmbeddingModel();
            if (embModel == null) return List.of();
            float[] queryVec = embedWithModel(query, embModel);
            if (queryVec == null) return List.of();

            // 统一使用默认集合检索
            String collName = DEFAULT_COLLECTION;
            getCollectionId(collName);
            return chromaQuery(collName, queryVec, topK).stream()
                    .filter(r -> r.get("content") != null)
                    .filter(r -> {
                        Object ragIdObj = r.get("ragId");
                        if (allowedRagIds == null || allowedRagIds.isEmpty()) return true;
                        if (ragIdObj instanceof Number n) return allowedRagIds.contains(n.longValue());
                        return true;
                    })
                    .sorted((a, b) -> Double.compare(
                            (Double) b.getOrDefault("similarity", 0.0),
                            (Double) a.getOrDefault("similarity", 0.0)))
                    .limit(topK)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("[RAG] 检索失败: " + e.getMessage());
            return List.of();
        }
    }

    private ModelConfig findEmbeddingModel() {
        List<ModelConfig> models = modelConfigRepo.findAll();
        for (ModelConfig m : models) {
            if (m.getApiKeyEncrypted() != null && !m.getApiKeyEncrypted().isBlank()) return m;
        }
        return null;
    }

    // ===================== Embedding =====================

    private float[] embedWithModel(String text, ModelConfig mc) {
        try {
            String url = mc.getBaseUrl();
            if (url == null || url.isBlank()) url = "https://api.openai.com/v1";
            if (!url.endsWith("/")) url += "/";
            url += "embeddings";

            String body = mapper.writeValueAsString(Map.of(
                    "model", mc.getModelName() != null ? mc.getModelName() : "text-embedding-ada-002",
                    "input", text));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + mc.getApiKeyEncrypted())
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(30)).build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = mapper.readTree(resp.body());
                JsonNode arr = root.has("data") ? root.get("data").get(0).get("embedding") : root.get("embedding");
                if (arr != null && arr.isArray()) {
                    float[] vec = new float[arr.size()];
                    for (int i = 0; i < arr.size(); i++) vec[i] = arr.get(i).floatValue();
                    return vec;
                }
            } else {
                System.err.println("[Embed] HTTP " + resp.statusCode() + ": " +
                        resp.body().substring(0, Math.min(200, resp.body().length())));
            }
        } catch (Exception e) { System.err.println("[Embed] " + e.getMessage()); }
        return null;
    }

    // ===================== Chroma REST API =====================

    private void chromaUpsert(String collName, String id, float[] vector, Map<String, Object> metadata) {
        try {
            String uuid = getCollectionId(collName);
            List<Float> embList = new ArrayList<>(vector.length);
            for (float v : vector) embList.add(v);
            String body = mapper.writeValueAsString(Map.of(
                    "ids", List.of(id),
                    "embeddings", List.of(embList),
                    "metadatas", List.of(metadata)));
            String url = chromaPath("/collections/" + uuid + "/upsert");
            HttpResponse<String> resp = http.send(HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(10)).build(),
                    HttpResponse.BodyHandlers.ofString());
            System.out.println("[Chroma] upsert " + uuid.substring(0,8) + "/" + id + " → " + resp.statusCode());
            if (resp.statusCode() >= 400) {
                System.err.println("[Chroma] " + url + "\n  " + resp.body().substring(0, Math.min(300, resp.body().length())));
            }
        } catch (Exception e) {
            System.err.println("[Chroma] upsert异常: " + e.getMessage());
        }
    }

    /** float[] → List<Float> 用于 JSON 序列化 */
    private static List<Float> convertVectorToList(float[] vec) {
        List<Float> list = new ArrayList<>(vec.length);
        for (float v : vec) list.add(v);
        return list;
    }

    private List<Map<String, Object>> chromaQuery(String collName, float[] vector, int topK) {
        try {
            String uuid = getCollectionId(collName);
            String body = mapper.writeValueAsString(Map.of(
                    "query_embeddings", List.of(vector), "n_results", topK,
                    "include", List.of("metadatas", "documents", "distances")));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(chromaPath("/collections/" + uuid + "/query")))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).timeout(Duration.ofSeconds(15)).build();
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

    private void chromaDelete(String collName, String id) {
        try {
            String uuid = getCollectionId(collName);
            http.send(HttpRequest.newBuilder()
                    .uri(URI.create(chromaPath("/collections/" + uuid + "/delete")))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                        mapper.writeValueAsString(Map.of("ids", List.of(id)))))
                    .timeout(Duration.ofSeconds(5)).build(),
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
