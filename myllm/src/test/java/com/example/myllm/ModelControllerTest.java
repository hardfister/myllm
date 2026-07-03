package com.example.myllm;

import com.example.myllm.controller.ModelController;
import com.example.myllm.model.dto.ModelSetting;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ModelControllerTest {

    @Autowired
    private ModelController modelController;

    @Test
    void contextLoads() {
        assertNotNull(modelController);
    }

    @Test
    void switchModelSuccess() {
        ModelSetting setting = new ModelSetting();
        setting.setApiKey("sk-test-key");
        setting.setBaseUrl("https://api.openai.com/v1");
        setting.setModelName("gpt-4o");

        String result = modelController.switchModel(setting);
        assertEquals("模型切换成功", result);
    }

    @Test
    void switchModelWithDifferentProvider() {
        ModelSetting setting = new ModelSetting();
        setting.setApiKey("test");
        setting.setBaseUrl("https://api.deepseek.com");
        setting.setModelName("DeepSeek-Chat");

        String result = modelController.switchModel(setting);
        assertEquals("模型切换成功", result);
    }

    @Test
    void switchModelWithEmptyApiKey() {
        ModelSetting setting = new ModelSetting();
        setting.setApiKey("");
        setting.setBaseUrl("https://api.openai.com/v1");
        setting.setModelName("gpt-4o");

        String result = modelController.switchModel(setting);
        assertEquals("模型切换成功", result);
    }
}
