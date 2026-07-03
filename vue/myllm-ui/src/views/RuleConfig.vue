<script setup lang="ts">
import { ref } from 'vue'
import ColorModal from './ColorModal.vue'

const props = defineProps<{ initialData: any }>()
const emit = defineEmits(['back', 'save'])

const title = ref(props.initialData.title || '')
const chosenColor = ref(props.initialData.color || '#1e3a8a')
const showColorModal = ref(false)
const maxTokens = ref(props.initialData.maxTokens || 4096)
const prompt = ref(props.initialData.prompt || '')

const openColorPicker = () => {
  showColorModal.value = true
}

const selectColor = (color: string) => {
  chosenColor.value = color
  showColorModal.value = false
}

const handleSave = () => {
  emit('save', {
    title: title.value,
    color: chosenColor.value,
    maxTokens: maxTokens.value,
    prompt: prompt.value
  })
}
</script>

<template>
  <div class="rule-layout">
    <div class="rule-header">
      <button class="back-link" @click="emit('back')">ㄑ 返回</button>
    </div>

    <div class="form-container">
      <div class="form-item">
        <label>配置标题：</label>
        <input type="text" v-model="title" placeholder="请输入模型设定的名称..." class="styled-input" />
      </div>

      <div class="form-item inline-item">
        <label>识别颜色主题：</label>
        <div class="color-picker-trigger" @click="openColorPicker" :style="{ backgroundColor: chosenColor }"></div>
        <span class="color-code" :style="{ color: chosenColor }">{{ chosenColor }}</span>
      </div>

      <div class="form-item">
        <label>最大输出 Token 数：</label>
        <input type="number" v-model="maxTokens" class="styled-input" style="width: 200px;" />
      </div>

      <div class="form-item">
        <label>系统提示词模板 (System Prompt)：</label>
        <textarea rows="5" v-model="prompt" placeholder="请输入系统提示词，如：你是一个专业的法律顾问..." class="styled-textarea"></textarea>
      </div>

      <button class="save-btn" :style="{ backgroundColor: chosenColor }" @click="handleSave">保存并应用配置</button>
    </div>

    <ColorModal v-if="showColorModal" @close="showColorModal = false" @select="selectColor" />
  </div>
</template>

<style scoped>
.rule-layout { display: flex; flex-direction: column; text-align: left; }
.back-link { background: transparent; border: none; font-size: 14px; color: #475569; cursor: pointer; font-weight: bold; margin-bottom: 12px; }
.form-container { display: flex; flex-direction: column; gap: 18px; }
.form-item label { display: block; font-size: 14px; font-weight: 600; margin-bottom: 6px; color: #334155; }
.inline-item { display: flex; align-items: center; gap: 12px; }
.color-picker-trigger { width: 32px; height: 32px; border-radius: 50%; cursor: pointer; border: 2px solid white; box-shadow: 0 0 8px rgba(0,0,0,0.15); }
.color-code { font-size: 13px; font-weight: bold; }
.styled-input { width: 100%; padding: 10px; border-radius: 8px; border: 1px solid rgba(0,0,0,0.1); background: rgba(255,255,255,0.6); box-sizing: border-box; outline: none; }
.styled-textarea { width: 100%; padding: 10px; border-radius: 8px; border: 1px solid rgba(0,0,0,0.1); background: rgba(255,255,255,0.6); box-sizing: border-box; outline: none; resize: none; }
.save-btn { align-self: flex-end; padding: 12px 28px; color: white; border: none; border-radius: 8px; cursor: pointer; font-weight: bold; box-shadow: 0 4px 12px rgba(0,0,0,0.05); }
</style>
