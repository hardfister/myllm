package com.example.myllm.repository;

import com.example.myllm.model.entity.CharacterPersona;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CharacterPersonaRepository extends JpaRepository<CharacterPersona, Long> {
    // 继承后自动具备增删改查(CRUD)能力
}