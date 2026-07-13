-- ============================================
-- MyLLM 数据库初始化脚本
-- 创建数据库: myllm_db
-- 创建时间: 2026-07-03
-- mysql -u root -p< myllm_db.sql
-- ============================================

-- 1. 创建数据库
-- ============================================
DROP DATABASE IF EXISTS myllm_db;
CREATE DATABASE myllm_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE myllm_db;

-- 2. 创建表
-- ============================================
-- ============================================
-- 1. 用户表 (User) - 保持原样
-- ============================================
CREATE TABLE User (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    username        VARCHAR(50)  NOT NULL                COMMENT '用户名',
    password_hash   VARCHAR(255) NOT NULL                COMMENT '密码哈希',
    email           VARCHAR(100)                         COMMENT '电子邮箱',
    phone           VARCHAR(20)                          COMMENT '手机号码',
    nickname        VARCHAR(100)                         COMMENT '昵称',
    avatar_path     VARCHAR(500)                         COMMENT '头像存储路径',
    avatar_url      VARCHAR(500)                         COMMENT '头像URL',
    status          TINYINT     DEFAULT 1                COMMENT '状态：1启用 0禁用 2待验证 3冻结',
    role            VARCHAR(20) DEFAULT 'user'           COMMENT '角色',
    last_login_at   DATETIME                             COMMENT '最后登录时间',
    last_login_ip   VARCHAR(45)                          COMMENT '最后登录IP',
    login_count     INT         DEFAULT 0                COMMENT '登录次数',
    created_at      DATETIME    DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at      DATETIME    NULL                     COMMENT '软删除时间',
    UNIQUE INDEX uk_username (username),
    UNIQUE INDEX uk_email (email),
    UNIQUE INDEX uk_phone (phone),
    INDEX idx_status (status),
    INDEX idx_role (role),
    INDEX idx_deleted_at (deleted_at),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ============================================
-- 2. 模型配置表 (ModelConfig) - 添加外键，统一字段名
-- ============================================
CREATE TABLE ModelConfig (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '模型配置ID',
    UserId              BIGINT                               COMMENT '所属用户ID（NULL表示全局默认）',
    model_name          VARCHAR(100) NOT NULL                COMMENT '模型名称',
    provider            VARCHAR(50)  NOT NULL                COMMENT '提供商',
    api_key_encrypted   TEXT                                 COMMENT '加密API Key',
    base_url            VARCHAR(500)                         COMMENT 'API基础URL',
    max_tokens          INT          DEFAULT 4096            COMMENT '最大输出Token数',
    prompt              TEXT                                 COMMENT '系统提示词模板',
    is_enabled          TINYINT(1)   DEFAULT 1               COMMENT '是否启用',
    created_at          DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- 外键：用户删除时，该用户的模型配置一并删除（业务上用户自建模型）
    CONSTRAINT fk_model_user FOREIGN KEY (UserId) REFERENCES User(id) ON DELETE CASCADE,

    -- 允许每个用户拥有唯一的模型名
    UNIQUE INDEX uk_user_model (UserId, model_name),
    INDEX idx_userid (UserId),
    INDEX idx_provider (provider)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型配置表';

-- ============================================
-- 3. 记忆配置表 (MemoryConfig) - 基本不变
-- ============================================
CREATE TABLE MemoryConfig (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    UserId                   BIGINT NOT NULL,
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
    created_at               DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at               DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_memory_user FOREIGN KEY (UserId) REFERENCES User(id) ON DELETE CASCADE,
    UNIQUE INDEX uk_user_id (UserId),
    INDEX idx_strategy (strategy_type),
    INDEX idx_rag_collection (rag_collection_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='LLM记忆配置表';

-- ============================================
-- 4. 知识库文档表 (Rag) - 基本不变，仅统一字段命名
-- ============================================
CREATE TABLE Rag (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    UserId          BIGINT,
    filename        VARCHAR(500)  NOT NULL,
    file_path       TEXT,
    file_size       BIGINT        DEFAULT 0,
    file_type       VARCHAR(100),
    collection_name VARCHAR(255)  NOT NULL,
    chunk_count     INT           DEFAULT 0,
    status          VARCHAR(20)   DEFAULT 'processing',
    description     VARCHAR(500),
    created_at      DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_rag_user FOREIGN KEY (UserId) REFERENCES User(id) ON DELETE SET NULL,
    INDEX idx_user_id (UserId),
    INDEX idx_collection (collection_name),
    INDEX idx_status (status),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库文档表';

-- ============================================
-- 5. 会话表 (Session) - 新增，替代history中的冗余字段
-- ============================================
CREATE TABLE Session (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    UserId          BIGINT,
    session_name    VARCHAR(255) NOT NULL COMMENT '会话名称',
    ModelId         BIGINT                               COMMENT '使用的模型配置ID',
    MemoryId        BIGINT                               COMMENT '关联的记忆配置ID',
    RagId           BIGINT                               COMMENT '关联的RAG配置ID（可绑定特定知识库）',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_session_user FOREIGN KEY (UserId) REFERENCES User(id) ON DELETE SET NULL,
    CONSTRAINT fk_session_model FOREIGN KEY (ModelId) REFERENCES ModelConfig(id) ON DELETE SET NULL,
    CONSTRAINT fk_session_memory FOREIGN KEY (MemoryId) REFERENCES MemoryConfig(id) ON DELETE SET NULL,
    CONSTRAINT fk_session_rag FOREIGN KEY (RagId) REFERENCES Rag(id) ON DELETE SET NULL,  -- 注意：这里引用Rag表

    INDEX idx_user_id (UserId),
    INDEX idx_model_id (ModelId),
    INDEX idx_memory_id (MemoryId),
    INDEX idx_rag_id (RagId),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天会话表';

-- ============================================
-- 6. 消息表 (Message) - 存储实际对话内容
-- ============================================
CREATE TABLE Message (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    SessionId       BIGINT NOT NULL COMMENT '所属会话ID',
    user_message    TEXT   NOT NULL COMMENT '用户发送的消息',
    ai_response     TEXT   COMMENT 'AI回复内容',
    tokens_used     INT    DEFAULT 0 COMMENT '本次交互消耗Token数',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_message_session FOREIGN KEY (SessionId) REFERENCES Session(id) ON DELETE CASCADE,

    INDEX idx_session_id (SessionId),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天消息表';

-- ============================================
-- 可选：保留旧history表改为视图或废弃，建议删除
-- ============================================
-- DROP TABLE IF EXISTS history;  -- 如果已存在，可迁移数据后删除
-- 3. 插入测试数据
-- ============================================

-- ============================================
-- 测试数据插入
-- ============================================

-- 1. 用户数据 (3个用户)
INSERT INTO User (username, password_hash, email, phone, nickname, status, role, last_login_at, last_login_ip, login_count)
VALUES
('alice', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'alice@example.com', '+8613800138000', 'Alice', 1, 'admin', NOW(), '192.168.1.100', 45),
('bob', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'bob@example.com', '+8613900139000', 'Bob', 1, 'user', NOW(), '192.168.1.101', 12),
('charlie', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'charlie@example.com', '+8613700137000', 'Charlie', 2, 'guest', NULL, NULL, 0);

-- 2. 模型配置 (每个用户自定义或全局默认)
INSERT INTO ModelConfig (UserId, model_name, provider, api_key_encrypted, base_url, max_tokens, prompt, is_enabled)
VALUES
(NULL, 'DeepSeek-Chat', 'deepseek', 'encrypted_key_global', 'https://api.deepseek.com', 4096, '你是一个通用的AI助手。', 1),
(1, 'Legal-Advisor', 'openai', 'encrypted_key_alice', 'https://api.openai.com/v1', 8192, '你是专业法律顾问，回答需引用法条。', 1),
(1, 'Code-Assistant', 'anthropic', 'encrypted_key_alice_2', 'https://api.anthropic.com', 4096, '你是资深全栈工程师，提供代码示例。', 1),
(2, 'Friendly-Chat', 'deepseek', 'encrypted_key_bob', 'https://api.deepseek.com', 2048, '你是一个友善的聊天伙伴。', 1),
(NULL, 'GPT-4o', 'openai', 'encrypted_key_global2', 'https://api.openai.com/v1', 4096, '通用GPT模型', 0); -- 禁用

-- 3. 记忆配置 (每个用户一条)
INSERT INTO MemoryConfig (UserId, strategy_type, window_size, summary_trigger_tokens, summary_max_length, enable_rag, rag_collection_name, rag_top_k, max_history_messages, enable_long_term_memory, compression_interval, reserve_system_prompt)
VALUES
(1, 'hybrid', 15, 2048, 300, 1, 'alice_long_term', 3, 100, 1, 10, 1),
(2, 'sliding_window', 10, 0, 0, 0, NULL, 3, 50, 0, 5, 1),
(3, 'summary', 5, 1024, 200, 0, NULL, 3, 30, 0, 8, 1);

-- 4. 知识库文档 (Rag)
INSERT INTO Rag (UserId, filename, file_path, file_size, file_type, collection_name, chunk_count, status, description)
VALUES
(1, '民法典.pdf', '/uploads/legal/civil_code.pdf', 5242880, 'application/pdf', 'legal_docs', 120, 'completed', '中国民法典全文'),
(1, '公司法注释.docx', '/uploads/legal/company_law.docx', 1048576, 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 'legal_docs', 45, 'completed', '公司法司法解释'),
(2, '旅行指南.md', '/uploads/travel/guide.md', 20480, 'text/markdown', 'travel_knowledge', 8, 'completed', '欧洲旅行攻略'),
(3, '产品手册.pdf', '/uploads/products/manual.pdf', 3145728, 'application/pdf', 'product_docs', 60, 'processing', '产品使用手册（处理中）');

-- 5. 会话 (Session)
INSERT INTO Session (UserId, session_name, ModelId, MemoryId, RagId)
VALUES
(1, '法律咨询 - 合同纠纷', 2, 1, 1),   -- Alice用法律顾问模型，记忆配置1，RAG文档1
(1, '代码审查讨论', 3, 1, NULL),      -- Alice用代码助手，无RAG
(2, '日常闲聊', 4, 2, NULL),          -- Bob用友好聊天
(2, '旅行规划', 4, 2, 2),             -- Bob使用旅行知识库
(3, '产品反馈', 1, 3, 3);             -- Charlie用默认模型，记忆配置3，RAG文档3

-- 6. 消息 (Message) - 每个会话至少5条，模拟对话
-- 会话1: 法律咨询
INSERT INTO Message (SessionId, user_message, ai_response, tokens_used)
VALUES
(1, '你好，我想咨询一下合同违约的赔偿标准。', '您好！根据《民法典》第584条，违约赔偿应相当于因违约造成的损失，包括合同履行后可获得的利益，但不得超过违约方订立合同时预见的损失。具体数额需结合合同约定和实际损失计算。', 256),
(1, '如果对方拒绝赔偿，我应该如何维权？', '您可以先发送书面催告函，保留证据。若对方仍不履行，可向法院提起诉讼。诉讼时效为三年，请注意及时主张权利。', 189),
(1, '诉讼费用大概多少？', '诉讼费根据索赔金额按比例收取，例如10万元以下案件受理费约2.5%。具体可参考《诉讼费用交纳办法》。', 134),
(1, '我需要准备哪些材料？', '需要准备起诉状、身份证明、合同原件及复印件、违约证据（如催告函、聊天记录）、损失计算明细等。', 201),
(1, '好的，非常感谢！', '不客气，如有进一步问题，欢迎随时咨询。', 78);

-- 会话2: 代码审查
INSERT INTO Message (SessionId, user_message, ai_response, tokens_used)
VALUES
(2, '请帮我review这段Vue3代码：<template>...</template>', '代码结构清晰，但建议将`computed`中的复杂逻辑拆分到单独函数，并添加`watch`监听props变化。另外注意使用`<script setup>`语法更简洁。', 345),
(2, '如何优化渲染性能？', '可以使用`v-once`、`v-memo`，避免不必要的重渲染。对于列表，使用`key`并避免使用index作为key。', 210),
(2, '组件间通信有什么最佳实践？', '父传子用props，子传父用emit。跨层级用provide/inject或Pinia状态管理。避免深层prop传递。', 278),
(2, '好的，我明白了。谢谢！', '不客气，如有具体代码问题可以发我详细审查。', 92),
(2, '再问一下，如何做单元测试？', '推荐使用Vitest + Vue Test Utils，测试组件渲染、事件触发和响应式数据。可以编写快照测试和交互测试。', 310);

-- 会话3: 日常闲聊
INSERT INTO Message (SessionId, user_message, ai_response, tokens_used)
VALUES
(3, '今天天气真好！', '是啊，阳光明媚，适合出去走走。你今天有什么计划吗？', 45),
(3, '我打算去公园野餐。', '太棒了！记得带上防晒霜和足够的水。野餐的话，三明治和水果是不错的选择。', 67),
(3, '有什么推荐的音乐播放列表？', '轻快的民谣或爵士乐很适合户外。可以在音乐平台搜索“春日野餐”歌单。', 82),
(3, '好的，谢谢你的建议！', '不客气，享受你的美好时光！', 39),
(3, '我准备出发了，再见！', '再见！祝玩得开心！', 28);

-- 会话4: 旅行规划
INSERT INTO Message (SessionId, user_message, ai_response, tokens_used)
VALUES
(4, '我计划下个月去欧洲旅行，有什么推荐的国家？', '意大利、法国、瑞士都很受欢迎。意大利有丰富的历史遗迹，法国有浪漫氛围，瑞士自然风光绝美。', 198),
(4, '预算有限，哪个国家性价比高？', '可以考虑东欧国家如捷克、匈牙利，消费较低且风景优美。或者选择淡季出行，提前预订机票住宿。', 213),
(4, '签证办理复杂吗？', '申根签证需提前准备材料，包括行程单、酒店预订、保险等。建议至少提前一个月申请，找专业代理可节省时间。', 256),
(4, '有哪些必带的物品？', '护照、转换插头、常用药品、信用卡、少量现金、充电宝。下载离线地图也很重要。', 178),
(4, '非常感谢！我会参考你的建议。', '祝你旅途愉快！有更多问题可以随时问我。', 64);

-- 会话5: 产品反馈
INSERT INTO Message (SessionId, user_message, ai_response, tokens_used)
VALUES
(5, '我购买的产品出现故障，无法开机。', '非常抱歉给您带来困扰。请先尝试长按电源键10秒强制重启，如果无效，请检查充电器和数据线是否正常。', 187),
(5, '重启后还是不行，屏幕没反应。', '可能涉及硬件问题，建议联系售后维修。您可以通过官网提交工单或拨打客服热线400-XXX-XXXX。', 165),
(5, '维修需要多久？', '一般维修周期为7-15个工作日，具体视配件库存而定。您也可以选择寄修服务。', 132),
(5, '我发起了维修申请，等待审批。', '好的，审批通过后会短信通知您，请保持手机畅通。期间如有问题可随时联系。', 112),
(5, '好的，谢谢。', '不客气，我们会尽快为您处理。', 45);
