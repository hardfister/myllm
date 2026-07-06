package com.example.myllm.repository;

import com.example.myllm.model.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * Session JPA 仓库
 * findBySessionName — 根据前端传入的短 UUID 查找数据库记录（用于保存消息时关联）
 * findAllByOrderByUpdatedAtDesc — 历史记录列表：最近活跃的会话排在最前
 */
@Repository
public interface SessionRepository extends JpaRepository<Session, Long> {
    Session findBySessionName(String sessionName);
    List<Session> findAllByOrderByUpdatedAtDesc();
    void deleteBySessionName(String sessionName);
}
