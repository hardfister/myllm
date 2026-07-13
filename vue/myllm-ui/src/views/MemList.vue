<script setup lang="ts">
/**
 * MemList.vue — 记忆策略配置管理列表
 * ---------------
 * 功能：
 *   1. 三种策略可选：滑动窗口 / 摘要压缩 / 混合策略
 *   2. 丰富参数配置：窗口大小、最大历史、摘要阈值、压缩间隔、RAG、长期记忆等
 *   3. ☑/☐ 多选启用（独立切换）
 *   4. 修改 / 删除操作
 *   5. 在线/离线双模式
 *
 * 数据流：
 *   在线：GET/POST/PUT/DELETE /api/memories + PUT /api/memories/{id}/toggle
 *   离线：loadMemories() / saveMemories()
 *
 * Emits：updateColor(color) — 固定使用 #7c3aed
 */
import { ref, onMounted } from 'vue'
import { getMemories, createMemory, updateMemory, deleteMemory, toggleMemory } from '../api'
import { saveMemories, loadMemories } from '../services/localStorage'
import { useAuth } from '../services/auth'
import type { MemoryConfig } from '../api'

const { isLoggedIn, isOffline } = useAuth()
const emit = defineEmits(['updateColor'])

const memories = ref<MemoryConfig[]>([])  // 记忆配置列表
const showForm = ref(false)               // 是否显示配置表单
const isEditing = ref(false)              // 新建/编辑
const saving = ref(false)                 // 保存中

const useServer = () => isLoggedIn.value && !isOffline.value
let localIdCounter = Date.now()

// 新建时的默认值
const defaultConfig = (): MemoryConfig => ({
  strategyType: 'sliding_window', windowSize: 10, summaryTriggerTokens: 2048,
  summaryMaxLength: 300, enableRag: 0, ragCollectionName: '', ragTopK: 3,
  maxHistoryMessages: 50, enableLongTermMemory: 0, compressionInterval: 10,
  reserveSystemPrompt: 1, isEnabled: 0  // 新建默认不启用，待用户手动开启
})
const form = ref<MemoryConfig & { id?: number }>(defaultConfig())

// ===== 数据加载：localStorage 底 + 服务器覆盖 =====
const loadMemoriesData = async () => {
  const localData = loadMemories()
  if (useServer()) {
    try {
      const res = await getMemories(); memories.value = res.data
      const serverIds = new Set(res.data.map((m: any) => m.id))
      const localOnly = localData.filter((m: any) => m.id != null && !serverIds.has(m.id))
      for (const m of localOnly) { if (!memories.value.some((s: any) => s.id === m.id)) memories.value.push(m) }
    } catch { memories.value = localData }
  } else { memories.value = localData }
}
onMounted(loadMemoriesData)
const persistMemories = () => { saveMemories(memories.value) }

// ===== 多选切换：翻转单个配置（记忆配置可多选） =====
const handleToggle = async (record: MemoryConfig, event: Event) => {
  event.stopPropagation()
  if (record.id == null) return
  if (useServer()) {
    try { await toggleMemory(record.id); await loadMemoriesData(); saveMemories(memories.value) }
    catch (e) { console.error('切换失败:', e) }
  } else {
    const t = memories.value.find(m => m.id === record.id)
    if (t) { t.isEnabled = t.isEnabled === 1 ? 0 : 1; persistMemories() }
  }
}

// ===== 新建/编辑表单 =====
const handleNew = () => { isEditing.value = false; form.value = { ...defaultConfig() }; showForm.value = true }
const handleEdit = (item: MemoryConfig, event?: Event) => {
  event?.stopPropagation(); isEditing.value = true; form.value = { ...item }; showForm.value = true
}

// ===== 保存配置（在线 POST/PUT，离线改数组） =====
const handleSave = async () => {
  saving.value = true
  try {
    const { id, ...data } = form.value
    if (useServer()) {
      if (isEditing.value && id != null) { await updateMemory(id, data) }
      else { await createMemory(data) }
      await loadMemoriesData()
      saveMemories(memories.value)
    } else {
      if (isEditing.value && id != null) {
        const idx = memories.value.findIndex(m => m.id === id)
        if (idx !== -1) memories.value[idx] = { ...memories.value[idx], ...data }
      } else {
        memories.value.unshift({ ...data, id: ++localIdCounter })
      }
      persistMemories()
    }
    showForm.value = false
  } catch (e) { console.error('保存失败:', e); alert('保存失败') }
  finally { saving.value = false }
}

// ===== 删除 =====
const handleDelete = async (id: number, event: Event) => {
  event.stopPropagation()
  if (!confirm('确认删除？')) return
  try {
    if (useServer()) { await deleteMemory(id); await loadMemoriesData(); saveMemories(memories.value) }
    else { memories.value = memories.value.filter(m => m.id !== id); persistMemories() }
  } catch (e) { console.error('删除失败:', e) }
}

// 策略类型的中文映射 + 颜色
const strategyLabel = (s: string): string => ({ sliding_window: '滑动窗口', summary: '摘要压缩', hybrid: '混合策略' } as Record<string, string>)[s] || s
const strategyColor = (s: string): string => ({ sliding_window: '#0284c7', summary: '#16a34a', hybrid: '#7c3aed' } as Record<string, string>)[s] || '#94a3b8'

emit('updateColor', '#7c3aed')
</script>

<template>
  <div class="rag-container-glass">
    <div class="list-layout">
      <div class="list-header">
        <span v-if="!useServer()" class="mode-badge">📱 本地模式</span>
        <button class="new-btn" @click="handleNew">＋ 新建记忆配置</button>
      </div>

      <!-- ===== 记忆配置表单 ===== -->
      <div v-if="showForm" class="config-form">
        <h4>{{ isEditing ? '编辑记忆配置' : '新建记忆配置' }}</h4>
        <div class="form-grid">
          <div class="form-item"><label>记忆策略：</label>
            <select v-model="form.strategyType" class="styled-input">
              <option value="sliding_window">滑动窗口</option>
              <option value="summary">摘要压缩</option>
              <option value="hybrid">混合策略</option>
            </select></div>
          <div class="form-item"><label>窗口大小：</label><input type="number" v-model="form.windowSize" class="styled-input" min="1" max="100" /></div>
          <div class="form-item"><label>最大历史消息数：</label><input type="number" v-model="form.maxHistoryMessages" class="styled-input" min="1" max="500" /></div>
          <div class="form-item"><label>触发摘要 Token 阈值：</label><input type="number" v-model="form.summaryTriggerTokens" class="styled-input" min="256" /></div>
          <div class="form-item"><label>摘要最大长度：</label><input type="number" v-model="form.summaryMaxLength" class="styled-input" min="50" max="2000" /></div>
          <div class="form-item"><label>压缩间隔（轮）：</label><input type="number" v-model="form.compressionInterval" class="styled-input" min="1" max="100" /></div>
        </div>
        <!-- RAG 增强开关 -->
        <div class="toggle-group">
          <div class="toggle-item"><label>启用 RAG：</label>
            <select v-model="form.enableRag" class="styled-input" style="width:auto"><option :value="0">关闭</option><option :value="1">开启</option></select></div>
          <div v-if="form.enableRag === 1" class="form-item" style="flex:1"><label>RAG 集合名称：</label><input type="text" v-model="form.ragCollectionName" class="styled-input" /></div>
          <div v-if="form.enableRag === 1" class="form-item"><label>Top-K：</label><input type="number" v-model="form.ragTopK" class="styled-input" min="1" max="20" style="width:100px" /></div>
        </div>
        <!-- 长期记忆 / 保留 System Prompt 开关 -->
        <div class="toggle-group">
          <div class="toggle-item"><label>启用长期记忆：</label>
            <select v-model="form.enableLongTermMemory" class="styled-input" style="width:auto"><option :value="0">关闭</option><option :value="1">开启</option></select></div>
          <div class="toggle-item"><label>保留系统提示词：</label>
            <select v-model="form.reserveSystemPrompt" class="styled-input" style="width:auto"><option :value="0">不保留</option><option :value="1">保留</option></select></div>
        </div>
        <div class="form-actions">
          <button class="cancel-btn" @click="showForm = false">取消</button>
          <button class="save-btn" :disabled="saving" @click="handleSave">{{ saving ? '保存中...' : '保存配置' }}</button>
        </div>
      </div>

      <!-- ===== 记忆配置卡片列表 ===== -->
      <div class="records-grid">
        <div v-for="item in memories" :key="item.id" class="record-card"
          :class="{ 'is-enabled': item.isEnabled === 1 }"
          :style="{ borderLeft: `6px solid ${item.isEnabled === 1 ? strategyColor(item.strategyType) : '#cbd5e1'}` }">
          <div class="card-info">
            <div class="title-row">
              <!-- 策略类型徽章 -->
              <span class="strategy-badge" :style="{ backgroundColor: strategyColor(item.strategyType) }">{{ strategyLabel(item.strategyType) }}</span>
              <span v-if="item.isEnabled === 1" class="enabled-tag" :style="{ color: strategyColor(item.strategyType), background: strategyColor(item.strategyType) + '18' }">已启用</span>
              <span class="window-info">窗口: {{ item.windowSize }} | 最大历史: {{ item.maxHistoryMessages }}</span>
            </div>
            <div class="meta-row">
              <span v-if="item.enableRag" class="badge" style="background:rgba(13,148,136,0.1);color:#0d9488">📚 RAG: {{ item.ragCollectionName || '启用' }}</span>
              <span v-if="item.enableLongTermMemory" class="badge" style="background:rgba(124,58,237,0.1);color:#7c3aed">🧠 长期记忆</span>
              <span v-if="item.reserveSystemPrompt" class="badge">📌 保留System Prompt</span>
            </div>
          </div>
          <div class="card-actions">
            <button class="edit-btn" @click="handleEdit(item, $event)">⚙️ 修改</button>
            <button class="toggle-btn" @click="handleToggle(item, $event)"
              :style="{ color: item.isEnabled === 1 ? '#16a34a' : '#94a3b8' }">
              {{ item.isEnabled === 1 ? '☑ 启用' : '☐ 未启用' }}
            </button>
            <button class="delete-btn" @click="handleDelete(item.id!, $event)">🗑 删除</button>
          </div>
        </div>
        <div v-if="memories.length === 0 && !showForm" style="text-align:center;color:#94a3b8;padding:40px;">暂无记忆配置</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.rag-container-glass { width: 90%; max-width: 750px; padding: 24px; border-radius: 20px; background: rgba(255,255,255,0.45); backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px); border: 1px solid rgba(255,255,255,0.5); box-shadow: 0 20px 40px rgba(0,0,0,0.02); max-height: 80vh; overflow-y: auto; }
.list-layout { display: flex; flex-direction: column; gap: 16px; }
.list-header { display: flex; justify-content: space-between; align-items: center; }
.mode-badge { font-size: 11px; color: #ea580c; background: rgba(234,88,12,0.1); padding: 4px 10px; border-radius: 10px; font-weight: bold; }
.new-btn { padding: 10px 20px; font-weight: bold; background: rgba(255,255,255,0.7); border: 1px solid rgba(0,0,0,0.1); border-radius: 10px; cursor: pointer; }
.new-btn:hover { background: #fff; transform: translateY(-1px); }
.config-form { display: flex; flex-direction: column; gap: 16px; padding: 20px; background: rgba(255,255,255,0.55); border-radius: 12px; border: 1px solid rgba(0,0,0,0.06); }
.config-form h4 { margin: 0 0 4px; font-size: 16px; color: #1e293b; }
.form-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 14px; }
.form-item { display: flex; flex-direction: column; gap: 4px; }
.form-item label { font-size: 13px; font-weight: 600; color: #475569; }
.styled-input { padding: 10px; border-radius: 8px; border: 1px solid rgba(0,0,0,0.1); background: rgba(255,255,255,0.6); outline: none; font-size: 14px; }
.toggle-group { display: flex; flex-wrap: wrap; gap: 16px; align-items: center; }
.toggle-item { display: flex; align-items: center; gap: 8px; }
.toggle-item label { font-size: 13px; font-weight: 600; color: #475569; }
.form-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 8px; }
.cancel-btn { padding: 10px 20px; background: rgba(0,0,0,0.05); border: 1px solid rgba(0,0,0,0.1); border-radius: 8px; cursor: pointer; font-weight: bold; color: #475569; }
.save-btn { padding: 10px 24px; background: #7c3aed; color: white; border: none; border-radius: 8px; cursor: pointer; font-weight: bold; }
.save-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.records-grid { display: flex; flex-direction: column; gap: 12px; margin-top: 10px; }
.record-card { display: flex; justify-content: space-between; align-items: center; padding: 16px 20px; background: rgba(255,255,255,0.6); border-radius: 12px; transition: background 0.2s; }
.record-card:hover { background: rgba(255,255,255,0.85); }
.record-card.is-enabled { background: rgba(255,255,255,0.8); }
.card-info { flex: 1; }
.title-row { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
.strategy-badge { font-size: 12px; color: #fff; padding: 3px 12px; border-radius: 12px; font-weight: bold; white-space: nowrap; }
.enabled-tag { font-size: 10px; padding: 1px 8px; border-radius: 20px; font-weight: bold; white-space: nowrap; }
.window-info { font-size: 13px; color: #64748b; }
.meta-row { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
.badge { font-size: 12px; padding: 2px 8px; border-radius: 4px; background: rgba(0,0,0,0.05); color: #64748b; }
.card-actions { display: flex; gap: 8px; flex-shrink: 0; margin-left: 12px; }
.edit-btn { padding: 4px 10px; font-size: 12px; background: rgba(0,0,0,0.04); border: 1px solid rgba(0,0,0,0.08); border-radius: 6px; cursor: pointer; color: #475569; white-space: nowrap; }
.edit-btn:hover { background: #f1f5f9; color: #0f172a; }
.toggle-btn { padding: 4px 10px; font-size: 12px; background: rgba(0,0,0,0.04); border: 1px solid rgba(0,0,0,0.08); border-radius: 6px; cursor: pointer; white-space: nowrap; }
.toggle-btn:hover { background: #f1f5f9; }
.delete-btn { padding: 4px 10px; font-size: 12px; background: rgba(220,38,38,0.08); border: 1px solid rgba(220,38,38,0.15); border-radius: 6px; cursor: pointer; color: #dc2626; white-space: nowrap; }
.delete-btn:hover { background: rgba(220,38,38,0.15); }
</style>
