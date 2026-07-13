<script setup lang="ts">
/**
 * ModelList.vue — 模型配置管理（多选 + 拖拽排序）
 * ---------------
 * 多选：☑/☐ 独立切换，可同时启用多个模型
 * 排序：拖拽手柄 ↕ 调整顺序，拖拽结束自动保存到后端
 * 多模型对话：启用多个后，对话时按排序依次调用，每个模型看到之前所有回复
 */
import { ref, onMounted, nextTick } from 'vue'
import ModelModal from './ModelModal.vue'
import RuleConfig from './RuleConfig.vue'
import { getModels, createModel, updateModel, deleteModel, toggleModel, reorderModels } from '../api'
import { saveModels, loadModels } from '../services/localStorage'
import { useAuth } from '../services/auth'
import type { ModelConfig } from '../api'

const { isLoggedIn, isOffline } = useAuth()
const emit = defineEmits(['updateColor'])

const step = ref<'list' | 'rule'>('list')
const showModelModal = ref<boolean>(false)
const isEditing = ref<boolean>(false)

const models = ref<ModelConfig[]>([])
const META_KEY = 'myllm_meta_models'
const loadMeta = (): Record<number, { title: string; color: string }> => {
  try { const r = localStorage.getItem(META_KEY); return r ? JSON.parse(r) : {} } catch { return {} }
}
const saveMeta = (m: Record<number, { title: string; color: string }>) => {
  localStorage.setItem(META_KEY, JSON.stringify(m))
}
const frontendMeta = ref<Record<number, { title: string; color: string }>>(loadMeta())

const useServer = () => isLoggedIn.value && !isOffline.value

const currentConfig = ref({
  id: 0 as number | undefined,
  modelName: 'DeepSeek-Chat', provider: 'DeepSeek',
  apiKeyEncrypted: '', baseUrl: 'https://api.deepseek.com',
  maxTokens: 4096, prompt: '',
  displayName: '',
  title: '', color: '#1e3a8a'
})

let localIdCounter = Date.now()
const dragIndex = ref<number | null>(null)  // 当前拖拽的项在数组中的索引

// ===== 数据加载：localStorage 底 + 服务器覆盖 =====
const loadModelsData = async () => {
  const localData = loadModels()
  if (useServer()) {
    try {
      const r = await getModels(); models.value = r.data
      // 合并仅存于本地的数据（未同步的）
      const serverIds = new Set(r.data.map((m: any) => m.id))
      const localOnly = localData.filter((m: any) => m.id != null && !serverIds.has(m.id))
      for (const m of localOnly) { if (!models.value.some((s: any) => s.id === m.id)) models.value.push(m) }
    } catch { models.value = localData }
  } else { models.value = localData }
  // 按 sortOrder 排序
  models.value.sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
  // 初始化 meta
  let mc = false
  for (const m of models.value) {
    if (m.id != null && !frontendMeta.value[m.id]) {
      frontendMeta.value[m.id] = { title: m.displayName || `${m.provider} - ${m.modelName}`, color: '#1e3a8a' }
      mc = true
    }
  }
  if (mc) saveMeta(frontendMeta.value)
}
onMounted(loadModelsData)
const persistModels = () => { saveModels(models.value) }

// ===== 新建 =====
const handleNewConfig = () => {
  isEditing.value = false
  currentConfig.value = { id: undefined, modelName: 'DeepSeek-Chat', provider: 'DeepSeek',
    apiKeyEncrypted: '', baseUrl: 'https://api.deepseek.com', maxTokens: 4096, prompt: '',
    displayName: '', title: '', color: '#1e3a8a' }
  showModelModal.value = true
}

// ===== 编辑 =====
const handleEditConfig = (item: ModelConfig, event: Event) => {
  event.stopPropagation()
  isEditing.value = true
  const meta = frontendMeta.value[item.id!] || { title: '', color: '#1e3a8a' }
  currentConfig.value = { id: item.id, modelName: item.modelName, provider: item.provider,
    apiKeyEncrypted: item.apiKeyEncrypted || '', baseUrl: item.baseUrl || '',
    maxTokens: item.maxTokens || 4096, prompt: item.prompt || '',
    displayName: item.displayName || '', title: meta.title, color: meta.color }
  showModelModal.value = true
}

// ===== 多选切换 =====
const handleToggle = async (item: ModelConfig, event: Event) => {
  event.stopPropagation()
  if (item.id == null) return
  if (useServer()) { await toggleModel(item.id); await loadModelsData(); saveModels(models.value) }
  else { const t = models.value.find(m => m.id === item.id); if (t) { t.isEnabled = t.isEnabled === 1 ? 0 : 1; persistModels() } }
}

// ===== ModelModal 确认 → RuleConfig =====
const confirmModel = (m: { model: string; apiKey: string; provider: string; baseUrl: string }) => {
  currentConfig.value.modelName = m.model; currentConfig.value.apiKeyEncrypted = m.apiKey
  currentConfig.value.provider = m.provider; currentConfig.value.baseUrl = m.baseUrl
  showModelModal.value = false; step.value = 'rule'
}

// ===== 保存 =====
const saveFinalConfig = async (fd: { title: string; color: string; maxTokens: number; prompt: string }) => {
  Object.assign(currentConfig.value, fd)
  const p: ModelConfig = {
    modelName: currentConfig.value.modelName, provider: currentConfig.value.provider,
    apiKeyEncrypted: currentConfig.value.apiKeyEncrypted, baseUrl: currentConfig.value.baseUrl,
    maxTokens: currentConfig.value.maxTokens, prompt: currentConfig.value.prompt,
    displayName: currentConfig.value.displayName || currentConfig.value.title,
    sortOrder: isEditing.value ? (currentConfig.value.id != null ? models.value.find(m => m.id === currentConfig.value.id)?.sortOrder ?? 0 : models.value.length) : models.value.length,
    isEnabled: 0
  }
  try {
    if (useServer()) {
      if (isEditing.value && currentConfig.value.id != null) { await updateModel(currentConfig.value.id, p) }
      else { const r = await createModel(p); if (r.data.id != null) currentConfig.value.id = r.data.id }
      await loadModelsData()
      saveModels(models.value)  // 备份到 localStorage
    } else {
      if (isEditing.value && currentConfig.value.id != null) {
        const i = models.value.findIndex(m => m.id === currentConfig.value.id)
        if (i !== -1) models.value[i] = { ...models.value[i], ...p }
      } else { models.value.push({ ...p, id: ++localIdCounter, sortOrder: models.value.length }) }
      persistModels()
    }
    if (currentConfig.value.id != null) {
      frontendMeta.value[currentConfig.value.id] = { title: currentConfig.value.title || p.modelName, color: currentConfig.value.color }
      saveMeta(frontendMeta.value)
    }
    emit('updateColor', currentConfig.value.color)
    step.value = 'list'
  } catch (e) { console.error('保存失败:', e); alert('保存失败') }
}

// ===== 删除 =====
const handleDeleteConfig = async (id: number, event: Event) => {
  event.stopPropagation()
  if (!confirm('确认删除？')) return
  try {
    if (useServer()) { await deleteModel(id); await loadModelsData(); saveModels(models.value) }
    else { models.value = models.value.filter(m => m.id !== id); persistModels() }
    delete frontendMeta.value[id]; saveMeta(frontendMeta.value)
  } catch (e) { console.error('删除失败:', e) }
}

// ===== 拖拽排序 =====
const onDragStart = (index: number) => { dragIndex.value = index }
const onDragOver = (e: DragEvent) => { e.preventDefault() }
const onDrop = async (targetIndex: number) => {
  if (dragIndex.value == null || dragIndex.value === targetIndex) return
  const items = [...models.value]
  const moved = items.splice(dragIndex.value, 1)[0]
  if (!moved) return
  items.splice(targetIndex, 0, moved)
  models.value = items
  // 更新 sortOrder
  models.value.forEach((m, i) => { m.sortOrder = i })
  if (useServer()) {
    await reorderModels(models.value.filter(m => m.id != null).map(m => ({ id: m.id!, sortOrder: m.sortOrder ?? 0 })))
  } else { persistModels() }
  dragIndex.value = null
}

const getTitle = (m: ModelConfig) => frontendMeta.value[m.id!]?.title || m.displayName || `${m.provider} - ${m.modelName}`
const getColor = (m: ModelConfig) => frontendMeta.value[m.id!]?.color || '#1e3a8a'
</script>

<template>
  <div class="rag-container-glass">
    <div v-if="step === 'list'" class="list-layout">
      <div class="list-header">
        <span v-if="!useServer()" class="mode-badge">📱 本地模式</span>
        <span v-if="models.filter(m => m.isEnabled === 1).length > 1" class="multi-hint">
          🎯 {{ models.filter(m => m.isEnabled === 1).length }} 个模型已选（按排序顺序依次对话）
        </span>
        <button class="new-btn" @click="handleNewConfig">＋ 新建配置</button>
      </div>
      <div class="records-grid">
        <div v-for="(item, idx) in models" :key="item.id" class="record-card"
          :class="{ 'is-enabled': item.isEnabled === 1 }"
          :style="{ borderLeft: `6px solid ${item.isEnabled === 1 ? getColor(item) : '#cbd5e1'}` }"
          :draggable="true"
          @dragstart="onDragStart(idx)" @dragover="onDragOver" @drop="onDrop(idx)">
          <!-- 拖拽手柄 -->
          <div class="drag-handle" title="拖拽排序">↕</div>
          <!-- 内容 -->
          <div class="card-info" @click="handleToggle(item, $event)">
            <div class="title-row">
              <span class="toggle-check">{{ item.isEnabled === 1 ? '☑' : '☐' }}</span>
              <h4 :style="{ color: getColor(item) }">{{ getTitle(item) }}</h4>
              <span v-if="item.isEnabled === 1" class="enabled-tag" :style="{ backgroundColor: getColor(item) }">{{ item.sortOrder ?? idx }}</span>
            </div>
            <span class="badge">模型：{{ item.modelName }}</span>
            <span class="badge" style="margin-left:6px">提供商：{{ item.provider }}</span>
            <span v-if="item.displayName" class="badge" style="margin-left:6px;color:var(--theme-color)">别称：{{ item.displayName }}</span>
          </div>
          <div class="card-actions">
            <button class="edit-btn" @click="handleEditConfig(item, $event)">⚙️ 修改</button>
            <button class="delete-btn" @click="handleDeleteConfig(item.id!, $event)">🗑 删除</button>
          </div>
        </div>
        <div v-if="models.length === 0" style="text-align:center;color:#94a3b8;padding:40px;">
          暂无模型配置
        </div>
      </div>
    </div>
    <RuleConfig v-if="step === 'rule'" :initialData="currentConfig" @back="step = 'list'" @save="saveFinalConfig" />
    <ModelModal v-if="showModelModal" :defaultModel="currentConfig.modelName" :defaultProvider="currentConfig.provider"
      :defaultBaseUrl="currentConfig.baseUrl" @close="showModelModal = false" @submit="confirmModel" />
  </div>
</template>

<style scoped>
.rag-container-glass { width: 90%; max-width: 780px; padding: 24px; border-radius: 20px; background: rgba(255,255,255,0.45); backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px); border: 1px solid rgba(255,255,255,0.5); box-shadow: 0 20px 40px rgba(0,0,0,0.02); max-height: 80vh; overflow-y: auto; }
.list-layout { display: flex; flex-direction: column; gap: 16px; }
.list-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.mode-badge { font-size: 11px; color: #ea580c; background: rgba(234,88,12,0.1); padding: 4px 10px; border-radius: 10px; font-weight: bold; }
.multi-hint { font-size: 12px; color: #7c3aed; background: rgba(124,58,237,0.08); padding: 4px 12px; border-radius: 10px; font-weight: 600; }
.new-btn { padding: 10px 20px; font-weight: bold; background: rgba(255,255,255,0.7); border: 1px solid rgba(0,0,0,0.1); border-radius: 10px; cursor: pointer; }
.new-btn:hover { background: #fff; transform: translateY(-1px); }
.records-grid { display: flex; flex-direction: column; gap: 10px; margin-top: 10px; }
.record-card { display: flex; align-items: center; padding: 14px 14px; background: rgba(255,255,255,0.6); border-radius: 12px; transition: all 0.2s; cursor: grab; gap: 10px; }
.record-card:hover { background: rgba(255,255,255,0.85); }
.record-card:active { cursor: grabbing; }
.record-card.is-enabled { background: rgba(255,255,255,0.8); }
.drag-handle { font-size: 18px; color: #94a3b8; cursor: grab; user-select: none; padding: 0 4px; flex-shrink: 0; }
.drag-handle:active { cursor: grabbing; }
.card-info { flex: 1; min-width: 0; cursor: pointer; }
.title-row { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.toggle-check { font-size: 16px; color: var(--theme-color); flex-shrink: 0; }
.title-row h4 { margin: 0; font-size: 15px; }
.badge { font-size: 12px; color: #64748b; background: rgba(0,0,0,0.05); padding: 2px 8px; border-radius: 4px; }
.enabled-tag { font-size: 10px; color: #fff; padding: 1px 8px; border-radius: 12px; font-weight: bold; min-width: 18px; text-align: center; }
.card-actions { display: flex; align-items: center; gap: 6px; flex-shrink: 0; }
.edit-btn { padding: 4px 8px; font-size: 12px; background: rgba(0,0,0,0.04); border: 1px solid rgba(0,0,0,0.08); border-radius: 6px; cursor: pointer; color: #475569; }
.edit-btn:hover { background: #f1f5f9; color: #0f172a; }
.delete-btn { padding: 4px 8px; font-size: 12px; background: rgba(220,38,38,0.08); border: 1px solid rgba(220,38,38,0.15); border-radius: 6px; cursor: pointer; color: #dc2626; }
.delete-btn:hover { background: rgba(220,38,38,0.15); }
</style>
