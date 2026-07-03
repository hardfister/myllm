package com.example.myllm;

import com.example.myllm.model.entity.CharacterPersona;
import com.example.myllm.repository.CharacterPersonaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CharacterPersonaRepositoryTest {

    @Autowired
    private CharacterPersonaRepository repository;

    @Test
    void contextLoads() {
        assertNotNull(repository);
    }

    @Test
    void saveAndFindPersona() {
        CharacterPersona persona = new CharacterPersona();
        persona.setName("测试角色");
        persona.setSystemPrompt("你是一个测试助手");
        persona.setTemperature(0.5);
        persona.setTargetCollection("test_collection");

        CharacterPersona saved = repository.save(persona);
        assertNotNull(saved.getId());
        assertEquals("测试角色", saved.getName());

        Optional<CharacterPersona> found = repository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("你是一个测试助手", found.get().getSystemPrompt());

        repository.deleteById(saved.getId());
    }

    @Test
    void findAllPersonas() {
        long count = repository.count();
        assertTrue(count >= 0);
    }

    @Test
    void updatePersona() {
        CharacterPersona persona = new CharacterPersona();
        persona.setName("待更新角色");
        persona.setSystemPrompt("原始提示词");
        persona.setTemperature(0.3);

        CharacterPersona saved = repository.save(persona);
        Long id = saved.getId();

        saved.setSystemPrompt("更新后的提示词");
        saved.setTemperature(0.8);
        repository.save(saved);

        Optional<CharacterPersona> found = repository.findById(id);
        assertTrue(found.isPresent());
        assertEquals("更新后的提示词", found.get().getSystemPrompt());
        assertEquals(0.8, found.get().getTemperature());

        repository.deleteById(id);
    }

    @Test
    void deletePersona() {
        CharacterPersona persona = new CharacterPersona();
        persona.setName("待删除角色");
        persona.setSystemPrompt("将被删除");

        CharacterPersona saved = repository.save(persona);
        Long id = saved.getId();

        repository.deleteById(id);
        Optional<CharacterPersona> found = repository.findById(id);
        assertFalse(found.isPresent());
    }
}
