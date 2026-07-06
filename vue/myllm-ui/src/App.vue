<script setup lang="ts">
/**
 * App.vue — 应用根组件
 * ---------------
 * 职责：提供全局路由出口 + 全局样式重置
 * 不包含任何业务逻辑，所有业务在 Layout.vue 中
 */
import { RouterView } from 'vue-router'
</script>

<template>
  <!-- 路由出口：所有路径都渲染 Layout.vue -->
  <RouterView />
</template>

<style>
/* 全局样式重置：去掉默认边距，撑满视口，禁止滚动条 */
html, body, #app {
  margin: 0;
  padding: 0;
  width: 100vw;
  height: 100%;
  overflow: hidden;
}
</style>
