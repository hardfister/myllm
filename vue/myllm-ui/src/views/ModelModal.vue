<script setup lang="ts">
import { ref, watch } from 'vue'

const props = defineProps<{ defaultModel: string; defaultProvider?: string; defaultBaseUrl?: string; defaultApiKey?: string }>()
const emit = defineEmits(['close', 'submit'])

const selectedModel = ref(props.defaultModel)
const apiKey = ref(props.defaultApiKey || '')
const provider = ref(props.defaultProvider || 'DeepSeek')
const baseUrl = ref(props.defaultBaseUrl || 'https://api.deepseek.com/v1')

const showPickerModal = ref(false)
const customModelName = ref('')

// 提供商 → 默认 Base URL 映射
const providers: { name: string; url: string }[] = [
  { name: 'DeepSeek', url: 'https://api.deepseek.com/v1' },
  { name: 'OpenAI', url: 'https://api.openai.com/v1' },
  { name: 'Anthropic (Claude)', url: 'https://api.anthropic.com/v1' },
  { name: 'Google (Gemini)', url: 'https://generativelanguage.googleapis.com/v1beta/openai' },
  { name: 'Qwen (通义千问)', url: 'https://dashscope.aliyuncs.com/compatible-mode/v1' },
  { name: 'GLM (智谱)', url: 'https://open.bigmodel.cn/api/paas/v4' },
  { name: 'Moonshot (月之暗面)', url: 'https://api.moonshot.cn/v1' },
  { name: 'Ollama', url: 'http://localhost:11434/v1' },
  { name: 'Custom', url: '' }
]

// 切换提供商时自动填充默认 Base URL
watch(provider, (name) => {
  const p = providers.find(p => p.name === name)
  if (p) baseUrl.value = p.url || ''
})

const famousModels = [
  'deepseek-v4-flash', 'deepseek-v4-pro[1m]', 'GPT-5.1', 'GPT-5.2',
  'Claude-4.6-Sonnet', 'Claude-4.7-Opus', 'Claude-4.8-Opus', 'Gemini-3.5-flash','text-embedding-v1','text-embedding-v3',
  'Gemini-3.1-pro', 'Llama-3-70B', 'Mistral-Large', 'Qwen-3.5-72B','qwen3-vl-embedding','multimodal-embedding-v1'
]

const openModelPicker = () => { showPickerModal.value = true }
const selectAndClose = (model: string) => { selectedModel.value = model; customModelName.value = ''; showPickerModal.value = false }
const submitCustomModel = () => { if (customModelName.value.trim()) { selectedModel.value = customModelName.value.trim(); showPickerModal.value = false } }

const handleContinue = () => {
  emit('submit', { model: selectedModel.value, apiKey: apiKey.value, provider: provider.value, baseUrl: baseUrl.value })
}
</script>

<template>
  <Teleport to="body">
    <div class="model-step-overlay" @click.self="emit('close')">
      <div class="model-step-page">
        <div class="page-header"><button class="back-link" @click="emit('close')">ㄑ 返回</button><h3>模型配置</h3></div>
        <div class="form-body">
          <div class="form-item">
            <label>选择基座模型：</label>
            <div class="model-trigger-box" @click="openModelPicker">
              <span class="current-model-text">{{ selectedModel || '点击选择大模型...' }}</span><span class="trigger-icon">▼</span>
            </div>
          </div>
          <div class="form-item">
            <label>模型提供商：</label>
            <select v-model="provider" class="styled-input">
              <option v-for="p in providers" :key="p.name" :value="p.name">{{ p.name }}</option>
            </select>
          </div>
          <div class="form-item">
            <label>API Base URL：</label>
            <input type="text" v-model="baseUrl" :placeholder="provider + ' 默认地址'" class="styled-input" />
          </div>
          <div class="form-item">
            <label>输入 API Key：</label>
            <input type="password" v-model="apiKey" placeholder="请输入对应的 API 鉴权密钥..." class="styled-input" />
          </div>
          <button class="continue-btn" @click="handleContinue">继续进入规则配置 ➔</button>
        </div>
      </div>
    </div>

    <div v-if="showPickerModal" class="picker-overlay-mask" @click.self="showPickerModal = false">
      <div class="picker-large-box">
        <div class="picker-header"><h4>🎯 选择基座大模型</h4><button class="close-picker-btn" @click="showPickerModal = false">✕</button></div>
        <div class="models-grid">
          <button v-for="m in famousModels" :key="m" :class="['model-grid-chip', { active: selectedModel === m }]" @click="selectAndClose(m)">{{ m }}</button>
        </div>
        <div class="divider"><span>或</span></div>
        <div class="custom-input-row">
          <input type="text" v-model="customModelName" placeholder="✍️ 在此手动输入其他自定义模型名称..." class="styled-input" @keyup.enter="submitCustomModel" />
          <button class="chip-confirm-btn" @click="submitCustomModel">确认</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.model-step-overlay { position: fixed; top: 0; left: 0; width: 100vw; height: 100vh; background: rgba(15,23,42,0.15); display: flex; align-items: center; justify-content: center; z-index: 200; backdrop-filter: blur(6px); }
.model-step-page { width: 480px; padding: 28px; border-radius: 20px; background: rgba(255,255,255,0.92); border: 1px solid rgba(255,255,255,0.6); box-shadow: 0 20px 50px rgba(0,0,0,0.12); display: flex; flex-direction: column; text-align: left; }
.page-header { display: flex; align-items: center; gap: 16px; margin-bottom: 8px; }
.page-header h3 { margin: 0; font-size: 18px; color: #1e293b; }
.back-link { background: transparent; border: none; font-size: 14px; color: #475569; cursor: pointer; font-weight: bold; }
.form-body { display: flex; flex-direction: column; gap: 20px; margin-top: 12px; }
.form-item label { display: block; font-size: 14px; font-weight: 600; margin-bottom: 6px; color: #334155; }
.model-trigger-box { display: flex; justify-content: space-between; align-items: center; width: 100%; padding: 12px 16px; border-radius: 8px; border: 1px solid rgba(0,0,0,0.12); background: rgba(255,255,255,0.65); cursor: pointer; box-sizing: border-box; transition: all 0.2s; }
.model-trigger-box:hover { background: rgba(255,255,255,0.9); border-color: #1e3a8a; }
.current-model-text { font-size: 14px; font-weight: 600; color: #1e3a8a; }
.trigger-icon { font-size: 12px; color: #64748b; }
.styled-input { width: 100%; padding: 12px; border-radius: 8px; border: 1px solid rgba(0,0,0,0.12); background: rgba(255,255,255,0.65); outline: none; box-sizing: border-box; font-size: 14px; }
select.styled-input { cursor: pointer; }
.continue-btn { align-self: flex-end; padding: 12px 28px; background: #1e3a8a; color: white; border: none; border-radius: 8px; cursor: pointer; font-weight: bold; box-shadow: 0 4px 12px rgba(30,58,138,0.2); }
.picker-overlay-mask { position: fixed; top: 0; left: 0; width: 100vw; height: 100vh; background: rgba(15,23,42,0.2); display: flex; align-items: center; justify-content: center; z-index: 300; backdrop-filter: blur(10px); }
.picker-large-box { width: 520px; padding: 28px; border-radius: 20px; background: rgba(255,255,255,0.9); border: 1px solid rgba(255,255,255,0.7); box-shadow: 0 30px 60px rgba(0,0,0,0.15); display: flex; flex-direction: column; gap: 20px; }
.picker-header { display: flex; justify-content: space-between; align-items: center; }
.picker-header h4 { margin: 0; font-size: 15px; color: #1e293b; }
.close-picker-btn { background: transparent; border: none; font-size: 18px; color: #94a3b8; cursor: pointer; }
.models-grid { display: grid; grid-template-columns: repeat(2,1fr); gap: 10px; max-height: 240px; overflow-y: auto; padding-right: 4px; }
.model-grid-chip { padding: 10px 14px; border-radius: 8px; border: 1px solid rgba(0,0,0,0.08); background: rgba(255,255,255,0.6); font-size: 13px; font-weight: 500; text-align: left; cursor: pointer; transition: all 0.15s; }
.model-grid-chip:hover { background: rgba(255,255,255,1); border-color: #1e3a8a; }
.model-grid-chip.active { background: #1e3a8a; color: white; border-color: #1e3a8a; }
.divider { display: flex; align-items: center; color: #94a3b8; font-size: 12px; }
.divider::before, .divider::after { content: ''; flex: 1; border-bottom: 1px solid rgba(0,0,0,0.08); }
.divider span { padding: 0 10px; }
.custom-input-row { display: flex; gap: 10px; }
.chip-confirm-btn { padding: 0 20px; background: #334155; color: white; border: none; border-radius: 8px; font-weight: bold; cursor: pointer; white-space: nowrap; }
</style>
