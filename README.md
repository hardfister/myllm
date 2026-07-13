# MyLLM - 大模型对话配置管理平台

> 最后更新: 2026-07-14

MyLLM 是一个全栈 LLM 对话应用，后端使用 Spring Boot，前端使用 Vue 3。项目支持多模型顺序调用、知识库上传与 Chroma 向量检索、记忆策略配置、JWT 登录注册、离线 localStorage 模式、历史会话持久化和主题色配置。

## 技术栈

### 后端 `myllm/`

| 技术 | 当前项目用途 |
|------|-------------|
| Java 21 | 后端运行环境 |
| Spring Boot 4.1.0 | Web、JPA、Security、Cache、Redis |
| Spring Web MVC | REST API |
| Spring Security | JWT 过滤器、BCrypt 密码哈希、CORS |
| Spring Data JPA | MySQL ORM |
| MySQL Connector/J | MySQL 8 驱动 |
| Redis | 缓存历史会话、知识库列表等 |
| LangChain4j OpenAI 0.33.0 | 调用 OpenAI 兼容 Chat API |
| JJWT 0.12.6 | JWT 生成和校验 |
| Jackson | 调 Chroma / Embedding REST API |
| Lombok | 实体和 DTO 样板代码 |

说明：当前 `pom.xml` 不再使用 Spring AI starter。聊天调用走 LangChain4j OpenAI 兼容接口；RAG 向量化和查询通过 `java.net.http.HttpClient` 调外部 Embedding API 与 Chroma REST API。

### 前端 `vue/myllm-ui/`

| 技术 | 当前项目用途 |
|------|-------------|
| Vue 3.5.x | UI 框架 |
| TypeScript ~6.0 | 类型检查 |
| Vite 8.x | 开发和构建 |
| Vue Router 5.x | 单页路由 |
| Axios 1.x | API 客户端和 JWT 拦截器 |
| lucide-vue-next | 图标 |

## 项目结构

```text
.
├── docs/
│   └── myllm_db.sql                         # MySQL 初始化脚本
├── myllm/                                   # Spring Boot 后端
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/example/myllm/
│       │   ├── config/                      # Security、JWT、Redis、AI 配置
│       │   ├── controller/                  # REST API
│       │   ├── model/
│       │   │   ├── dto/                     # 请求/响应 DTO
│       │   │   └── entity/                  # JPA 实体
│       │   ├── repository/                  # Spring Data Repository
│       │   ├── service/                     # 业务逻辑
│       │   ├── util/                        # JWT 工具
│       │   └── MyllmApplication.java
│       └── resources/
│           └── application.yml
├── vue/myllm-ui/                            # Vue 前端
│   ├── package.json
│   └── src/
│       ├── api/index.ts                     # Axios 实例和 API 函数
│       ├── router/index.ts
│       ├── services/                        # auth/localStorage
│       └── views/                           # 页面和弹窗
└── README.md
```

## 快速开始

### 环境要求

| 组件 | 建议版本 |
|------|----------|
| JDK | 21+ |
| Node.js | 22.18+ 或 24.12+ |
| MySQL | 8.0+ |
| Redis | 本地 `localhost:6379`，用于缓存 |
| Chroma | 可选，用于 RAG 向量检索 |

### 1. 初始化数据库

```bash
mysql -u root -p < docs/myllm_db.sql
```

脚本会创建 `myllm_db`，并创建 7 张表：`User`、`ModelConfig`、`MemoryConfig`、`Rag`、`Session`、`Message`、`character_persona`。

> 注意：示例用户使用的是演示 BCrypt hash。若登录失败，请通过注册接口新建用户，或在数据库中替换为你自己生成的 BCrypt 密码。

### 2. 配置后端

编辑 [application.yml](E:/document/1myweb/myllm/src/main/resources/application.yml)：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/myllm_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
    username: root
    password: your-mysql-password

  data:
    redis:
      host: localhost
      port: 6379

app:
  jwt:
    secret: myllm-jwt-secret-key-2026-please-change-this-in-production-environment
    expiration-ms: 86400000
```

### 3. 启动后端

```bash
cd myllm
mvn spring-boot:run
```

后端默认运行在 `http://localhost:8080`。

### 4. 启动前端

```bash
cd vue/myllm-ui
npm install
npm run dev
```

前端默认运行在 `http://localhost:5173`。

## 当前 API

### 认证

| Method | Path | Body | 说明 |
|--------|------|------|------|
| `POST` | `/api/auth/register` | `{username, password, email?, nickname?}` | 注册，返回 token 和用户信息 |
| `POST` | `/api/auth/login` | `{username, password}` | 登录，返回 token 和用户信息 |
| `GET` | `/api/auth/me` | - | 获取当前用户，需要 Bearer token |

### 数据同步

| Method | Path | Body | 说明 |
|--------|------|------|------|
| `POST` | `/api/sync/import` | `{models[], memories[], rags[]}` | 导入本地数据到云端 |

### 模型配置

| Method | Path | 说明 |
|--------|------|------|
| `GET` | `/api/models` | 获取模型配置 |
| `POST` | `/api/models` | 创建模型配置 |
| `PUT` | `/api/models/{id}` | 更新模型配置 |
| `DELETE` | `/api/models/{id}` | 删除模型配置 |
| `PUT` | `/api/models/{id}/toggle` | 启用/禁用该模型 |
| `PUT` | `/api/models/reorder` | 批量更新多模型调用顺序 |

示例：

```json
{
  "modelName": "gpt-4o",
  "provider": "openai",
  "apiKeyEncrypted": "sk-xxx",
  "baseUrl": "https://api.openai.com/v1",
  "maxTokens": 4096,
  "prompt": "你是一个友好的 AI 助手",
  "isEnabled": 1,
  "displayName": "写作助手",
  "sortOrder": 0
}
```

### 知识库文档

| Method | Path | 说明 |
|--------|------|------|
| `GET` | `/api/rags` | 获取知识库文档 |
| `POST` | `/api/rags` | 上传文档，`multipart/form-data` |
| `PUT` | `/api/rags/{id}` | 更新文档元数据或切片配置 |
| `DELETE` | `/api/rags/{id}` | 删除文档并清理磁盘文件 |
| `PUT` | `/api/rags/{id}/toggle` | 启用/禁用该知识库 |
| `POST` | `/api/rags/{id}/embed?modelId={modelId}` | 使用指定模型配置向量化文档 |

上传参数：

| 参数 | 说明 |
|------|------|
| `file` | 必填，上传文件 |
| `collectionName` | 可选，Chroma 集合名 |
| `chunkSize` | 可选，默认 500 |
| `chunkOverlap` | 可选，默认 50 |
| `chunkMethod` | 可选，`fixed_size` / `paragraph` / `sentence` |

### 记忆配置

| Method | Path | 说明 |
|--------|------|------|
| `GET` | `/api/memories` | 获取记忆配置 |
| `POST` | `/api/memories` | 创建记忆配置 |
| `PUT` | `/api/memories/{id}` | 更新记忆配置 |
| `DELETE` | `/api/memories/{id}` | 删除记忆配置 |
| `PUT` | `/api/memories/{id}/toggle` | 启用/禁用该记忆配置 |

### 聊天和历史

| Method | Path | Body | 说明 |
|--------|------|------|------|
| `POST` | `/api/chat` | `{content, sessionId?}` | 发送消息 |
| `POST` | `/api/chat/new` | - | 创建空会话 ID |
| `DELETE` | `/api/chat/{sessionId}` | - | 清除内存中的会话历史 |
| `GET` | `/api/sessions` | - | 获取历史会话列表 |
| `GET` | `/api/sessions/{sessionId}/messages` | - | 获取历史消息 |
| `DELETE` | `/api/sessions/{dbSessionId}` | - | 删除数据库会话及消息 |

## 安全说明

当前 [SecurityConfig.java](E:/document/1myweb/myllm/src/main/java/com/example/myllm/config/SecurityConfig.java) 中，`/api/models/**`、`/api/rags/**`、`/api/memories/**`、`/api/sync/**`、`/api/sessions/**` 均为 `permitAll()`。JWT 过滤器仍会在请求带 token 时解析用户身份，但这些业务接口当前不是强制认证。

这是为了兼容前端的在线/离线双模式和本地调试。若要生产使用，应把配置类中的授权规则收紧，并在各 Service 中按当前用户隔离数据。

## 数据库设计

[docs/myllm_db.sql](E:/document/1myweb/docs/myllm_db.sql) 与当前 JPA 实体保持一致。

```text
User ──1:N── ModelConfig
User ──1:N── MemoryConfig
User ──1:N── Rag
User ──1:N── Session ──1:N── Message
ModelConfig ──1:N── Rag(embedding_model_id)
Session ──N:1── ModelConfig
Session ──N:1── MemoryConfig
Session ──N:1── Rag
```

关键字段：

| 表 | 关键字段 |
|----|----------|
| `User` | `username`, `password_hash`, `email`, `phone`, `nickname`, `status`, `role`, `last_login_at`, `deleted_at` |
| `ModelConfig` | `UserId`, `model_name`, `provider`, `api_key_encrypted`, `base_url`, `max_tokens`, `prompt`, `is_enabled`, `display_name`, `sort_order` |
| `MemoryConfig` | `UserId`, `strategy_type`, `window_size`, `summary_trigger_tokens`, `enable_rag`, `rag_collection_name`, `rag_top_k`, `is_enabled` |
| `Rag` | `UserId`, `filename`, `file_path`, `collection_name`, `chunk_count`, `chunk_size`, `chunk_overlap`, `chunk_method`, `content`, `status`, `is_enabled`, `embedding_model_id` |
| `Session` | `session_name`, `title`, `user_id`, `model_id`, `memory_id`, `rag_id` |
| `Message` | `session_id`, `user_message`, `ai_response`, `tokens_used` |
| `character_persona` | `name`, `system_prompt`, `temperature`, `target_collection` |

## 主要功能

- 模型配置：支持多个模型同时启用，并按 `sortOrder` 顺序接力调用。
- 知识库：上传后先切片入库，点击向量化后写入 Chroma。
- RAG：聊天时只检索当前启用的知识库文档。
- 记忆：当前聊天上下文按滑动窗口裁剪。
- 历史：会话和消息异步写入 MySQL，历史列表按 `updated_at` 排序。
- 离线模式：未登录或断网时，前端模型/记忆/知识库数据可落 localStorage。
- 主题：前端支持预设色和 HEX 自定义。

## 配置注意事项

- `spring.jpa.hibernate.ddl-auto` 当前为 `update`，开发环境方便，生产建议改为 `validate` 或使用迁移工具。
- Redis 未启动时，带缓存的接口可能影响后端启动或运行，请先启动 Redis，或改造为本地缓存。
- Chroma 默认地址写在 `RagService` 中：`http://127.0.0.1:8000`。
- Embedding API 走 OpenAI 兼容 `/embeddings` 接口，需在模型配置中填写可用的 `baseUrl`、`apiKeyEncrypted` 和 embedding `modelName`。
- `RagService` 的文件读取目前偏向文本文件，复杂 PDF/DOCX 解析仍需扩展。

## 测试

### 后端

```bash
cd myllm
mvn compile
mvn test
```

### 前端

```bash
cd vue/myllm-ui
npm run build
```

当前验证状态：

- `mvn test` 通过
- `npm run build` 通过
- 已知非阻断提示：`GenericJackson2JsonRedisSerializer` 过时警告；Vite 提示 `auth.ts` 动态导入不会拆成独立 chunk

## License

MIT
