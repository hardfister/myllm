package com.example.myllm.controller;

import com.example.myllm.model.dto.ChatRequest;
import com.example.myllm.service.ChatService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public String chat(@RequestBody ChatRequest request) {
        return chatService.chat(request.getPrompt(), request.getSystemMessage());
    }
}
