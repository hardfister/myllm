package com.example.myllm.controller;

import com.example.myllm.model.dto.ChatRequest;
import com.example.myllm.service.ChatService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public String handleChat(@RequestBody ChatRequest request) {
        String userMessage = request.getContent();
        if (userMessage == null || userMessage.isBlank()) {
            return "消息内容不能为空";
        }

        System.out.println("收到用户消息: " + userMessage);

        try {
            String systemMessage = request.getSystemMessage();
            if (systemMessage == null || systemMessage.isBlank()) {
                systemMessage = "你是一个友好的AI助手";
            }

            String aiResponse = chatService.chat(userMessage, systemMessage);
            System.out.println("大模型回复: " + aiResponse);
            return aiResponse;
        } catch (Exception e) {
            e.printStackTrace();
            return "大模型调用失败，错误信息: " + e.getMessage();
        }
    }
}
