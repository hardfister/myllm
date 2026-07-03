package com.example.myllm.repository;

import com.example.myllm.model.entity.MemoryConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MemoryConfigRepository extends JpaRepository<MemoryConfig, Long> {
    List<MemoryConfig> findAllByOrderByUpdatedAtDesc();
}
