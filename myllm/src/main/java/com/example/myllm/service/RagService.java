package com.example.myllm.service;

import com.example.myllm.model.entity.Rag;
import com.example.myllm.repository.RagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
public class RagService {

    private final RagRepository ragRepository;
    private static final String UPLOAD_DIR = "./uploads/";

    public RagService(RagRepository ragRepository) {
        this.ragRepository = ragRepository;
    }

    public List<Rag> getAllRags() {
        return ragRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public Rag createRag(MultipartFile file, String description) throws IOException {
        Path uploadPath = Paths.get(UPLOAD_DIR);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String originalFilename = file.getOriginalFilename();
        String storedFilename = UUID.randomUUID() + "_" + (originalFilename != null ? originalFilename : "unknown");
        Path targetPath = uploadPath.resolve(storedFilename);
        file.transferTo(targetPath.toFile());

        Rag rag = new Rag();
        rag.setFilename(originalFilename != null ? originalFilename : "unknown");
        rag.setFilePath(targetPath.toAbsolutePath().toString());
        rag.setFileSize(file.getSize());
        rag.setFileType(file.getContentType());
        rag.setDescription(description);
        rag.setCollectionName("default_collection");
        rag.setChunkCount(0);
        rag.setStatus("processing");

        return ragRepository.save(rag);
    }

    @Transactional
    public Rag updateRag(Long id, Rag updated) {
        Rag existing = ragRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("知识库文档不存在: " + id));

        if (updated.getFilename() != null) existing.setFilename(updated.getFilename());
        if (updated.getDescription() != null) existing.setDescription(updated.getDescription());
        if (updated.getCollectionName() != null) existing.setCollectionName(updated.getCollectionName());
        if (updated.getStatus() != null) existing.setStatus(updated.getStatus());

        return ragRepository.save(existing);
    }

    @Transactional
    public void deleteRag(Long id) {
        Rag rag = ragRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("知识库文档不存在: " + id));

        // Delete file from disk
        if (rag.getFilePath() != null) {
            try {
                Files.deleteIfExists(Paths.get(rag.getFilePath()));
            } catch (IOException e) {
                // Log but don't fail the delete
                System.err.println("无法删除文件: " + rag.getFilePath());
            }
        }

        ragRepository.deleteById(id);
    }
}
