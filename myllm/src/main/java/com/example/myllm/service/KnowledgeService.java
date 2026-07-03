package com.example.myllm.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class KnowledgeService {

    public String uploadKnowledge(MultipartFile file) throws IOException {
        // TODO: 实现知识库上传逻辑 - 需要 Chroma vector store
        return "文件上传成功";
    }
}
