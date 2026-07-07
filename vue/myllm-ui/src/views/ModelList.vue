<script setup lang="ts">
/**
 * ModelList.vue — 模型配置管理列表
 * ---------------
 * 功能：
 *   1. 列表展示所有模型配置（名称 / 提供商 / 启用状态）
 *   2. 三步新建流程：ModelModal（选模型+APIKey）→ RuleConfig（配Prompt+Token+颜色）→ 保存
 *   3. 点击卡片 → 独占激活该模型（调用 activateModel，其余模型自动停用）
 *   4. 修改 / 删除操作
 *   5. 在线/离线双模式：已登录且联网 → API；否则 → localStorage
 *
 * 数据流：
 *   在线：onMounted → GET /api/models → 渲染 → 点击 → PUT /api/models/{id}/activate
 *   离线：onMounted → loadModels() → 渲染 → 点击 → 修改数组 → saveModels()
 *
 * Emits：
 *   updateColor(color) — 更新全局主题色
 */
import { ref, onMounted } from 'vue'
import ModelModal from './ModelModal.vue'
import RuleConfig from './RuleConfig.vue'
import { getModels, createModel, updateModel, deleteModel, activateModel } from '../api'
import { saveModels, loadModels } from '../services/localStorage'
import { useAuth } from '../services/auth'
import type { ModelConfig } from '../api'

const { isLoggedIn, isOffline } = useAuth()
const emit = defineEmits(['updateColor'])

// ===== 步骤控制 =====
const step = ref<'list' | 'rule'>('list')   // list=列表页, rule=规则配置表单页
const showModelModal = ref<boolean>(false)   // 是否显示模型选择弹窗
const isEditing = ref<boolean>(false)        // 当前操作是"新建"还是"修改"
const activePresetId = ref<number | null>(null)  // 当前激活的配置 ID

// ===== 数据 =====
const models = ref<ModelConfig[]>([])        // 模型列表（来自 API 或 localStorage）
// 前端扩展元数据：标题和主题色不存 DB，改为持久化到 localStorage 的 key "myllm_meta_models"
const META_KEY = 'myllm_meta_models'
const loadMeta = (): Record<number, { title: string; color: string }> => {
  try { const raw = localStorage.getItem(META_KEY); return raw ? JSON.parse(raw) : {} }
  catch { return {} }
}
const saveMeta = (meta: Record<number, { title: string; color: string }>) => {
  localStorage.setItem(META_KEY, JSON.stringify(meta))
}
const frontendMeta = ref<Record<number, { title: string; color: string }>>(loadMeta())

/** 判断是否使用服务器模式（已登录 + 有网络） */
const useServer = () => isLoggedIn.value && !isOffline.value

// 当前正在创建/编辑的配置（双向绑定到子组件）
const currentConfig = ref({
  id: 0 as number | undefined,
  modelName: 'DeepSeek-Chat',
  provider: 'DeepSeek',
  apiKeyEncrypted: '',
  baseUrl: 'https://api.deepseek.com',
  maxTokens: 4096,
  prompt: '',
  title: '',        // 前端展示用
  color: '#1e3a8a'  // 前端展示用
})

// 为离线模式生成临时 ID（负数，避免与服务器 ID 冲突）
let localIdCounter = Date.now()

// ===== 数据加载 =====

const loadModelsData = async () => {
  if (useServer()) {
    try {
      const res = await getModels()
      models.value = res.data
    } catch (e) {
      console.error('加载模型列表失败:', e)
      models.value = loadModels()  // 降级到 local
    }
  } else {
    models.value = loadModels()
  }

  // 为没有 meta 的模型初始化默认的标题和颜色
  let metaChanged = false
  for (const m of models.value) {
    if (m.id != null && !frontendMeta.value[m.id]) {
      frontendMeta.value[m.id] = {
        title: `${m.provider} - ${m.modelName}`,
        color: '#1e3a8a'
      }
      metaChanged = true
    }
  }
  if (metaChanged) saveMeta(frontendMeta.value)  // 新模型初始化后持久化
  // 自动选中第一个启用的模型
  if (activePresetId.value == null && models.value.length > 0) {
    const active = models.value.find(m => m.isEnabled === 1)
    if (active && active.id != null) {
      activePresetId.value = active.id
      emit('updateColor', frontendMeta.value[active.id]?.color || '#1e3a8a')
    }
  }
}

onMounted(loadModelsData)

/** 离线模式下持久化数据到 localStorage */
const persistModels = () => {
  if (!useServer()) saveModels(models.value)
}

// ===== 新建配置 =====
const handleNewConfig = () => {
  isEditing.value = false
  currentConfig.value = {
    id: undefined,
    modelName: 'DeepSeek-Chat', provider: 'DeepSeek',
    apiKeyEncrypted: '', baseUrl: 'https://api.deepseek.com',
    maxTokens: 4096, prompt: '', title: '', color: '#1e3a8a'
  }
  showModelModal.value = true  // 打开模型选择弹窗 → 第一步
}

// ===== 编辑配置 =====
const handleEditConfig = (item: ModelConfig, event: Event) => {
  event.stopPropagation()  // 防止触卡片点击激活
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

// ===== 点击卡片 → 独占激活（模型只能选一个） =====
const selectRecord = async (record: ModelConfig) => {
  if (record.id == null) return
  if (useServer()) {
    try {
      await activateModel(record.id)  // PUT /api/models/{id}/activate
      activePresetId.value = record.id
      emit('updateColor', frontendMeta.value[record.id]?.color || '#1e3a8a')
      await loadModelsData()
    } catch (e) { console.error('激活失败:', e) }
  } else {
    // 离线：更新数组中的 isEnabled，本地持久化
    models.value.forEach(m => { if (m.id != null) m.isEnabled = 0 })
    const target = models.value.find(m => m.id === record.id)
    if (target) { target.isEnabled = 1; activePresetId.value = target.id! }
    emit('updateColor', frontendMeta.value[record.id]?.color || '#1e3a8a')
    persistModels()
  }
}

// ===== ModelModal 确认 → 进入 RuleConfig 步骤 =====
const confirmModel = (modelData: { model: string; apiKey: string; provider: string; baseUrl: string }) => {
  currentConfig.value.modelName = modelData.model
  currentConfig.value.apiKeyEncrypted = modelData.apiKey
  currentConfig.value.provider = modelData.provider
  currentConfig.value.baseUrl = modelData.baseUrl
  showModelModal.value = false
  step.value = 'rule'  // 进入第二步
}

// ===== RuleConfig 保存 → 提交到服务器/local =====
const saveFinalConfig = async (finalData: { title: string; color: string; maxTokens: number; prompt: string }) => {
  currentConfig.value.title = finalData.title
  currentConfig.value.color = finalData.color
  currentConfig.value.maxTokens = finalData.maxTokens
  currentConfig.value.prompt = finalData.prompt

  // 构造后端所需的 payload
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
      // 服务器模式：POST 或 PUT
      if (isEditing.value && currentConfig.value.id != null) {
        await updateModel(currentConfig.value.id, payload)
      } else {
        const res = await createModel(payload)
        if (res.data.id != null) currentConfig.value.id = res.data.id
      }
      // 新建后自动激活
      if (!isEditing.value && currentConfig.value.id != null) {
        await activateModel(currentConfig.value.id)
        activePresetId.value = currentConfig.value.id
      }
      await loadModelsData()
    } else {
      // 离线模式：修改数组 + localStorage
      if (isEditing.value && currentConfig.value.id != null) {
        const idx = models.value.findIndex(m => m.id === currentConfig.value.id)
        if (idx !== -1) models.value[idx] = { ...models.value[idx], ...payload }
      } else {
        const newId = ++localIdCounter
        const newModel: ModelConfig = { ...payload, id: newId, isEnabled: 1 }
        models.value.forEach(m => { if (m.id != null) m.isEnabled = 0 })  // 独占激活
        models.value.unshift(newModel)
        currentConfig.value.id = newId
        activePresetId.value = newId
      }
      persistModels()
    }

    // 更新前端 meta（标题/颜色）并持久化到 localStorage
    if (currentConfig.value.id != null) {
      frontendMeta.value[currentConfig.value.id] = {
        title: currentConfig.value.title || payload.modelName,
        color: currentConfig.value.color
      }
      saveMeta(frontendMeta.value)  // 每次编辑/新建都持久化
    }
    emit('updateColor', currentConfig.value.color)
    step.value = 'list'  // 回到列表
  } catch (e) {
    console.error('保存失败:', e)
    alert('保存失败')
  }
}

// ===== 删除配置 =====
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
    saveMeta(frontendMeta.value)  // 删除后同步持久化
    if (activePresetId.value === id) activePresetId.value = null
  } catch (e) { console.error('删除失败:', e) }
}

// 从 frontendMeta 获取展示信息
const getTitle = (m: ModelConfig) => frontendMeta.value[m.id!]?.title || `${m.provider} - ${m.modelName}`
const getColor = (m: ModelConfig) => frontendMeta.value[m.id!]?.color || '#1e3a8a'
</script>

<template>
  <div class="rag-container-glass">
    <!-- ===== 列表视图 ===== -->
    <div v-if="step === 'list'" class="list-layout">
      <div class="list-header">
        <!-- 离线模式下显示"本地模式"角标 -->
        <span v-if="!useServer()" class="mode-badge">📱 本地模式</span>
        <button class="new-btn" @click="handleNewConfig">＋ 新建配置</button>
      </div>

      <!-- 模型卡片列表 -->
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

    <!-- ===== RuleConfig 子视图（第二步） ===== -->
    <RuleConfig v-if="step === 'rule'" :initialData="currentConfig" @back="step = 'list'" @save="saveFinalConfig" />

    <!-- ===== ModelModal 子视图（第一步） ===== -->
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
.new-btn { padding: 10px 20px; font-weight: bold; background: rgba(255,255,255,0.7); border: 1px solid rgba(0,0,0,0.1); border-radius: 10px; cursor: pointer; }
.new-btn:hover { background: #fff; transform: translateY(-1px); }
.records-grid { display: flex; flex-direction: column; gap: 12px; margin-top: 10px; }
.record-card {
  display: flex; justify-content: space-between; align-items: center;
  padding: 16px 20px; background: rgba(255,255,255,0.6); border-radius: 12px;
  cursor: pointer; transition: transform 0.2s, background 0.2s;
}
.record-card:hover { transform: scale(1.01); background: rgba(255,255,255,0.9); }
.record-card.is-active { background: rgba(255, 255, 255, 0.85); box-shadow: 0 4px 12px rgba(0,0,0,0.05); }
.title-row { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.title-row h4 { margin: 0; font-size: 16px; }
.badge { font-size: 12px; color: #64748b; background: rgba(0,0,0,0.05); padding: 2px 8px; border-radius: 4px; }
.active-tag { font-size: 10px; color: #fff; padding: 1px 6px; border-radius: 20px; font-weight: bold; }
.card-actions { display: flex; align-items: center; gap: 8px; }
.edit-btn { padding: 4px 8px; font-size: 12px; background: rgba(0,0,0,0.04); border: 1px solid rgba(0,0,0,0.08); border-radius: 6px; cursor: pointer; color: #475569; }
.edit-btn:hover { background: #f1f5f9; color: #0f172a; }
.delete-btn { padding: 4px 8px; font-size: 12px; background: rgba(220,38,38,0.08); border: 1px solid rgba(220,38,38,0.15); border-radius: 6px; cursor: pointer; color: #dc2626; }
.delete-btn:hover { background: rgba(220,38,38,0.15); }
</style>
