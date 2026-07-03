package com.example.myllm.controller;

import com.example.myllm.model.dto.ChatRequest;
import com.example.myllm.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ChatController 集成测试
 */
@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatService chatService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldReturnChatResponse() throws Exception {
        // Arrange
        ChatRequest request = new ChatRequest();
        request.setPrompt("你好");
        request.setSystemMessage("你是一个AI助手");

        when(chatService.chat(anyString(), anyString()))
                .thenReturn("你好！我是AI助手，很高兴为你服务。");

        // Act & Assert
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("你好！我是AI助手，很高兴为你服务。"));
    }

    @Test
    void shouldHandleEmptyPrompt() throws Exception {
        // Arrange
        ChatRequest request = new ChatRequest();
        request.setPrompt("");
        request.setSystemMessage("系统提示");

        when(chatService.chat(anyString(), anyString()))
                .thenReturn("请提供具体问题。");

        // Act & Assert
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("请提供具体问题。"));
    }

    @Test
    void shouldHandleNullSystemMessage() throws Exception {
        // Arrange
        ChatRequest request = new ChatRequest();
        request.setPrompt("测试问题");
        request.setSystemMessage(null);

        when(chatService.chat(anyString(), anyString()))
                .thenReturn("测试回复");

        // Act & Assert
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("测试回复"));
    }

    @Test
    void shouldReturn400WhenRequestBodyIsInvalid() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("invalid json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldHandleLongPrompt() throws Exception {
        // Arrange
        String longPrompt = "这是一段很长的文本".repeat(100);
        ChatRequest request = new ChatRequest();
        request.setPrompt(longPrompt);
        request.setSystemMessage("系统提示");

        when(chatService.chat(anyString(), anyString()))
                .thenReturn("已处理长文本");

        // Act & Assert
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("已处理长文本"));
    }
}
