package com.example.myllm.controller;

import com.example.myllm.model.entity.Rag;
import com.example.myllm.service.RagService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/rags")
@CrossOrigin(origins = "*")
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    @GetMapping
    public List<Rag> getAllRags() {
        return ragService.getAllRags();
    }

    @PostMapping
    public Rag createRag(@RequestParam("file") MultipartFile file,
                         @RequestParam(value = "description", required = false) String description) throws Exception {
        return ragService.createRag(file, description);
    }

    @PutMapping("/{id}")
    public Rag updateRag(@PathVariable Long id, @RequestBody Rag rag) {
        return ragService.updateRag(id, rag);
    }

    @DeleteMapping("/{id}")
    public String deleteRag(@PathVariable Long id) {
        ragService.deleteRag(id);
        return "删除成功";
    }

    @PutMapping("/{id}/toggle")
    public Rag toggleRag(@PathVariable Long id) {
        return ragService.toggleRag(id);
    }
}
