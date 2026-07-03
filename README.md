# MyLLM - 智能大模型管理平台

> 文档生成时间: 2026-07-03T00:00:00Z | 自动生成

一个基于 Spring Boot + Vue 3 的 LLM 应用管理平台，支持多模型切换、RAG 知识库管理、角色人设配置等功能。
# npm run dev

## 项目概述

MyLLM 是一个全栈大模型应用平台，提供：
- 多模型支持（DeepSeek、GPT-4o、Claude、Gemini、Llama、Mistral、Qwen 等）
- RAG 知识库管理与检索
- 角色人设（Character Persona）配置
- 毛玻璃（Glassmorphism）现代 UI 设计
- 主题色自定义

## 技术栈

### 后端 (myllm/)
| 技术 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 4.1.0 | Web 框架 |
| Java | 21 | 运行时 |
| Spring AI | 2.0.0 | AI 模型集成 |
| Spring Data JPA | - | ORM 框架 |
| MySQL | 8.0+ | 关系型数据库 |
| Chroma | - | 向量数据库（RAG） |
| Lombok | - | 代码简化 |

### 前端 (vue/myllm-ui/)
| 技术 | 版本 | 说明 |
|------|------|------|
| Vue | 3.5.38 | UI 框架 |
| TypeScript | 6.0 | 类型系统 |
| Vite | 8.0.16 | 构建工具 |
| Vue Router | 5.1.0 | 路由管理 |
| Axios | 1.18.1 | HTTP 客户端 |
| Lucide Icons | 0.577.0 | 图标库 |

## 项目结构

```
myllm/
├── myllm/                          # 后端项目
│   ├── src/main/java/com/example/myllm/
│   │   ├── config/
│   │   │   └── AiConfig.java       # AI 模型配置 (ChatClient Bean)
│   │   ├── controller/
│   │   │   ├── ChatController.java  # 聊天接口 POST /api/chat
│   │   │   ├── ModelController.java # 模型切换接口 POST /api/model/switch
│   │   │   └── KnowledgeController.java # 知识库上传接口 POST /api/knowledge/upload
│   │   ├── service/
│   │   │   ├── ChatService.java     # 聊天业务逻辑，调用 ChatClient
│   │   │   └── KnowledgeService.java # 知识库业务逻辑，调用 VectorStore
│   │   ├── model/
│   │   │   ├── dto/
│   │   │   │   ├── ChatRequest.java # 聊天请求 DTO (prompt, systemMessage)
│   │   │   │   └── ModelSetting.java # 模型配置 DTO (apiKey, baseUrl, modelName)
│   │   │   └── entity/
│   │   │       └── CharacterPersona.java # 角色人设实体 (JPA Entity)
│   │   ├── repository/
│   │   │   └── interface.java       # CharacterPersonaRepository (JPA Repository)
│   │   └── MyllmApplication.java   # Spring Boot 启动类
│   ├── src/main/resources/
│   │   └── application.yml          # 数据库、AI 配置
│   └── pom.xml                      # Maven 依赖
│
├── vue/myllm-ui/                    # 前端项目
│   ├── src/
│   │   ├── views/
│   │   │   ├── Layout.vue           # 主布局（侧边栏 + 内容区 + 聊天输入框）
│   │   │   ├── RagList.vue          # RAG 配置列表页
│   │   │   ├── RuleConfig.vue       # 规则配置表单页
│   │   │   ├── ModelModal.vue       # 模型选择弹窗 (11+ 预设模型)
│   │   │   └── ColorModal.vue       # 主题色选择弹窗 (8 预设 + 自定义 HEX)
│   │   ├── router/
│   │   │   └── index.ts             # 路由配置 (单页 Layout)
│   │   ├── App.vue                  # 根组件 (RouterView)
│   │   └── main.ts                  # 入口文件
│   ├── package.json                 # npm 依赖
│   └── vite.config.ts               # Vite 配置
│
├── myllm_db.sql                     # 数据库初始化脚本
└── README.md                        # 项目文档
```

## 快速开始

### 环境要求

- JDK 21+
- Node.js 22.18+ 或 24.12+
- MySQL 8.0+
- Docker（用于 Chroma 向量数据库）

### 1. 初始化数据库

```bash
mysql -u root -p < myllm_db.sql
```

该脚本会自动创建 `myllm_db` 数据库、5 张表、2 个视图和测试数据。

### 2. 启动 Chroma 向量数据库

```bash
docker run -p 8000:8000 chromadb/chroma
```

### 3. 启动后端

```bash
cd myllm

# 使用 Maven
./mvnw spring-boot:run

# 或使用 Gradle
./gradlew bootRun
```

后端默认运行在 http://localhost:8080

### 4. 启动前端

```bash
cd vue/myllm-ui

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

前端默认运行在 http://localhost:5173

## API 接口

### 聊天接口

```http
POST /api/chat
Content-Type: application/json

{
  "prompt": "你好，请介绍一下自己",
  "systemMessage": "你是一个友好的AI助手"
}
```

### 模型切换

```http
POST /api/model/switch
Content-Type: application/json

{
  "apiKey": "your-api-key",
  "baseUrl": "https://api.openai.com/v1",
  "modelName": "gpt-4o"
}
```

### 知识库上传

```http
POST /api/knowledge/upload
Content-Type: multipart/form-data

file: <上传的文件>
```

## 数据库表结构

### character_persona（角色人设表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| name | VARCHAR(255) | 角色名称 |
| system_prompt | TEXT | 核心系统提示词 |
| temperature | DOUBLE | 创造力参数 (0.0-1.0) |
| target_collection | VARCHAR(255) | 绑定的 Chroma 向量集合名称 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

### chat_history（聊天记录表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| persona_id | BIGINT FK | 关联的角色人设 ID |
| user_message | TEXT | 用户发送的消息 |
| ai_response | TEXT | AI 返回的回复 |
| tokens_used | INT | 消耗的 token 数量 |
| created_at | DATETIME | 消息发送时间 |

### knowledge_document（知识库文档表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| filename | VARCHAR(500) | 原始文件名 |
| file_path | VARCHAR(1000) | 服务器存储路径 |
| file_size | BIGINT | 文件大小（字节） |
| collection_name | VARCHAR(255) | Chroma 向量集合名称 |
| chunk_count | INT | 文档切片数量 |
| status | VARCHAR(20) | 状态: processing/completed/failed |
| created_at | DATETIME | 上传时间 |

### rag_config（RAG 配置表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| title | VARCHAR(255) | 配置标题 |
| model_name | VARCHAR(100) | 使用的模型名称 |
| api_key_encrypted | VARCHAR(500) | 加密存储的 API Key |
| base_url | VARCHAR(500) | API 基础 URL |
| theme_color | VARCHAR(20) | 界面主题颜色 (HEX) |
| max_words | INT | AI 最大输出字数 |
| rules | TEXT | RAG 检索规则 / Prompt 模板 |
| is_active | TINYINT(1) | 是否启用 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

### model_config（模型配置表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| model_name | VARCHAR(100) | 模型名称 |
| provider | VARCHAR(50) | 提供商: openai/ollama/deepseek |
| api_key_encrypted | VARCHAR(500) | 加密存储的 API Key |
| base_url | VARCHAR(500) | API 基础 URL |
| max_tokens | INT | 最大 token 数 |
| is_default | TINYINT(1) | 是否默认模型 |
| is_enabled | TINYINT(1) | 是否启用 |
| created_at | DATETIME | 创建时间 |

## 功能特性

### 1. 多模型支持
- DeepSeek-Chat / DeepSeek-Coder
- GPT-4o / GPT-4-turbo
- Claude-3.5-Sonnet / Claude-3-Opus
- Gemini-1.5-Pro / Gemini-1.5-Flash
- Llama-3-70B
- Mistral-Large
- Qwen-2.5-72B
- 自定义模型

### 2. RAG 知识库管理
- 上传文档向量化
- 配置检索规则
- 绑定角色人设

### 3. 角色人设配置
- 自定义 System Prompt
- 设置创造力参数 (Temperature)
- 绑定知识库集合

### 4. 主题定制
- 8 种预设主题色: `#1e3a8a` `#0284c7` `#0d9488` `#16a34a` `#ca8a04` `#ea580c` `#dc2626` `#7c3aed`
- 自定义 HEX 色值
- 实时预览

## 配置说明

### application.yml

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/myllm_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
    username: root
    password:  # 替换为你的 MySQL 密码

  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true

  ai:
    chroma:
      store:
        host: http://localhost:8000
        collection-name: my_rag_collection
```

## 开发说明

### 后端开发

1. 实体类使用 Lombok `@Data` 注解自动生成 getter/setter
2. JPA Repository 继承 `JpaRepository` 获得 CRUD 能力
3. `ddl-auto: update` 模式会自动同步实体到数据库表

### 前端开发

1. 组件使用 Vue 3 Composition API (`<script setup>`)
2. 样式使用 Scoped CSS 隔离
3. 路由配置在 `src/router/index.ts`
4. UI 采用毛玻璃（Glassmorphism）设计风格

## 测试

### 后端单元测试

```bash
cd myllm
./mvnw test
```

### 前端类型检查

```bash
cd vue/myllm-ui
npm run type-check
```

## License

MIT
#   m y l l m 
 
 
