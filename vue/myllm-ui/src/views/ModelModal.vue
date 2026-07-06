<script setup lang="ts">
/**
 * ModelModal.vue — 模型选择弹窗（ModelList 三步流程的第一步）
 * ---------------
 * 功能：
 *   1. 11 个知名模型缩略图网格，点击秒选
 *   2. 自定义模型名称输入（Or 分隔线下方）
 *   3. 提供商下拉选择（DeepSeek / OpenAI / Anthropic / Ollama / Custom）
 *   4. API Base URL 输入（如 https://api.deepseek.com）
 *   5. API Key 密码输入框
 *
 * Props：
 *   defaultModel    — 当前模型名（编辑时回填）
 *   defaultProvider — 当前提供商
 *   defaultBaseUrl  — 当前 Base URL
 *
 * Emits：
 *   close  — 关闭弹窗
 *   submit — 用户点击"继续" → { model, apiKey, provider, baseUrl }
 */
import { ref } from 'vue'

const props = defineProps<{ defaultModel: string; defaultProvider?: string; defaultBaseUrl?: string }>()
const emit = defineEmits(['close', 'submit'])

// 当前选中的模型信息
const selectedModel = ref<string>(props.defaultModel)
const apiKey = ref<string>('')
const provider = ref<string>(props.defaultProvider || 'DeepSeek')
const baseUrl = ref<string>(props.defaultBaseUrl || 'https://api.deepseek.com')

// 大选择器弹窗状态
const showPickerModal = ref<boolean>(false)
const customModelName = ref<string>('')

// 提供商列表
const providers = ['DeepSeek', 'OpenAI', 'Anthropic', 'Ollama', 'Custom']

// 知名模型列表
const famousModels = [
  'deepseek-v4-flash', 'deepseek-v4-pro[1m]', 'GPT-5.1', 'GPT-5.2',
  'Claude-4.6-Sonnet', 'Claude-4.7-Opus', 'Claude-4.8-Opus', 'Gemini-3.5-flash',
  'Gemini-3.1-pro', 'Llama-3-70B', 'Mistral-Large', 'Qwen-3.5-72B'
]

const openModelPicker = () => { showPickerModal.value = true }

/** 从快捷网格选中模型 → 自动关闭选择器 */
const selectAndClose = (model: string) => {
  selectedModel.value = model
  customModelName.value = ''
  showPickerModal.value = false
}

/** 提交自定义输入的模型名 */
const submitCustomModel = () => {
  if (customModelName.value.trim()) {
    selectedModel.value = customModelName.value.trim()
    showPickerModal.value = false
  }
}

/** 确认 → 进入 RuleConfig 步骤 */
const handleContinue = () => {
  emit('submit', {
    model: selectedModel.value,
    apiKey: apiKey.value,
    provider: provider.value,
    baseUrl: baseUrl.value
  })
}
</script>

<template>
  <Teleport to="body">
    <!-- 半透明遮罩 + 表单 -->
    <div class="model-step-overlay" @click.self="emit('close')">
      <div class="model-step-page">
        <div class="page-header">
          <button class="back-link" @click="emit('close')">ㄑ 返回</button>
          <h3>模型配置</h3>
        </div>

        <div class="form-body">
          <!-- 1. 基座模型选择入口（点击打开大选择器） -->
          <div class="form-item">
            <label>选择基座模型：</label>
            <div class="model-trigger-box" @click="openModelPicker">
              <span class="current-model-text">{{ selectedModel || '点击选择大模型...' }}</span>
              <span class="trigger-icon">▼</span>
            </div>
          </div>

          <!-- 2. 提供商下拉 -->
          <div class="form-item">
            <label>模型提供商：</label>
            <select v-model="provider" class="styled-input">
              <option v-for="p in providers" :key="p" :value="p">{{ p }}</option>
            </select>
          </div>

          <!-- 3. API Base URL -->
          <div class="form-item">
            <label>API Base URL：</label>
            <input type="text" v-model="baseUrl" placeholder="如 https://api.deepseek.com" class="styled-input" />
          </div>

          <!-- 4. API Key -->
          <div class="form-item">
            <label>输入 API Key：</label>
            <input type="password" v-model="apiKey" placeholder="请输入对应的 API 鉴权密钥..." class="styled-input" />
          </div>

          <button class="continue-btn" @click="handleContinue">继续进入规则配置 ➔</button>
        </div>
      </div>
    </div>

    <!-- ===== 大选择器：知名模型快捷网格 ===== -->
    <div v-if="showPickerModal" class="picker-overlay-mask" @click.self="showPickerModal = false">
      <div class="picker-large-box">
        <div class="picker-header">
          <h4>🎯 选择基座大模型 (已列出常用知名模型)</h4>
          <button class="close-picker-btn" @click="showPickerModal = false">✕</button>
        </div>

        <!-- 模型网格：2 列，可滚动 -->
        <div class="models-grid">
          <button
            v-for="m in famousModels" :key="m"
            :class="['model-grid-chip', { active: selectedModel === m }]"
            @click="selectAndClose(m)"
          >{{ m }}</button>
        </div>

        <div class="divider"><span>或</span></div>

        <!-- 自定义模型输入 -->
        <div class="custom-input-row">
          <input type="text" v-model="customModelName" placeholder="✍️ 在此手动输入其他自定义模型名称..."
            class="styled-input" @keyup.enter="submitCustomModel" />
          <button class="chip-confirm-btn" @click="submitCustomModel">确认</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.model-step-overlay {
  position: fixed; top: 0; left: 0; width: 100vw; height: 100vh;
  background: rgba(15, 23, 42, 0.15); display: flex;
  align-items: center; justify-content: center; z-index: 200;
  backdrop-filter: blur(6px); -webkit-backdrop-filter: blur(6px);
}
.model-step-page {
  width: 480px; padding: 28px; border-radius: 20px;
  background: rgba(255, 255, 255, 0.92); border: 1px solid rgba(255, 255, 255, 0.6);
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.12);
  display: flex; flex-direction: column; text-align: left;
}
.page-header { display: flex; align-items: center; gap: 16px; margin-bottom: 8px; }
.page-header h3 { margin: 0; font-size: 18px; color: #1e293b; }
.back-link { background: transparent; border: none; font-size: 14px; color: #475569; cursor: pointer; font-weight: bold; }
.form-body { display: flex; flex-direction: column; gap: 20px; margin-top: 12px; }
.form-item label { display: block; font-size: 14px; font-weight: 600; margin-bottom: 6px; color: #334155; }

.model-trigger-box {
  display: flex; justify-content: space-between; align-items: center;
  width: 100%; padding: 12px 16px; border-radius: 8px;
  border: 1px solid rgba(0, 0, 0, 0.12); background: rgba(255, 255, 255, 0.65);
  cursor: pointer; box-sizing: border-box; transition: all 0.2s ease;
}
.model-trigger-box:hover { background: rgba(255, 255, 255, 0.9); border-color: #1e3a8a; }
.current-model-text { font-size: 14px; font-weight: 600; color: #1e3a8a; }
.trigger-icon { font-size: 12px; color: #64748b; }

.styled-input {
  width: 100%; padding: 12px; border-radius: 8px;
  border: 1px solid rgba(0, 0, 0, 0.12); background: rgba(255, 255, 255, 0.65);
  outline: none; box-sizing: border-box; font-size: 14px;
}
select.styled-input { cursor: pointer; }
.continue-btn {
  align-self: flex-end; padding: 12px 28px; background: #1e3a8a; color: white;
  border: none; border-radius: 8px; cursor: pointer; font-weight: bold;
  box-shadow: 0 4px 12px rgba(30, 58, 138, 0.2);
}

/* ===== 大选择器弹窗 ===== */
.picker-overlay-mask {
  position: fixed; top: 0; left: 0; width: 100vw; height: 100vh;
  background: rgba(15, 23, 42, 0.2); display: flex; align-items: center;
  justify-content: center; z-index: 300;
  backdrop-filter: blur(10px); -webkit-backdrop-filter: blur(10px);
}
.picker-large-box {
  width: 520px; padding: 28px; border-radius: 20px;
  background: rgba(255, 255, 255, 0.9); border: 1px solid rgba(255, 255, 255, 0.7);
  box-shadow: 0 30px 60px rgba(0, 0, 0, 0.15);
  display: flex; flex-direction: column; gap: 20px;
}
.models-grid {
  display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px;
  max-height: 240px; overflow-y: auto; padding-right: 4px;
}
.model-grid-chip {
  padding: 10px 14px; border-radius: 8px; border: 1px solid rgba(0, 0, 0, 0.08);
  background: rgba(255, 255, 255, 0.6); font-size: 13px; font-weight: 500;
  text-align: left; cursor: pointer; transition: all 0.15s ease;
}
.model-grid-chip:hover { background: rgba(255, 255, 255, 1); border-color: #1e3a8a; }
.model-grid-chip.active { background: #1e3a8a; color: white; border-color: #1e3a8a; }
.divider { display: flex; align-items: center; color: #94a3b8; font-size: 12px; }
.divider::before, .divider::after { content: ''; flex: 1; border-bottom: 1px solid rgba(0, 0, 0, 0.08); }
.divider span { padding: 0 10px; }
.custom-input-row { display: flex; gap: 10px; }
.chip-confirm-btn {
  padding: 0 20px; background: #334155; color: white; border: none;
  border-radius: 8px; font-weight: bold; cursor: pointer; white-space: nowrap;
}
</style>
