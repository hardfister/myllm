package com.example.myllm.service;

import com.example.myllm.model.entity.*;
import com.example.myllm.repository.MessageRepository;
import com.example.myllm.repository.SessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 会话持久化服务
 * ---------------
 * 从 ChatService 中拆出，确保 DB 写操作有独立的事务管理。
 *
 * 为什么需要独立 Service：
 *   ChatService.chat() 不能标 @Transactional（会持有连接 60-120s），
 *   而 private 方法无法被 Spring AOP 代理拦截，所以 @Transactional 在 private 方法上不生效。
 *   独立 Service bean 的 public 方法标 @Transactional 才真正被 Spring 管理。
 */
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
     * 持久化会话和消息到 MySQL
     * @return Session 的数据库主键 ID
     */
    @Transactional
    public Long saveSessionAndMessage(String sessionId, boolean isNew,
                                       String userMsg, String aiReply,
                                       ModelConfig model, MemoryConfig mem, Rag rag) {
        Session session;
        if (isNew) {
            session = new Session();
            session.setSessionName(sessionId);
            session.setTitle("新对话");
            session.setModelId(model != null ? model.getId() : null);
            session.setMemoryId(mem != null ? mem.getId() : null);
            session.setRagId(rag != null ? rag.getId() : null);
            session = sessionRepo.save(session);
        } else {
            session = sessionRepo.findBySessionName(sessionId);
            if (session != null) {
                session.setUpdatedAt(LocalDateTime.now());
                session = sessionRepo.save(session);
            }
        }

        if (session != null) {
            Message msg = new Message();
            msg.setSessionId(session.getId());
            msg.setUserMessage(userMsg);
            msg.setAiResponse(aiReply);
            msg.setTokensUsed(Math.max(1, (userMsg.length() + (aiReply != null ? aiReply.length() : 0)) / 2));
            messageRepo.save(msg);
            return session.getId();
        }
        return null;
    }

    /** 更新会话标题 */
    @Transactional
    public void updateTitle(Long sessionDbId, String title) {
        sessionRepo.findById(sessionDbId).ifPresent(s -> {
            s.setTitle(title);
            sessionRepo.save(s);
        });
    }
}
