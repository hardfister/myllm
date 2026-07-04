import axios from 'axios'

const api = axios.create({
  baseURL: 'http://localhost:8080',
  timeout: 60000,
  headers: { 'Content-Type': 'application/json' }
})

// ========== JWT 拦截器 ==========

api.interceptors.request.use(config => {
  const token = localStorage.getItem('myllm_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401) {
      localStorage.removeItem('myllm_token')
      localStorage.removeItem('myllm_user')
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

export function syncData(data: SyncData): Promise<{ data: { message: string; modelCount: number; memoryCount: number; ragCount: number } }> {
  return api.post('/api/sync/import', data)
}

// ==================== 模型配置 API ====================

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

export function activateModel(id: number): Promise<{ data: ModelConfig }> {
  return api.put(`/api/models/${id}/activate`)
}

// ==================== 记忆配置 API ====================

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

// ==================== 知识库文档 API ====================

export function getRags(): Promise<{ data: Rag[] }> {
  return api.get('/api/rags')
}

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

export function toggleRag(id: number): Promise<{ data: Rag }> {
  return api.put(`/api/rags/${id}/toggle`)
}

// ==================== 聊天 API ====================

export function sendChatMessage(content: string): Promise<{ data: string }> {
  return api.post('/api/chat', { content })
}

export default api
