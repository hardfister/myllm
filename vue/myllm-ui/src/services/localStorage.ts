/**
 * MyLLM 浏览器本地存储服务
 * ---------------
 * 职责：
 *   1. 在浏览器 localStorage 中保存/读取模型、记忆、知识库数据
 *   2. 支持可配置的存储前缀（默认 "myllm_local_"），方便多实例隔离
 *   3. JSON 导出/导入 — 备份恢复 + 跨设备迁移
 *   4. 统计各类型数据数量和占用空间
 *
 * 存储结构（localStorage）：
 *   myllm_storage_prefix  → 自定义前缀字符串
 *   {prefix}models         → JSON: ModelConfig[]
 *   {prefix}memories       → JSON: MemoryConfig[]
 *   {prefix}rags           → JSON: Rag[]（文件内容为 base64）
 *
 * 使用场景：
 *   - 未登录 → 所有 CRUD 经此服务存 localStorage
 *   - 已登录 → 数据走服务器 API，localStorage 仅作缓存
 *   - 登录后检测到本地数据 → 提示导入服务器
 */

import type { ModelConfig, MemoryConfig, Rag } from '../api'

const DEFAULT_PREFIX = 'myllm_local_'

// ==================== 存储前缀管理 ====================

/** 获取当前存储前缀 */
function getPrefix(): string {
  return localStorage.getItem('myllm_storage_prefix') || DEFAULT_PREFIX
}

/** 设置自定义存储前缀（用户可在系统设置中修改） */
export function setStoragePrefix(prefix: string) {
  localStorage.setItem('myllm_storage_prefix', prefix)
}

export function getStoragePrefix(): string {
  return getPrefix()
}

// ==================== 模型配置的本地读写 ====================

export function saveModels(data: ModelConfig[]) {
  localStorage.setItem(getPrefix() + 'models', JSON.stringify(data))
}

export function loadModels(): ModelConfig[] {
  const raw = localStorage.getItem(getPrefix() + 'models')
  return raw ? JSON.parse(raw) : []
}

export function clearModels() {
  localStorage.removeItem(getPrefix() + 'models')
}

// ==================== 记忆配置的本地读写 ====================

export function saveMemories(data: MemoryConfig[]) {
  localStorage.setItem(getPrefix() + 'memories', JSON.stringify(data))
}

export function loadMemories(): MemoryConfig[] {
  const raw = localStorage.getItem(getPrefix() + 'memories')
  return raw ? JSON.parse(raw) : []
}

export function clearMemories() {
  localStorage.removeItem(getPrefix() + 'memories')
}

// ==================== 知识库文档的本地读写 ====================

export function saveRags(data: Rag[]) {
  localStorage.setItem(getPrefix() + 'rags', JSON.stringify(data))
}

export function loadRags(): Rag[] {
  const raw = localStorage.getItem(getPrefix() + 'rags')
  return raw ? JSON.parse(raw) : []
}

export function clearRags() {
  localStorage.removeItem(getPrefix() + 'rags')
}

// ==================== 批量操作 ====================

/** 检测是否有未同步的本地数据 */
export function hasLocalData(): boolean {
  return loadModels().length > 0 || loadMemories().length > 0 || loadRags().length > 0
}

/** 获取全部本地数据（用于同步到服务器） */
export function getAllLocalData(): { models: ModelConfig[]; memories: MemoryConfig[]; rags: Rag[] } {
  return {
    models: loadModels(),
    memories: loadMemories(),
    rags: loadRags()
  }
}

/** 清除所有本地数据 */
export function clearAll() {
  clearModels()
  clearMemories()
  clearRags()
}

// ==================== JSON 备份/恢复 ====================

/**
 * 导出全部本地数据为 JSON 文件并触发浏览器下载
 * 文件命名：myllm-backup-2026-07-04.json
 */
export function exportToJson() {
  const data: Record<string, unknown> = { ...getAllLocalData() }
  data['_exported_at'] = new Date().toISOString()  // 导出时间戳
  data['_prefix'] = getPrefix()                     // 记录存储前缀

  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `myllm-backup-${new Date().toISOString().slice(0, 10)}.json`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)  // 释放内存
}

/**
 * 从 JSON 字符串导入本地数据
 * @returns true=成功, false=格式错误
 */
export function importFromJson(jsonStr: string): boolean {
  try {
    const data = JSON.parse(jsonStr)
    if (Array.isArray(data.models)) saveModels(data.models)
    if (Array.isArray(data.memories)) saveMemories(data.memories)
    if (Array.isArray(data.rags)) saveRags(data.rags)
    if (data._prefix) setStoragePrefix(data._prefix)  // 恢复前缀
    return true
  } catch (e) {
    console.error('导入失败:', e)
    return false
  }
}

// ==================== 存储统计 ====================

/** 获取当前存储中的数据统计：各类型数量 + 总占用字节 */
export function getStorageStats(): { models: number; memories: number; rags: number; totalBytes: number } {
  const modelsBytes = JSON.stringify(loadModels()).length
  const memoriesBytes = JSON.stringify(loadMemories()).length
  const ragsBytes = JSON.stringify(loadRags()).length
  return {
    models: loadModels().length,
    memories: loadMemories().length,
    rags: loadRags().length,
    totalBytes: modelsBytes + memoriesBytes + ragsBytes
  }
}
