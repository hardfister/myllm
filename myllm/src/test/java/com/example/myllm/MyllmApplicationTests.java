package com.example.myllm;

import com.example.myllm.controller.ChatController;
import com.example.myllm.controller.KnowledgeController;
import com.example.myllm.controller.ModelController;
import com.example.myllm.model.dto.ChatRequest;
import com.example.myllm.model.dto.ModelSetting;
import com.example.myllm.service.ChatService;
import com.example.myllm.service.KnowledgeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MyllmApplicationTests {

    @Autowired
    private ChatController chatController;

    @Autowired
    private ModelController modelController;

    @Autowired
    private KnowledgeController knowledgeController;

    @Autowired
    private ChatService chatService;

    @Autowired
    private KnowledgeService knowledgeService;

    @Test
    void contextLoads() {
        assertNotNull(chatController);
        assertNotNull(modelController);
        assertNotNull(knowledgeController);
    }

    @Test
    void chatRequestFields() {
        ChatRequest request = new ChatRequest();
        request.setPrompt("你好");
        request.setSystemMessage("你是一个助手");

        assertEquals("你好", request.getPrompt());
        assertEquals("你是一个助手", request.getSystemMessage());
    }

    @Test
    void chatRequestNullFields() {
        ChatRequest request = new ChatRequest();
        assertNull(request.getPrompt());
        assertNull(request.getSystemMessage());
    }

    @Test
    void modelSettingFields() {
        ModelSetting setting = new ModelSetting();
        setting.setApiKey("test-key");
        setting.setBaseUrl("https://api.example.com");
        setting.setModelName("gpt-4o");

        assertEquals("test-key", setting.getApiKey());
        assertEquals("https://api.example.com", setting.getBaseUrl());
        assertEquals("gpt-4o", setting.getModelName());
    }

    @Test
    void modelSwitchReturnsSuccess() {
        ModelSetting setting = new ModelSetting();
        setting.setApiKey("test-key");
        setting.setBaseUrl("https://api.openai.com/v1");
        setting.setModelName("gpt-4o");

        String result = modelController.switchModel(setting);
        assertEquals("模型切换成功", result);
    }
}
