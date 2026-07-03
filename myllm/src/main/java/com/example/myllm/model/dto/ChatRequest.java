package com.example.myllm.model.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private String prompt;
    private String systemMessage;
    private String content;
    public ChatRequest(){

    }// Getter 方法：允许 Spring 获取该属性值
    public String getContent() {
        return content;
    }

    // Setter 方法：允许 Spring 将前端传来的值注入到该属性中
    public void setContent(String content) {
        this.content = content;
    }
}
