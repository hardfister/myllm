package com.example.myllm.controller;

import com.example.myllm.model.entity.MemoryConfig;
import com.example.myllm.service.MemoryConfigService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/memories")
@CrossOrigin(origins = "*")
public class MemoryController {

    private final MemoryConfigService memoryConfigService;

    public MemoryController(MemoryConfigService memoryConfigService) {
        this.memoryConfigService = memoryConfigService;
    }

    @GetMapping
    public List<MemoryConfig> getAllMemories() {
        return memoryConfigService.getAllMemories();
    }

    @PostMapping
    public MemoryConfig createMemory(@RequestBody MemoryConfig config) {
        return memoryConfigService.createMemory(config);
    }

    @PutMapping("/{id}")
    public MemoryConfig updateMemory(@PathVariable Long id, @RequestBody MemoryConfig config) {
        return memoryConfigService.updateMemory(id, config);
    }

    @DeleteMapping("/{id}")
    public String deleteMemory(@PathVariable Long id) {
        memoryConfigService.deleteMemory(id);
        return "删除成功";
    }

    @PutMapping("/{id}/toggle")
    public MemoryConfig toggleMemory(@PathVariable Long id) {
        return memoryConfigService.toggleMemory(id);
    }
}
