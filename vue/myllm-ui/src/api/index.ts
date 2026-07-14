/**
 * MyLLM 前端 API 层
 * ---------------
 * 职责：
 *   1. 创建 axios 实例，统一 baseURL、超时、请求头
 *   2. JWT 拦截器：请求自动附加 Bearer token，响应 401 自动清除登录态
 *   3. 定义所有 TypeScript 类型（与后端 Java 实体一一对应）
 *   4. 导出所有 API 函数
 *
 * 401 处理：收到 401 时不只清除 localStorage，还会调用 auth 模块重置全局状态。
 * 采用延迟导入避免循环依赖。
 */

import axios from 'axios'

// ==================== Axios 实例配置 ====================

const api = axios.create({
  baseURL: 'http://localhost:8080',
  timeout: 60000,
  headers: { 'Content-Type': 'application/json' }
})

// FormData 请求自动删除 Content-Type，让浏览器设置正确的 multipart boundary
api.interceptors.request.use(config => {
  if (config.data instanceof FormData) {
    delete config.headers['Content-Type']
  }
  return config
})

// ==================== 请求拦截器：自动注入 JWT ====================

api.interceptors.request.use(config => {
  const token = localStorage.getItem('myllm_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
}, error => Promise.reject(error))

// ==================== 响应拦截器：401 → 清除登录态 ====================

api.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401) {
      // 清除持久化的 token
      localStorage.removeItem('myllm_token')
      localStorage.removeItem('myllm_user')
      // 延迟导入 auth 模块重置全局 isLoggedIn 状态（避免循环依赖）
      import('../services/auth').then(m => {
        m.logout()
      }).catch(() => {})
    }
    return Promise.reject(error)
  }
)

// ==================== 类型定义 ====================

export interface ModelConfig {
  id?: number
  userId?: number
  modelName: string
  provider: string
  apiKeyEncrypted: string
  baseUrl: string
  maxTokens: number
  prompt: string
  isEnabled?: number
  displayName?: string
  sortOrder?: number
  createdAt?: string
  updatedAt?: string
}

export interface MemoryConfig {
  id?: number
  userId?: number
  strategyType: string
  windowSize: number
  summaryTriggerTokens: number
  summaryMaxLength: number
  enableRag: number
  ragCollectionName: string
  ragTopK: number
  maxHistoryMessages: number
  enableLongTermMemory: number
  compressionInterval: number
  reserveSystemPrompt: number
  isEnabled?: number
  createdAt?: string
  updatedAt?: string
}

export interface Rag {
  id?: number
  userId?: number
  filename: string
  filePath?: string
  fileSize: number
  fileType?: string
  collectionName: string
  chunkCount: number
  chunkSize?: number
  chunkOverlap?: number
  chunkMethod?: string
  content?: string
  status: string
  description?: string
  isEnabled?: number
  createdAt?: string
  updatedAt?: string
}

export interface UserInfo {
  token?: string
  userId: number
  username: string
  nickname: string
  role: string
}

export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  password: string
  email?: string
  nickname?: string
}

export interface SyncData {
  models: ModelConfig[]
  memories: MemoryConfig[]
  rags: Rag[]
}

/** 多模型模式下单条模型的回复 */
export interface ChatReply {
  model: string
  displayName: string
  content: string
}

export interface ChatResponse {
  reply: string | null
  sessionId: string
  error: string | null
  sources: string[]
  modelUsed: string
  /** 多模型模式 — 所有模型按顺序的回复列表 */
  replies?: ChatReply[]
}

/** 后端返回的每条消息记录 */
export interface SessionMessage {
  role: string
  content: string
  modelId?: number
  timestamp?: string
}

export interface ChatMessage {
  role: 'user' | 'assistant' | 'error' | 'system'
  content: string
  modelUsed?: string
  sources?: string[]
  timestamp?: string
  sessionId?: string
}

export interface HistorySession {
  id: number
  sessionId: string
  title: string
  messageCount: number
  updatedAt: string
}

// ==================== 认证 API ====================

export function login(data: LoginRequest): Promise<{ data: UserInfo }> {
  return api.post('/api/auth/login', data)
}
export function register(data: RegisterRequest): Promise<{ data: UserInfo }> {
  return api.post('/api/auth/register', data)
}
export function getMe(): Promise<{ data: UserInfo }> {
  return api.get('/api/auth/me')
}

// ==================== 数据同步 API ====================

export function syncData(data: SyncData): Promise<{
  data: { message: string; modelCount: number; memoryCount: number; ragCount: number }
}> {
  return api.post('/api/sync/import', data)
}

// ==================== 模型配置 CRUD ====================

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
export function toggleModel(id: number): Promise<{ data: ModelConfig }> {
  return api.put(`/api/models/${id}/toggle`)
}
export function reorderModels(items: { id: number; sortOrder: number }[]): Promise<{ data: string }> {
  return api.put('/api/models/reorder', items)
}

// ==================== 记忆配置 CRUD ====================

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
export function toggleMemory(id: number): Promise<{ data: MemoryConfig }> {
  return api.put(`/api/memories/${id}/toggle`)
}

// ==================== 知识库 CRUD ====================

export function getRags(): Promise<{ data: Rag[] }> {
  return api.get('/api/rags')
}
/** 上传文件：不设 Content-Type，让浏览器自动加 multipart boundary */
export function createRag(formData: FormData): Promise<{ data: Rag }> {
  return api.post('/api/rags', formData)
}
export function updateRag(id: number, data: Partial<Rag>): Promise<{ data: Rag }> {
  return api.put(`/api/rags/${id}`, data)
}
export function deleteRag(id: number): Promise<{ data: string }> {
  return api.delete(`/api/rags/${id}`)
}
export function toggleRag(id: number): Promise<{ data: Rag }> {
  return api.put(`/api/rags/${id}/toggle`)
}
/** 向量化指定文档 — 传入嵌入模型 ID */
export function embedRag(id: number, modelId: number): Promise<{ data: Rag }> {
  return api.post(`/api/rags/${id}/embed?modelId=${modelId}`)
}
export function listVectors(userId?: number): Promise<{ data: any[] }> {
  return api.get('/api/rags/vectors', { params: userId ? { userId } : {} })
}
export function clearVectors(): Promise<{ data: { deleted: number; message: string } }> {
  return api.delete('/api/rags/vectors')
}

// ==================== 聊天 ====================

export function sendChatMessage(content: string, sessionId?: string): Promise<{ data: ChatResponse }> {
  return api.post('/api/chat', { content, sessionId })
}

/** SSE 流式聊天 — 返回 ReadableStream，逐 token 推送 */
export function sendChatMessageStream(
  content: string,
  sessionId: string | undefined,
  onStartModel: (displayName: string) => void,
  onToken: (displayName: string, token: string) => void,
  onEndModel: (displayName: string) => void,
  onDone: (sessionId: string) => void,
  onError: (err: string) => void
): AbortController {
  const controller = new AbortController()
  fetch('http://localhost:8080/api/chat/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ content, sessionId }),
    signal: controller.signal
  }).then(async (resp) => {
    if (!resp.ok || !resp.body) { onError('HTTP ' + resp.status); return }
    const reader = resp.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let currentModel = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      // 解析 SSE: "event: xxx\ndata: yyy\n\n"
      const parts = buffer.split('\n\n')
      buffer = parts.pop() || ''
      for (const part of parts) {
        const lines = part.split('\n')
        let eventType = ''
        let eventData = ''
        for (const line of lines) {
          if (line.startsWith('event: ')) eventType = line.slice(7)
          else if (line.startsWith('data: ')) eventData = line.slice(6).replace(/\\n/g, '\n')
        }
        if (eventType === 'start_model') { currentModel = eventData; onStartModel(eventData) }
        else if (eventType === 'token') { onToken(currentModel, eventData) }
        else if (eventType === 'end_model') { onEndModel(eventData) }
        else if (eventType === 'done') { onDone(eventData) }
        else if (eventType === 'error') { onError(eventData) }
      }
    }
  }).catch((e) => {
    if (e.name !== 'AbortError') onError(e.message || '连接失败')
  })
  return controller
}

export function newSession(): Promise<{ data: ChatResponse }> {
  return api.post('/api/chat/new')
}
export function clearSession(sessionId: string): Promise<{ data: string }> {
  return api.delete(`/api/chat/${sessionId}`)
}

// ==================== 历史记录 ====================

export function listSessions(): Promise<{ data: HistorySession[] }> {
  return api.get('/api/sessions')
}
export function getSessionMessages(sessionId: string): Promise<{ data: SessionMessage[] }> {
  return api.get(`/api/sessions/${sessionId}/messages`)
}
export function deleteSession(dbId: number): Promise<{ data: { message: string } }> {
  return api.delete(`/api/sessions/${dbId}`)
}

export default api
