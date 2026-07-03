-- ============================================
-- MyLLM 数据库初始化脚本
-- 创建数据库: myllm_db
-- 创建时间: 2026-07-03
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

-- 2.1 角色人设表 (character_persona)
CREATE TABLE character_persona (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL COMMENT '角色名称',
    system_prompt TEXT COMMENT '核心系统提示词',
    temperature DOUBLE DEFAULT 0.7 COMMENT '创造力参数 (0.0-1.0)',
    target_collection VARCHAR(255) COMMENT '绑定的 Chroma 向量集合名称',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_name (name),
    INDEX idx_target_collection (target_collection)
) ENGINE=InnoDB COMMENT='角色人设配置表';

-- 2.2 聊天记录表 (chat_history)
CREATE TABLE chat_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    persona_id BIGINT COMMENT '关联的角色人设 ID',
    user_message TEXT NOT NULL COMMENT '用户发送的消息',
    ai_response TEXT COMMENT 'AI 返回的回复',
    tokens_used INT DEFAULT 0 COMMENT '消耗的 token 数量',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (persona_id) REFERENCES character_persona(id) ON DELETE SET NULL,
    INDEX idx_persona_id (persona_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB COMMENT='聊天记录表';

-- 2.3 知识库文档表 (knowledge_document)
CREATE TABLE knowledge_document (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    filename VARCHAR(500) NOT NULL COMMENT '原始文件名',
    file_path VARCHAR(1000) NOT NULL COMMENT '服务器存储路径',
    file_size BIGINT DEFAULT 0 COMMENT '文件大小 (字节)',
    collection_name VARCHAR(255) NOT NULL COMMENT 'Chroma 向量集合名称',
    chunk_count INT DEFAULT 0 COMMENT '文档切片数量',
    status VARCHAR(20) DEFAULT 'processing' COMMENT '状态: processing/completed/failed',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_collection_name (collection_name),
    INDEX idx_status (status)
) ENGINE=InnoDB COMMENT='知识库文档表';

-- 2.4 RAG 配置表 (rag_config)
CREATE TABLE rag_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL COMMENT '配置标题',
    model_name VARCHAR(100) DEFAULT 'DeepSeek-Chat' COMMENT '使用的模型名称',
    api_key_encrypted VARCHAR(500) COMMENT '加密存储的 API Key',
    base_url VARCHAR(500) COMMENT 'API 基础 URL',
    theme_color VARCHAR(20) DEFAULT '#1e3a8a' COMMENT '界面主题颜色 (HEX)',
    max_words INT DEFAULT 500 COMMENT 'AI 最大输出字数',
    rules TEXT COMMENT 'RAG 检索规则 / Prompt 模板',
    is_active TINYINT(1) DEFAULT 1 COMMENT '是否启用: 1=启用, 0=禁用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_model_name (model_name),
    INDEX idx_is_active (is_active)
) ENGINE=InnoDB COMMENT='RAG 规则配置表';

-- 2.5 模型配置表 (model_config)
CREATE TABLE model_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    model_name VARCHAR(100) NOT NULL COMMENT '模型名称',
    provider VARCHAR(50) NOT NULL COMMENT '提供商: openai/ollama/deepseek',
    api_key_encrypted VARCHAR(500) COMMENT '加密存储的 API Key',
    base_url VARCHAR(500) COMMENT 'API 基础 URL',
    max_tokens INT DEFAULT 4096 COMMENT '最大 token 数',
    is_default TINYINT(1) DEFAULT 0 COMMENT '是否默认模型',
    is_enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX uk_model_name (model_name),
    INDEX idx_provider (provider)
) ENGINE=InnoDB COMMENT='模型配置表';

-- 3. 插入测试数据
-- ============================================

-- 3.1 角色人设测试数据
INSERT INTO character_persona (name, system_prompt, temperature, target_collection) VALUES
('法律顾问', '你是一名专业的法律顾问，精通中国法律法规。请根据用户提供的法律问题，给出专业、准确的法律建议。回答时请引用相关法律条文，并注意保护当事人隐私。', 0.3, 'legal_knowledge'),
('文案大师', '你是一位创意文案专家，擅长撰写各种营销文案、广告语、社交媒体内容。请根据用户需求，创作富有感染力和传播力的文案内容。', 0.8, 'marketing_knowledge'),
('心理咨询师', '你是一位经验丰富的心理咨询师，擅长倾听和共情。请用温暖、理解的语气回应用户，帮助他们梳理情绪，但不要给出医学诊断建议。必要时建议寻求专业帮助。', 0.6, 'psychology_knowledge'),
('代码助手', '你是一位资深的全栈开发工程师，精通 Java、Python、JavaScript、Vue、React 等技术栈。请帮助用户解决编程问题，提供清晰的代码示例和最佳实践建议。', 0.4, 'tech_knowledge'),
('英语教师', '你是一位专业的英语教师，擅长英语语法、词汇、口语教学。请用简单易懂的方式解释英语知识点，提供例句和练习建议。', 0.5, NULL);

-- 3.2 聊天记录测试数据
INSERT INTO chat_history (persona_id, user_message, ai_response, tokens_used) VALUES
(1, '什么是合同违约？', '合同违约是指合同当事人一方或双方不履行合同义务，或者履行合同义务不符合约定的行为。根据《中华人民共和国民法典》第五百七十七条，当事人一方不履行合同义务或者履行合同义务不符合约定的，应当承担继续履行、采取补救措施或者赔偿损失等违约责任。', 256),
(1, '租房押金不退怎么办？', '如果房东无故不退押金，您可以采取以下措施：1. 保留租房合同、押金收据等证据；2. 与房东协商解决；3. 向当地住建部门投诉；4. 向人民法院提起诉讼。', 312),
(2, '帮我写一个咖啡店的广告语', '【晨光咖啡】\n\n每一杯，都是清晨的仪式感。\n\n当第一缕阳光洒进窗台，\n让咖啡的香气唤醒你的灵魂。\n\n晨光咖啡 —— 用一杯好咖啡，\n开启你元气满满的一天。', 128),
(3, '最近工作压力很大，感觉很焦虑', '我理解你现在的心情，工作压力确实会让人感到焦虑和疲惫。首先，你的感受是完全正常的。可以尝试以下方法缓解：1. 深呼吸练习 2. 适当运动 3. 与信任的人倾诉。', 198),
(4, 'Vue 3 的 Composition API 有什么优势？', 'Vue 3 Composition API 的主要优势包括：1. 更好的代码组织 2. 更好的类型推导 3. 逻辑复用（Composables） 4. 更小的打包体积（Tree-shaking）。', 289);

-- 3.3 知识库文档测试数据
INSERT INTO knowledge_document (filename, file_path, file_size, collection_name, chunk_count, status) VALUES
('民法典全文.pdf', '/uploads/legal/2026/07/01/民法典全文.pdf', 5242880, 'legal_knowledge', 1250, 'completed'),
('劳动合同法.txt', '/uploads/legal/2026/07/01/劳动合同法.txt', 524288, 'legal_knowledge', 85, 'completed'),
('营销案例集.pdf', '/uploads/marketing/2026/07/02/营销案例集.pdf', 10485760, 'marketing_knowledge', 320, 'completed'),
('Vue3官方文档.pdf', '/uploads/tech/2026/07/02/Vue3官方文档.pdf', 8388608, 'tech_knowledge', 580, 'completed'),
('心理学入门.pdf', '/uploads/psychology/2026/07/03/心理学入门.pdf', 3145728, 'psychology_knowledge', 210, 'processing');

-- 3.4 RAG 配置测试数据
INSERT INTO rag_config (title, model_name, theme_color, max_words, rules, is_active) VALUES
('法律顾问配置', 'DeepSeek-Chat', '#1e3a8a', 800, '优先匹配法律条文原文；引用时标注具体条款编号；区分民事、刑事、行政案件类型；语言专业但通俗易懂', 1),
('创意文案配置', 'GPT-4o', '#ea580c', 500, '文案风格活泼有感染力；适当使用网络流行语；控制段落长度；提供多个版本供选择', 1),
('代码助手配置', 'Claude-3.5-Sonnet', '#0284c7', 1000, '代码注释使用中文；提供完整可运行示例；标注技术栈版本；给出最佳实践建议', 1),
('心理咨询配置', 'DeepSeek-Chat', '#0d9488', 600, '语气温暖共情；不使用专业术语；避免直接给建议；引导用户自我探索；必要时建议专业帮助', 1),
('英语教学配置', 'Gemini-1.5-Pro', '#7c3aed', 500, '解释简单易懂；提供音标标注；给出多个例句；区分正式与非正式用法', 0);

-- 3.5 模型配置测试数据
INSERT INTO model_config (model_name, provider, base_url, max_tokens, is_default, is_enabled) VALUES
('DeepSeek-Chat', 'deepseek', 'https://api.deepseek.com', 4096, 1, 1),
('DeepSeek-Coder', 'deepseek', 'https://api.deepseek.com', 8192, 0, 1),
('GPT-4o', 'openai', 'https://api.openai.com/v1', 4096, 0, 1),
('GPT-4-turbo', 'openai', 'https://api.openai.com/v1', 4096, 0, 1),
('Claude-3.5-Sonnet', 'anthropic', 'https://api.anthropic.com', 4096, 0, 1),
('Claude-3-Opus', 'anthropic', 'https://api.anthropic.com', 4096, 0, 1),
('Gemini-1.5-Pro', 'google', 'https://generativelanguage.googleapis.com', 8192, 0, 1),
('Gemini-1.5-Flash', 'google', 'https://generativelanguage.googleapis.com', 8192, 0, 1),
('Llama-3-70B', 'ollama', 'http://localhost:11434', 4096, 0, 1),
('Mistral-Large', 'mistral', 'https://api.mistral.ai', 4096, 0, 1),
('Qwen-2.5-72B', 'alibaba', 'https://dashscope.aliyuncs.com', 8192, 0, 1);

-- 4. 创建视图
-- ============================================

-- 4.1 聊天统计视图
CREATE OR REPLACE VIEW v_chat_statistics AS
SELECT
    cp.name AS persona_name,
    COUNT(ch.id) AS chat_count,
    SUM(ch.tokens_used) AS total_tokens,
    AVG(ch.tokens_used) AS avg_tokens,
    MAX(ch.created_at) AS last_chat_time
FROM chat_history ch
LEFT JOIN character_persona cp ON ch.persona_id = cp.id
GROUP BY cp.id, cp.name;

-- 4.2 知识库统计视图
CREATE OR REPLACE VIEW v_knowledge_statistics AS
SELECT
    kd.collection_name,
    COUNT(*) AS document_count,
    SUM(kd.chunk_count) AS total_chunks,
    SUM(kd.file_size) AS total_size_bytes,
    SUM(CASE WHEN kd.status = 'completed' THEN 1 ELSE 0 END) AS completed_count
FROM knowledge_document kd
GROUP BY kd.collection_name;

-- 5. 完成提示
-- ============================================
SELECT
    'MyLLM 数据库初始化完成！' AS message,
    '已创建 5 张表 + 2 个视图' AS tables_created,
    '已插入 5 条角色人设、5 条聊天记录、5 条知识库文档、5 条 RAG 配置、11 条模型配置' AS test_data_inserted;
