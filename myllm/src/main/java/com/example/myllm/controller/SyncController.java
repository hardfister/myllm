package com.example.myllm.controller;

import com.example.myllm.model.dto.SyncDataRequest;
import com.example.myllm.model.entity.MemoryConfig;
import com.example.myllm.model.entity.ModelConfig;
import com.example.myllm.model.entity.Rag;
import com.example.myllm.model.entity.User;
import com.example.myllm.repository.MemoryConfigRepository;
import com.example.myllm.repository.ModelConfigRepository;
import com.example.myllm.repository.RagRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/sync")
@CrossOrigin(origins = "*")
public class SyncController {

    private final ModelConfigRepository modelConfigRepository;
    private final MemoryConfigRepository memoryConfigRepository;
    private final RagRepository ragRepository;

    public SyncController(ModelConfigRepository modelConfigRepository,
                          MemoryConfigRepository memoryConfigRepository,
                          RagRepository ragRepository) {
        this.modelConfigRepository = modelConfigRepository;
        this.memoryConfigRepository = memoryConfigRepository;
        this.ragRepository = ragRepository;
    }

    @PostMapping("/import")
    @Transactional
    public ResponseEntity<?> importLocalData(@RequestBody SyncDataRequest data,
                                              Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        User user = (User) authentication.getPrincipal();
        int modelCount = 0;
        int memoryCount = 0;
        int ragCount = 0;

        if (data.getModels() != null) {
            for (ModelConfig m : data.getModels()) {
                m.setId(null);
                m.setUserId(user.getId());
                modelConfigRepository.save(m);
                modelCount++;
            }
        }

        if (data.getMemories() != null) {
            for (MemoryConfig m : data.getMemories()) {
                m.setId(null);
                m.setUserId(user.getId());
                memoryConfigRepository.save(m);
                memoryCount++;
            }
        }

        if (data.getRags() != null) {
            for (Rag r : data.getRags()) {
                r.setId(null);
                r.setUserId(user.getId());
                ragRepository.save(r);
                ragCount++;
            }
        }

        Map<String, Object> result = Map.of(
                "message", "导入完成",
                "modelCount", modelCount,
                "memoryCount", memoryCount,
                "ragCount", ragCount
        );
        return ResponseEntity.ok(result);
    }
}
