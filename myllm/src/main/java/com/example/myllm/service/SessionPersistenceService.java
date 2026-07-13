package com.example.myllm.service;

import com.example.myllm.model.entity.*;
import com.example.myllm.repository.MessageRepository;
import com.example.myllm.repository.SessionRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class SessionPersistenceService {

    private final SessionRepository sessionRepo;
    private final MessageRepository messageRepo;

    public SessionPersistenceService(SessionRepository sessionRepo,
                                     MessageRepository messageRepo) {
        this.sessionRepo = sessionRepo;
        this.messageRepo = messageRepo;
    }

    /**
     * 持久化会话和消息到 MySQL — 同时清除 Redis 历史缓存。
     * 任何异常都内部捕获，永不向外抛。
     */
    @CacheEvict(value = {"history_sessions", "session_msgs"}, allEntries = true)
    @Transactional
    public Long saveSessionAndMessage(String sessionId, boolean isNew,
                                       String userMsg, String aiReply,
                                       ModelConfig model, MemoryConfig mem, Rag rag) {
        try {
            System.out.println("[DB] 持久化: sessionId=" + sessionId
                    + " isNew=" + isNew + " userLen=" + userMsg.length());

            Session session = sessionRepo.findBySessionName(sessionId);
            if (session == null) {
                session = new Session();
                session.setSessionName(sessionId);
                session.setTitle("新对话");
                session.setModelId(model != null ? model.getId() : null);
                session.setMemoryId(mem != null ? mem.getId() : null);
                session.setRagId(rag != null ? rag.getId() : null);
                session = sessionRepo.save(session);
                System.out.println("[DB] ✅ 新建 Session id=" + session.getId());
            } else {
                session.setUpdatedAt(LocalDateTime.now());
                sessionRepo.save(session);
                System.out.println("[DB] ✅ 更新 Session id=" + session.getId());
            }

            Message msg = new Message();
            msg.setSessionId(session.getId());
            msg.setUserMessage(userMsg);
            msg.setAiResponse(aiReply);
            int tokens = Math.max(1, (userMsg.length() + (aiReply != null ? aiReply.length() : 0)) / 2);
            msg.setTokensUsed(tokens);
            messageRepo.save(msg);
            System.out.println("[DB] ✅ Message 已保存 tokens=" + tokens);

            return session.getId();

        } catch (Exception e) {
            System.err.println("[DB] ❌ 持久化失败(聊天不受影响): " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    @CacheEvict(value = "session_msgs", allEntries = true)
    @Transactional
    public void updateTitle(Long sessionDbId, String title) {
        try {
            sessionRepo.findById(sessionDbId).ifPresent(s -> {
                s.setTitle(title);
                sessionRepo.save(s);
                System.out.println("[DB] 标题更新: " + title);
            });
        } catch (Exception e) {
            System.err.println("[DB] 标题更新失败: " + e.getMessage());
        }
    }
}
