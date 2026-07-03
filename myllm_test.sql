-- ============================================
-- MyLLM 数据库测试用例
-- 创建时间: 2026-07-03
-- ============================================

USE myllm_db;

-- ============================================
-- 测试 1: 角色人设 CRUD 操作
-- ============================================

-- 1.1 查询所有角色人设
SELECT * FROM character_persona;

-- 1.2 按温度参数筛选 (温度越高创造力越强)
SELECT name, temperature, target_collection
FROM character_persona
WHERE temperature > 0.5
ORDER BY temperature DESC;

-- 1.3 插入新角色人设
INSERT INTO character_persona (name, system_prompt, temperature, target_collection)
VALUES ('产品经理', '你是一位经验丰富的产品经理，擅长需求分析、PRD 撰写和项目管理。请帮助用户梳理产品思路，提供专业的建议。', 0.5, 'product_knowledge');

-- 1.4 更新角色人设的系统提示词
UPDATE character_persona
SET system_prompt = '你是一名专业的法律顾问，精通中国法律法规，尤其擅长合同法和劳动法。请根据用户提供的法律问题，给出专业、准确的法律建议。'
WHERE name = '法律顾问';

-- 1.5 删除测试角色
DELETE FROM character_persona WHERE name = '产品经理';

-- ============================================
-- 测试 2: 聊天记录查询
-- ============================================

-- 2.1 查询某个角色的所有聊天记录
SELECT ch.user_message, ch.ai_response, ch.tokens_used, ch.created_at
FROM chat_history ch
JOIN character_persona cp ON ch.persona_id = cp.id
WHERE cp.name = '法律顾问'
ORDER BY ch.created_at DESC;

-- 2.2 统计每个角色的聊天次数和总 token 消耗
SELECT * FROM v_chat_statistics;

-- 2.3 查询 token 消耗最高的聊天记录
SELECT cp.name AS persona_name, ch.user_message, ch.tokens_used
FROM chat_history ch
JOIN character_persona cp ON ch.persona_id = cp.id
ORDER BY ch.tokens_used DESC
LIMIT 3;

-- 2.4 模拟插入新聊天记录
INSERT INTO chat_history (persona_id, user_message, ai_response, tokens_used)
VALUES (4, '如何实现 JWT 认证？', 'JWT 认证实现步骤：1. 用户登录获取 token 2. 后续请求在 Header 中携带 token 3. 服务端验证 token 有效性', 180);

-- ============================================
-- 测试 3: 知识库文档管理
-- ============================================

-- 3.1 查询所有已完成的知识库文档
SELECT filename, collection_name, chunk_count, file_size
FROM knowledge_document
WHERE status = 'completed';

-- 3.2 按知识库集合统计文档数量和总切片数
SELECT * FROM v_knowledge_statistics;

-- 3.3 查询正在处理中的文档
SELECT filename, status, created_at
FROM knowledge_document
WHERE status = 'processing';

-- 3.4 模拟文档处理完成
UPDATE knowledge_document
SET status = 'completed', chunk_count = 210
WHERE filename = '心理学入门.pdf';

-- 3.5 插入新知识库文档
INSERT INTO knowledge_document (filename, file_path, file_size, collection_name, chunk_count, status)
VALUES ('Python高级编程.pdf', '/uploads/tech/2026/07/03/Python高级编程.pdf', 4194304, 'tech_knowledge', 320, 'completed');

-- ============================================
-- 测试 4: RAG 配置管理
-- ============================================

-- 4.1 查询所有启用的 RAG 配置
SELECT id, title, model_name, theme_color, max_words
FROM rag_config
WHERE is_active = 1;

-- 4.2 查询某个模型的所有配置
SELECT title, theme_color, max_words, rules
FROM rag_config
WHERE model_name = 'DeepSeek-Chat';

-- 4.3 插入新的 RAG 配置
INSERT INTO rag_config (title, model_name, theme_color, max_words, rules, is_active)
VALUES ('翻译助手配置', 'GPT-4o', '#16a34a', 1000, '翻译准确自然；保持原文语境和语气；提供多种翻译版本；标注专业术语', 1);

-- 4.4 禁用某个配置
UPDATE rag_config
SET is_active = 0
WHERE title = '英语教学配置';

-- ============================================
-- 测试 5: 模型配置管理
-- ============================================

-- 5.1 查询所有启用的模型
SELECT model_name, provider, max_tokens, is_default
FROM model_config
WHERE is_enabled = 1;

-- 5.2 查询默认模型
SELECT model_name, provider, base_url
FROM model_config
WHERE is_default = 1;

-- 5.3 按提供商分组统计模型数量
SELECT provider, COUNT(*) AS model_count
FROM model_config
GROUP BY provider
ORDER BY model_count DESC;

-- 5.4 切换默认模型
UPDATE model_config SET is_default = 0 WHERE is_default = 1;
UPDATE model_config SET is_default = 1 WHERE model_name = 'GPT-4o';

-- ============================================
-- 测试 6: 跨表联查
-- ============================================

-- 6.1 查询角色人设及其最近的聊天记录
SELECT
    cp.name AS persona_name,
    cp.temperature,
    ch.user_message,
    ch.ai_response,
    ch.created_at
FROM character_persona cp
LEFT JOIN chat_history ch ON cp.id = ch.persona_id
ORDER BY ch.created_at DESC;

-- 6.2 查询知识库文档与对应 RAG 配置的关联
SELECT
    kd.filename,
    kd.collection_name,
    kd.chunk_count,
    rc.title AS rag_config_title,
    rc.model_name
FROM knowledge_document kd
LEFT JOIN rag_config rc ON kd.collection_name = rc.title
ORDER BY kd.collection_name;

-- 6.3 综合统计：每个角色的人均聊天量和 token 消耗
SELECT
    cp.name,
    COUNT(ch.id) AS total_chats,
    SUM(ch.tokens_used) AS total_tokens,
    ROUND(AVG(ch.tokens_used), 0) AS avg_tokens_per_chat,
    MIN(ch.created_at) AS first_chat,
    MAX(ch.created_at) AS last_chat
FROM character_persona cp
INNER JOIN chat_history ch ON cp.id = ch.persona_id
GROUP BY cp.id, cp.name
HAVING total_chats >= 1
ORDER BY total_tokens DESC;

-- ============================================
-- 测试 7: 数据完整性验证
-- ============================================

-- 7.1 验证所有外键约束是否正常
SELECT
    ch.id,
    ch.persona_id,
    CASE WHEN cp.id IS NULL THEN '外键断裂' ELSE '正常' END AS fk_status
FROM chat_history ch
LEFT JOIN character_persona cp ON ch.persona_id = cp.id;

-- 7.2 验证索引是否生效（查看表索引）
SHOW INDEX FROM character_persona;
SHOW INDEX FROM chat_history;
SHOW INDEX FROM knowledge_document;
SHOW INDEX FROM rag_config;
SHOW INDEX FROM model_config;

-- 7.3 验证唯一约束
-- 以下语句应该报错（重复插入相同 model_name）
-- INSERT INTO model_config (model_name, provider, base_url) VALUES ('DeepSeek-Chat', 'deepseek', 'https://api.deepseek.com');

-- ============================================
-- 测试 8: 边界条件测试
-- ============================================

-- 8.1 测试 NULL 值处理
INSERT INTO character_persona (name, system_prompt, temperature, target_collection)
VALUES ('临时角色', NULL, NULL, NULL);

SELECT * FROM character_persona WHERE system_prompt IS NULL;

-- 8.2 测试空字符串
INSERT INTO character_persona (name, system_prompt, temperature, target_collection)
VALUES ('空提示角色', '', 0.5, '');

SELECT * FROM character_persona WHERE system_prompt = '';

-- 8.3 测试超长文本
INSERT INTO character_persona (name, system_prompt, temperature, target_collection)
VALUES ('长文本角色', REPEAT('测试文本内容。', 100), 0.7, 'test_collection');

-- 8.4 清理边界测试数据
DELETE FROM character_persona WHERE name IN ('临时角色', '空提示角色', '长文本角色');
DELETE FROM character_persona WHERE target_collection = 'product_knowledge';
DELETE FROM rag_config WHERE title = '翻译助手配置';
DELETE FROM knowledge_document WHERE filename = 'Python高级编程.pdf';
DELETE FROM chat_history WHERE user_message = '如何实现 JWT 认证？';

-- 恢复默认模型
UPDATE model_config SET is_default = 0 WHERE is_default = 1;
UPDATE model_config SET is_default = 1 WHERE model_name = 'DeepSeek-Chat';
-- 恢复英语教学配置状态
UPDATE rag_config SET is_active = 0 WHERE title = '英语教学配置';
-- 恢复心理学入门文档状态
UPDATE knowledge_document SET status = 'processing', chunk_count = 210 WHERE filename = '心理学入门.pdf';
