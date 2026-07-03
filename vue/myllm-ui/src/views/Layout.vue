<script setup lang="ts">
import { ref, onMounted } from 'vue'
import RagList from './RagList.vue'
import ModelList from './ModelList.vue'
import MemList from './MemList.vue'
import LoginModal from './LoginModal.vue'
import { sendChatMessage } from '../api'
import { useAuth, checkAuth, logout } from '../services/auth'
import {
  hasLocalData, getAllLocalData, clearAll,
  exportToJson, importFromJson,
  getStoragePrefix, setStoragePrefix, getStorageStats
} from '../services/localStorage'

const { isLoggedIn, user, isOffline, nickname } = useAuth()

const isSidebarOpen = ref<boolean>(false)
const currentView = ref<'chat' | 'rag' | 'model' | 'mem' | 'settings'>('chat')
const globalThemeColor = ref<string>('#1e3a8a')

// Chat
const messageText = ref<string>('')

// Login
const showLoginModal = ref(false)
const showUserMenu = ref(false)

// Settings
const storagePrefix = ref(getStoragePrefix())
const storageStats = ref(getStorageStats())

onMounted(async () => {
  await checkAuth()
  storageStats.value = getStorageStats()
})

// Chat send
const sendMessage = async () => {
  if (!messageText.value.trim()) {
    alert('请输入消息内容')
    return
  }
  try {
    const response = await sendChatMessage(messageText.value)
    console.log('大模型响应结果:', response.data)
    messageText.value = ''
  } catch (error) {
    console.error('发送消息到后端失败:', error)
  }
}

// Auth
const onLoginSuccess = () => {
  showLoginModal.value = false
  storageStats.value = getStorageStats()
}

const handleLogout = () => {
  showUserMenu.value = false
  logout()
  currentView.value = 'chat'
}

// Sidebar
const toggleSidebar = () => {
  isSidebarOpen.value = !isSidebarOpen.value
  showUserMenu.value = false
}

// Settings
const saveStoragePrefix = () => {
  setStoragePrefix(storagePrefix.value)
  storageStats.value = getStorageStats()
}

const handleExportData = () => {
  exportToJson()
}

const handleImportData = () => {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.json'
  input.onchange = (e: Event) => {
    const file = (e.target as HTMLInputElement).files?.[0]
    if (!file) return
    const reader = new FileReader()
    reader.onload = () => {
      if (typeof reader.result === 'string') {
        const ok = importFromJson(reader.result)
        if (ok) {
          storageStats.value = getStorageStats()
          alert('导入成功')
        } else {
          alert('导入失败，文件格式错误')
        }
      }
    }
    reader.readAsText(file)
  }
  input.click()
}

const handleClearAll = () => {
  if (confirm('确定要清除所有本地数据吗？此操作不可撤销。')) {
    clearAll()
    storageStats.value = getStorageStats()
  }
}
</script>

<template>
  <div class="app-layout" :style="{ '--theme-color': globalThemeColor }">
    <!-- ===== 侧边栏 ===== -->
    <div :class="['sidebar', { 'is-open': isSidebarOpen }]">
      <div class="sidebar-top">
        <div class="menu-item logo-area" @click="toggleSidebar">
          <span class="icon">
            <img src='./pic/logo.svg' alt="logo" class="logo-img">
          </span>
          <span class="text logo-text">关闭侧栏</span>
        </div>
        <div class="menu-item" @click="currentView = 'chat'">
          <span class="icon"><img src='./pic/create.svg' class="icon"></span>
          <span class="text">新建对话</span>
        </div>
        <div class="menu-item">
          <span class="icon"><img src='./pic/search.svg' class="icon"></span>
          <span class="text">搜索历史</span>
        </div>
        <div class="menu-item prompt-lib" :class="{ 'active-route': currentView === 'model' }" @click="currentView = 'model'">
          <span class="icon"><img src='./pic/model.svg' class="icon"></span>
          <span class="text">自定义模型</span>
        </div>
        <div class="menu-item prompt-lib" :class="{ 'active-route': currentView === 'rag' }" @click="currentView = 'rag'">
          <span class="icon"><img src='./pic/layers.svg' class="icon"></span>
          <span class="text">自定义知识库</span>
        </div>
        <div class="menu-item prompt-lib" :class="{ 'active-route': currentView === 'mem' }" @click="currentView = 'mem'">
          <span class="icon"><img src='./pic/memory.svg' class="icon"></span>
          <span class="text">自定义记忆存储方式</span>
        </div>
      </div>
      <div class="sidebar-bottom">
        <div class="menu-item" :class="{ 'active-route': currentView === 'settings' }" @click="currentView = 'settings'">
          <span class="icon"><img src='./pic/settings.svg' class="icon"></span>
          <span class="text">系统设置</span>
        </div>
      </div>
    </div>

    <!-- ===== 主内容区 ===== -->
    <div class="main-content">
      <header class="main-header">
        <!-- 离线指示 -->
        <div v-if="isOffline" class="offline-badge">
          ⚡ 离线模式
        </div>

        <div class="user-detail">
          <!-- 未登录 -->
          <button v-if="!isLoggedIn" class="login-btn" @click="showLoginModal = true">
            登录 / 注册
          </button>

          <!-- 已登录 -->
          <div v-else class="user-menu-wrapper">
            <div class="user-info" @click="showUserMenu = !showUserMenu">
              <span class="nickname">{{ nickname }}</span>
              <div class="avatar-circle" :style="{ borderColor: globalThemeColor }">
                {{ (nickname?.toString() || 'U')[0] }}
              </div>
            </div>
            <div v-if="showUserMenu" class="user-dropdown" @click.stop>
              <div class="dropdown-item user-detail-item">
                <span>{{ user?.username }}</span>
                <span class="role-tag">{{ user?.role === 'admin' ? '管理员' : '用户' }}</span>
              </div>
              <hr />
              <button class="dropdown-item logout-item" @click="handleLogout">退出登录</button>
            </div>
          </div>
        </div>
      </header>

      <div class="content-body">
        <div v-if="isSidebarOpen" class="overlay" @click="isSidebarOpen = false"></div>

        <ModelList v-if="currentView === 'model'" @updateColor="(color: string) => globalThemeColor = color" />
        <RagList v-if="currentView === 'rag'" @updateColor="(color: string) => globalThemeColor = color" />
        <MemList v-if="currentView === 'mem'" @updateColor="(color: string) => globalThemeColor = color" />

        <!-- 系统设置 -->
        <div v-if="currentView === 'settings'" class="settings-panel">
          <h3>⚙️ 系统设置</h3>

          <div class="setting-section">
            <h4>💾 本地存储</h4>
            <div class="form-item">
              <label>存储前缀：</label>
              <div class="input-row">
                <input type="text" v-model="storagePrefix" class="styled-input" style="flex:1" />
                <button class="small-btn" @click="saveStoragePrefix">保存</button>
              </div>
            </div>
            <div class="stats-row">
              <span>模型: {{ storageStats.models }}</span>
              <span>记忆: {{ storageStats.memories }}</span>
              <span>知识库: {{ storageStats.rags }}</span>
              <span>总计: {{ (storageStats.totalBytes / 1024).toFixed(0) }} KB</span>
            </div>
            <div class="btn-row">
              <button class="action-btn-secondary" @click="handleExportData">📥 导出 JSON</button>
              <button class="action-btn-secondary" @click="handleImportData">📤 导入 JSON</button>
              <button class="action-btn-danger" @click="handleClearAll">🗑 清除本地数据</button>
            </div>
          </div>
        </div>

        <!-- 聊天欢迎 -->
        <div v-if="currentView === 'chat'" class="welcome-box" :style="{ color: globalThemeColor }">
          <template v-if="isLoggedIn">
            💬 欢迎回来，{{ nickname }}！点击左侧菜单开始配置。
          </template>
          <template v-else>
            💬 当前为离线模式，数据将保存在浏览器本地。登录后可同步到服务器。
          </template>
        </div>
      </div>

      <!-- 聊天输入 -->
      <footer class="main-footer">
        <div class="input-container-glass">
          <button class="action-btn" :style="{ color: globalThemeColor }">＋</button>
          <input type="text" placeholder="发送消息..." v-model="messageText" @keyup.enter="sendMessage" />
          <button class="action-btn" :style="{ color: globalThemeColor }" @click="sendMessage">🎙️</button>
        </div>
      </footer>
    </div>

    <!-- 登录弹窗 -->
    <LoginModal v-if="showLoginModal" @close="showLoginModal = false" @login-success="onLoginSuccess" />
  </div>
</template>

<style scoped>
.app-layout {
  display: flex; width: 100vw; height: 100vh; position: absolute;
  top: 0; left: 0; overflow: hidden;
  background: linear-gradient(135deg, #e2e8f0 0%, #fff7ed 100%);
  font-family: system-ui, -apple-system, sans-serif;
}

/* ===== 侧边栏 ===== */
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
.menu-item .icon { font-size: 20px; width: 24px; text-align: center; flex-shrink: 0; }
.menu-item .text {
  margin-left: 16px; opacity: 0; transform: translateX(-10px);
  transition: opacity 0.2s, transform 0.2s; font-size: 14px; font-weight: 600;
}
.sidebar.is-open .text { opacity: 1; transform: translateX(0); }

/* ===== 主内容 ===== */
.main-content {
  flex: 1; margin-left: 64px; width: calc(100vw - 64px);
  display: flex; flex-direction: column; height: 100vh; position: relative;
}

/* ===== 头部 ===== */
.main-header {
  height: 60px; display: flex; align-items: center; justify-content: flex-end;
  padding: 0 24px; gap: 12px;
}
.offline-badge {
  font-size: 11px; font-weight: bold; color: #ea580c;
  background: rgba(234, 88, 12, 0.1); padding: 4px 12px; border-radius: 12px;
}
.login-btn {
  padding: 8px 20px; background: #1e3a8a; color: white;
  border: none; border-radius: 8px; cursor: pointer; font-weight: 600; font-size: 13px;
}
.login-btn:hover { background: #1e40af; }

.user-menu-wrapper { position: relative; }
.user-info {
  display: flex; align-items: center; gap: 10px; cursor: pointer;
}
.nickname { font-size: 14px; font-weight: 600; color: #1e293b; }
.avatar-circle {
  width: 36px; height: 36px; border-radius: 50%;
  background: rgba(255, 255, 255, 0.6); border: 2px solid;
  display: flex; align-items: center; justify-content: center;
  font-weight: bold; font-size: 16px; color: var(--theme-color);
}
.user-dropdown {
  position: absolute; top: 46px; right: 0; width: 200px;
  background: rgba(255,255,255,0.95); border-radius: 12px;
  box-shadow: 0 8px 30px rgba(0,0,0,0.12);
  border: 1px solid rgba(0,0,0,0.06); z-index: 200;
  backdrop-filter: blur(12px); overflow: hidden;
}
.dropdown-item {
  width: 100%; padding: 12px 16px; border: none; background: transparent;
  display: flex; justify-content: space-between; align-items: center;
  font-size: 13px; cursor: pointer; color: #1e293b;
}
.dropdown-item:hover { background: rgba(0,0,0,0.04); }
.user-detail-item { cursor: default; }
.role-tag { font-size: 10px; background: rgba(0,0,0,0.06); padding: 2px 8px; border-radius: 8px; color: #64748b; }
.logout-item { color: #dc2626; }
hr { margin: 0; border: none; border-top: 1px solid rgba(0,0,0,0.06); }

/* ===== 内容体 ===== */
.content-body {
  flex: 1; display: flex; align-items: center; justify-content: center;
  padding: 20px; width: 100%; box-sizing: border-box;
}
.welcome-box {
  font-size: 16px; font-weight: bold; background: rgba(255,255,255,0.4);
  padding: 30px; border-radius: 16px; backdrop-filter: blur(10px);
  text-align: center; line-height: 1.6;
}

/* ===== 设置面板 ===== */
.settings-panel {
  width: 90%; max-width: 600px; padding: 28px; border-radius: 20px;
  background: rgba(255, 255, 255, 0.5); backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.5);
}
.settings-panel h3 { margin: 0 0 20px; color: #1e293b; }
.setting-section { margin-bottom: 24px; }
.setting-section h4 { margin: 0 0 12px; color: #475569; font-size: 14px; }
.form-item { margin-bottom: 10px; }
.form-item label { font-size: 12px; font-weight: 600; color: #64748b; display: block; margin-bottom: 4px; }
.input-row { display: flex; gap: 8px; }
.styled-input {
  padding: 8px 12px; border-radius: 8px; border: 1px solid rgba(0,0,0,0.1);
  background: rgba(255,255,255,0.6); outline: none; font-size: 13px;
}
.small-btn {
  padding: 8px 16px; background: #1e3a8a; color: white; border: none;
  border-radius: 8px; cursor: pointer; font-weight: 600; font-size: 12px; white-space: nowrap;
}
.stats-row {
  display: flex; gap: 16px; flex-wrap: wrap;
  font-size: 12px; color: #64748b; margin: 12px 0;
}
.btn-row { display: flex; gap: 8px; flex-wrap: wrap; }
.action-btn-secondary {
  padding: 8px 14px; background: rgba(255,255,255,0.7); border: 1px solid rgba(0,0,0,0.1);
  border-radius: 8px; cursor: pointer; font-size: 12px; transition: all 0.2s;
}
.action-btn-secondary:hover { background: white; }
.action-btn-danger {
  padding: 8px 14px; background: rgba(220,38,38,0.08); border: 1px solid rgba(220,38,38,0.2);
  border-radius: 8px; cursor: pointer; font-size: 12px; color: #dc2626; transition: all 0.2s;
}
.action-btn-danger:hover { background: rgba(220,38,38,0.15); }

/* ===== 底部输入 ===== */
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
