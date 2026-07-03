package com.example.myllm.controller;

import com.example.myllm.service.KnowledgeService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @PostMapping("/upload")
    public String uploadKnowledge(@RequestParam("file") MultipartFile file) throws Exception {
        return knowledgeService.uploadKnowledge(file);
    }
}
