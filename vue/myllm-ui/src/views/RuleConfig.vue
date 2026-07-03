<script setup lang="ts">
import { ref } from 'vue'
import ColorModal from './ColorModal.vue'

const props = defineProps<{ initialData: any }>()
const emit = defineEmits(['back', 'save'])

const title = ref(props.initialData.title)
const chosenColor = ref(props.initialData.color)
const showColorModal = ref(false)
const maxWords = ref(props.initialData.maxWords)
const rules = ref(props.initialData.rules)

const openColorPicker = () => {
  showColorModal.value = true // 点击颜色圆圈，拉起覆盖式颜色小页面
}

const selectColor = (color: string) => {
  chosenColor.value = color
  showColorModal.value = false
}

const handleSave = () => {
  emit('save', {
    title: title.value,
    color: chosenColor.value,
    maxWords: maxWords.value,
    rules: rules.value
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
        <input type="text" v-model="title" placeholder="请输入当前知识库规则方案的名称..." class="styled-input" />
      </div>

      <div class="form-item inline-item">
        <label>识别颜色主题：</label>
        <div class="color-picker-trigger" @click="openColorPicker" :style="{ backgroundColor: chosenColor }"></div>
        <span class="color-code" :style="{ color: chosenColor }">{{ chosenColor }}</span>
      </div>

      <div class="form-item">
        <label>输出字数限制 (字符范围)：</label>
        <div class="range-row">
          <input type="number" v-model="maxWords" class="styled-input min-input" />
          <span>字以内</span>
        </div>
      </div>

      <div class="form-item">
        <label>知识拆解与检索核心规则 (RAG Prompt)：</label>
        <textarea rows="5" v-model="rules" placeholder="请输入文档向量化切片后的深度提取规则，如：优先匹配第二章内容..." class="styled-textarea"></textarea>
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
.min-input { width: 120px; text-align: center; margin-right: 8px; }
.range-row { display: flex; align-items: center; font-size: 14px; color: #475569; }
.styled-textarea { width: 100%; padding: 10px; border-radius: 8px; border: 1px solid rgba(0,0,0,0.1); background: rgba(255,255,255,0.6); box-sizing: border-box; outline: none; resize: none; }
.save-btn { align-self: flex-end; padding: 12px 28px; color: white; border: none; border-radius: 8px; cursor: pointer; font-weight: bold; box-shadow: 0 4px 12px rgba(0,0,0,0.05); }
</style>