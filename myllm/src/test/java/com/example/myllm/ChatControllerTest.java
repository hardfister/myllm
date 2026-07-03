package com.example.myllm;

import com.example.myllm.controller.ChatController;
import com.example.myllm.model.dto.ChatRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ChatControllerTest {

    @Autowired
    private ChatController chatController;

    @Test
    void contextLoads() {
        assertNotNull(chatController);
    }

    @Test
    void chatWithValidRequest() {
        ChatRequest request = new ChatRequest();
        request.setPrompt("你好");
        request.setSystemMessage("你是一个友好的AI助手");

        String result = chatController.chat(request);
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void chatWithEmptyPrompt() {
        ChatRequest request = new ChatRequest();
        request.setPrompt("");
        request.setSystemMessage("你是一个助手");

        String result = chatController.chat(request);
        assertNotNull(result);
    }

    @Test
    void chatWithNullSystemMessage() {
        ChatRequest request = new ChatRequest();
        request.setPrompt("你好");

        String result = chatController.chat(request);
        assertNotNull(result);
    }
}
