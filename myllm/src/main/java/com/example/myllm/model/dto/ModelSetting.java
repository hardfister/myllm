package com.example.myllm.model.dto;

import lombok.Data;

@Data
public class ModelSetting {
    private String apiKey;
    private String modelName;
    private String color;
    private String baseUrl;
    private Integer maxTokens;
    private Integer minTokens;
    private String prompt;
}
