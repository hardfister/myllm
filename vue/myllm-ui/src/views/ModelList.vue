<script setup lang="ts">
import { ref, onMounted } from 'vue'
import ModelModal from './ModelModal.vue'
import RuleConfig from './RuleConfig.vue'
import { getModels, createModel, updateModel, deleteModel, activateModel } from '../api'
import { saveModels, loadModels } from '../services/localStorage'
import { useAuth } from '../services/auth'
import type { ModelConfig } from '../api'

const { isLoggedIn, isOffline } = useAuth()
const emit = defineEmits(['updateColor'])

const step = ref<'list' | 'rule'>('list')
const showModelModal = ref<boolean>(false)
const isEditing = ref<boolean>(false)
const activePresetId = ref<number | null>(null)

const models = ref<ModelConfig[]>([])
const frontendMeta = ref<Record<number, { title: string; color: string }>>({})

const useServer = () => isLoggedIn.value && !isOffline.value

const currentConfig = ref({
  id: 0 as number | undefined,
  modelName: 'DeepSeek-Chat',
  provider: 'DeepSeek',
  apiKeyEncrypted: '',
  baseUrl: 'https://api.deepseek.com',
  maxTokens: 4096,
  prompt: '',
  title: '',
  color: '#1e3a8a'
})

// Generate temp IDs for local storage
let localIdCounter = Date.now()

const loadModelsData = async () => {
  if (useServer()) {
    try {
      const res = await getModels()
      models.value = res.data
    } catch (e) {
      console.error('加载模型列表失败:', e)
      // Fallback to local
      models.value = loadModels()
    }
  } else {
    models.value = loadModels()
  }

  // Init meta
  for (const m of models.value) {
    if (m.id != null && !frontendMeta.value[m.id]) {
      frontendMeta.value[m.id] = {
        title: `${m.provider} - ${m.modelName}`,
        color: '#1e3a8a'
      }
    }
  }
  if (activePresetId.value == null && models.value.length > 0) {
    const active = models.value.find(m => m.isEnabled === 1)
    if (active && active.id != null) {
      activePresetId.value = active.id
      emit('updateColor', frontendMeta.value[active.id]?.color || '#1e3a8a')
    }
  }
}

onMounted(loadModelsData)

const persistModels = () => {
  if (!useServer()) {
    saveModels(models.value)
  }
}

const handleNewConfig = () => {
  isEditing.value = false
  currentConfig.value = {
    id: undefined,
    modelName: 'DeepSeek-Chat', provider: 'DeepSeek',
    apiKeyEncrypted: '', baseUrl: 'https://api.deepseek.com',
    maxTokens: 4096, prompt: '', title: '', color: '#1e3a8a'
  }
  showModelModal.value = true
}

const handleEditConfig = (item: ModelConfig, event: Event) => {
  event.stopPropagation()
  isEditing.value = true
  const meta = frontendMeta.value[item.id!] || { title: '', color: '#1e3a8a' }
  currentConfig.value = {
    id: item.id, modelName: item.modelName, provider: item.provider,
    apiKeyEncrypted: item.apiKeyEncrypted || '', baseUrl: item.baseUrl || '',
    maxTokens: item.maxTokens || 4096, prompt: item.prompt || '',
    title: meta.title, color: meta.color
  }
  showModelModal.value = true
}

const selectRecord = async (record: ModelConfig) => {
  if (record.id == null) return
  if (useServer()) {
    try {
      await activateModel(record.id)
      activePresetId.value = record.id
      emit('updateColor', frontendMeta.value[record.id]?.color || '#1e3a8a')
      await loadModelsData()
    } catch (e) { console.error('激活失败:', e) }
  } else {
    // Offline: just set active locally
    models.value.forEach(m => { if (m.id != null) m.isEnabled = 0 })
    const target = models.value.find(m => m.id === record.id)
    if (target) { target.isEnabled = 1; activePresetId.value = target.id! }
    emit('updateColor', frontendMeta.value[record.id]?.color || '#1e3a8a')
    persistModels()
  }
}

const confirmModel = (modelData: { model: string; apiKey: string; provider: string; baseUrl: string }) => {
  currentConfig.value.modelName = modelData.model
  currentConfig.value.apiKeyEncrypted = modelData.apiKey
  currentConfig.value.provider = modelData.provider
  currentConfig.value.baseUrl = modelData.baseUrl
  showModelModal.value = false
  step.value = 'rule'
}

const saveFinalConfig = async (finalData: { title: string; color: string; maxTokens: number; prompt: string }) => {
  currentConfig.value.title = finalData.title
  currentConfig.value.color = finalData.color
  currentConfig.value.maxTokens = finalData.maxTokens
  currentConfig.value.prompt = finalData.prompt

  const payload: ModelConfig = {
    modelName: currentConfig.value.modelName,
    provider: currentConfig.value.provider,
    apiKeyEncrypted: currentConfig.value.apiKeyEncrypted,
    baseUrl: currentConfig.value.baseUrl,
    maxTokens: currentConfig.value.maxTokens,
    prompt: currentConfig.value.prompt,
    isEnabled: 1
  }

  try {
    if (useServer()) {
      if (isEditing.value && currentConfig.value.id != null) {
        await updateModel(currentConfig.value.id, payload)
      } else {
        const res = await createModel(payload)
        if (res.data.id != null) currentConfig.value.id = res.data.id
      }
      if (!isEditing.value && currentConfig.value.id != null) {
        await activateModel(currentConfig.value.id)
        activePresetId.value = currentConfig.value.id
      }
      await loadModelsData()
    } else {
      // Offline save
      if (isEditing.value && currentConfig.value.id != null) {
        const idx = models.value.findIndex(m => m.id === currentConfig.value.id)
        if (idx !== -1) {
          models.value[idx] = { ...models.value[idx], ...payload }
        }
      } else {
        const newId = ++localIdCounter
        const newModel: ModelConfig = { ...payload, id: newId, isEnabled: 1 }
        models.value.forEach(m => { if (m.id != null) m.isEnabled = 0 })
        models.value.unshift(newModel)
        currentConfig.value.id = newId
        activePresetId.value = newId
      }
      persistModels()
    }

    if (currentConfig.value.id != null) {
      frontendMeta.value[currentConfig.value.id] = {
        title: currentConfig.value.title || payload.modelName,
        color: currentConfig.value.color
      }
    }
    emit('updateColor', currentConfig.value.color)
    step.value = 'list'
  } catch (e) {
    console.error('保存失败:', e)
    alert('保存失败')
  }
}

const handleDeleteConfig = async (id: number, event: Event) => {
  event.stopPropagation()
  if (!confirm('确认删除此模型配置？')) return
  try {
    if (useServer()) {
      await deleteModel(id)
      await loadModelsData()
    } else {
      models.value = models.value.filter(m => m.id !== id)
      persistModels()
    }
    delete frontendMeta.value[id]
    if (activePresetId.value === id) activePresetId.value = null
  } catch (e) {
    console.error('删除失败:', e)
  }
}

const getTitle = (m: ModelConfig) => frontendMeta.value[m.id!]?.title || `${m.provider} - ${m.modelName}`
const getColor = (m: ModelConfig) => frontendMeta.value[m.id!]?.color || '#1e3a8a'
</script>

<template>
  <div class="rag-container-glass">
    <div v-if="step === 'list'" class="list-layout">
      <div class="list-header">
        <span v-if="!useServer()" class="mode-badge">📱 本地模式</span>
        <button class="new-btn" @click="handleNewConfig">＋ 新建配置</button>
      </div>
      <div class="records-grid">
        <div v-for="item in models" :key="item.id" class="record-card"
          :class="{ 'is-active': item.id === activePresetId }"
          :style="{ borderLeft: `6px solid ${getColor(item)}` }"
          @click="selectRecord(item)">
          <div class="card-info">
            <div class="title-row">
              <h4 :style="{ color: getColor(item) }">{{ getTitle(item) }}</h4>
              <span v-if="item.id === activePresetId" class="active-tag" :style="{ backgroundColor: getColor(item) }">生效中</span>
            </div>
            <span class="badge">模型：{{ item.modelName }}</span>
            <span class="badge" style="margin-left:6px">提供商：{{ item.provider }}</span>
          </div>
          <div class="card-actions">
            <button class="edit-btn" @click="handleEditConfig(item, $event)">⚙️ 修改</button>
            <button class="delete-btn" @click="handleDeleteConfig(item.id!, $event)">🗑 删除</button>
          </div>
        </div>
        <div v-if="models.length === 0" style="text-align:center;color:#94a3b8;padding:40px;">
          暂无模型配置，点击"新建配置"开始
        </div>
      </div>
    </div>

    <RuleConfig v-if="step === 'rule'" :initialData="currentConfig" @back="step = 'list'" @save="saveFinalConfig" />
    <ModelModal v-if="showModelModal" :defaultModel="currentConfig.modelName" :defaultProvider="currentConfig.provider"
      :defaultBaseUrl="currentConfig.baseUrl" @close="showModelModal = false" @submit="confirmModel" />
  </div>
</template>

<style scoped>
.rag-container-glass {
  width: 90%; max-width: 750px; padding: 24px; border-radius: 20px;
  background: rgba(255, 255, 255, 0.45); backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.5); box-shadow: 0 20px 40px rgba(0,0,0,0.02);
}
.list-layout { display: flex; flex-direction: column; gap: 16px; }
.list-header { display: flex; justify-content: space-between; align-items: center; }
.mode-badge { font-size: 11px; color: #ea580c; background: rgba(234,88,12,0.1); padding: 4px 10px; border-radius: 10px; font-weight: bold; }
.new-btn {
  padding: 10px 20px; font-weight: bold; background: rgba(255,255,255,0.7);
  border: 1px solid rgba(0,0,0,0.1); border-radius: 10px; cursor: pointer; transition: all 0.2s;
}
.new-btn:hover { background: #ffffff; transform: translateY(-1px); }
.records-grid { display: flex; flex-direction: column; gap: 12px; margin-top: 10px; }
.record-card {
  display: flex; justify-content: space-between; align-items: center;
  padding: 16px 20px; background: rgba(255,255,255,0.6); border-radius: 12px;
  cursor: pointer; transition: transform 0.2s, background 0.2s;
}
.record-card:hover { transform: scale(1.01); background: rgba(255,255,255,0.9); }
.record-card.is-active { background: rgba(255, 255, 255, 0.85); box-shadow: 0 4px 12px rgba(0,0,0,0.05); }
.card-info h4 { margin: 0 0 6px 0; font-size: 16px; }
.badge { font-size: 12px; color: #64748b; background: rgba(0,0,0,0.05); padding: 2px 8px; border-radius: 4px; }
.title-row { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.title-row h4 { margin: 0; font-size: 16px; }
.active-tag { font-size: 10px; color: #fff; padding: 1px 6px; border-radius: 20px; font-weight: bold; }
.card-actions { display: flex; align-items: center; gap: 8px; }
.edit-btn {
  padding: 4px 8px; font-size: 12px; background: rgba(0,0,0,0.04);
  border: 1px solid rgba(0,0,0,0.08); border-radius: 6px; cursor: pointer; color: #475569;
}
.edit-btn:hover { background: #f1f5f9; color: #0f172a; }
.delete-btn {
  padding: 4px 8px; font-size: 12px; background: rgba(220,38,38,0.08);
  border: 1px solid rgba(220,38,38,0.15); border-radius: 6px; cursor: pointer; color: #dc2626;
}
.delete-btn:hover { background: rgba(220,38,38,0.15); }
</style>
