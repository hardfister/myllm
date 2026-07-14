<script setup lang="ts">
/**
 * RagList.vue — 自定义知识库管理
 * ---------------
 * 上半部分：上传文档 + 切片配置 + 文档列表
 * 下半部分：嵌入模型选择（复用 ModelList 逻辑，单选）+ 每文档"向量化"按钮
 *
 * 流程：
 *   1. 上传文件 → 服务器保存 (status=pending)
 *   2. 在下半部分选择一个嵌入模型（已配置 apiKey 的模型）
 *   3. 点击文档上的"向量化"按钮 → POST /api/rags/{id}/embed?modelId=xx
 *   4. 向量化完成 → status=completed → 聊天时自动 RAG 检索
 */
import { ref, onMounted } from 'vue'
import { getRags, createRag, deleteRag, updateRag, toggleRag, embedRag } from '../api'
import { getModels } from '../api'
import { saveRags, loadRags } from '../services/localStorage'
import { useAuth } from '../services/auth'
import type { Rag, ModelConfig } from '../api'

const { isLoggedIn, isOffline } = useAuth()
const emit = defineEmits(['updateColor'])

// ===== 文档列表 =====
const rags = ref<Rag[]>([])
const showUploadForm = ref(false)
const uploading = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)
const selectedFile = ref<File | null>(null)
const dragOver = ref(false)
const textInput = ref('')                // 直接粘贴文本
const inputMode = ref<'file' | 'text'>('file')  // 文件上传 / 文本粘贴
const editingRag = ref<Rag | null>(null)
const showEditModal = ref(false)

// ===== 上传/切片配置 =====
const collectionName = ref('default_collection')
const chunkSize = ref(500)
const chunkOverlap = ref(50)
const chunkMethod = ref<'fixed_size' | 'paragraph' | 'sentence'>('fixed_size')

// ===== 嵌入模型选择 =====
const embeddingModels = ref<ModelConfig[]>([])        // 所有 ModelConfig（用户可在模型页配置）
const selectedEmbeddingModelId = ref<number | null>(null)  // 当前选中的嵌入模型
const embeddingInProgress = ref<Record<number, boolean>>({})  // 正在向量化的文档 ID

const useServer = () => isLoggedIn.value && !isOffline.value
let localIdCounter = Date.now()

// ===== 数据加载 =====
const loadRagsData = async () => {
  if (useServer()) {
    try { const res = await getRags(); rags.value = res.data }
    catch { console.error('加载知识库列表失败') }
  } else { rags.value = loadRags() }
}
const loadEmbeddingModels = async () => {
  if (useServer()) {
    try { const r = await getModels(); embeddingModels.value = r.data }
    catch { /* local fallback */ }
  }
}
onMounted(() => { loadRagsData(); loadEmbeddingModels() })
const persistRags = () => { saveRags(rags.value) }

// ===== 文件选择/拖拽 =====
const onFileChange = (e: Event) => { const t = e.target as HTMLInputElement; if (t.files?.[0]) selectedFile.value = t.files[0] }
const onDragOver = (e: DragEvent) => { e.preventDefault(); dragOver.value = true }
const onDragLeave = () => { dragOver.value = false }
const onDrop = (e: DragEvent) => { e.preventDefault(); dragOver.value = false; if (e.dataTransfer?.files?.[0]) selectedFile.value = e.dataTransfer.files[0] }

// ===== 上传/粘贴 =====
const handleUpload = async () => {
  if (inputMode.value === 'file' && !selectedFile.value) { alert('请先选择文件'); return }
  if (inputMode.value === 'text' && !textInput.value.trim()) { alert('请先输入文本'); return }
  uploading.value = true
  try {
    let success = false
    if (useServer()) {
      try {
        if (inputMode.value === 'file' && selectedFile.value) {
          const fd = new FormData()
          fd.append('file', selectedFile.value)
          fd.append('collectionName', collectionName.value)
          fd.append('chunkSize', String(chunkSize.value))
          fd.append('chunkOverlap', String(chunkOverlap.value))
          fd.append('chunkMethod', chunkMethod.value)
          await createRag(fd)
        } else {
          // 文本粘贴 → 创建 Blob → FormData
          const blob = new Blob([textInput.value], { type: 'text/plain' })
          const fd = new FormData()
          fd.append('file', blob, 'pasted-text-' + Date.now() + '.txt')
          fd.append('collectionName', collectionName.value)
          fd.append('chunkSize', String(chunkSize.value))
          fd.append('chunkOverlap', String(chunkOverlap.value))
          fd.append('chunkMethod', chunkMethod.value)
          await createRag(fd)
        }
        await loadRagsData()
        success = true
      } catch (e) {
        console.warn('服务器上传失败，降级到本地存储:', e)
        alert('上传到服务器失败\n数据已保存到浏览器本地存储。')
      }
    }
    if (!success) {
      const content = inputMode.value === 'file' ? await readFileAsBase64() : textInput.value
      rags.value.unshift({
        id: ++localIdCounter,
        filename: inputMode.value === 'file' ? selectedFile.value!.name : '粘贴文本.txt',
        fileSize: content.length, fileType: 'text/plain',
        collectionName: collectionName.value, chunkCount: 0,
        chunkSize: chunkSize.value, chunkOverlap: chunkOverlap.value, chunkMethod: chunkMethod.value,
        status: 'uploaded', description: '', filePath: content, isEnabled: 0
      })
      persistRags()
    }
    selectedFile.value = null; textInput.value = ''
    showUploadForm.value = false
    collectionName.value = 'default_collection'; chunkSize.value = 500; chunkOverlap.value = 50; chunkMethod.value = 'fixed_size'
  } catch (e) { console.error('上传失败:', e); alert('上传失败') }
  finally { uploading.value = false }
}

const readFileAsBase64 = (): Promise<string> => {
  return new Promise(r => {
    const reader = new FileReader()
    reader.onload = () => r(reader.result as string)
    reader.readAsDataURL(selectedFile.value!)
  })
}

// ===== 向量化 =====
const handleEmbed = async (item: Rag) => {
  if (!selectedEmbeddingModelId.value || !item.id) { alert('请先在下方选择一个嵌入模型'); return }
  embeddingInProgress.value[item.id] = true
  try {
    await embedRag(item.id, selectedEmbeddingModelId.value)
    await loadRagsData()
  } catch (e) { console.error('向量化失败:', e); alert('向量化失败，请确认嵌入模型已配置 API Key') }
  finally { delete embeddingInProgress.value[item.id] }
}

// ===== 编辑 =====
const openEditModal = (item: Rag, event: Event) => { event.stopPropagation(); editingRag.value = { ...item }; showEditModal.value = true }
const saveEdit = async () => {
  if (!editingRag.value?.id) return
  try {
    if (useServer()) { await updateRag(editingRag.value.id, editingRag.value); await loadRagsData() }
    else { const idx = rags.value.findIndex(r => r.id === editingRag.value!.id); if (idx !== -1) rags.value[idx] = { ...editingRag.value! }; persistRags() }
    showEditModal.value = false
  } catch (e) { console.error('保存失败:', e); alert('保存失败') }
}

// ===== 启用/禁用 =====
const handleToggle = async (record: Rag, event: Event) => {
  event.stopPropagation()
  if (record.id == null) return
  if (useServer()) { try { await toggleRag(record.id); await loadRagsData() } catch (e) { console.error(e) } }
  else { const t = rags.value.find(r => r.id === record.id); if (t) { t.isEnabled = t.isEnabled === 1 ? 0 : 1; persistRags() } }
}

// ===== 删除 =====
const handleDelete = async (id: number, event: Event) => {
  event.stopPropagation()
  if (!confirm('确认删除此文档？')) return
  try { if (useServer()) { await deleteRag(id); await loadRagsData() } else { rags.value = rags.value.filter(r => r.id !== id); persistRags() } }
  catch (e) { console.error('删除失败:', e) }
}

// ===== 工具 =====
const formatFileSize = (b: number) => b < 1024 ? b + ' B' : b < 1048576 ? (b / 1024).toFixed(1) + ' KB' : (b / 1048576).toFixed(1) + ' MB'
const statusLabel = (s?: string): string => ({ completed: '✅ 已向量化', embedding: '⏳ 向量化中', chunking: '📝 切片中', uploaded: '📄 已上传', pending: '⏸ 待处理', failed: '❌ 失败' } as Record<string, string>)[s || ''] || s || ''
const statusColor = (s?: string): string => ({ completed: '#16a34a', embedding: '#ea580c', chunking: '#ca8a04', uploaded: '#0284c7', pending: '#94a3b8', failed: '#dc2626' } as Record<string, string>)[s || ''] || '#94a3b8'

emit('updateColor', '#0d9488')
</script>

<template>
  <div class="rag-container-glass">
    <div class="list-layout">
      <div class="list-header">
        <span v-if="!useServer()" class="mode-badge">📱 本地模式</span>
        <button class="new-btn" @click="showUploadForm = !showUploadForm">{{ showUploadForm ? '取消上传' : '＋ 新建知识库' }}</button>
      </div>

      <!-- ===== 上传表单 ===== -->
      <div v-if="showUploadForm" class="upload-form">
        <h4>📄 新建知识库</h4>
        <!-- 模式切换 -->
        <div class="mode-tabs">
          <button :class="['mode-tab', { active: inputMode === 'file' }]" @click="inputMode = 'file'">📁 上传文件</button>
          <button :class="['mode-tab', { active: inputMode === 'text' }]" @click="inputMode = 'text'">📝 粘贴文本</button>
        </div>
        <!-- 文件上传区 -->
        <div v-if="inputMode === 'file'" :class="['drop-zone', { 'drag-over': dragOver }]" @dragover="onDragOver" @dragleave="onDragLeave" @drop="onDrop" @click="fileInput?.click()">
          <div v-if="!selectedFile" class="drop-text"><span class="drop-icon">📁</span><p>拖拽文件到此处，或点击选择</p><p class="hint">支持 TXT / MD / CSV / JSON / PDF / DOCX / HTML</p></div>
          <div v-else class="selected-file"><span class="file-icon">📄</span><span>{{ selectedFile.name }}</span><span class="file-size">{{ formatFileSize(selectedFile.size) }}</span></div>
        </div>
        <input v-if="inputMode === 'file'" ref="fileInput" type="file" accept=".txt,.md,.csv,.json,.pdf,.docx,.doc,.html,.htm,.xml,.yaml,.yml,.log,.py,.js,.ts,.java,.c,.cpp,.rs,.go,.rb,.sh,.bat,.ps1,.ini,.cfg,.toml" style="display:none" @change="onFileChange" />
        <!-- 文本粘贴区 -->
        <textarea v-if="inputMode === 'text'" v-model="textInput" class="text-paste-area" rows="8" placeholder="在此粘贴文本内容...&#10;&#10;支持纯文本、Markdown、代码等"></textarea>
        <div class="chunk-config-section">
          <h5>🔪 切片配置</h5>
          <div class="chunk-config-grid">
            <div class="form-item"><label>向量集合名称：</label><input type="text" v-model="collectionName" class="styled-input" /></div>
            <div class="form-item"><label>切片方式：</label><select v-model="chunkMethod" class="styled-input"><option value="fixed_size">固定大小</option><option value="paragraph">按段落</option><option value="sentence">按句子</option></select></div>
            <div class="form-item" v-if="chunkMethod !== 'paragraph'"><label>切片大小（字符）：</label><input type="number" v-model="chunkSize" class="styled-input" min="100" max="10000" step="100" /></div>
            <div class="form-item" v-if="chunkMethod !== 'paragraph'"><label>重叠（字符）：</label><input type="number" v-model="chunkOverlap" class="styled-input" min="0" max="2000" step="50" /></div>
          </div>
        </div>
        <button class="upload-btn" :disabled="(inputMode === 'file' && !selectedFile) || (inputMode === 'text' && !textInput.trim()) || uploading" @click="handleUpload">{{ uploading ? '上传中...' : inputMode === 'text' ? '确认提交' : '确认上传' }}</button>
      </div>

      <!-- ===== 文档卡片列表 ===== -->
      <div class="records-grid">
        <div v-for="item in rags" :key="item.id" class="record-card" :class="{ 'is-enabled': item.isEnabled === 1 }"
          :style="{ borderLeft: `6px solid ${item.isEnabled === 1 ? '#0d9488' : '#cbd5e1'}` }">
          <div class="card-info">
            <div class="title-row">
              <span class="file-type-badge" :style="{ backgroundColor: statusColor(item.status) }">{{ item.fileType || '文件' }}</span>
              <h4>{{ item.filename }}</h4>
              <span v-if="item.isEnabled === 1" class="enabled-tag">已启用</span>
            </div>
            <div class="meta-row">
              <span class="badge">📦 {{ item.collectionName }}</span>
              <span class="badge">✂️ {{ item.chunkMethod === 'fixed_size' ? '固定' : item.chunkMethod === 'paragraph' ? '段落' : '句子' }} · {{ item.chunkSize || 500 }}字/片</span>
              <span class="badge">📊 {{ item.chunkCount }} 片</span>
              <span class="badge">{{ formatFileSize(item.fileSize) }}</span>
              <span class="status-badge" :style="{ backgroundColor: statusColor(item.status), color: '#fff' }">{{ statusLabel(item.status) }}</span>
            </div>
          </div>
          <div class="card-actions">
            <!-- 向量化按钮：只有选了嵌入模型 + 文档需要向量化时才启用 -->
            <button class="embed-btn" :disabled="!selectedEmbeddingModelId || item.status === 'embedding' || embeddingInProgress[item.id!]"
              :style="{ color: item.status === 'completed' ? '#16a34a' : '#0d9488' }"
              @click="handleEmbed(item)">
              {{ embeddingInProgress[item.id!] ? '⏳' : item.status === 'completed' ? '🔄 重向量化' : item.status === 'pending' ? '⚡ 向量化' : '⚡ 向量化' }}
            </button>
            <button class="edit-btn" @click="openEditModal(item, $event)">✏️</button>
            <button class="toggle-btn" @click="handleToggle(item, $event)" :style="{ color: item.isEnabled === 1 ? '#16a34a' : '#94a3b8' }">{{ item.isEnabled === 1 ? '☑' : '☐' }}</button>
            <button class="delete-btn" @click="handleDelete(item.id!, $event)">🗑</button>
          </div>
        </div>
        <div v-if="rags.length === 0 && !showUploadForm" style="text-align:center;color:#94a3b8;padding:40px;">暂无知识库文档</div>
      </div>

      <!-- ====== 嵌入模型选择（页面下半部分） ====== -->
      <div class="embed-section">
        <div class="embed-section-header">
          <h4>🧩 嵌入模型（向量化用）</h4>
          <span class="hint-text">选一个模型用于向量化，需已配置 API Key</span>
        </div>
        <div class="embed-models-grid" v-if="embeddingModels.length > 0">
          <div v-for="m in embeddingModels" :key="m.id"
            :class="['embed-model-chip', { selected: m.id === selectedEmbeddingModelId }]"
            @click="selectedEmbeddingModelId = m.id!">
            <span class="chip-name">{{ m.displayName || m.modelName }}</span>
            <span class="chip-provider">{{ m.provider }}</span>
            <span v-if="!m.apiKeyEncrypted" class="chip-warn">⚠ 未配置 Key</span>
          </div>
        </div>
        <div v-else class="hint-text" style="margin-top:8px;">暂无模型配置，请先在「自定义模型」中创建</div>
        <div v-if="selectedEmbeddingModelId" class="selected-hint">
          ✅ 已选择：{{ embeddingModels.find(m => m.id === selectedEmbeddingModelId)?.displayName || embeddingModels.find(m => m.id === selectedEmbeddingModelId)?.modelName }}
          — 点击文档上的"⚡ 向量化"开始
        </div>
      </div>
    </div>

    <!-- ===== 编辑弹窗 ===== -->
    <Teleport to="body">
      <div v-if="showEditModal" class="login-overlay" @click.self="showEditModal = false">
        <div class="login-box" style="width:480px;max-height:80vh;overflow-y:auto;">
          <div class="login-header"><h3>📄 {{ editingRag?.filename }}</h3><button class="close-btn" @click="showEditModal = false">✕</button></div>
          <div class="form-body">
            <div class="form-item"><label>集合名称</label><input type="text" v-model="editingRag!.collectionName" class="styled-input" /></div>
            <div class="form-item"><label>描述</label><input type="text" v-model="editingRag!.description" class="styled-input" /></div>
            <div class="form-item"><label>切片方式</label><select v-model="editingRag!.chunkMethod" class="styled-input"><option value="fixed_size">固定大小</option><option value="paragraph">按段落</option><option value="sentence">按句子</option></select></div>
            <div class="form-item" v-if="editingRag?.chunkMethod !== 'paragraph'"><label>切片大小</label><input type="number" v-model="editingRag!.chunkSize" class="styled-input" min="100" max="10000" /></div>
            <div class="form-item" v-if="editingRag?.chunkMethod !== 'paragraph'"><label>重叠</label><input type="number" v-model="editingRag!.chunkOverlap" class="styled-input" min="0" max="2000" /></div>
            <div class="form-item"><label>切片数：<strong>{{ editingRag?.chunkCount || 0 }}</strong> | 状态：<strong :style="{color:statusColor(editingRag?.status)}">{{ statusLabel(editingRag?.status) }}</strong></label></div>
            <p style="font-size:11px;color:#94a3b8;">修改切片参数后需重新点击向量化（上传时只保存文件，向量化时才提取文本+切片）</p>
            <button class="submit-btn" @click="saveEdit">保存</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.rag-container-glass { width: 90%; max-width: 820px; padding: 24px; border-radius: 20px; background: rgba(255,255,255,0.45); backdrop-filter: blur(20px); border: 1px solid rgba(255,255,255,0.5); box-shadow: 0 20px 40px rgba(0,0,0,0.02); max-height: 80vh; overflow-y: auto; }
.list-layout { display: flex; flex-direction: column; gap: 16px; }
.list-header { display: flex; justify-content: space-between; align-items: center; }
.mode-badge { font-size: 11px; color: #ea580c; background: rgba(234,88,12,0.1); padding: 4px 10px; border-radius: 10px; font-weight: bold; }
.new-btn { padding: 10px 20px; font-weight: bold; background: rgba(255,255,255,0.7); border: 1px solid rgba(0,0,0,0.1); border-radius: 10px; cursor: pointer; }
.new-btn:hover { background: #fff; transform: translateY(-1px); }
.upload-form { display: flex; flex-direction: column; gap: 14px; padding: 20px; background: rgba(255,255,255,0.5); border-radius: 12px; border: 1px solid rgba(0,0,0,0.06); }
.upload-form h4 { margin: 0; font-size: 15px; color: #1e293b; }
.drop-zone { border: 2px dashed rgba(0,0,0,0.12); border-radius: 12px; padding: 32px; text-align: center; cursor: pointer; background: rgba(255,255,255,0.4); }
.drop-zone:hover, .drag-over { border-color: #0d9488; background: rgba(13,148,136,0.05); }
.drop-text p { margin: 8px 0 0; color: #475569; font-size: 14px; }
.hint { color: #94a3b8; font-size: 12px; }
.drop-icon { font-size: 36px; }
.selected-file { display: flex; align-items: center; gap: 10px; font-size: 14px; }
.file-icon { font-size: 26px; }
.file-size { color: #94a3b8; font-size: 12px; }
.chunk-config-section { background: rgba(0,0,0,0.02); border-radius: 10px; padding: 14px; }
.chunk-config-section h5 { margin: 0 0 10px; font-size: 13px; color: #475569; }
.chunk-config-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; }
.form-item { display: flex; flex-direction: column; gap: 4px; }
.form-item label { font-size: 12px; font-weight: 600; color: #64748b; }
.styled-input { padding: 9px 12px; border-radius: 8px; border: 1px solid rgba(0,0,0,0.1); background: rgba(255,255,255,0.6); outline: none; font-size: 13px; }
.upload-btn { padding: 12px 24px; background: #0d9488; color: #fff; border: none; border-radius: 10px; cursor: pointer; font-weight: bold; font-size: 15px; }
.upload-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.mode-tabs { display: flex; gap: 0; border-radius: 10px; overflow: hidden; border: 1px solid rgba(0,0,0,0.1); margin-bottom: 4px; }
.mode-tab { flex: 1; padding: 10px; border: none; background: rgba(0,0,0,0.03); cursor: pointer; font-size: 13px; font-weight: 600; color: #64748b; transition: all 0.2s; }
.mode-tab.active { background: white; color: #0d9488; }
.text-paste-area { width: 100%; padding: 12px; border-radius: 10px; border: 1px solid rgba(0,0,0,0.1); background: rgba(255,255,255,0.6); outline: none; font-size: 13px; font-family: monospace; resize: vertical; box-sizing: border-box; }
.records-grid { display: flex; flex-direction: column; gap: 10px; }
.record-card { display: flex; justify-content: space-between; align-items: center; padding: 14px 16px; background: rgba(255,255,255,0.6); border-radius: 12px; transition: background 0.2s; }
.record-card:hover { background: rgba(255,255,255,0.85); }
.record-card.is-enabled { background: rgba(255,255,255,0.8); }
.card-info { flex: 1; min-width: 0; }
.title-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.title-row h4 { margin: 0; font-size: 14px; color: #1e293b; word-break: break-all; }
.file-type-badge { font-size: 10px; color: #fff; padding: 2px 8px; border-radius: 4px; font-weight: bold; white-space: nowrap; flex-shrink: 0; }
.enabled-tag { font-size: 10px; color: #16a34a; background: rgba(22,163,74,0.1); padding: 1px 8px; border-radius: 20px; font-weight: bold; white-space: nowrap; }
.meta-row { display: flex; flex-wrap: wrap; gap: 5px; align-items: center; }
.badge { font-size: 11px; color: #64748b; background: rgba(0,0,0,0.04); padding: 2px 7px; border-radius: 4px; white-space: nowrap; }
.status-badge { font-size: 11px; padding: 2px 10px; border-radius: 10px; font-weight: bold; white-space: nowrap; }
.card-actions { display: flex; gap: 5px; flex-shrink: 0; margin-left: 8px; }
.embed-btn { padding: 4px 8px; font-size: 11px; background: rgba(0,0,0,0.04); border: 1px solid rgba(0,0,0,0.08); border-radius: 6px; cursor: pointer; white-space: nowrap; font-weight: 600; }
.embed-btn:hover:not(:disabled) { background: rgba(13,148,136,0.08); }
.embed-btn:disabled { opacity: 0.35; cursor: not-allowed; }
.edit-btn, .toggle-btn { padding: 4px 8px; font-size: 12px; background: rgba(0,0,0,0.04); border: 1px solid rgba(0,0,0,0.08); border-radius: 6px; cursor: pointer; color: #475569; white-space: nowrap; }
.edit-btn:hover, .toggle-btn:hover { background: #f1f5f9; }
.delete-btn { padding: 4px 8px; font-size: 12px; background: rgba(220,38,38,0.08); border: 1px solid rgba(220,38,38,0.15); border-radius: 6px; cursor: pointer; color: #dc2626; white-space: nowrap; }
.delete-btn:hover { background: rgba(220,38,38,0.15); }

/* ===== 嵌入模型选择 ===== */
.embed-section { margin-top: 8px; padding: 16px; background: rgba(255,255,255,0.4); border-radius: 12px; border: 1px solid rgba(0,0,0,0.06); }
.embed-section-header { display: flex; align-items: baseline; gap: 10px; margin-bottom: 10px; }
.embed-section-header h4 { margin: 0; font-size: 14px; color: #1e293b; }
.hint-text { font-size: 11px; color: #94a3b8; }
.embed-models-grid { display: flex; flex-wrap: wrap; gap: 8px; }
.embed-model-chip { padding: 8px 14px; border-radius: 10px; border: 1px solid rgba(0,0,0,0.1); background: rgba(255,255,255,0.6); cursor: pointer; display: flex; align-items: center; gap: 8px; transition: all 0.15s; }
.embed-model-chip:hover { background: #fff; border-color: #7c3aed; }
.embed-model-chip.selected { background: rgba(124,58,237,0.1); border-color: #7c3aed; box-shadow: 0 0 0 1px rgba(124,58,237,0.3); }
.chip-name { font-size: 13px; font-weight: 600; color: #1e293b; }
.chip-provider { font-size: 10px; color: #94a3b8; background: rgba(0,0,0,0.04); padding: 2px 6px; border-radius: 4px; }
.chip-warn { font-size: 10px; color: #dc2626; }
.selected-hint { margin-top: 8px; font-size: 12px; color: #7c3aed; font-weight: 600; }

/* ===== 弹窗复用 ===== */
.login-overlay { position: fixed; top: 0; left: 0; width: 100vw; height: 100vh; background: rgba(15,23,42,0.2); display: flex; align-items: center; justify-content: center; z-index: 400; backdrop-filter: blur(8px); }
.login-box { padding: 28px; border-radius: 20px; background: rgba(255,255,255,0.92); border: 1px solid rgba(255,255,255,0.6); box-shadow: 0 20px 50px rgba(0,0,0,0.12); }
.login-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.login-header h3 { margin: 0; font-size: 17px; color: #1e293b; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 380px; }
.close-btn { background: transparent; border: none; font-size: 20px; color: #94a3b8; cursor: pointer; }
.form-body { display: flex; flex-direction: column; gap: 12px; }
.form-body label { font-size: 13px; font-weight: 600; color: #475569; display: block; margin-bottom: 2px; }
.form-body .styled-input { width: 100%; padding: 10px; border-radius: 8px; border: 1px solid rgba(0,0,0,0.12); background: rgba(255,255,255,0.65); outline: none; font-size: 13px; box-sizing: border-box; }
.submit-btn { margin-top: 4px; padding: 12px; background: #1e3a8a; color: white; border: none; border-radius: 10px; cursor: pointer; font-weight: bold; font-size: 15px; }
</style>
