package com.example.myllm.model.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private String prompt;
    private String systemMessage;
}
