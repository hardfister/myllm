package com.example.myllm.dto;

import com.example.myllm.model.dto.ModelSetting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ModelSetting DTO 单元测试
 */
class ModelSettingTest {

    @Test
    void shouldCreateModelSettingWithAllFields() {
        // Arrange & Act
        ModelSetting setting = new ModelSetting();
        setting.setApiKey("sk-test123");
        setting.setBaseUrl("https://api.openai.com/v1");
        setting.setModelName("gpt-4o");

        // Assert
        assertEquals("sk-test123", setting.getApiKey());
        assertEquals("https://api.openai.com/v1", setting.getBaseUrl());
        assertEquals("gpt-4o", setting.getModelName());
    }

    @Test
    void shouldCreateModelSettingWithNullFields() {
        // Arrange & Act
        ModelSetting setting = new ModelSetting();

        // Assert
        assertNull(setting.getApiKey());
        assertNull(setting.getBaseUrl());
        assertNull(setting.getModelName());
    }

    @Test
    void shouldReturnCorrectToString() {
        // Arrange
        ModelSetting setting = new ModelSetting();
        setting.setApiKey("key");
        setting.setBaseUrl("url");
        setting.setModelName("model");

        // Act
        String result = setting.toString();

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("ModelSetting"));
    }

    @Test
    void shouldImplementEqualsCorrectly() {
        // Arrange
        ModelSetting setting1 = new ModelSetting();
        setting1.setApiKey("key1");
        setting1.setBaseUrl("url1");
        setting1.setModelName("model1");

        ModelSetting setting2 = new ModelSetting();
        setting2.setApiKey("key1");
        setting2.setBaseUrl("url1");
        setting2.setModelName("model1");

        ModelSetting setting3 = new ModelSetting();
        setting3.setApiKey("key2");
        setting3.setBaseUrl("url1");
        setting3.setModelName("model1");

        // Assert
        assertEquals(setting1, setting2);
        assertNotEquals(setting1, setting3);
    }

    @Test
    void shouldReturnCorrectHashCode() {
        // Arrange
        ModelSetting setting1 = new ModelSetting();
        setting1.setApiKey("key");
        setting1.setBaseUrl("url");
        setting1.setModelName("model");

        ModelSetting setting2 = new ModelSetting();
        setting2.setApiKey("key");
        setting2.setBaseUrl("url");
        setting2.setModelName("model");

        // Assert
        assertEquals(setting1.hashCode(), setting2.hashCode());
    }
}
