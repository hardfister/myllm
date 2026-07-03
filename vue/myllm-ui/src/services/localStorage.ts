import type { ModelConfig, MemoryConfig, Rag } from '../api'

const DEFAULT_PREFIX = 'myllm_local_'

function getPrefix(): string {
  return localStorage.getItem('myllm_storage_prefix') || DEFAULT_PREFIX
}

export function setStoragePrefix(prefix: string) {
  localStorage.setItem('myllm_storage_prefix', prefix)
}

export function getStoragePrefix(): string {
  return getPrefix()
}

// ========== Models ==========

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

// ========== Memories ==========

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

// ========== Rags ==========

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

// ========== 批量操作 ==========

export function hasLocalData(): boolean {
  return loadModels().length > 0 || loadMemories().length > 0 || loadRags().length > 0
}

export function getAllLocalData(): { models: ModelConfig[]; memories: MemoryConfig[]; rags: Rag[] } {
  return {
    models: loadModels(),
    memories: loadMemories(),
    rags: loadRags()
  }
}

export function clearAll() {
  clearModels()
  clearMemories()
  clearRags()
}

// ========== JSON 导出/导入 ==========

export function exportToJson() {
  const data: Record<string, unknown> = { ...getAllLocalData() }
  data['_exported_at'] = new Date().toISOString()
  data['_prefix'] = getPrefix()

  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `myllm-backup-${new Date().toISOString().slice(0, 10)}.json`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

export function importFromJson(jsonStr: string): boolean {
  try {
    const data = JSON.parse(jsonStr)
    if (Array.isArray(data.models)) saveModels(data.models)
    if (Array.isArray(data.memories)) saveMemories(data.memories)
    if (Array.isArray(data.rags)) saveRags(data.rags)
    if (data._prefix) setStoragePrefix(data._prefix)
    return true
  } catch (e) {
    console.error('导入失败:', e)
    return false
  }
}

// ========== 统计 ==========

export function getStorageStats(): { models: number; memories: number; rags: number; totalBytes: number } {
  const models = JSON.stringify(loadModels()).length
  const memories = JSON.stringify(loadMemories()).length
  const rags = JSON.stringify(loadRags()).length
  return {
    models: loadModels().length,
    memories: loadMemories().length,
    rags: loadRags().length,
    totalBytes: models + memories + rags
  }
}
