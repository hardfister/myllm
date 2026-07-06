<script setup lang="ts">
/**
 * ColorModal.vue — 主题色选择弹窗
 * ---------------
 * 功能：
 *   1. 8 种预设色点击秒选
 *   2. 自定义 HEX 色值输入
 *   3. 点击预设色或"应用"按钮 → emit('select', hexColor)
 *
 * Emits：
 *   close  — 关闭弹窗
 *   select — 选中颜色，参数为 hex 字符串如 "#1e3a8a"
 */
import { ref } from 'vue'

const emit = defineEmits(['close', 'select'])
const customHexColor = ref<string>('#1e3a8a')

// 8 种预设高级设计色
const presetColors = ['#1e3a8a', '#0284c7', '#0d9488', '#16a34a', '#ca8a04', '#ea580c', '#dc2626', '#7c3aed']
</script>

<template>
  <Teleport to="body">
    <div class="color-mask">
      <div class="color-box">
        <h4>🎨 选择主题色 (影响系统文字与图标)</h4>
        <!-- 预设色块：4 列网格 -->
        <div class="color-presets">
          <div
            v-for="color in presetColors"
            :key="color"
            class="color-dot"
            :style="{ backgroundColor: color }"
            @click="emit('select', color)"
          ></div>
        </div>
        <!-- 自定义 HEX 输入 -->
        <div class="custom-hex">
          <label>或输入16进制色值：</label>
          <input type="text" v-model="customHexColor" class="hex-input" placeholder="#FFFFFF" />
          <button class="confirm-hex-btn" @click="emit('select', customHexColor)">应用</button>
        </div>
        <button class="close-btn" @click="emit('close')">取消</button>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.color-mask { position: fixed; top: 0; left: 0; width: 100vw; height: 100vh; background: rgba(0,0,0,0.1); display: flex; align-items: center; justify-content: center; z-index: 300; }
.color-box { width: 320px; padding: 20px; border-radius: 12px; background: white; box-shadow: 0 10px 30px rgba(0,0,0,0.15); }
.color-box h4 { margin: 0 0 16px 0; font-size: 14px; color: #334155; }
.color-presets { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 20px; }
.color-dot { width: 44px; height: 44px; border-radius: 8px; cursor: pointer; transition: transform 0.15s; }
.color-dot:hover { transform: scale(1.1); }
.custom-hex { display: flex; flex-direction: column; gap: 6px; margin-bottom: 16px; text-align: left; }
.custom-hex label { font-size: 12px; font-weight: 600; color: #475569; }
.hex-input { padding: 8px; border: 1px solid #cbd5e1; border-radius: 6px; outline: none; }
.confirm-hex-btn { padding: 8px; background: #334155; color: white; border: none; border-radius: 6px; cursor: pointer; margin-top: 4px; }
.close-btn { width: 100%; background: transparent; border: 1px solid #cbd5e1; padding: 6px; border-radius: 6px; cursor: pointer; font-size: 12px; }
</style>
