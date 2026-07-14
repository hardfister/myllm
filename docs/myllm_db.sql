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
    error_detail        TEXT,
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
    model_id        BIGINT,
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

-- 测试数据通过前端 UI 录入，注册后即可添加模型/知识库/记忆配置
