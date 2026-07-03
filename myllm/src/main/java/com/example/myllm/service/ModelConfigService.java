package com.example.myllm.service;

import com.example.myllm.model.entity.ModelConfig;
import com.example.myllm.repository.ModelConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

        return modelConfigRepository.save(existing);
    }

    @Transactional
    public void deleteModel(Long id) {
        modelConfigRepository.deleteById(id);
    }

    @Transactional
    public ModelConfig activateModel(Long id) {
        List<ModelConfig> all = modelConfigRepository.findAll();
        for (ModelConfig m : all) {
            m.setIsEnabled(0);
            modelConfigRepository.save(m);
        }

        ModelConfig target = modelConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("模型配置不存在: " + id));
        target.setIsEnabled(1);
        return modelConfigRepository.save(target);
    }
}
