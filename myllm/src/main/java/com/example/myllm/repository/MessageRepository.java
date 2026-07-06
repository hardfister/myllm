package com.example.myllm.repository;

import com.example.myllm.model.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * Message JPA 仓库
 * findBySessionIdOrderByCreatedAt — 按发送时间顺序获取某会话的全部消息
 * countBySessionId — 统计会话中的消息条数
 */
@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findBySessionIdOrderByCreatedAt(Long sessionId);
    int countBySessionId(Long sessionId);
    void deleteBySessionId(Long sessionId);
}
