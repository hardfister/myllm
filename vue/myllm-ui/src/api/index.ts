/**
 * MyLLM 前端 API 层
 * ---------------
 * 职责：
 *   1. 创建 axios 实例，统一 baseURL、超时、请求头
 *   2. JWT 拦截器：请求自动附加 Bearer token，响应 401 自动清除登录态
 *   3. 定义所有 TypeScript 类型（与后端 Java 实体一一对应）
 *   4. 导出所有 API 函数（认证 / 同步 / 模型 / 记忆 / 知识库 / 聊天）
 *
 * 数据流：Vue 组件 → api/index.ts → axios → Spring Boot → MySQL/localStorage
 */

import axios from 'axios'

// ==================== Axios 实例配置 ====================

const api = axios.create({
  baseURL: 'http://localhost:8080',  // 后端地址（开发环境）
  timeout: 60000,                    // 60 秒超时（大模型回复可能较慢）
  headers: { 'Content-Type': 'application/json' }
})

// ==================== 请求拦截器：自动注入 JWT ====================

api.interceptors.request.use(config => {
  // 从 localStorage 取出登录时保存的 token
  const token = localStorage.getItem('myllm_token')
  if (token) {
    // 注入 Authorization 头，格式：Bearer <jwt_token>
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// ==================== 响应拦截器：处理 401 未授权 ====================

api.interceptors.response.use(
  response => response,  // 正常响应直接透传
  error => {
    // 收到 401 → token 过期或无效 → 清除登录状态
    if (error.response?.status === 401) {
      localStorage.removeItem('myllm_token')
      localStorage.removeItem('myllm_user')
    }
    return Promise.reject(error)
  }
)

// ==================== 类型定义（与后端 JPA Entity 字段对应） ====================

/** 模型配置 */
export interface ModelConfig {
  id?: number
  userId?: number
  modelName: string             // 模型名称，如 "GPT-4o"
  provider: string              // 提供商：DeepSeek / OpenAI / Anthropic / Ollama
  apiKeyEncrypted: string       // API 密钥（明文传输，后端可加密存储）
  baseUrl: string               // API 基础地址
  maxTokens: number             // 最大输出 Token 数
  prompt: string                // 系统提示词模板
  isEnabled?: number            // 是否启用：1=启用（模型独占，同时只有一个激活）
  createdAt?: string
  updatedAt?: string
}

/** 记忆策略配置 */
export interface MemoryConfig {
  id?: number
  userId?: number
  strategyType: string          // 策略类型：sliding_window / summary / hybrid
  windowSize: number            // 滑动窗口大小
  summaryTriggerTokens: number  // 触发摘要压缩的 Token 阈值
  summaryMaxLength: number      // 摘要最大长度
  enableRag: number             // 是否启用 RAG 增强
  ragCollectionName: string     // RAG 向量集合名称
  ragTopK: number               // RAG 检索返回条数
  maxHistoryMessages: number    // 最大保留历史消息数
  enableLongTermMemory: number  // 是否启用长期记忆
  compressionInterval: number   // 压缩间隔（多少轮对话触发一次压缩）
  reserveSystemPrompt: number   // 是否保留系统提示词
  isEnabled?: number            // 是否启用：1=启用（多选独立切换）
  createdAt?: string
  updatedAt?: string
}

/** 知识库文档 */
export interface Rag {
  id?: number
  userId?: number
  filename: string              // 原始文件名
  filePath?: string             // 服务器存储路径（离线模式存 base64）
  fileSize: number              // 文件大小（字节）
  fileType?: string             // MIME 类型
  collectionName: string        // Chroma 向量集合名称
  chunkCount: number            // 文档切片数量
  status: string                // 处理状态：processing / completed / failed
  description?: string          // 文档描述
  isEnabled?: number            // 是否启用：1=启用（多选独立切换）
  createdAt?: string
  updatedAt?: string
}

/** 登录/注册后返回的用户信息 */
export interface UserInfo {
  token?: string                // JWT（仅登录/注册时返回）
  userId: number
  username: string
  nickname: string
  role: string                  // admin / user / guest
}

/** 登录请求 */
export interface LoginRequest {
  username: string
  password: string
}

/** 注册请求 */
export interface RegisterRequest {
  username: string
  password: string
  email?: string
  nickname?: string
}

/** 数据同步请求体：本地 → 服务器批量导入 */
export interface SyncData {
  models: ModelConfig[]
  memories: MemoryConfig[]
  rags: Rag[]
}

// ==================== 认证 API（公开接口，无需 token） ====================

/** POST /api/auth/login — 登录 */
export function login(data: LoginRequest): Promise<{ data: UserInfo }> {
  return api.post('/api/auth/login', data)
}

/** POST /api/auth/register — 注册 */
export function register(data: RegisterRequest): Promise<{ data: UserInfo }> {
  return api.post('/api/auth/register', data)
}

/** GET /api/auth/me — 获取当前用户信息（需 token，用于页面刷新时恢复会话） */
export function getMe(): Promise<{ data: UserInfo }> {
  return api.get('/api/auth/me')
}

// ==================== 数据同步 API（需认证） ====================

/** POST /api/sync/import — 将离线本地数据批量导入服务器 */
export function syncData(data: SyncData): Promise<{
  data: { message: string; modelCount: number; memoryCount: number; ragCount: number }
}> {
  return api.post('/api/sync/import', data)
}

// ==================== 模型配置 CRUD API（需认证） ====================

export function getModels(): Promise<{ data: ModelConfig[] }> {
  return api.get('/api/models')
}
export function createModel(data: ModelConfig): Promise<{ data: ModelConfig }> {
  return api.post('/api/models', data)
}
export function updateModel(id: number, data: Partial<ModelConfig>): Promise<{ data: ModelConfig }> {
  return api.put(`/api/models/${id}`, data)
}
export function deleteModel(id: number): Promise<{ data: string }> {
  return api.delete(`/api/models/${id}`)
}
/** 独占激活：启用当前模型，自动停用其他所有模型 */
export function activateModel(id: number): Promise<{ data: ModelConfig }> {
  return api.put(`/api/models/${id}/activate`)
}

// ==================== 记忆配置 CRUD API（需认证） ====================

export function getMemories(): Promise<{ data: MemoryConfig[] }> {
  return api.get('/api/memories')
}
export function createMemory(data: MemoryConfig): Promise<{ data: MemoryConfig }> {
  return api.post('/api/memories', data)
}
export function updateMemory(id: number, data: Partial<MemoryConfig>): Promise<{ data: MemoryConfig }> {
  return api.put(`/api/memories/${id}`, data)
}
export function deleteMemory(id: number): Promise<{ data: string }> {
  return api.delete(`/api/memories/${id}`)
}
/** 多选切换：翻转单个配置的启用状态，不影响其他 */
export function toggleMemory(id: number): Promise<{ data: MemoryConfig }> {
  return api.put(`/api/memories/${id}/toggle`)
}

// ==================== 知识库文档 CRUD API（需认证） ====================

export function getRags(): Promise<{ data: Rag[] }> {
  return api.get('/api/rags')
}
/** 上传文件：multipart/form-data 格式，包含文件二进制 + 描述文本 */
export function createRag(formData: FormData): Promise<{ data: Rag }> {
  return api.post('/api/rags', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
export function updateRag(id: number, data: Partial<Rag>): Promise<{ data: Rag }> {
  return api.put(`/api/rags/${id}`, data)
}
export function deleteRag(id: number): Promise<{ data: string }> {
  return api.delete(`/api/rags/${id}`)
}
/** 多选切换：翻转单个知识库的启用状态 */
export function toggleRag(id: number): Promise<{ data: Rag }> {
  return api.put(`/api/rags/${id}/toggle`)
}

// ==================== 聊天类型 ====================

/** 聊天响应 — 后端 ChatResponse.java 对应 */
export interface ChatResponse {
  reply: string | null
  sessionId: string
  error: string | null
  sources: string[]
  modelUsed: string
}

/** 单条聊天消息（前端展示用） */
export interface ChatMessage {
  role: 'user' | 'assistant' | 'error' | 'system'
  content: string
  modelUsed?: string
  sources?: string[]
  timestamp?: string
}

/** 历史会话 — 后端 listSessions 返回 */
export interface HistorySession {
  id: number
  sessionId: string
  title: string
  messageCount: number
  updatedAt: string
}

// ==================== 聊天 API（公开接口） ====================

/** POST /api/chat — 发送消息给大模型 */
export function sendChatMessage(content: string, sessionId?: string): Promise<{ data: ChatResponse }> {
  return api.post('/api/chat', { content, sessionId })
}

/** POST /api/chat/new — 新建空会话 */
export function newSession(): Promise<{ data: ChatResponse }> {
  return api.post('/api/chat/new')
}

/** DELETE /api/chat/{id} — 清除内存中的会话 */
export function clearSession(sessionId: string): Promise<{ data: string }> {
  return api.delete(`/api/chat/${sessionId}`)
}

// ==================== 历史记录 API（需认证） ====================

/** GET /api/sessions — 获取全部历史会话列表 */
export function listSessions(): Promise<{ data: HistorySession[] }> {
  return api.get('/api/sessions')
}

/** DELETE /api/sessions/{dbId} — 删除指定会话及其消息 */
export function deleteSession(dbId: number): Promise<{ data: { message: string } }> {
  return api.delete(`/api/sessions/${dbId}`)
}

export default api
