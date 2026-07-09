package com.example.myllm.service;

import com.example.myllm.model.entity.ModelConfig;
import com.example.myllm.repository.ModelConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class ModelConfigService {

    private final ModelConfigRepository modelConfigRepository;

    public ModelConfigService(ModelConfigRepository modelConfigRepository) {
        this.modelConfigRepository = modelConfigRepository;
    }

    public List<ModelConfig> getAllModels() {
        return modelConfigRepository.findAllByOrderByUpdatedAtDesc();
    }

    @Transactional
    public ModelConfig createModel(ModelConfig model) {
        return modelConfigRepository.save(model);
    }

    @Transactional
    public ModelConfig updateModel(Long id, ModelConfig updated) {
        ModelConfig existing = modelConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("模型配置不存在: " + id));

        if (updated.getModelName() != null) existing.setModelName(updated.getModelName());
        if (updated.getProvider() != null) existing.setProvider(updated.getProvider());
        if (updated.getApiKeyEncrypted() != null) existing.setApiKeyEncrypted(updated.getApiKeyEncrypted());
        if (updated.getBaseUrl() != null) existing.setBaseUrl(updated.getBaseUrl());
        if (updated.getMaxTokens() != null) existing.setMaxTokens(updated.getMaxTokens());
        if (updated.getPrompt() != null) existing.setPrompt(updated.getPrompt());
        if (updated.getIsEnabled() != null) existing.setIsEnabled(updated.getIsEnabled());
        if (updated.getDisplayName() != null) existing.setDisplayName(updated.getDisplayName());
        if (updated.getSortOrder() != null) existing.setSortOrder(updated.getSortOrder());

        return modelConfigRepository.save(existing);
    }

    @Transactional
    public void deleteModel(Long id) {
        modelConfigRepository.deleteById(id);
    }

    /** 多选切换 — 不排他，只翻转当前项 */
    @Transactional
    public ModelConfig toggleModel(Long id) {
        ModelConfig t = modelConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("不存在: " + id));
        t.setIsEnabled(t.getIsEnabled() != null && t.getIsEnabled() == 1 ? 0 : 1);
        return modelConfigRepository.save(t);
    }

    /** 批量更新排序 — 拖拽后前端把整组 id→sortOrder 映射发过来 */
    @Transactional
    public void reorder(List<Map<String, Object>> items) {
        for (Map<String, Object> item : items) {
            Long id = Long.valueOf(item.get("id").toString());
            int order = ((Number) item.get("sortOrder")).intValue();
            modelConfigRepository.findById(id).ifPresent(m -> {
                m.setSortOrder(order);
                modelConfigRepository.save(m);
            });
        }
    }
}
