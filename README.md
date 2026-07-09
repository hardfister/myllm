# MyLLM — 大模型对话配置管理平台

> 最后更新: 2026-07-09

全栈 LLM 对话应用，基于 **Spring Boot 4.1 + Vue 3 + MySQL + Chroma**。支持多模型切换、**Chroma RAG 向量检索**、知识库管理、记忆策略、JWT 认证、离线存储、智能对话、历史记录。

---

## 目录

- [项目概述](#项目概述)
- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [快速开始](#快速开始)
- [API 接口文档](#api-接口文档)
- [数据库设计](#数据库设计)
- [功能特性](#功能特性)
- [用户认证系统](#用户认证系统)
- [离线 / 本地模式](#离线--本地模式)
- [配置说明](#配置说明)
- [更新日志](#更新日志)
- [测试](#测试)
- [License](#license)

---

## 项目概述

MyLLM 提供从模型配置到对话的完整链路：

| 功能模块 | 说明 |
|---------|------|
| **模型预设管理** | 创建多个模型配置（模型名称 / 提供商 / API Key / Base URL / System Prompt），一键激活切换 |
| **知识库管理** | 拖拽 / 选择文件上传，关联 Chroma 向量集合，显示切片状态 |
| **记忆策略配置** | 三种策略（滑动窗口 / 摘要压缩 / 混合）、RAG 开关、长期记忆开关 |
| **用户认证** | JWT 登录 / 注册、BCrypt 密码加密、Spring Security 权限控制 |
| **离线 / 本地模式** | 未登录或断网时数据存 localStorage；登录后一键导入云端；JSON 导出 / 导入备份 |
| **智能对话** | 自动加载启用的模型/RAG/记忆配置，Chroma 向量检索 + Ollama embedding + LangChain4j 调 LLM |
| **Chroma RAG** | 上传文档 → 切片 → Ollama nomic-embed-text 向量化 → Chroma 存储 → 聊天时实时语义检索 |
| **历史记录** | 栈式排列，第一条消息触发入库；⋮ 菜单右展开 → 二次确认删除；MySQL 持久化 |
| **主题定制** | 8 种预设色 + HEX 自定义，全局 CSS 变量实时生效 |
| **毛玻璃 UI** | backdrop-filter 半透明玻璃态卡片 + 可折叠侧边栏 |

---

## 技术栈

### 后端 (`myllm/`)

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 4.1.0 | 应用框架 |
| Java | 21 | 运行环境 |
| Spring Security | 4.1.0 | 认证与授权 (JWT + BCrypt) |
| Spring Data JPA | 4.1.0 | ORM (Hibernate) |
| Spring AI | 2.0.0 | ChatClient, ChatModel (OpenAI/Ollama) |
| LangChain4j | 0.33.0 | OpenAI ChatModel (备选) |
| JJWT | 0.12.6 | JWT 生成与验证 |
| MySQL Connector | - | MySQL 8.0+ 驱动 |
| Chroma | REST v2 | 向量数据库（RAG 语义检索） |
| Ollama | nomic-embed-text | 本地嵌入模型 (768 维向量) |
| Jackson | - | JSON 序列化（调 REST API） |
| Lombok | - | 样板代码简化 |

### 前端 (`vue/myllm-ui/`)

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue | 3.5.38 | UI 框架 (Composition API, `<script setup>`) |
| TypeScript | ~6.0 | 类型系统 |
| Vite | 8.0.16 | 构建工具 |
| Vue Router | 5.1.0 | 路由 (单页 Layout) |
| Axios | 1.18.1 | HTTP 客户端 + JWT 拦截器 |

---

## 项目结构

```
.
├── myllm/                                   # 后端 (Spring Boot)
│   ├── src/main/java/com/example/myllm/
│   │   ├── config/
│   │   │   ├── AiConfig.java                # ChatClient Bean
│   │   │   ├── SecurityConfig.java          # Spring Security + CORS 配置
│   │   │   └── JwtAuthFilter.java           # JWT 认证过滤器
│   │   ├── controller/
│   │   │   ├── ChatController.java          # POST /api/chat
│   │   │   ├── ModelController.java         # /api/models CRUD + activate
│   │   │   ├── RagController.java           # /api/rags CRUD + 文件上传
│   │   │   ├── MemoryController.java        # /api/memories CRUD
│   │   │   ├── AuthController.java          # /api/auth 登录/注册/me
│   │   │   ├── SyncController.java          # /api/sync/import 本地→云端
│   │   │   ├── KnowledgeController.java     # 知识库上传桩 (保留)
│   │   │   └── UserController.java          # 用户管理桩 (保留)
│   │   ├── service/
│   │   │   ├── ChatService.java             # Spring AI 聊天
│   │   │   ├── ModelConfigService.java      # 模型配置 CRUD + 激活
│   │   │   ├── MemoryConfigService.java     # 记忆配置 CRUD
│   │   │   ├── RagService.java              # 知识库 CRUD + 文件落地
│   │   │   └── KnowledgeService.java        # 向量存储桩 (保留)
│   │   ├── model/
│   │   │   ├── entity/
│   │   │   │   ├── User.java                # 用户 (JPA)
│   │   │   │   ├── ModelConfig.java         # 模型配置 (JPA)
│   │   │   │   ├── MemoryConfig.java        # 记忆配置 (JPA)
│   │   │   │   ├── Rag.java                 # 知识库文档 (JPA)
│   │   │   │   └── CharacterPersona.java    # 角色人设 (JPA)
│   │   │   └── dto/
│   │   │       ├── ChatRequest.java         # 聊天请求
│   │   │       ├── AuthRequest.java         # 登录请求
│   │   │       ├── RegisterRequest.java     # 注册请求
│   │   │       ├── LoginResponse.java       # 登录响应
│   │   │       ├── SyncDataRequest.java     # 同步数据请求
│   │   │       ├── ModelSetting.java        # 模型设定 DTO
│   │   │       └── ModelSettingsWrapper.java
│   │   ├── repository/
│   │   │   ├── UserRepository.java
│   │   │   ├── ModelConfigRepository.java
│   │   │   ├── MemoryConfigRepository.java
│   │   │   ├── RagRepository.java
│   │   │   └── CharacterPersonaRepository.java
│   │   ├── util/
│   │   │   └── JwtUtil.java                 # JWT 工具类
│   │   └── MyllmApplication.java
│   ├── src/main/resources/
│   │   └── application.yml
│   └── pom.xml
│
├── vue/myllm-ui/                            # 前端 (Vue 3)
│   ├── src/
│   │   ├── api/
│   │   │   └── index.ts                     # Axios 实例 + 全部 API 函数 + JWT 拦截器
│   │   ├── services/
│   │   │   ├── auth.ts                      # Vue 组合式认证服务 (useAuth)
│   │   │   └── localStorage.ts              # 浏览器本地存储服务 (CRUD + JSON 导入导出)
│   │   ├── views/
│   │   │   ├── Layout.vue                   # 主布局 (侧边栏/头部/设置/底部)
│   │   │   ├── LoginModal.vue               # 登录/注册弹窗 + 本地数据导入提示
│   │   │   ├── ModelList.vue                # 模型配置列表 (在线/离线双模式)
│   │   │   ├── RagList.vue                  # 知识库列表 (在线/离线双模式)
│   │   │   ├── MemList.vue                  # 记忆配置列表 (在线/离线双模式)
│   │   │   ├── ModelModal.vue               # 模型选择弹窗 (提供商/BaseURL/APIKey)
│   │   │   ├── RuleConfig.vue               # 规则配置表单 (标题/颜色/Token/Prompt)
│   │   │   └── ColorModal.vue               # 主题色选择弹窗
│   │   ├── router/
│   │   │   └── index.ts
│   │   ├── App.vue
│   │   └── main.ts
│   ├── package.json
│   └── vite.config.ts
│
├── myllm_db.sql                             # 数据库初始化脚本 (6 表 + 测试数据)
└── README.md
```

---

## 快速开始

### 环境要求

| 组件 | 版本 |
|------|------|
| JDK | 21+ |
| Node.js | 22.18+ 或 24.12+ |
| MySQL | 8.0+ |
| Docker | 用于 Chroma (可选) |

### 1. 初始化数据库

```bash
mysql -u root -p < myllm_db.sql
```

创建 `myllm_db` 数据库、6 张表 (User, ModelConfig, MemoryConfig, Rag, Session, Message) 以及 3 个测试用户 + 对话示例数据。

### 2. 配置后端

编辑 `myllm/src/main/resources/application.yml`，设置 MySQL 密码：

```yaml
spring:
  datasource:
    password: your-mysql-password

app:
  jwt:
    secret: your-64-char-random-secret-change-me
    expiration-ms: 86400000
```

### 3. 启动后端

```bash
cd myllm
./mvnw spring-boot:run
```

后端运行在 **http://localhost:8080**。JPA `ddl-auto: update` 自动同步实体到表结构。

### 4. 启动前端

```bash
cd vue/myllm-ui
npm install
npm run dev
```

前端运行在 **http://localhost:5173**。

> **测试账户**: `alice` / `password123` (admin), `bob` / `password123` (user)

---

## API 接口文档

### 认证接口 (公开)

| Method | Path | Body | 说明 |
|--------|------|------|------|
| `POST` | `/api/auth/register` | `{username, password, email?, nickname?}` | 注册，返回 JWT + 用户信息 |
| `POST` | `/api/auth/login` | `{username, password}` | 登录，更新 last_login 记录，返回 JWT |
| `GET` | `/api/auth/me` | - | 获取当前用户信息 (需 Bearer Token) |

### 数据同步 (需认证)

| Method | Path | Body | 说明 |
|--------|------|------|------|
| `POST` | `/api/sync/import` | `{models[], memories[], rags[]}` | 将本地数据导入到当前用户账户 |

### 模型配置 (需认证)

| Method | Path | 说明 |
|--------|------|------|
| `GET` | `/api/models` | 获取全部模型配置 |
| `POST` | `/api/models` | 创建模型配置 |
| `PUT` | `/api/models/{id}` | 更新模型配置 (部分字段) |
| `DELETE` | `/api/models/{id}` | 删除模型配置 |
| `PUT` | `/api/models/{id}/activate` | 激活指定模型 (其余模型的 isEnabled 设为 0) |

请求体示例 `POST /api/models`:
```json
{
  "modelName": "GPT-4o",
  "provider": "OpenAI",
  "apiKeyEncrypted": "sk-xxx",
  "baseUrl": "https://api.openai.com/v1",
  "maxTokens": 4096,
  "prompt": "你是一个友好的 AI 助手",
  "isEnabled": 1
}
```

### 知识库文档 (需认证)

| Method | Path | 说明 |
|--------|------|------|
| `GET` | `/api/rags` | 获取全部文档 |
| `POST` | `/api/rags` | 上传文档 (multipart: `file` + `description`) |
| `PUT` | `/api/rags/{id}` | 更新文档元数据 |
| `DELETE` | `/api/rags/{id}` | 删除文档 (同时清理磁盘文件) |

### 记忆配置 (需认证)

| Method | Path | 说明 |
|--------|------|------|
| `GET` | `/api/memories` | 获取全部记忆配置 |
| `POST` | `/api/memories` | 创建记忆配置 |
| `PUT` | `/api/memories/{id}` | 更新记忆配置 (部分字段) |
| `DELETE` | `/api/memories/{id}` | 删除记忆配置 |

### 聊天 (公开)

| Method | Path | Body | 说明 |
|--------|------|------|------|
| `POST` | `/api/chat` | `{content: "..."}` | 发送消息，返回 AI 回复 |

---

## 数据库设计

`myllm_db.sql` 包含 6 张表，基于 MySQL 8.0 InnoDB / utf8mb4：

```
User ──1:N── ModelConfig
User ──1:N── MemoryConfig
User ──1:N── Rag
User ──1:N── Session ──1:N── Message
Session ──N:1── ModelConfig
Session ──N:1── MemoryConfig
Session ──N:1── Rag
```

### Key 表概要

| 表名 | 核心字段 | 说明 |
|------|---------|------|
| **User** | username, password_hash(bcrypt), email, phone, nickname, status, role, last_login_at, last_login_ip, login_count, deleted_at | 软删除 + 登录追溯 |
| **ModelConfig** | UserId(FK), model_name, provider, api_key_encrypted, base_url, max_tokens, prompt, is_enabled | 多模型切换 |
| **MemoryConfig** | UserId(FK), strategy_type(sliding_window/summary/hybrid), window_size, summary_trigger_tokens, enable_rag, rag_collection_name, enable_long_term_memory | 三种记忆策略 |
| **Rag** | UserId(FK), filename, file_path, file_size, file_type, collection_name, chunk_count, status(processing/completed/failed) | 知识库文档 |
| **Session** | UserId(FK), session_name, ModelId(FK), MemoryId(FK), RagId(FK) | 对话会话 |
| **Message** | SessionId(FK), user_message, ai_response, tokens_used | 消息记录 |

---

## 功能特性

### 用户认证系统

- **JWT 令牌认证**: 登录返回 token，请求自动附带 `Authorization: Bearer <token>`
- **BCrypt 密码加密**: 密码使用 `BCryptPasswordEncoder` (强度 10) 哈希存储
- **注册验证**: 用户名唯一性、密码长度 ≥ 6、邮箱唯一性检查
- **登录追溯**: 记录 `last_login_at`, `last_login_ip`, `login_count`
- **账户状态**: 正常(1) / 禁用(0) / 待验证(2) / 冻结(3)
- **Spring Security**: 公开访问 `/api/auth/**` 和 `/api/chat`，其余 `/api/models/**`, `/api/rags/**`, `/api/memories/**`, `/api/sync/**` 需认证
- **CORS**: 允许 `localhost:5173` 带凭据跨域

### 离线 / 本地模式

- **自动检测**: `navigator.onLine` + 登录状态 → 决定走 API 还是 localStorage
- **本地存储服务** (`services/localStorage.ts`):
  - 模型/记忆/知识库数据分别序列化到 localStorage
  - 可配置存储前缀 (默认 `myllm_local_`)
  - JSON 导出 / 导入 (备份恢复)
  - 统计各类型数量和占用空间
- **离线上传**: RagList 离线模式用 FileReader 将文件转为 base64 存入 localStorage
- **云端同步**:
  - 登录后检测本地数据 → 弹窗询问是否导入
  - 调用 `POST /api/sync/import` 批量导入
  - 导入后自动清除本地数据
  - 网络恢复时自动触发同步
- **离线角标**: 未登录/断网时在头部和每个列表页显示 "📱 本地模式"

### 模型配置管理

- 三步创建流程: ModelModal (选择模型+提供商+BaseURL+APIKey) → RuleConfig (Prompt+Token+颜色+标题)
- 11 个预设模型 + 自定义模型名称
- 一键激活 (其余模型自动禁用)
- 在线存 API，离线存 localStorage

### 知识库文档管理

- 拖拽 / 点击选择文件上传
- 文件类型标签、切片数、处理状态 (processing/completed/failed)
- 删除同时清理磁盘文件
- 支持 PDF / DOCX / MD / TXT / CSV / JSON

### 记忆策略配置

- 三种策略: 滑动窗口 / 摘要压缩 / 混合
- RAG 开关 → 条件显示集合名称 + Top-K
- 长期记忆 / 保留 System Prompt 开关
- 窗口大小 / 最大历史 / 摘要阈值 / 压缩间隔

### UI 设计

- 毛玻璃风格: `backdrop-filter: blur(20px)` + 半透明背景 + 白边框
- 可折叠侧边栏 (64px ⇔ 240px)
- 全局主题色 CSS 变量 (`--theme-color`)
- 8 种预设 + 自定义 HEX 实时预览
- 用户头像 + 下拉菜单

---

## 配置说明

### application.yml (后端)

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/myllm_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
    username: root
    password: your-mysql-password

  jpa:
    hibernate:
      ddl-auto: update       # 开发: update, 生产: validate
    show-sql: true

app:
  jwt:
    secret: your-64-char-secret
    expiration-ms: 86400000  # 24 小时
```

### package.json (前端)

```json
{
  "engines": { "node": "^22.18.0 || >=24.12.0" },
  "scripts": {
    "dev": "vite",
    "build": "run-p type-check \"build-only {@}\" --",
    "type-check": "vue-tsc --build"
  }
}
```

---

## 更新日志

### v0.5.0 (2026-07-09) — Chroma RAG 向量检索

**后端 (2 文件重写)**:
- `RagService.java` — 全功能 RAG 引擎，直接调 REST API:
  - `embed(text)` → Ollama `POST /api/embeddings` → float[768]
  - `chromaUpsert(id, vec, meta)` → Chroma `POST /collections/{name}/documents`
  - `chromaQuery(vec, topK)` → Chroma `POST /collections/{name}/query` → Top-K 文段+相似度
  - `searchRelevant(query, topK)` → embedding + 向量检索 → 返回最相关文段列表
  - `ensureChromaCollection()` → 启动时自动创建 Chroma 集合
  - `deleteRag()` → 级联清理 Chroma 向量 + 磁盘文件 + MySQL 记录
- `ChatService.java` — 注入 `RagService`, 用实时向量检索替代静态描述注入; Chroma 不可用时自动回退到 MySQL 全文

**其他**:
- `AiConfig.java` — 重建（RAG 不依赖 Spring AI starter）
- `application.yml` — 精简配置

### v0.4.0 (2026-07-07) — 自定义知识库

**后端 (2 文件重写)**:
- `Rag.java` — 新增 `chunkSize`(默认500), `chunkOverlap`(默认50), `chunkMethod`(fixed_size/paragraph/sentence), `content`(MEDIUMTEXT)
- `RagService.java` — 完整重写: 文件上传时实时提取文本(UTF-8/GBK), 三种切片方式, 切片结果存 content 列
- `RagController.java` — 上传接口新增 `chunkSize` / `chunkOverlap` / `chunkMethod` 参数

**前端 (2 文件)**:
- `RagList.vue` — 上传表单含切片配置区(集合名称/切片方式/大小/重叠); "查看"按钮编辑切片参数; 卡片显示切片元信息
- `api/index.ts` — `Rag` 接口新增 chunk 字段

### v0.3.0 (2026-07-06) — 智能对话 + 历史记录

**后端新增 (4 文件)**:
- `Session.java` / `Message.java` — JPA 实体，映射 Session 和 Message 表
- `SessionRepository.java` / `MessageRepository.java` — JPA 仓储

**后端重构 (2 文件)**:
- `ChatService.java` — 完整重写:
  - 动态查询启用的模型/RAG/记忆配置 → 自动拼装 System Prompt
  - LangChain4j 调 LLM（兼容 OpenAI/DeepSeek 所有兼容接口）
  - 错误分类：连接失败 / 超时 / 认证失败 / 配额用尽
  - 用户首条消息自动创建 Session 记录并持久化到 MySQL
  - 每轮对话保存 Message（user_message + ai_response + tokens_used）
  - 记忆策略裁剪（sliding_window/hybrid）
  - `listSessions()` — 历史会话列表，按最近活跃排序
  - `deleteSession(dbId)` — 级联删除会话及全部消息
- `ChatController.java` — 新增 `GET /api/sessions` 和 `DELETE /api/sessions/{dbId}` 端点
- `ChatRequest.java` / `ChatResponse.java` — DTO 重构

**前端新增/重构 (2 文件)**:
- `Layout.vue` — 聊天界面完整实现:
  - 聊天气泡 UI（用户蓝右/AI 白左/错误红中）
  - 打字动画（三点跳跃指示器）
  - 历史记录面板（栈式排列，侧边栏展开时可见）
  - 每条历史记录右侧 ⋯ 按钮 → 右展开 → 删除（二次确认）
  - 自动滚动到最新消息
  - 发送后自动刷新历史列表
- `api/index.ts` — 新增 `HistorySession` 接口 / `listSessions` / `deleteSession` API

### v0.2.0 (2026-07-04) — 认证 + 离线同步

**后端新增 (12 文件)**:
- `SecurityConfig.java` — Spring Security 配置 (JWT + BCrypt + CORS + 路径权限)
- `JwtAuthFilter.java` — Bearer Token 提取与验证
- `JwtUtil.java` — JWT 生成 / 解析 / 验证工具
- `User.java` — 用户 JPA 实体 (映射现有 User 表)
- `UserRepository.java` — 用户查询接口
- `AuthController.java` — `/api/auth/register`, `/api/auth/login`, `/api/auth/me`
- `SyncController.java` — `/api/sync/import` 批量导入本地数据
- `ModelConfig.java` / `MemoryConfig.java` / `Rag.java` — 3 张核心表 JPA 实体
- `ModelConfigService.java` / `MemoryConfigService.java` / `RagService.java` — CRUD 服务
- `ModelConfigRepository.java` / `MemoryConfigRepository.java` / `RagRepository.java` — JPA 仓储
- `AuthRequest.java` / `RegisterRequest.java` / `LoginResponse.java` / `SyncDataRequest.java` — DTO

**后端重构 (4 文件)**:
- `ChatController.java` — 修复 `MessageRequest`→`ChatRequest`，路径统一为 `/api/chat`，注入 `ChatService`
- `ModelController.java` — 从桩实现重写为完整 REST CRUD + `/activate`
- `UserController.java` — 从空文件修复为合法控制器
- `ModelSetting.java` — 修复 `integer`→`Integer`，添加 `baseUrl` 字段

**后端修复 (3 文件)**:
- `interface.java` → `CharacterPersonaRepository.java` (文件重命名)
- `KnowledgeService.java` — 移除不可用的 Chroma VectorStore 依赖
- `AiConfig.java` — 清理 VectorStore 引用
- `pom.xml` — 添加 `spring-boot-starter-security` + `jjwt` 三件套，注释不可用的 `spring-ai-chroma-store-starter`

**前端新增 (6 文件)**:
- `api/index.ts` — Axios 实例 + JWT 拦截器 + 全部类型定义 + API 函数
- `services/auth.ts` — Vue 认证组合式函数 (`useAuth`)
- `services/localStorage.ts` — 本地存储服务 (CRUD + JSON 导出/导入 + 统计)
- `LoginModal.vue` — 登录/注册弹窗 (双标签 + 同步提示)
- `MemList.vue` — 记忆策略配置 (独立 UI，在线/离线双模式)
- `views/pic/*.svg` — 侧边栏图标资源

**前端重构 (5 文件)**:
- `Layout.vue` — 完整重写: 登录按钮/用户信息/下拉菜单/离线角标/系统设置面板 (存储前缀+导出+导入+清空)
- `ModelList.vue` — 后端 API 集成 + 离线 localStorage 模式 + 激活/删除按钮
- `RagList.vue` — 文件上传/拖拽 + 离线 base64 + 状态标签 + 后端集成
- `ModelModal.vue` — 添加提供商下拉 + BaseURL 输入
- `RuleConfig.vue` — 重命名 `maxWords`→`maxTokens`, `rules`→`prompt`

### v0.1.0 — 初始版本

基础项目框架搭建，前端毛玻璃 UI，后端桩实现，数据库设计。

---

## 测试

### 后端编译与测试

```bash
cd myllm
./mvnw compile        # 编译检查
./mvnw test           # 运行单元测试
./mvnw spring-boot:run # 启动
```

### 前端类型检查

```bash
cd vue/myllm-ui
npm run type-check    # vue-tsc 类型检查
npm run build         # 生产构建
```

### 集成测试步骤

1. 启动 MySQL，运行 `myllm_db.sql`
2. 启动后端 `./mvnw spring-boot:run`
3. 启动前端 `npm run dev`
4. 浏览器访问 `http://localhost:5173`
5. 点击右上"登录/注册" → 用 `alice` / `password123` 登录
6. 验证模型/知识库/记忆配置的 CRUD
7. 退出登录 → 创建一些离线数据 → 重新登录 → 验证同步弹窗 → 确认导入
8. 系统设置 → 导出 JSON → 清空 → 导入 JSON → 验证数据恢复
9. `curl -X GET http://localhost:8080/api/models` → 应返回 401/403

---

## License

MIT
