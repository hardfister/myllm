-- ============================================
-- MyLLM database initialization script
-- Database: myllm_db
-- Updated: 2026-07-14
-- Usage on Windows PowerShell:
--   cmd /c "mysql --default-character-set=utf8mb4 -u root < docs\myllm_db.sql"
-- ============================================

DROP DATABASE IF EXISTS myllm_db;
CREATE DATABASE myllm_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE myllm_db;

CREATE TABLE `user` (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    email           VARCHAR(100),
    phone           VARCHAR(20),
    nickname        VARCHAR(100),
    avatar_path     VARCHAR(500),
    avatar_url      VARCHAR(500),
    status          TINYINT     DEFAULT 1,
    role            VARCHAR(20) DEFAULT 'user',
    last_login_at   DATETIME,
    last_login_ip   VARCHAR(45),
    login_count     INT         DEFAULT 0,
    created_at      DATETIME    DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at      DATETIME,
    UNIQUE INDEX uk_user_username (username),
    UNIQUE INDEX uk_user_email (email),
    UNIQUE INDEX uk_user_phone (phone),
    INDEX idx_user_status (status),
    INDEX idx_user_role (role),
    INDEX idx_user_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

CREATE TABLE model_config (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT,
    model_name          VARCHAR(100) NOT NULL,
    provider            VARCHAR(50)  NOT NULL,
    api_key_encrypted   TEXT,
    base_url            VARCHAR(500),
    max_tokens          INT          DEFAULT 4096,
    prompt              TEXT,
    is_enabled          TINYINT(1)   DEFAULT 1,
    display_name        VARCHAR(100),
    sort_order          INT          DEFAULT 0,
    created_at          DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_model_user FOREIGN KEY (user_id) REFERENCES `user`(id) ON DELETE CASCADE,
    INDEX idx_model_user_id (user_id),
    INDEX idx_model_enabled_sort (is_enabled, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型配置表';

CREATE TABLE memory_config (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id                  BIGINT,
    strategy_type            VARCHAR(20)  DEFAULT 'sliding_window',
    window_size              INT          DEFAULT 10,
    summary_trigger_tokens   INT          DEFAULT 2048,
    summary_max_length       INT          DEFAULT 300,
    enable_rag               TINYINT(1)   DEFAULT 0,
    rag_collection_name      VARCHAR(255),
    rag_top_k                INT          DEFAULT 3,
    max_history_messages     INT          DEFAULT 50,
    enable_long_term_memory  TINYINT(1)   DEFAULT 0,
    compression_interval     INT          DEFAULT 10,
    reserve_system_prompt    TINYINT(1)   DEFAULT 1,
    is_enabled               TINYINT(1)   DEFAULT 1,
    created_at               DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at               DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_memory_user FOREIGN KEY (user_id) REFERENCES `user`(id) ON DELETE CASCADE,
    INDEX idx_memory_user_id (user_id),
    INDEX idx_memory_enabled (is_enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='LLM记忆配置表';

CREATE TABLE rag (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT,
    filename            VARCHAR(500) NOT NULL,
    file_path           TEXT,
    file_size           BIGINT       DEFAULT 0,
    file_type           VARCHAR(100),
    collection_name     VARCHAR(255) NOT NULL,
    chunk_count         INT          DEFAULT 0,
    chunk_size          INT          DEFAULT 500,
    chunk_overlap       INT          DEFAULT 50,
    chunk_method        VARCHAR(20)  DEFAULT 'fixed_size',
    content             MEDIUMTEXT,
    status              VARCHAR(20)  DEFAULT 'processing',
    description         VARCHAR(500),
    is_enabled          TINYINT(1)   DEFAULT 1,
    embedding_model_id  BIGINT,
    created_at          DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_rag_user FOREIGN KEY (user_id) REFERENCES `user`(id) ON DELETE SET NULL,
    CONSTRAINT fk_rag_embedding_model FOREIGN KEY (embedding_model_id) REFERENCES model_config(id) ON DELETE SET NULL,
    INDEX idx_rag_user_id (user_id),
    INDEX idx_rag_status (status),
    INDEX idx_rag_enabled (is_enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库文档表';

CREATE TABLE `session` (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_name    VARCHAR(255) NOT NULL,
    title           VARCHAR(255),
    user_id         BIGINT,
    model_id        BIGINT,
    memory_id       BIGINT,
    rag_id          BIGINT,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES `user`(id) ON DELETE SET NULL,
    CONSTRAINT fk_session_model FOREIGN KEY (model_id) REFERENCES model_config(id) ON DELETE SET NULL,
    CONSTRAINT fk_session_memory FOREIGN KEY (memory_id) REFERENCES memory_config(id) ON DELETE SET NULL,
    CONSTRAINT fk_session_rag FOREIGN KEY (rag_id) REFERENCES rag(id) ON DELETE SET NULL,
    INDEX idx_session_name (session_name),
    INDEX idx_session_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天会话表';

CREATE TABLE message (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id      BIGINT NOT NULL,
    user_message    TEXT   NOT NULL,
    ai_response     TEXT,
    tokens_used     INT    DEFAULT 0,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_message_session FOREIGN KEY (session_id) REFERENCES `session`(id) ON DELETE CASCADE,
    INDEX idx_message_session_id (session_id),
    INDEX idx_message_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天消息表';

CREATE TABLE character_persona (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    name                VARCHAR(255) NOT NULL,
    system_prompt       TEXT,
    temperature         DOUBLE,
    target_collection   VARCHAR(255)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色人设表';

-- BCrypt hash value is the common demo hash for password: password
INSERT INTO `user` (username, password_hash, email, phone, nickname, status, role, last_login_at, last_login_ip, login_count)
VALUES
('alice', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'alice@example.com', '+8613800138000', 'Alice', 1, 'admin', NOW(), '192.168.1.100', 45),
('bob', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'bob@example.com', '+8613900139000', 'Bob', 1, 'user', NOW(), '192.168.1.101', 12),
('charlie', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'charlie@example.com', '+8613700137000', 'Charlie', 2, 'guest', NULL, NULL, 0);

INSERT INTO model_config (user_id, model_name, provider, api_key_encrypted, base_url, max_tokens, prompt, is_enabled, display_name, sort_order)
VALUES
(NULL, 'deepseek-chat', 'deepseek', 'demo_key_global', 'https://api.deepseek.com/v1', 4096, '你是一个通用的AI助手。', 1, '通用助手', 0),
(1, 'gpt-4o', 'openai', 'demo_key_alice', 'https://api.openai.com/v1', 8192, '你是专业法律顾问，回答需引用法条。', 1, '法律顾问', 1),
(1, 'claude-3-5-sonnet', 'anthropic-compatible', 'demo_key_alice_2', 'https://api.example.com/v1', 4096, '你是资深全栈工程师，提供代码示例。', 1, '代码助手', 2),
(2, 'deepseek-chat', 'deepseek', 'demo_key_bob', 'https://api.deepseek.com/v1', 2048, '你是一个友善的聊天伙伴。', 1, '聊天伙伴', 0),
(NULL, 'text-embedding-3-small', 'openai', 'demo_embedding_key', 'https://api.openai.com/v1', 2048, '嵌入模型配置', 0, 'OpenAI Embedding', 10);

INSERT INTO memory_config (user_id, strategy_type, window_size, summary_trigger_tokens, summary_max_length, enable_rag, rag_collection_name, rag_top_k, max_history_messages, enable_long_term_memory, compression_interval, reserve_system_prompt, is_enabled)
VALUES
(1, 'hybrid', 15, 2048, 300, 1, 'legal_docs', 3, 100, 1, 10, 1, 1),
(2, 'sliding_window', 10, 0, 0, 0, NULL, 3, 50, 0, 5, 1, 1),
(3, 'summary', 5, 1024, 200, 0, NULL, 3, 30, 0, 8, 1, 0);

INSERT INTO rag (user_id, filename, file_path, file_size, file_type, collection_name, chunk_count, chunk_size, chunk_overlap, chunk_method, content, status, description, is_enabled, embedding_model_id)
VALUES
(1, '民法典.txt', './uploads/legal/civil_code.txt', 524288, 'text/plain', 'legal_docs', 3, 500, 50, 'fixed_size', '合同违约责任示例片段一\n---CHUNK---\n损害赔偿范围示例片段二\n---CHUNK---\n诉讼时效示例片段三', 'completed', '中国民法典示例片段', 1, 5),
(1, '公司法注释.md', './uploads/legal/company_law.md', 104857, 'text/markdown', 'legal_docs', 2, 500, 50, 'paragraph', '公司法司法解释片段一\n---CHUNK---\n股东责任片段二', 'pending', '公司法司法解释', 1, NULL),
(2, '旅行指南.md', './uploads/travel/guide.md', 20480, 'text/markdown', 'travel_knowledge', 2, 500, 50, 'paragraph', '欧洲旅行路线建议\n---CHUNK---\n申根签证材料建议', 'completed', '欧洲旅行攻略', 1, 5),
(3, '产品手册.txt', './uploads/products/manual.txt', 314572, 'text/plain', 'product_docs', 1, 500, 50, 'fixed_size', '产品故障排查示例', 'processing', '产品使用手册', 0, NULL);

INSERT INTO `session` (session_name, title, user_id, model_id, memory_id, rag_id)
VALUES
('legal-demo', '法律咨询', 1, 2, 1, 1),
('code-demo', '代码审查讨论', 1, 3, 1, NULL),
('chat-demo', '日常闲聊', 2, 4, 2, NULL),
('travel-demo', '旅行规划', 2, 4, 2, 3),
('product-demo', '产品反馈', 3, 1, 3, 4);

INSERT INTO message (session_id, user_message, ai_response, tokens_used)
VALUES
(1, '你好，我想咨询一下合同违约的赔偿标准。', '您好！违约赔偿通常以实际损失和可得利益为基础，具体还要结合合同约定与证据。', 256),
(1, '如果对方拒绝赔偿，我应该如何维权？', '可以先发送书面催告并固定证据，协商不成时再考虑仲裁或诉讼。', 189),
(2, '请帮我review这段Vue3代码。', '建议拆分复杂逻辑，保持组件职责清晰，并补充交互测试。', 345),
(3, '今天天气真好！', '是啊，适合出去走走。你今天有什么计划吗？', 45),
(4, '我计划下个月去欧洲旅行，有什么推荐？', '预算有限可以考虑捷克、匈牙利等地，也建议提前准备申根签证材料。', 198),
(5, '我购买的产品无法开机。', '请先尝试长按电源键强制重启，并检查充电器和数据线是否正常。', 187);
