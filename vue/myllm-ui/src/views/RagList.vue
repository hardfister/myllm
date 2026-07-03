<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getRags, createRag, deleteRag } from '../api'
import { saveRags, loadRags } from '../services/localStorage'
import { useAuth } from '../services/auth'
import type { Rag } from '../api'

const { isLoggedIn, isOffline } = useAuth()
const emit = defineEmits(['updateColor'])

const rags = ref<Rag[]>([])
const showUploadForm = ref(false)
const uploading = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)
const selectedFile = ref<File | null>(null)
const description = ref('')
const dragOver = ref(false)

const useServer = () => isLoggedIn.value && !isOffline.value

let localIdCounter = Date.now()

const loadRagsData = async () => {
  if (useServer()) {
    try {
      const res = await getRags()
      rags.value = res.data
    } catch (e) {
      rags.value = loadRags()
    }
  } else {
    rags.value = loadRags()
  }
}

onMounted(loadRagsData)

const persistRags = () => {
  if (!useServer()) saveRags(rags.value)
}

const onFileChange = (e: Event) => {
  const target = e.target as HTMLInputElement
  if (target.files?.[0]) selectedFile.value = target.files[0]
}

const onDragOver = (e: DragEvent) => { e.preventDefault(); dragOver.value = true }
const onDragLeave = () => { dragOver.value = false }
const onDrop = (e: DragEvent) => {
  e.preventDefault(); dragOver.value = false
  if (e.dataTransfer?.files?.[0]) selectedFile.value = e.dataTransfer.files[0]
}

const handleUpload = async () => {
  if (!selectedFile.value) { alert('请先选择文件'); return }
  uploading.value = true
  try {
    if (useServer()) {
      const formData = new FormData()
      formData.append('file', selectedFile.value)
      if (description.value) formData.append('description', description.value)
      await createRag(formData)
      await loadRagsData()
    } else {
      // Offline: create local record with base64
      const reader = new FileReader()
      const fileData = await new Promise<string>((resolve) => {
        reader.onload = () => resolve(reader.result as string)
        reader.readAsDataURL(selectedFile.value!)
      })
      const newRag: Rag = {
        id: ++localIdCounter,
        filename: selectedFile.value!.name,
        fileSize: selectedFile.value!.size,
        fileType: selectedFile.value!.type || 'unknown',
        collectionName: description.value || 'local_collection',
        chunkCount: 0,
        status: 'completed',
        description: description.value || '',
        filePath: fileData
      }
      rags.value.unshift(newRag)
      persistRags()
    }
    selectedFile.value = null
    description.value = ''
    showUploadForm.value = false
  } catch (e) {
    console.error('上传失败:', e)
    alert('上传失败')
  } finally {
    uploading.value = false
  }
}

const handleDelete = async (id: number) => {
  if (!confirm('确认删除？')) return
  try {
    if (useServer()) {
      await deleteRag(id)
      await loadRagsData()
    } else {
      rags.value = rags.value.filter(r => r.id !== id)
      persistRags()
    }
  } catch (e) { console.error('删除失败:', e) }
}

const formatFileSize = (bytes: number) => {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1048576).toFixed(1) + ' MB'
}

const statusLabel = (s: string) => ({ completed: '已完成', processing: '处理中', failed: '失败' }[s] || s)
const statusColor = (s: string) => ({ completed: '#16a34a', processing: '#ea580c', failed: '#dc2626' }[s] || '#94a3b8')

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

      <div v-if="showUploadForm" class="upload-form">
        <div :class="['drop-zone', { 'drag-over': dragOver }]" @dragover="onDragOver" @dragleave="onDragLeave"
          @drop="onDrop" @click="fileInput?.click()">
          <div v-if="!selectedFile" class="drop-text">
            <span class="drop-icon">📁</span>
            <p>拖拽文件到此处，或点击选择文件</p>
            <p class="hint">支持 PDF、DOCX、MD、TXT 等</p>
          </div>
          <div v-else class="selected-file">
            <span class="file-icon">📄</span>
            <span>{{ selectedFile.name }}</span>
            <span class="file-size">{{ formatFileSize(selectedFile.size) }}</span>
          </div>
        </div>
        <input ref="fileInput" type="file" accept=".pdf,.docx,.doc,.md,.txt,.csv,.json" style="display:none"
          @change="onFileChange" />
        <div class="form-row">
          <input type="text" v-model="description" placeholder="文档描述（可选）" class="styled-input" />
          <button class="upload-btn" :disabled="!selectedFile || uploading" @click="handleUpload">
            {{ uploading ? '上传中...' : '确认上传' }}
          </button>
        </div>
      </div>

      <div class="records-grid">
        <div v-for="item in rags" :key="item.id" class="record-card">
          <div class="card-info">
            <div class="title-row">
              <span class="file-type-badge" :style="{ backgroundColor: statusColor(item.status) }">{{ item.fileType || '文件' }}</span>
              <h4>{{ item.filename }}</h4>
            </div>
            <div class="meta-row">
              <span class="badge">集合：{{ item.collectionName }}</span>
              <span class="badge">切片：{{ item.chunkCount }}</span>
              <span class="badge">{{ formatFileSize(item.fileSize) }}</span>
              <span class="status-badge" :style="{ backgroundColor: statusColor(item.status), color: '#fff' }">{{ statusLabel(item.status) }}</span>
            </div>
            <div v-if="item.description" class="desc-text">{{ item.description }}</div>
          </div>
          <div class="card-actions">
            <button class="delete-btn" @click="handleDelete(item.id!)">🗑 删除</button>
          </div>
        </div>
        <div v-if="rags.length === 0 && !showUploadForm" style="text-align:center;color:#94a3b8;padding:40px;">
          暂无知识库文档
        </div>
      </div>
    </div>
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
.upload-form { display: flex; flex-direction: column; gap: 12px; padding: 20px; background: rgba(255,255,255,0.5); border-radius: 12px; border: 1px solid rgba(0,0,0,0.06); }
.drop-zone { border: 2px dashed rgba(0,0,0,0.12); border-radius: 12px; padding: 40px; text-align: center; cursor: pointer; background: rgba(255,255,255,0.4); }
.drop-zone:hover, .drag-over { border-color: #0d9488; background: rgba(13,148,136,0.05); }
.drop-text p { margin: 8px 0 0; color: #475569; font-size: 14px; }
.hint { color: #94a3b8; font-size: 12px; }
.drop-icon { font-size: 40px; }
.selected-file { display: flex; align-items: center; gap: 10px; font-size: 14px; color: #1e293b; }
.file-icon { font-size: 28px; }
.file-size { color: #94a3b8; font-size: 12px; }
.form-row { display: flex; gap: 10px; }
.styled-input { flex: 1; padding: 10px; border-radius: 8px; border: 1px solid rgba(0,0,0,0.1); background: rgba(255,255,255,0.6); outline: none; font-size: 14px; }
.upload-btn { padding: 10px 24px; background: #0d9488; color: #fff; border: none; border-radius: 8px; cursor: pointer; font-weight: bold; white-space: nowrap; }
.upload-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.records-grid { display: flex; flex-direction: column; gap: 12px; margin-top: 10px; }
.record-card { display: flex; justify-content: space-between; align-items: center; padding: 16px 20px; background: rgba(255,255,255,0.6); border-radius: 12px; }
.record-card:hover { background: rgba(255,255,255,0.85); }
.card-info { flex: 1; }
.title-row { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
.title-row h4 { margin: 0; font-size: 15px; color: #1e293b; word-break: break-all; }
.file-type-badge { font-size: 10px; color: #fff; padding: 2px 8px; border-radius: 4px; font-weight: bold; white-space: nowrap; }
.meta-row { display: flex; flex-wrap: wrap; gap: 6px; }
.badge { font-size: 12px; color: #64748b; background: rgba(0,0,0,0.05); padding: 2px 8px; border-radius: 4px; }
.status-badge { font-size: 11px; padding: 2px 10px; border-radius: 10px; font-weight: bold; }
.desc-text { font-size: 12px; color: #94a3b8; margin-top: 4px; }
.card-actions { display: flex; gap: 8px; flex-shrink: 0; }
.delete-btn { padding: 4px 10px; font-size: 12px; background: rgba(220,38,38,0.08); border: 1px solid rgba(220,38,38,0.15); border-radius: 6px; cursor: pointer; color: #dc2626; }
.delete-btn:hover { background: rgba(220,38,38,0.15); }
</style>
