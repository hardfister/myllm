package com.example.myllm.model.dto;

import lombok.Data;

@Data
public class ModelSetting {
    private String apiKey;
    private String baseUrl;
    private String modelName;
}
