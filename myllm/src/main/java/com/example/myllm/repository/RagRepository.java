package com.example.myllm.repository;

import com.example.myllm.model.entity.Rag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface RagRepository extends JpaRepository<Rag, Long> {
    List<Rag> findAllByOrderByCreatedAtDesc();
}
