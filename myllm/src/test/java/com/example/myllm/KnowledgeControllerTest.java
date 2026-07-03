package com.example.myllm;

import com.example.myllm.controller.KnowledgeController;
import com.example.myllm.service.KnowledgeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class KnowledgeControllerTest {

    @Autowired
    private KnowledgeController knowledgeController;

    @Autowired
    private KnowledgeService knowledgeService;

    @Test
    void contextLoads() {
        assertNotNull(knowledgeController);
        assertNotNull(knowledgeService);
    }
}
