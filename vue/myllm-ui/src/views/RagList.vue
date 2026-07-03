<script setup lang="ts">
import { ref } from 'vue'
import ModelModal from './ModelModal.vue'
import RuleConfig from './RuleConfig.vue'

const emit = defineEmits(['updateColor'])

// 步骤流控制：'list' 列表页 -> 'rule' 规则配置页
const step = ref<'list' | 'rule'>('list')
const showModelModal = ref<boolean>(false)

// 历史记录列表：点击某一项后，大模型对话直接继承此项目的设定
const ragRecords = ref([
  { id: 1, title: '代码写小助手', model: 'Gemini', color: '#0284c7', maxWords: 500, rules: '严格遵循模版' },
  { id: 2, title: '心理医疗小助理', model: 'Deepseek', color: '#ea580c', maxWords: 800, rules: '温柔共情风格' }
])

// 缓存当前正在新建或最后选中的配置项（保持进入后默认为上次未被更新的选项）
const currentConfig = ref({
  title: '',
  model: 'DeepSeek-Chat',
  apiKey: '',
  color: '#1e3a8a',
  maxWords: 300,
  rules: ''
})

const handleNewConfig = () => {
  showModelModal.value = true // 点击新建，拉起模型与 APIKey 弹窗
}

const confirmModel = (modelData: { model: string, apiKey: string }) => {
  currentConfig.value.model = modelData.model
  currentConfig.value.apiKey = modelData.apiKey
  showModelModal.value = false
  step.value = 'rule' // 确认后，自动进入图 3 的规则详细配置页
}

const saveFinalConfig = (finalData: any) => {
  currentConfig.value = { ...currentConfig.value, ...finalData }
  ragRecords.value.unshift({
    id: Date.now(),
    title: currentConfig.value.title || '未命名配置',
    model: currentConfig.value.model,
    color: currentConfig.value.color,
    maxWords: currentConfig.value.maxWords,
    rules: currentConfig.value.rules
  })
  emit('updateColor', currentConfig.value.color) // 让全局字体/图标同步变色
  step.value = 'list' // 返回列表
}

const selectRecord = (record: any) => {
  currentConfig.value = { ...record, apiKey: '********' }
  emit('updateColor', record.color) // 继承历史颜色配置
}
</script>

<template>
  <div class="rag-container-glass">
    <div v-if="step === 'list'" class="list-layout">
      <div class="list-header">
        <button class="new-btn" @click="handleNewConfig">＋ 新建配置</button>
      </div>
      <div class="records-grid">
        <div 
          v-for="item in ragRecords" 
          :key="item.id" 
          class="record-card" 
          :style="{ borderLeft: `6px solid ${item.color}` }"
          @click="selectRecord(item)"
        >
          <div class="card-info">
            <h4 :style="{ color: item.color }">{{ item.title }}</h4>
            <span class="badge">模型：{{ item.model }}</span>
          </div>
          <span class="arrow">➔</span>
        </div>
      </div>
    </div>

    <RuleConfig 
      v-if="step === 'rule'" 
      :initialData="currentConfig"
      @back="step = 'list'"
      @save="saveFinalConfig"
    />

    <ModelModal 
      v-if="showModelModal" 
      :defaultModel="currentConfig.model"
      @close="showModelModal = false"
      @submit="confirmModel"
    />
  </div>
</template>

<style scoped>
.rag-container-glass {
  width: 90%; max-width: 750px; padding: 24px; border-radius: 20px;
  background: rgba(255, 255, 255, 0.45); backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.5); box-shadow: 0 20px 40px rgba(0,0,0,0.02);
}
.list-layout { display: flex; flex-direction: column; gap: 16px; }
.list-header { display: flex; justify-content: flex-start; }
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
.card-info h4 { margin: 0 0 6px 0; font-size: 16px; }
.badge { font-size: 12px; color: #64748b; background: rgba(0,0,0,0.05); padding: 2px 8px; border-radius: 4px; }
.arrow { color: #94a3b8; font-size: 18px; }
</style>