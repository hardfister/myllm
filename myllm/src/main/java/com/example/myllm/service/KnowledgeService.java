package com.example.myllm.service;

import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class KnowledgeService {

    private final VectorStore vectorStore;

    public KnowledgeService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public String uploadKnowledge(MultipartFile file) throws IOException {
        // TODO: 实现知识库上传逻辑
        return "文件上传成功";
    }
}
