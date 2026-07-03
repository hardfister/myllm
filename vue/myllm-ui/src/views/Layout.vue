<script setup lang="ts">
import { ref } from 'vue'
import RagList from './RagList.vue'

const isSidebarOpen = ref<boolean>(false)
const currentView = ref<'chat' | 'rag'>('chat') // 默认主页或 RAG 库页
const globalThemeColor = ref<string>('#1e3a8a') // 全局动态主题色

const toggleSidebar = () => {
  isSidebarOpen.value = !isSidebarOpen.value
}
</script>

<template>
  <div class="app-layout" :style="{ '--theme-color': globalThemeColor }">
    <div :class="['sidebar', { 'is-open': isSidebarOpen }]">
      <div class="sidebar-top">
        <div class="menu-item logo-area" @click="toggleSidebar">
          <span class="icon">
            <img src='./pic/logo.svg' alt="logo" class="logo-img">
          </span>
          <span class="text logo-text">关闭侧栏</span>
        </div>
        <div class="menu-item" @click="currentView = 'chat'">
          <span class="icon">📝</span>
          <span class="text">新建对话</span>
        </div>
        <div class="menu-item">
          <span class="icon">🔍</span>
          <span class="text">搜索历史</span>
        </div>
        <div class="menu-item prompt-lib" :class="{ 'active-route': currentView === 'rag' }" @click="currentView = 'rag'">
          <span class="icon">🎛️</span>
          <span class="text">自定义 RAG / Prompt 库</span>
        </div>
      </div>
      <div class="sidebar-bottom">
        <div class="menu-item">
          <span class="icon">⚙️</span>
          <span class="text">系统设置</span>
        </div>
      </div>
    </div>

    <div class="main-content">
      <header class="main-header">
        <div class="user-detail">
          <span :style="{ color: globalThemeColor }">用户详情</span>
          <div class="avatar-circle" :style="{ borderColor: globalThemeColor }"></div>
        </div>
      </header>

      <div class="content-body">
        <div v-if="isSidebarOpen" class="overlay" @click="isSidebarOpen = false"></div>
        
        <RagList 
          v-if="currentView === 'rag'" 
          @updateColor="(color) => globalThemeColor = color"
        />
        <div v-else class="welcome-box" :style="{ color: globalThemeColor }">
          💬 当前为对话调试状态，点击左侧“自定义 RAG 库”开始配置
        </div>
      </div>

      <footer class="main-footer">
        <div class="input-container-glass">
          <button class="action-btn" :style="{ color: globalThemeColor }">＋</button>
          <input type="text" placeholder="给大模型发送消息..." />
          <button class="action-btn" :style="{ color: globalThemeColor }">🎙️</button>
        </div>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.app-layout {
  display: flex;
  width: 100vw;
  height: 100vh;
  position: absolute;
  top: 0; left: 0;
  overflow: hidden;
  background: linear-gradient(135deg, #e2e8f0 0%, #fff7ed 100%);
  font-family: system-ui, -apple-system, sans-serif;
}
.sidebar {
  position: fixed; top: 0; left: 0; height: 100vh; width: 64px;
  display: flex; flex-direction: column; justify-content: space-between;
  padding: 16px 0; box-sizing: border-box; z-index: 100;
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  background: rgba(255, 255, 255, 0.4);
  backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px);
  border-right: 1px solid rgba(255, 255, 255, 0.4);
}
.sidebar.is-open { width: 240px; }
.menu-item {
  display: flex; align-items: center; height: 48px; padding: 0 20px;
  cursor: pointer; color: #475569; white-space: nowrap; transition: all 0.2s;
}
.menu-item:hover, .active-route {
  background-color: rgba(255, 255, 255, 0.5);
  color: var(--theme-color) !important;
}
.menu-item .icon { font-size: 20px; width: 24px; text-align: center; flex-shrink: 0; color: var(--theme-color); }
.menu-item .text {
  margin-left: 16px; opacity: 0; transform: translateX(-10px);
  transition: opacity 0.2s, transform 0.2s; font-size: 14px; font-weight: 600;
}
.sidebar.is-open .text { opacity: 1; transform: translateX(0); }
.main-content {
  flex: 1; margin-left: 64px; width: calc(100vw - 64px);
  display: flex; flex-direction: column; height: 100vh; position: relative;
}
.main-header { height: 60px; display: flex; align-items: center; justify-content: flex-end; padding: 0 24px; }
.user-detail { display: flex; align-items: center; gap: 12px; font-size: 14px; font-weight: 600; }
.avatar-circle { width: 36px; height: 36px; border-radius: 50%; background: rgba(255, 255, 255, 0.6); border: 2px solid; }
.content-body { flex: 1; display: flex; align-items: center; justify-content: center; padding: 20px; width: 100%; box-sizing: border-box; }
.welcome-box { font-size: 18px; font-weight: bold; background: rgba(255,255,255,0.4); padding: 30px; border-radius: 16px; backdrop-filter: blur(10px); }
.main-footer { padding: 24px 40px; display: flex; justify-content: center; width: 100%; box-sizing: border-box; }
.input-container-glass {
  display: flex; align-items: center; width: 100%; max-width: 800px; padding: 8px 16px; border-radius: 30px;
  background: rgba(255, 255, 255, 0.5); backdrop-filter: blur(12px); -webkit-backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.4); box-shadow: 0 8px 32px rgba(0, 0, 0, 0.04);
}
.input-container-glass input { flex: 1; border: none; background: transparent; padding: 8px 12px; font-size: 15px; outline: none; }
.action-btn { background: transparent; border: none; font-size: 20px; cursor: pointer; padding: 4px 8px; }
.overlay { position: absolute; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.01); z-index: 50; }
</style>