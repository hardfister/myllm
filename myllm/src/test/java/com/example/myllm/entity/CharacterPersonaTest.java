package com.example.myllm.entity;

import com.example.myllm.model.entity.CharacterPersona;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CharacterPersona 实体单元测试
 */
class CharacterPersonaTest {

    @Test
    void shouldCreateCharacterPersonaWithAllFields() {
        // Arrange & Act
        CharacterPersona persona = new CharacterPersona();
        persona.setId(1L);
        persona.setName("法律顾问");
        persona.setSystemPrompt("你是一名专业的法律顾问");
        persona.setTemperature(0.3);
        persona.setTargetCollection("legal_knowledge");

        // Assert
        assertEquals(1L, persona.getId());
        assertEquals("法律顾问", persona.getName());
        assertEquals("你是一名专业的法律顾问", persona.getSystemPrompt());
        assertEquals(0.3, persona.getTemperature());
        assertEquals("legal_knowledge", persona.getTargetCollection());
    }

    @Test
    void shouldCreateCharacterPersonaWithNullOptionalFields() {
        // Arrange & Act
        CharacterPersona persona = new CharacterPersona();
        persona.setName("测试角色");

        // Assert
        assertNull(persona.getId());
        assertEquals("测试角色", persona.getName());
        assertNull(persona.getSystemPrompt());
        assertNull(persona.getTemperature());
        assertNull(persona.getTargetCollection());
    }

    @Test
    void shouldHaveCorrectTableName() {
        // Arrange
        CharacterPersona persona = new CharacterPersona();

        // Act
        Table table = persona.getClass().getAnnotation(Table.class);

        // Assert
        assertNotNull(table);
        assertEquals("character_persona", table.name());
    }

    @Test
    void shouldImplementEqualsCorrectly() {
        // Arrange
        CharacterPersona persona1 = new CharacterPersona();
        persona1.setId(1L);
        persona1.setName("法律顾问");

        CharacterPersona persona2 = new CharacterPersona();
        persona2.setId(1L);
        persona2.setName("法律顾问");

        CharacterPersona persona3 = new CharacterPersona();
        persona3.setId(2L);
        persona3.setName("文案大师");

        // Assert
        assertEquals(persona1, persona2);
        assertNotEquals(persona1, persona3);
    }

    @Test
    void shouldImplementHashCodeCorrectly() {
        // Arrange
        CharacterPersona persona1 = new CharacterPersona();
        persona1.setId(1L);
        persona1.setName("法律顾问");

        CharacterPersona persona2 = new CharacterPersona();
        persona2.setId(1L);
        persona2.setName("法律顾问");

        // Assert
        assertEquals(persona1.hashCode(), persona2.hashCode());
    }

    @Test
    void shouldReturnCorrectToString() {
        // Arrange
        CharacterPersona persona = new CharacterPersona();
        persona.setId(1L);
        persona.setName("法律顾问");
        persona.setSystemPrompt("专业法律顾问");
        persona.setTemperature(0.3);
        persona.setTargetCollection("legal_knowledge");

        // Act
        String result = persona.toString();

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("CharacterPersona"));
        assertTrue(result.contains("id=1"));
        assertTrue(result.contains("name=法律顾问"));
    }

    @Test
    void shouldAllowTemperatureRange() {
        // Arrange & Act
        CharacterPersona personaLow = new CharacterPersona();
        personaLow.setTemperature(0.0);

        CharacterPersona personaHigh = new CharacterPersona();
        personaHigh.setTemperature(1.0);

        // Assert
        assertEquals(0.0, personaLow.getTemperature());
        assertEquals(1.0, personaHigh.getTemperature());
    }
}
