package com.example.myllm.model.dto;

import lombok.Data;
import java.util.Map;

@Data
public class ModelSettingsWrapper {
    /**
     * 当前默认激活的预设名称（例如: "gpt4-turbo", "claude-pro"）
     */
    private String activePreset;

    /**
     * 所有的预设集合
     * Key: 预设名称 (如 "deepseek-fast")
     * Value: 对应的模型配置详情
     */
    private Map<String, ModelSetting> presets;
}