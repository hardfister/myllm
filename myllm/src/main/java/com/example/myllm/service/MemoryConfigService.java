package com.example.myllm.service;

import com.example.myllm.model.entity.MemoryConfig;
import com.example.myllm.repository.MemoryConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MemoryConfigService {

    private final MemoryConfigRepository memoryConfigRepository;

    public MemoryConfigService(MemoryConfigRepository memoryConfigRepository) {
        this.memoryConfigRepository = memoryConfigRepository;
    }

    public List<MemoryConfig> getAllMemories() {
        return memoryConfigRepository.findAllByOrderByUpdatedAtDesc();
    }

    @Transactional
    public MemoryConfig createMemory(MemoryConfig config) {
        return memoryConfigRepository.save(config);
    }

    @Transactional
    public MemoryConfig updateMemory(Long id, MemoryConfig updated) {
        MemoryConfig existing = memoryConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("记忆配置不存在: " + id));

        if (updated.getStrategyType() != null) existing.setStrategyType(updated.getStrategyType());
        if (updated.getWindowSize() != null) existing.setWindowSize(updated.getWindowSize());
        if (updated.getSummaryTriggerTokens() != null) existing.setSummaryTriggerTokens(updated.getSummaryTriggerTokens());
        if (updated.getSummaryMaxLength() != null) existing.setSummaryMaxLength(updated.getSummaryMaxLength());
        if (updated.getEnableRag() != null) existing.setEnableRag(updated.getEnableRag());
        if (updated.getRagCollectionName() != null) existing.setRagCollectionName(updated.getRagCollectionName());
        if (updated.getRagTopK() != null) existing.setRagTopK(updated.getRagTopK());
        if (updated.getMaxHistoryMessages() != null) existing.setMaxHistoryMessages(updated.getMaxHistoryMessages());
        if (updated.getEnableLongTermMemory() != null) existing.setEnableLongTermMemory(updated.getEnableLongTermMemory());
        if (updated.getCompressionInterval() != null) existing.setCompressionInterval(updated.getCompressionInterval());
        if (updated.getReserveSystemPrompt() != null) existing.setReserveSystemPrompt(updated.getReserveSystemPrompt());
        if (updated.getIsEnabled() != null) existing.setIsEnabled(updated.getIsEnabled());

        return memoryConfigRepository.save(existing);
    }

    @Transactional
    public MemoryConfig toggleMemory(Long id) {
        MemoryConfig target = memoryConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("记忆配置不存在: " + id));
        target.setIsEnabled(target.getIsEnabled() != null && target.getIsEnabled() == 1 ? 0 : 1);
        return memoryConfigRepository.save(target);
    }

    @Transactional
    public void deleteMemory(Long id) {
        memoryConfigRepository.deleteById(id);
    }
}
