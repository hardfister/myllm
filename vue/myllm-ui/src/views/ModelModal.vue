<script setup lang="ts">
import { ref } from 'vue'

const props = defineProps<{ defaultModel: string }>()
const emit = defineEmits(['close', 'submit'])

// 基础变量
const selectedModel = ref<string>(props.defaultModel)
const apiKey = ref<string>('')

// 控制“覆盖式大选择器弹窗”的显示与隐藏
const showPickerModal = ref<boolean>(false)

// 临时存储在弹窗里自定义输入的模型名
const customModelName = ref<string>('')

// 满足你要求的 10+ 知名模型列表
const famousModels = [
  'DeepSeek-Chat', 'DeepSeek-Coder', 'GPT-4o', 'GPT-4-turbo', 
  'Claude-3.5-Sonnet', 'Claude-3-Opus', 'Gemini-1.5-Pro', 
  'Gemini-1.5-Flash', 'Llama-3-70B', 'Mistral-Large', 'Qwen-2.5-72B'
]

// 打开弹窗选择器
const openModelPicker = () => {
  showPickerModal.value = true
}

// 在弹窗里选中了某一个快捷模型
const selectAndClose = (model: string) => {
  selectedModel.value = model
  customModelName.value = ''
  showPickerModal.value = false // 选中后自动关闭弹窗
}

// 在弹窗里提交自定义输入的模型
const submitCustomModel = () => {
  if (customModelName.value.trim()) {
    selectedModel.value = customModelName.value.trim()
    showPickerModal.value = false // 提交后自动关闭弹窗
  }
}

// 整个表单页面点击“继续”
const handleContinue = () => {
  emit('submit', { model: selectedModel.value, apiKey: apiKey.value })
}
</script>

<template>
  <div class="model-step-page">
    <div class="page-header">
      <button class="back-link" @click="emit('close')">ㄑ 返回</button>
      <h3>🤖 模型鉴权配置</h3>
    </div>
    
    <div class="form-body">
      <div class="form-item">
        <label>选择基座模型：</label>
        <div class="model-trigger-box" @click="openModelPicker">
          <span class="current-model-text">{{ selectedModel || '点击选择大模型...' }}</span>
          <span class="trigger-icon">▼</span>
        </div>
      </div>

      <div class="form-item">
        <label>输入 API Key：</label>
        <input 
          type="password" 
          v-model="apiKey" 
          placeholder="请输入对应的 API 鉴权密钥..." 
          class="styled-input"
        />
      </div>

      <button class="continue-btn" @click="handleContinue">继续进入规则配置 ➔</button>
    </div>
    <Teleport to="body">
    <div v-if="showPickerModal" class="picker-overlay-mask" @click.self="showPickerModal = false">
      <div class="picker-large-box">
        <div class="picker-header">
          <h4>🎯 选择基座大模型 (已列出常用知名模型)</h4>
          <button class="close-picker-btn" @click="showPickerModal = false">✕</button>
        </div>

        <div class="models-grid">
          <button 
            v-for="m in famousModels" 
            :key="m"
            :class="['model-grid-chip', { active: selectedModel === m }]"
            @click="selectAndClose(m)"
          >
            {{ m }}
          </button>
        </div>

        <div class="divider"><span>或</span></div>

        <div class="custom-input-row">
          <input 
            type="text" 
            v-model="customModelName" 
            placeholder="✍️ 在此手动输入其他自定义模型名称..." 
            class="styled-input"
            @keyup.enter="submitCustomModel"
          />
          <button class="chip-confirm-btn" @click="submitCustomModel">确认</button>
        </div>
      </div>
    </div>
    </Teleport>
  </div>
</template>

<style scoped>
/* 主页面基础样式 */
.model-step-page {
  display: flex;
  flex-direction: column;
  width: 100%;
  text-align: left;
}
.page-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 8px;
}
.page-header h3 {
  margin: 0;
  font-size: 18px;
  color: #1e293b;
}
.back-link { background: transparent; border: none; font-size: 14px; color: #475569; cursor: pointer; font-weight: bold; }
.form-body { display: flex; flex-direction: column; gap: 24px; margin-top: 16px; }
.form-item label { display: block; font-size: 14px; font-weight: 600; margin-bottom: 8px; color: #334155; }

/* ✨ 模型触发选择框（做成像输入框但有点击感） */
.model-trigger-box {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  padding: 12px 16px;
  border-radius: 8px;
  border: 1px solid rgba(0, 0, 0, 0.12);
  background: rgba(255, 255, 255, 0.65);
  cursor: pointer;
  box-sizing: border-box;
  transition: all 0.2s ease;
}
.model-trigger-box:hover {
  background: rgba(255, 255, 255, 0.9);
  border-color: #1e3a8a;
}
.current-model-text {
  font-size: 14px;
  font-weight: 600;
  color: #1e3a8a;
}
.trigger-icon {
  font-size: 12px;
  color: #64748b;
}

/* 常规文本输入框 */
.styled-input {
  width: 100%;
  padding: 12px;
  border-radius: 8px;
  border: 1px solid rgba(0, 0, 0, 0.12);
  background: rgba(255, 255, 255, 0.65);
  outline: none;
  box-sizing: border-box;
  font-size: 14px;
}
.continue-btn {
  align-self: flex-end;
  padding: 12px 28px;
  background: #1e3a8a;
  color: white;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-weight: bold;
  box-shadow: 0 4px 12px rgba(30, 58, 138, 0.2);
}

/* ================= 🌟 覆盖式大弹窗样式 ================= */
.picker-overlay-mask {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background: rgba(15, 23, 42, 0.2); /* 更加深邃的磨砂底色 */
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 300;
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
}

/* 尺寸比原来更宽、更大，容纳 10 种以上模型 */
.picker-large-box {
  width: 520px;
  padding: 28px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(255, 255, 255, 0.7);
  box-shadow: 0 30px 60px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.picker-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.picker-header h4 {
  margin: 0;
  font-size: 15px;
  color: #1e293b;
}
.close-picker-btn {
  background: transparent;
  border: none;
  font-size: 18px;
  color: #94a3b8;
  cursor: pointer;
}
.close-picker-btn:hover { color: #475569; }

/* 模型紧凑网格排版 */
.models-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr); /* 两列并排 */
  gap: 10px;
  max-height: 240px;
  overflow-y: auto;
  padding-right: 4px;
}

.model-grid-chip {
  padding: 10px 14px;
  border-radius: 8px;
  border: 1px solid rgba(0, 0, 0, 0.08);
  background: rgba(255, 255, 255, 0.6);
  font-size: 13px;
  font-weight: 500;
  text-align: left;
  cursor: pointer;
  transition: all 0.15s ease;
}
.model-grid-chip:hover {
  background: rgba(255, 255, 255, 1);
  border-color: #1e3a8a;
  transform: translateY(-1px);
}
.model-grid-chip.active {
  background: #1e3a8a;
  color: white;
  border-color: #1e3a8a;
}

.divider {
  display: flex;
  align-items: center;
  text-align: center;
  color: #94a3b8;
  font-size: 12px;
}
.divider::before, .divider::after {
  content: '';
  flex: 1;
  border-bottom: 1px solid rgba(0, 0, 0, 0.08);
}
.divider span { padding: 0 10px; }

.custom-input-row {
  display: flex;
  gap: 10px;
}
.chip-confirm-btn {
  padding: 0 20px;
  background: #334155;
  color: white;
  border: none;
  border-radius: 8px;
  font-weight: bold;
  cursor: pointer;
  white-space: nowrap;
}
</style>