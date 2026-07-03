package com.example.myllm.dto;

import com.example.myllm.model.dto.ChatRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChatRequest DTO 单元测试
 */
class ChatRequestTest {

    @Test
    void shouldCreateChatRequestWithAllFields() {
        // Arrange & Act
        ChatRequest request = new ChatRequest();
        request.setPrompt("你好");
        request.setSystemMessage("你是一个AI助手");

        // Assert
        assertEquals("你好", request.getPrompt());
        assertEquals("你是一个AI助手", request.getSystemMessage());
    }

    @Test
    void shouldCreateChatRequestWithPromptOnly() {
        // Arrange & Act
        ChatRequest request = new ChatRequest();
        request.setPrompt("测试问题");

        // Assert
        assertEquals("测试问题", request.getPrompt());
        assertNull(request.getSystemMessage());
    }

    @Test
    void shouldReturnCorrectToString() {
        // Arrange
        ChatRequest request = new ChatRequest();
        request.setPrompt("test");
        request.setSystemMessage("system");

        // Act
        String result = request.toString();

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("ChatRequest"));
        assertTrue(result.contains("prompt=test"));
    }

    @Test
    void shouldReturnCorrectHashCode() {
        // Arrange
        ChatRequest request1 = new ChatRequest();
        request1.setPrompt("same");
        request1.setSystemMessage("same");

        ChatRequest request2 = new ChatRequest();
        request2.setPrompt("same");
        request2.setSystemMessage("same");

        // Assert
        assertEquals(request1.hashCode(), request2.hashCode());
    }

    @Test
    void shouldImplementEqualsCorrectly() {
        // Arrange
        ChatRequest request1 = new ChatRequest();
        request1.setPrompt("test");
        request1.setSystemMessage("system");

        ChatRequest request2 = new ChatRequest();
        request2.setPrompt("test");
        request2.setSystemMessage("system");

        ChatRequest request3 = new ChatRequest();
        request3.setPrompt("different");
        request3.setSystemMessage("system");

        // Assert
        assertEquals(request1, request2);
        assertNotEquals(request1, request3);
    }
}
