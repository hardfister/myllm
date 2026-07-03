package com.example.myllm.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private final ChatClient chatClient;

    public ChatService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String chat(String prompt, String systemMessage) {
        return chatClient.prompt()
                .system(systemMessage)
                .user(prompt)
                .call()
                .content();
    }
}
