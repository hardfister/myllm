package com.example.myllm.service;

import com.example.myllm.model.entity.Rag;
import com.example.myllm.repository.RagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 知识库文档服务 — 上传 / 切片 / CRUD
 * ---------------
 * 支持格式：TXT, MD, CSV, JSON（纯文本格式）
 * 切片方式：
 *   fixed_size — 按固定字符数切片（chunkSize=500, chunkOverlap=50）
 *   paragraph  — 按段落（空行）切片
 *   sentence   — 按句子（。！？.!?）切片
 */
@Service
public class RagService {

    private final RagRepository ragRepository;
    private static final String UPLOAD_DIR = "./uploads/";

    public RagService(RagRepository ragRepository) {
        this.ragRepository = ragRepository;
    }

    /** 获取全部文档 */
    public List<Rag> getAllRags() {
        return ragRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * 上传文档 + 自定义切片配置 + 实时提取文本
     * @param file          上传的文件
     * @param collectionName 向量集合名称
     * @param chunkSize     切片大小（字符数）
     * @param chunkOverlap  切片重叠（字符数）
     * @param chunkMethod   切片方式：fixed_size / paragraph / sentence
     */
    @Transactional
    public Rag createRag(MultipartFile file, String collectionName,
                          Integer chunkSize, Integer chunkOverlap, String chunkMethod) throws IOException {
        // 1. 保存文件到磁盘
        Path uploadPath = Paths.get(UPLOAD_DIR);
        if (!Files.exists(uploadPath)) Files.createDirectories(uploadPath);

        String originalFilename = file.getOriginalFilename();
        String storedFilename = UUID.randomUUID().toString().substring(0, 8)
                + "_" + (originalFilename != null ? originalFilename : "unknown");
        Path targetPath = uploadPath.resolve(storedFilename);
        file.transferTo(targetPath.toFile());

        // 2. 读取文件文本内容
        String text = extractText(targetPath.toFile().getAbsolutePath(), file.getContentType());

        // 3. 应用切片配置（默认值兜底）
        int cs = (chunkSize != null && chunkSize > 0) ? chunkSize : 500;
        int co = (chunkOverlap != null && chunkOverlap >= 0) ? chunkOverlap : 50;
        String cm = (chunkMethod != null && !chunkMethod.isBlank()) ? chunkMethod : "fixed_size";

        List<String> chunks = chunkText(text, cs, co, cm);

        // 4. 构建实体
        Rag rag = new Rag();
        rag.setFilename(originalFilename != null ? originalFilename : "unknown");
        rag.setFilePath(targetPath.toAbsolutePath().toString());
        rag.setFileSize(file.getSize());
        rag.setFileType(file.getContentType());
        rag.setCollectionName(collectionName != null ? collectionName : "default_collection");
        rag.setChunkSize(cs);
        rag.setChunkOverlap(co);
        rag.setChunkMethod(cm);
        rag.setChunkCount(chunks.size());
        rag.setContent(String.join("\n---CHUNK---\n", chunks)); // 全文存 MEDIUMTEXT，聊天时注入
        rag.setStatus("completed"); // 切片完成

        return ragRepository.save(rag);
    }

    /** 更新文档元数据 + 切片配置 */
    @Transactional
    public Rag updateRag(Long id, Rag updated) {
        Rag existing = ragRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("知识库文档不存在: " + id));

        if (updated.getFilename() != null) existing.setFilename(updated.getFilename());
        if (updated.getDescription() != null) existing.setDescription(updated.getDescription());
        if (updated.getCollectionName() != null) existing.setCollectionName(updated.getCollectionName());
        if (updated.getStatus() != null) existing.setStatus(updated.getStatus());
        if (updated.getIsEnabled() != null) existing.setIsEnabled(updated.getIsEnabled());
        if (updated.getChunkSize() != null) existing.setChunkSize(updated.getChunkSize());
        if (updated.getChunkOverlap() != null) existing.setChunkOverlap(updated.getChunkOverlap());
        if (updated.getChunkMethod() != null) existing.setChunkMethod(updated.getChunkMethod());

        // 如果切片参数变了且有文件内容，重新切片
        if (updated.getChunkSize() != null || updated.getChunkMethod() != null) {
            if (existing.getContent() != null && !existing.getContent().isBlank()) {
                // 去掉旧的 CHUNK 分隔符，重新切片
                String raw = existing.getContent().replace("---CHUNK---", "");
                List<String> newChunks = chunkText(raw,
                        existing.getChunkSize() != null ? existing.getChunkSize() : 500,
                        existing.getChunkOverlap() != null ? existing.getChunkOverlap() : 50,
                        existing.getChunkMethod() != null ? existing.getChunkMethod() : "fixed_size");
                existing.setChunkCount(newChunks.size());
                existing.setContent(String.join("\n---CHUNK---\n", newChunks));
            }
        }

        return ragRepository.save(existing);
    }

    @Transactional
    public Rag toggleRag(Long id) {
        Rag target = ragRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("知识库文档不存在: " + id));
        target.setIsEnabled(target.getIsEnabled() != null && target.getIsEnabled() == 1 ? 0 : 1);
        return ragRepository.save(target);
    }

    @Transactional
    public void deleteRag(Long id) {
        Rag rag = ragRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("知识库文档不存在: " + id));
        if (rag.getFilePath() != null) {
            try { Files.deleteIfExists(Paths.get(rag.getFilePath())); }
            catch (IOException e) { System.err.println("无法删除文件: " + rag.getFilePath()); }
        }
        ragRepository.deleteById(id);
    }

    // ===================== 文本提取 =====================

    /** 从上传文件提取纯文本（支持 TXT / MD / CSV / JSON） */
    private String extractText(String filePath, String contentType) {
        try {
            return Files.readString(Path.of(filePath), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // UTF-8 失败尝试系统默认编码（Windows GBK 等）
            try {
                return new String(Files.readAllBytes(Path.of(filePath)), "GBK");
            } catch (Exception e2) {
                System.err.println("[RAG] 文本提取失败: " + e.getMessage());
                return "[文件内容无法读取: " + filePath + "]";
            }
        }
    }

    // ===================== 文本切片 =====================

    /**
     * 将文本按指定策略切片
     * @param text     原始文本
     * @param size     切片大小（字符）
     * @param overlap  重叠字符数
     * @param method   方式：fixed_size / paragraph / sentence
     */
    public static List<String> chunkText(String text, int size, int overlap, String method) {
        if (text == null || text.isBlank()) return List.of();

        return switch (method) {
            case "paragraph" -> chunkByParagraph(text, size);
            case "sentence" -> chunkBySentence(text, size, overlap);
            default -> chunkByFixedSize(text, size, overlap); // fixed_size
        };
    }

    /** 固定大小切片（带重叠） */
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

    /** 按段落切片（空行分割），每段不超过 size 字符，超出的继续切 */
    private static List<String> chunkByParagraph(String text, int size) {
        List<String> chunks = new ArrayList<>();
        String[] paragraphs = text.split("\\n\\s*\\n"); // 空行分割
        for (String p : paragraphs) {
            String trimmed = p.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.length() <= size) {
                chunks.add(trimmed);
            } else {
                // 段落太长 → 固定大小续切
                chunks.addAll(chunkByFixedSize(trimmed, size, 0));
            }
        }
        return chunks;
    }

    /** 按句子切片（中英文标点分割），合并到接近 size 大小 */
    private static List<String> chunkBySentence(String text, int size, int overlap) {
        List<String> sentences = new ArrayList<>();
        // 按中英文句子标点分割
        String[] parts = text.split("(?<=[。！？.!?])\\s*");
        StringBuilder current = new StringBuilder();
        for (String s : parts) {
            String trimmed = s.trim();
            if (trimmed.isEmpty()) continue;
            if (current.length() + trimmed.length() > size && current.length() > 0) {
                sentences.add(current.toString().trim());
                // 重叠：保留最后 overlap 个字符
                if (overlap > 0 && current.length() > overlap) {
                    current = new StringBuilder(current.substring(current.length() - overlap));
                } else {
                    current = new StringBuilder();
                }
            }
            current.append(trimmed);
        }
        if (current.length() > 0) sentences.add(current.toString().trim());
        return sentences.isEmpty() ? List.of(text) : sentences;
    }
}
