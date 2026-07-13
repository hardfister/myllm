<script setup lang="ts">
/**
 * RagList.vue — 自定义知识库管理
 * ---------------
 * 上传文档 → 自定义切片参数 → 实时提取文本并切片 → 保存到 MySQL
 *
 * 切片方式：
 *   fixed_size — 固定字符数切片（如每 500 字一片，重叠 50 字）
 *   paragraph  — 按段落（空行）切片
 *   sentence   — 按句子（。！？.!?）切片
 *
 * 在线：上传文件 + 切片配置 → POST /api/rags (multipart)
 * 离线：FileReader base64 → localStorage
 */
import { ref, onMounted } from 'vue'
import { getRags, createRag, deleteRag, updateRag, toggleRag } from '../api'
import { saveRags, loadRags } from '../services/localStorage'
import { useAuth } from '../services/auth'
import type { Rag } from '../api'

const { isLoggedIn, isOffline } = useAuth()
const emit = defineEmits(['updateColor'])

// ===== 列表状态 =====
const rags = ref<Rag[]>([])
const showUploadForm = ref(false)
const uploading = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)
const selectedFile = ref<File | null>(null)
const dragOver = ref(false)
const editingRag = ref<Rag | null>(null)   // 正在编辑的文档（查看/修改切片配置）
const showEditModal = ref(false)

// ===== 上传/切片配置 =====
const collectionName = ref('default_collection')
const chunkSize = ref(500)
const chunkOverlap = ref(50)
const chunkMethod = ref<'fixed_size' | 'paragraph' | 'sentence'>('fixed_size')

const useServer = () => isLoggedIn.value && !isOffline.value
let localIdCounter = Date.now()

// ===== 数据加载：localStorage 总是作为底，服务器数据叠加在上 =====
const loadRagsData = async () => {
  // 总是先从 localStorage 加载（保证刷新不丢数据）
  const localData = loadRags()
  if (useServer()) {
    try {
      const res = await getRags()
      // 以服务器数据为准，但同时保留仅存于本地的数据（未同步的）
      const serverIds = new Set(res.data.map((r: Rag) => r.id))
      const localOnly = localData.filter((r: Rag) => r.id != null && !serverIds.has(r.id))
      rags.value = [...res.data, ...localOnly]
    } catch {
      rags.value = localData
    }
  } else {
    rags.value = localData
  }
}
onMounted(loadRagsData)
// 始终存 localStorage 作备份（服务器模式亦然，防止刷新丢数据）
const persistRags = () => { saveRags(rags.value) }

// ===== 文件选择/拖拽 =====
const onFileChange = (e: Event) => {
  const t = e.target as HTMLInputElement
  if (t.files?.[0]) selectedFile.value = t.files[0]
}
const onDragOver = (e: DragEvent) => { e.preventDefault(); dragOver.value = true }
const onDragLeave = () => { dragOver.value = false }
const onDrop = (e: DragEvent) => {
  e.preventDefault(); dragOver.value = false
  if (e.dataTransfer?.files?.[0]) selectedFile.value = e.dataTransfer.files[0]
}

// ===== 文件上传（在线优先，失败自动降级到本地） =====
const handleUpload = async () => {
  if (!selectedFile.value) { alert('请先选择文件'); return }
  uploading.value = true
  try {
    let success = false
    if (useServer()) {
      try {
        const fd = new FormData()
        fd.append('file', selectedFile.value)
        fd.append('collectionName', collectionName.value)
        fd.append('chunkSize', String(chunkSize.value))
        fd.append('chunkOverlap', String(chunkOverlap.value))
        fd.append('chunkMethod', chunkMethod.value)
        await createRag(fd)
        await loadRagsData()
        saveRags(rags.value)   // 备份到 localStorage 防刷新丢失
        success = true
      } catch (e) {
        console.warn('服务器上传失败，降级到本地存储:', e)
        alert('⚠️ 上传到服务器失败（可能是 Ollama/Chroma 未启动）\n文件已保存到浏览器本地存储，联网后会自动同步。')
      }
    }
    if (!success) {
      // 离线/降级模式：FileReader → base64 存 localStorage
      const reader = new FileReader()
      const fileData = await new Promise<string>(r => {
        reader.onload = () => r(reader.result as string)
        reader.readAsDataURL(selectedFile.value!)
      })
      rags.value.unshift({
        id: ++localIdCounter, filename: selectedFile.value!.name,
        fileSize: selectedFile.value!.size, fileType: selectedFile.value!.type || 'unknown',
        collectionName: collectionName.value, chunkCount: 0,
        chunkSize: chunkSize.value, chunkOverlap: chunkOverlap.value,
        chunkMethod: chunkMethod.value,
        status: 'completed', description: '', filePath: fileData, isEnabled: 0
      })
      persistRags()
    }
    selectedFile.value = null
    showUploadForm.value = false
    // 重置切片配置
    collectionName.value = 'default_collection'
    chunkSize.value = 500; chunkOverlap.value = 50; chunkMethod.value = 'fixed_size'
  } catch (e) { console.error('上传失败:', e); alert('上传失败') }
  finally { uploading.value = false }
}

// ===== 打开编辑弹窗（查看切片配置 / 重新切片） =====
const openEditModal = (item: Rag, event: Event) => {
  event.stopPropagation()
  editingRag.value = { ...item }
  showEditModal.value = true
}

// ===== 保存编辑（更新切片参数 → 触发重新切片） =====
const saveEdit = async () => {
  if (!editingRag.value?.id) return
  try {
    if (useServer()) {
      await updateRag(editingRag.value.id, {
        collectionName: editingRag.value.collectionName,
        description: editingRag.value.description,
        chunkSize: editingRag.value.chunkSize,
        chunkOverlap: editingRag.value.chunkOverlap,
        chunkMethod: editingRag.value.chunkMethod
      })
      await loadRagsData()
      saveRags(rags.value)
    } else {
      const idx = rags.value.findIndex(r => r.id === editingRag.value!.id)
      if (idx !== -1) rags.value[idx] = { ...editingRag.value! }
      persistRags()
    }
    showEditModal.value = false
  } catch (e) { console.error('保存失败:', e); alert('保存失败') }
}

// ===== 启用/禁用 =====
const handleToggle = async (record: Rag, event: Event) => {
  event.stopPropagation()
  if (record.id == null) return
  if (useServer()) {
    try { await toggleRag(record.id); await loadRagsData(); saveRags(rags.value) }
    catch (e) { console.error('切换失败:', e) }
  } else {
    const t = rags.value.find(r => r.id === record.id)
    if (t) { t.isEnabled = t.isEnabled === 1 ? 0 : 1; persistRags() }
  }
}

// ===== 删除 =====
const handleDelete = async (id: number, event: Event) => {
  event.stopPropagation()
  if (!confirm('确认删除此文档？将同时删除磁盘文件和全部切片数据。')) return
  try {
    if (useServer()) { await deleteRag(id); await loadRagsData(); saveRags(rags.value) }
    else { rags.value = rags.value.filter(r => r.id !== id); persistRags() }
  } catch (e) { console.error('删除失败:', e) }
}

// ===== 工具 =====
const formatFileSize = (b: number) => b < 1024 ? b + ' B' : b < 1048576 ? (b / 1024).toFixed(1) + ' KB' : (b / 1048576).toFixed(1) + ' MB'
const statusLabel = (s: string): string => ({ completed: '已完成', processing: '处理中', failed: '失败' } as Record<string, string>)[s] || s
const statusColor = (s: string): string => ({ completed: '#16a34a', processing: '#ea580c', failed: '#dc2626' } as Record<string, string>)[s] || '#94a3b8'
const chunkLabel = (m?: string) => ({ fixed_size: '固定大小', paragraph: '段落', sentence: '句子' } as Record<string, string>)[m || 'fixed_size'] || m || '固定大小'

emit('updateColor', '#0d9488')
</script>

<template>
  <div class="rag-container-glass">
    <div class="list-layout">
      <div class="list-header">
        <span v-if="!useServer()" class="mode-badge">📱 本地模式</span>
        <button class="new-btn" @click="showUploadForm = !showUploadForm">
          {{ showUploadForm ? '取消上传' : '＋ 新建知识库' }}
        </button>
      </div>

      <!-- ===== 上传表单（含切片配置） ===== -->
      <div v-if="showUploadForm" class="upload-form">
        <h4>📄 上传文档</h4>

        <!-- 拖拽区 -->
        <div :class="['drop-zone', { 'drag-over': dragOver }]" @dragover="onDragOver" @dragleave="onDragLeave"
          @drop="onDrop" @click="fileInput?.click()">
          <div v-if="!selectedFile" class="drop-text">
            <span class="drop-icon">📁</span><p>拖拽文件到此处，或点击选择</p>
            <p class="hint">支持 TXT / MD / CSV / JSON（UTF-8）</p>
          </div>
          <div v-else class="selected-file">
            <span class="file-icon">📄</span><span>{{ selectedFile.name }}</span>
            <span class="file-size">{{ formatFileSize(selectedFile.size) }}</span>
          </div>
        </div>
        <input ref="fileInput" type="file" accept=".txt,.md,.csv,.json,.pdf,.docx,.html" style="display:none" @change="onFileChange" />

        <!-- 切片配置区 -->
        <div class="chunk-config-section">
          <h5>🔪 切片配置</h5>
          <div class="chunk-config-grid">
            <!-- 集合名称 -->
            <div class="form-item">
              <label>向量集合名称：</label>
              <input type="text" v-model="collectionName" placeholder="如 legal_knowledge" class="styled-input" />
            </div>
            <!-- 切片方式 -->
            <div class="form-item">
              <label>切片方式：</label>
              <select v-model="chunkMethod" class="styled-input">
                <option value="fixed_size">固定大小切片</option>
                <option value="paragraph">按段落切片</option>
                <option value="sentence">按句子切片</option>
              </select>
            </div>
            <!-- 切片大小 -->
            <div class="form-item" v-if="chunkMethod !== 'paragraph'">
              <label>切片大小（字符数）：</label>
              <input type="number" v-model="chunkSize" class="styled-input" min="100" max="10000" step="100" />
            </div>
            <!-- 重叠大小 -->
            <div class="form-item" v-if="chunkMethod !== 'paragraph'">
              <label>切片重叠（字符数）：</label>
              <input type="number" v-model="chunkOverlap" class="styled-input" min="0" max="2000" step="50" />
            </div>
          </div>
        </div>

        <button class="upload-btn" :disabled="!selectedFile || uploading" @click="handleUpload">
          {{ uploading ? '上传并切片中...' : '确认上传并切片' }}
        </button>
      </div>

      <!-- ===== 文档列表 ===== -->
      <div class="records-grid">
        <div v-for="item in rags" :key="item.id" class="record-card"
          :class="{ 'is-enabled': item.isEnabled === 1 }"
          :style="{ borderLeft: `6px solid ${item.isEnabled === 1 ? '#0d9488' : '#cbd5e1'}` }">
          <div class="card-info">
            <div class="title-row">
              <span class="file-type-badge" :style="{ backgroundColor: statusColor(item.status) }">{{ item.fileType || '文件' }}</span>
              <h4>{{ item.filename }}</h4>
              <span v-if="item.isEnabled === 1" class="enabled-tag">已启用</span>
            </div>
            <div class="meta-row">
              <span class="badge">📦 {{ item.collectionName }}</span>
              <span class="badge">✂️ {{ chunkLabel(item.chunkMethod) }} · {{ item.chunkSize || 500 }}字/片</span>
              <span class="badge">📊 {{ item.chunkCount }} 片</span>
              <span class="badge">{{ formatFileSize(item.fileSize) }}</span>
              <span class="status-badge" :style="{ backgroundColor: statusColor(item.status), color: '#fff' }">{{ statusLabel(item.status) }}</span>
            </div>
          </div>
          <div class="card-actions">
            <button class="toggle-btn" @click="handleToggle(item, $event)"
              :style="{ color: item.isEnabled === 1 ? '#16a34a' : '#94a3b8' }">
              {{ item.isEnabled === 1 ? '☑ 启用' : '☐ 未启用' }}
            </button>
            <button class="edit-btn" @click="openEditModal(item, $event)">✏️ 查看</button>
            <button class="delete-btn" @click="handleDelete(item.id!, $event)">🗑 删除</button>
          </div>
        </div>
        <div v-if="rags.length === 0 && !showUploadForm" style="text-align:center;color:#94a3b8;padding:40px;">
          暂无知识库文档，点击"新建知识库"上传
        </div>
      </div>
    </div>

    <!-- ===== 编辑弹窗（查看/修改切片配置） ===== -->
    <Teleport to="body">
      <div v-if="showEditModal" class="login-overlay" @click.self="showEditModal = false">
        <div class="login-box" style="width:480px;max-height:80vh;overflow-y:auto;">
          <div class="login-header"><h3>📄 {{ editingRag?.filename }}</h3><button class="close-btn" @click="showEditModal = false">✕</button></div>
          <div class="form-body">
            <div class="form-item"><label>集合名称</label>
              <input type="text" v-model="editingRag!.collectionName" class="styled-input" /></div>
            <div class="form-item"><label>描述</label>
              <input type="text" v-model="editingRag!.description" placeholder="文档用途说明" class="styled-input" /></div>
            <div class="form-item"><label>切片方式</label>
              <select v-model="editingRag!.chunkMethod" class="styled-input">
                <option value="fixed_size">固定大小切片</option>
                <option value="paragraph">按段落切片</option>
                <option value="sentence">按句子切片</option>
              </select></div>
            <div class="form-item" v-if="editingRag?.chunkMethod !== 'paragraph'"><label>切片大小（字符数）</label>
              <input type="number" v-model="editingRag!.chunkSize" class="styled-input" min="100" max="10000" step="100" /></div>
            <div class="form-item" v-if="editingRag?.chunkMethod !== 'paragraph'"><label>切片重叠（字符数）</label>
              <input type="number" v-model="editingRag!.chunkOverlap" class="styled-input" min="0" max="2000" step="50" /></div>
            <div class="form-item">
              <label>切片数：<strong>{{ editingRag?.chunkCount || 0 }}</strong> 片</label>
              <label>文件大小：<strong>{{ formatFileSize(editingRag?.fileSize || 0) }}</strong></label>
              <label>状态：<strong :style="{ color: statusColor(editingRag?.status || '') }">{{ statusLabel(editingRag?.status || '') }}</strong></label>
            </div>
            <p style="font-size:11px;color:#94a3b8;">修改切片参数后保存将触发重新切片</p>
            <button class="submit-btn" @click="saveEdit">保存</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.rag-container-glass { width: 90%; max-width: 780px; padding: 24px; border-radius: 20px; background: rgba(255,255,255,0.45); backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px); border: 1px solid rgba(255,255,255,0.5); box-shadow: 0 20px 40px rgba(0,0,0,0.02); max-height: 80vh; overflow-y: auto; }
.list-layout { display: flex; flex-direction: column; gap: 16px; }
.list-header { display: flex; justify-content: space-between; align-items: center; }
.mode-badge { font-size: 11px; color: #ea580c; background: rgba(234,88,12,0.1); padding: 4px 10px; border-radius: 10px; font-weight: bold; }
.new-btn { padding: 10px 20px; font-weight: bold; background: rgba(255,255,255,0.7); border: 1px solid rgba(0,0,0,0.1); border-radius: 10px; cursor: pointer; }
.new-btn:hover { background: #fff; transform: translateY(-1px); }

/* ===== 上传表单 ===== */
.upload-form {
  display: flex; flex-direction: column; gap: 14px;
  padding: 20px; background: rgba(255,255,255,0.5); border-radius: 12px; border: 1px solid rgba(0,0,0,0.06);
}
.upload-form h4 { margin: 0; font-size: 15px; color: #1e293b; }
.drop-zone { border: 2px dashed rgba(0,0,0,0.12); border-radius: 12px; padding: 32px; text-align: center; cursor: pointer; background: rgba(255,255,255,0.4); transition: all 0.2s; }
.drop-zone:hover, .drag-over { border-color: #0d9488; background: rgba(13,148,136,0.05); }
.drop-text p { margin: 8px 0 0; color: #475569; font-size: 14px; }
.hint { color: #94a3b8; font-size: 12px; }
.drop-icon { font-size: 36px; }
.selected-file { display: flex; align-items: center; gap: 10px; font-size: 14px; }
.file-icon { font-size: 26px; }
.file-size { color: #94a3b8; font-size: 12px; }

/* ===== 切片配置 ===== */
.chunk-config-section { background: rgba(0,0,0,0.02); border-radius: 10px; padding: 14px; }
.chunk-config-section h5 { margin: 0 0 10px; font-size: 13px; color: #475569; }
.chunk-config-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; }
.form-item { display: flex; flex-direction: column; gap: 4px; }
.form-item label { font-size: 12px; font-weight: 600; color: #64748b; }
.styled-input { padding: 9px 12px; border-radius: 8px; border: 1px solid rgba(0,0,0,0.1); background: rgba(255,255,255,0.6); outline: none; font-size: 13px; }
select.styled-input { cursor: pointer; }
.upload-btn {
  padding: 12px 24px; background: #0d9488; color: #fff;
  border: none; border-radius: 10px; cursor: pointer; font-weight: bold; font-size: 15px;
}
.upload-btn:disabled { opacity: 0.5; cursor: not-allowed; }

/* ===== 卡片列表 ===== */
.records-grid { display: flex; flex-direction: column; gap: 12px; margin-top: 10px; }
.record-card { display: flex; justify-content: space-between; align-items: center; padding: 14px 18px; background: rgba(255,255,255,0.6); border-radius: 12px; transition: background 0.2s; }
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
.card-actions { display: flex; gap: 6px; flex-shrink: 0; margin-left: 10px; }
.toggle-btn, .edit-btn { padding: 4px 8px; font-size: 11px; background: rgba(0,0,0,0.04); border: 1px solid rgba(0,0,0,0.08); border-radius: 6px; cursor: pointer; white-space: nowrap; color: #475569; }
.toggle-btn:hover, .edit-btn:hover { background: #f1f5f9; }
.delete-btn { padding: 4px 8px; font-size: 11px; background: rgba(220,38,38,0.08); border: 1px solid rgba(220,38,38,0.15); border-radius: 6px; cursor: pointer; color: #dc2626; white-space: nowrap; }
.delete-btn:hover { background: rgba(220,38,38,0.15); }

/* ===== 弹窗复用 ===== */
.login-overlay { position: fixed; top: 0; left: 0; width: 100vw; height: 100vh; background: rgba(15,23,42,0.2); display: flex; align-items: center; justify-content: center; z-index: 400; backdrop-filter: blur(8px); -webkit-backdrop-filter: blur(8px); }
.login-box { padding: 28px; border-radius: 20px; background: rgba(255,255,255,0.92); border: 1px solid rgba(255,255,255,0.6); box-shadow: 0 20px 50px rgba(0,0,0,0.12); }
.login-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.login-header h3 { margin: 0; font-size: 17px; color: #1e293b; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 380px; }
.close-btn { background: transparent; border: none; font-size: 20px; color: #94a3b8; cursor: pointer; }
.form-body { display: flex; flex-direction: column; gap: 12px; }
.form-body .form-item label { font-size: 13px; font-weight: 600; color: #475569; display: block; margin-bottom: 2px; }
.form-body .styled-input { width: 100%; padding: 10px; border-radius: 8px; border: 1px solid rgba(0,0,0,0.12); background: rgba(255,255,255,0.65); outline: none; font-size: 13px; box-sizing: border-box; }
.submit-btn { margin-top: 4px; padding: 12px; background: #1e3a8a; color: white; border: none; border-radius: 10px; cursor: pointer; font-weight: bold; font-size: 15px; }
</style>
