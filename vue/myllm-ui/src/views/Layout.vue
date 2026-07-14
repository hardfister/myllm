<script setup lang="ts">
/**
 * Layout.vue — 应用主布局
 * ---------------
 * IsLogin 控制数据流向：false→localStorage / true→后端 API
 *
 * 历史记录面板（左侧侧边栏内）：
 *   - 判定条件：用户发出第一条消息 → 后端创建 Session + Message → 前端刷新列表
 *   - 以栈形式从上往下排列（最近活跃的会话在最上面）
 *   - 记录足够多时可用滚轮滚动（max-height + overflow-y:auto）
 *   - 每条记录右侧有 ⋯ 按钮 → 点击向右展开小弹窗 → 选择"删除"
 *   - 二次确认后调用 DELETE /api/sessions/{dbId} 从 MySQL 删除
 */
import { ref, onMounted, nextTick, watch } from 'vue'
import RagList from './RagList.vue'
import ModelList from './ModelList.vue'
import MemList from './MemList.vue'
import LoginModal from './LoginModal.vue'
import { sendChatMessage, sendChatMessageStream, newSession, clearSession, listSessions, deleteSession, renameSession, generateAiTitle, getSessionMessages } from '../api'
import type { ChatResponse, ChatMessage, HistorySession, SessionMessage } from '../api'
import { useAuth, checkAuth, logout } from '../services/auth'
import {
  clearAll, exportToJson, importFromJson,
  getStoragePrefix, setStoragePrefix, getStorageStats
} from '../services/localStorage'

const { isLoggedIn, user, isOffline, nickname } = useAuth()

// ===== UI 状态 =====
const isSidebarOpen = ref(false)
const currentView = ref<'chat' | 'rag' | 'model' | 'mem' | 'settings'>('chat')
const globalThemeColor = ref('#1e3a8a')
const messageText = ref('')
const showLoginModal = ref(false)
const showUserMenu = ref(false)
const showProfileModal = ref(false)
const listReloadKey = ref(0)

// ===== 聊天状态 =====
const sessionId = ref('')
const messages = ref<ChatMessage[]>([])
const isStreaming = ref(false)
const streamingAbort = ref<AbortController | null>(null)
const chatBodyRef = ref<HTMLElement | null>(null)

// ===== 历史记录状态 =====
const historySessions = ref<HistorySession[]>([])   // 会话列表
const historyLoading = ref(false)                     // 加载中
const openMenuId = ref<number | null>(null)            // 当前打开 ⋯ 菜单的会话 dbId

// ===== 设置面板 =====
const storagePrefix = ref(getStoragePrefix())
const storageStats = ref(getStorageStats())
const profileNickname = ref('')
const profileEmail = ref('')

onMounted(async () => {
  await checkAuth()
  storageStats.value = getStorageStats()
  if (isLoggedIn.value) await loadHistorySessions()
})

// ===== 自动滚动到最新消息 =====
watch(messages, async () => {
  await nextTick()
  if (chatBodyRef.value) chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight
}, { deep: true })

// ===== 加载历史会话列表 =====
const loadHistorySessions = async () => {
  if (!isLoggedIn.value || isOffline.value) return
  historyLoading.value = true
  try {
    const res = await listSessions()
    historySessions.value = res.data
  } catch (e) { console.error('加载历史记录失败:', e) }
  finally { historyLoading.value = false }
}

// ===== 新建对话 =====
// 不调 newSession() — 后端在发第一条消息时自动创建 Session
const startNewChat = () => {
  currentView.value = 'chat'
  messages.value = []
  sessionId.value = ''
  isStreaming.value = false
  // 刷新历史列表（可能刚删除过会话）
  if (isLoggedIn.value) loadHistorySessions()
}

// ===== 点击历史会话 → 加载历史消息并切换到该会话 =====
const openHistorySession = async (item: HistorySession) => {
  currentView.value = 'chat'
  sessionId.value = item.sessionId
  isStreaming.value = false
  messages.value = []

  // 从后端加载该会话的完整消息记录
  if (isLoggedIn.value) {
    try {
      const res = await getSessionMessages(item.sessionId)
      const historyMsgs = res.data as SessionMessage[]
      messages.value = historyMsgs.map(m => {
        // 识别多模型角色名，显示名放入 modelUsed
        const isUser = m.role === 'user'
        const isKnownRole = ['user', 'assistant', 'error', 'system'].includes(m.role)
        return {
          role: (isUser ? 'user' : 'assistant') as ChatMessage['role'],
          content: m.content,
          modelUsed: isKnownRole ? undefined : m.role,  // 多模型标识
          timestamp: m.timestamp
        }
      })
    } catch (e) {
      console.error('加载历史消息失败:', e)
    }
  }
}

// ===== 发送消息（流式 SSE） =====
const sendMessage = () => {
  const text = messageText.value.trim()
  if (!text || isStreaming.value) return

  const userMsg: ChatMessage = { role: 'user', content: text, timestamp: new Date().toISOString() }
  messages.value.push(userMsg)
  messageText.value = ''
  isStreaming.value = true

  // 使用流式 SSE — 每个模型逐 token 输出
  streamingAbort.value = sendChatMessageStream(
    text,
    sessionId.value || undefined,
    // onStartModel: 创建一条新的空 AI 气泡
    (displayName: string) => {
      messages.value.push({
        role: 'assistant',
        content: '',
        modelUsed: displayName,
        timestamp: new Date().toISOString()
      })
    },
    // onToken: 追加 token 到当前气泡
    (_displayName: string, token: string) => {
      const last = messages.value[messages.value.length - 1]
      if (last && last.role === 'assistant') {
        last.content += token
      }
    },
    // onEndModel: 标记完成
    (_displayName: string) => {
      // 气泡已完整
    },
    // onDone: 拿到 sessionId + 刷新侧栏历史列表
    (newSessionId: string) => {
      if (newSessionId && !sessionId.value) sessionId.value = newSessionId
      isStreaming.value = false
      // 去除空的 AI 气泡（模型调用失败 etc）
      messages.value = messages.value.filter(m => m.role !== 'assistant' || m.content.trim() !== '')
      if (isLoggedIn.value) loadHistorySessions()
      streamingAbort.value = null
    },
    // onError
    (err: string) => {
      messages.value.push({ role: 'error', content: '⚠️ ' + err, timestamp: new Date().toISOString() })
      isStreaming.value = false
    }
  )
}

// ===== 历史会话操作 =====
const renameText = ref('')

const doRename = (item?: HistorySession) => {
  if (!item) return
  const newTitle = renameText.value.trim()
  if (!newTitle) return
  renameSession(item.sessionId, newTitle).then(() => {
    item.title = newTitle
    renameText.value = ''
    openMenuId.value = null
  }).catch(e => console.error('改名失败:', e))
}

const doAiTitle = (item?: HistorySession) => {
  if (!item) return
  generateAiTitle(item.sessionId).then(res => {
    item.title = res.data.title
    renameText.value = ''
    openMenuId.value = null
  }).catch(e => { console.error('AI起名失败:', e); alert('AI起名失败') })
}

const confirmDeleteSession = (item?: HistorySession) => {
  if (!item) return
  if (!confirm(`确定要删除会话「${item.title}」吗？\n\n此操作不可撤销。`)) return
  openMenuId.value = null
  handleDeleteSession(item.id)
}

const handleDeleteSession = async (dbId: number) => {
  try {
    await deleteSession(dbId)
    // 从本地列表移除
    historySessions.value = historySessions.value.filter(s => s.id !== dbId)
    // 如果删除的是当前活跃会话，清空聊天区
    const deleted = historySessions.value.find(s => s.id === dbId)
    if (!deleted && sessionId.value) {
      // 删除后列表里没了—如果当前 sessionId 对应已删除的记录，清空
    }
  } catch (e) { console.error('删除会话失败:', e); alert('删除失败') }
}

// ===== ⋯ 菜单切换 =====
const toggleMenu = (dbId: number, event: Event) => {
  event.stopPropagation()
  openMenuId.value = openMenuId.value === dbId ? null : dbId
}

// 点击其他地方关闭 ⋯ 菜单
const closeMenu = () => { openMenuId.value = null }

// ===== 清除当前会话 =====
const handleClearChat = async () => {
  if (sessionId.value) {
    try { await clearSession(sessionId.value) } catch { /* ignore */ }
  }
  messages.value = []
  sessionId.value = ''
}

// ===== 认证 =====
const onLoginSuccess = async () => {
  showLoginModal.value = false
  storageStats.value = getStorageStats()
  listReloadKey.value++
  await loadHistorySessions()  // 登录后立即加载历史会话列表
}
const onLoginClick = () => { showLoginModal.value = true }
const openProfileEdit = () => {
  showUserMenu.value = false
  if (user.value) profileNickname.value = user.value.nickname || ''
  profileEmail.value = ''
  showProfileModal.value = true
}
const saveProfile = () => {
  if (user.value) {
    user.value.nickname = profileNickname.value || user.value.nickname
    localStorage.setItem('myllm_user', JSON.stringify(user.value))
  }
  showProfileModal.value = false
}
const handleLogout = () => {
  showUserMenu.value = false
  logout()
  currentView.value = 'chat'
  listReloadKey.value++
  historySessions.value = []
}
const handleSwitchAccount = () => {
  showUserMenu.value = false
  logout()
  showLoginModal.value = true
  historySessions.value = []
}

// ===== 侧边栏 =====
const toggleSidebar = () => {
  isSidebarOpen.value = !isSidebarOpen.value
  showUserMenu.value = false
  openMenuId.value = null
  // 侧边栏展开时加载历史记录
  if (isSidebarOpen.value && isLoggedIn.value) loadHistorySessions()
}

// ===== 设置 =====
const saveStoragePrefix = () => { setStoragePrefix(storagePrefix.value); storageStats.value = getStorageStats() }
const handleExportData = () => exportToJson()
const handleImportData = () => {
  const input = document.createElement('input')
  input.type = 'file'; input.accept = '.json'
  input.onchange = (e: Event) => {
    const file = (e.target as HTMLInputElement).files?.[0]
    if (!file) return
    const reader = new FileReader()
    reader.onload = () => {
      if (typeof reader.result === 'string') {
        if (importFromJson(reader.result)) { storageStats.value = getStorageStats(); alert('导入成功') }
        else { alert('导入失败') }
      }
    }
    reader.readAsText(file)
  }
  input.click()
}
const handleClearAll = () => {
  if (confirm('确定要清除所有本地数据吗？此操作不可撤销。')) { clearAll(); storageStats.value = getStorageStats() }
}
</script>

<template>
  <div class="app-layout" :style="{ '--theme-color': globalThemeColor }" @click="closeMenu">
    <!-- ===== 左侧毛玻璃侧边栏 ===== -->
    <div :class="['sidebar', { 'is-open': isSidebarOpen }]">
      <div class="sidebar-top">
        <!-- Logo -->
        <div class="menu-item logo-area" @click.stop="toggleSidebar">
          <span class="icon"><img src='./pic/logo.svg' alt="logo" class="logo-img"></span>
          <span class="text logo-text">关闭侧栏</span>
        </div>

        <!-- 新建对话 -->
        <div class="menu-item" :class="{ 'active-route': currentView === 'chat' }" @click.stop="startNewChat">
          <span class="icon"><img src='./pic/create.svg' class="icon"></span>
          <span class="text">新建对话</span>
        </div>

        <!-- 自定义模型 -->
        <div class="menu-item prompt-lib" :class="{ 'active-route': currentView === 'model' }" @click.stop="currentView = 'model'">
          <span class="icon"><img src='./pic/model.svg' class="icon"></span>
          <span class="text">自定义模型</span>
        </div>
        <!-- 自定义知识库 -->
        <div class="menu-item prompt-lib" :class="{ 'active-route': currentView === 'rag' }" @click.stop="currentView = 'rag'">
          <span class="icon"><img src='./pic/layers.svg' class="icon"></span>
          <span class="text">自定义知识库</span>
        </div>
        <!-- 自定义记忆 -->
        <div class="menu-item prompt-lib" :class="{ 'active-route': currentView === 'mem' }" @click.stop="currentView = 'mem'">
          <span class="icon"><img src='./pic/memory.svg' class="icon"></span>
          <span class="text">自定义记忆存储方式</span>
        </div>

        <!-- ====== 历史记录面板（位于记忆配置下方，栈式排列） ====== -->
        <div class="history-section" v-if="isSidebarOpen">
          <div class="history-header">
            <span class="text history-label">历史记录</span>
            <button v-if="isLoggedIn" class="history-refresh-btn" @click.stop="loadHistorySessions"
              :disabled="historyLoading" title="刷新">
              {{ historyLoading ? '⏳' : '🔄' }}
            </button>
          </div>
          <div class="history-list" v-if="historySessions.length > 0">
            <div v-for="item in historySessions" :key="item.id" class="history-item"
              :class="{ 'active-session': item.sessionId === sessionId }"
              @click.stop="openHistorySession(item)">
              <div class="history-item-icon">💬</div>
              <div class="history-item-body">
                <div class="history-item-title">{{ item.title }}</div>
                <div class="history-item-meta">{{ item.messageCount }} 条消息</div>
              </div>
              <div class="history-menu-wrapper" @click.stop>
                <button class="history-menu-btn" @click.stop="toggleMenu(item.id, $event)" title="更多操作">⋯</button>
              </div>
            </div>
          </div>
          <div v-else-if="isLoggedIn" class="history-empty">
            <span class="text" style="color:#94a3b8;font-size:11px;">暂无历史记录</span>
          </div>
          <div v-else class="history-empty">
            <span class="text" style="color:#94a3b8;font-size:11px;">登录后查看历史记录</span>
          </div>
        </div>
      </div>

      <div class="sidebar-bottom">
        <div class="menu-item" :class="{ 'active-route': currentView === 'settings' }" @click.stop="currentView = 'settings'">
          <span class="icon"><img src='./pic/settings.svg' class="icon"></span>
          <span class="text">系统设置</span>
        </div>
      </div>
    </div>

    <!-- ===== 右侧主内容 ===== -->
    <div class="main-content">
      <header class="main-header">
        <div v-if="isOffline" class="offline-badge">⚡ 离线模式</div>
        <div class="user-detail">
          <button v-if="!isLoggedIn" class="login-btn" @click="onLoginClick">登录 / 注册</button>
          <div v-else class="user-menu-wrapper">
            <div class="avatar-circle solo" :style="{ borderColor: globalThemeColor }"
              @click.stop="showUserMenu = !showUserMenu">{{ (nickname?.toString() || 'U')[0] }}</div>
            <div v-if="showUserMenu" class="user-dropdown" @click.stop>
              <div class="dropdown-header">
                <span class="dropdown-nickname">{{ nickname }}</span>
                <span class="role-tag">{{ user?.role === 'admin' ? '管理员' : '用户' }}</span>
              </div><hr />
              <button class="dropdown-item" @click="openProfileEdit"><span class="dd-icon">🖼️</span> 修改头像</button>
              <button class="dropdown-item" @click="openProfileEdit"><span class="dd-icon">⚙️</span> 设置资料</button><hr />
              <button class="dropdown-item logout-item" @click="handleLogout"><span class="dd-icon">🚪</span> 退出登录</button>
              <button class="dropdown-item" @click="handleSwitchAccount"><span class="dd-icon">🔄</span> 更换账号</button>
            </div>
          </div>
        </div>
      </header>

      <div class="content-body">
        <div v-if="isSidebarOpen" class="overlay" @click="isSidebarOpen = false"></div>
        <ModelList v-if="currentView === 'model'" :key="'model-' + listReloadKey" @updateColor="(c: string) => globalThemeColor = c" />
        <RagList v-if="currentView === 'rag'" :key="'rag-' + listReloadKey" @updateColor="(c: string) => globalThemeColor = c" />
        <MemList v-if="currentView === 'mem'" :key="'mem-' + listReloadKey" @updateColor="(c: string) => globalThemeColor = c" />

        <!-- 聊天区 -->
        <div v-if="currentView === 'chat'" class="chat-container">
          <div class="chat-toolbar">
            <span class="chat-session-badge" v-if="sessionId">会话 {{ sessionId }}<span v-if="messages.length"> · {{ messages.length }} 条</span></span>
            <span class="chat-session-badge muted" v-else>新会话</span>
            <button v-if="messages.length > 0" class="chat-clear-btn" @click="handleClearChat">清空对话</button>
          </div>
          <div class="chat-messages" ref="chatBodyRef">
            <div v-if="messages.length === 0 && !isStreaming" class="chat-empty">
              <div class="chat-empty-icon">💬</div>
              <p>{{ isLoggedIn ? '点击左侧「新建对话」或选择历史会话开始' : '离线模式：对话记录仅存于当前页面' }}</p>
            </div>
            <div v-for="(msg, i) in messages" :key="i" :class="['chat-bubble', msg.role]">
              <div class="bubble-avatar">{{ msg.role === 'user' ? (nickname?.toString() || '我')[0] : msg.role === 'error' ? '⚠' : 'AI' }}</div>
              <div class="bubble-body">
                <div class="bubble-text">{{ msg.content }}</div>
                <div v-if="msg.role === 'assistant' && (msg.modelUsed || msg.sources?.length)" class="bubble-meta">
                  <span v-if="msg.modelUsed" class="meta-tag">🤖 {{ msg.modelUsed }}</span>
                  <span v-if="msg.sources?.length" class="meta-tag" v-for="s in msg.sources" :key="s">📄 {{ s }}</span>
                </div>
              </div>
            </div>
            <div v-if="isStreaming" class="chat-bubble assistant">
              <div class="bubble-avatar">AI</div>
              <div class="bubble-body"><div class="typing-indicator"><span></span><span></span><span></span></div></div>
            </div>
          </div>
        </div>

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
              <span>模型: {{ storageStats.models }}</span><span>记忆: {{ storageStats.memories }}</span>
              <span>知识库: {{ storageStats.rags }}</span><span>总计: {{ (storageStats.totalBytes / 1024).toFixed(0) }} KB</span>
            </div>
            <div class="btn-row">
              <button class="action-btn-secondary" @click="handleExportData">📥 导出 JSON</button>
              <button class="action-btn-secondary" @click="handleImportData">📤 导入 JSON</button>
              <button class="action-btn-danger" @click="handleClearAll">🗑 清除本地数据</button>
            </div>
          </div>
        </div>
      </div>

      <footer class="main-footer">
        <div class="input-container-glass">
          <button class="action-btn" :style="{ color: globalThemeColor }" @click="startNewChat">＋</button>
          <input type="text" placeholder="发送消息..." v-model="messageText" @keyup.enter="sendMessage" :disabled="isStreaming" />
          <button class="action-btn send-btn" :style="{ color: globalThemeColor }" @click="sendMessage" :disabled="isStreaming">{{ isStreaming ? '⏳' : '➤' }}</button>
        </div>
      </footer>
    </div>

    <!-- 弹窗 -->
    <LoginModal v-if="showLoginModal" @close="showLoginModal = false" @login-success="onLoginSuccess" />

    <!-- 会话编辑弹窗（居中） -->
    <Teleport to="body">
      <div v-if="openMenuId !== null" class="login-overlay" @click.self="openMenuId = null">
        <div class="session-edit-box">
          <h4>编辑会话</h4>
          <input v-model="renameText" class="styled-input" placeholder="输入新名称..."
            @keyup.enter="doRename(historySessions.find(s => s.id === openMenuId)!)"
            style="width:100%;box-sizing:border-box;margin:12px 0;" />
          <div class="session-edit-btns">
            <button class="popup-item" @click="doRename(historySessions.find(s => s.id === openMenuId)!)">✏️ 改名</button>
            <button class="popup-item" @click="doAiTitle(historySessions.find(s => s.id === openMenuId)!)">🤖 AI起名</button>
            <button class="popup-item danger" @click="confirmDeleteSession(historySessions.find(s => s.id === openMenuId)!)">🗑 删除</button>
          </div>
          <button class="close-btn" style="margin-top:8px;width:100%;" @click="openMenuId = null">取消</button>
        </div>
      </div>
    </Teleport>

    <Teleport to="body">
      <div v-if="showProfileModal" class="login-overlay" @click.self="showProfileModal = false">
        <div class="login-box">
          <div class="login-header"><h3>⚙️ 设置资料</h3><button class="close-btn" @click="showProfileModal = false">✕</button></div>
          <div class="form-body">
            <div class="form-item"><label>头像</label>
              <div class="avatar-circle large" :style="{ borderColor: globalThemeColor }" style="width:64px;height:64px;font-size:28px;margin:0 auto;">{{ (nickname?.toString() || 'U')[0] }}</div>
              <p style="text-align:center;color:#94a3b8;font-size:11px;margin-top:4px;">头像修改（即将上线）</p></div>
            <div class="form-item"><label>昵称</label><input type="text" v-model="profileNickname" :placeholder="user?.username" class="styled-input" /></div>
            <div class="form-item"><label>邮箱</label><input type="email" v-model="profileEmail" placeholder="绑定邮箱（即将上线）" class="styled-input" disabled /></div>
            <button class="submit-btn" @click="saveProfile">保存</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
/* ===== 根 ===== */
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
  background: rgba(255,255,255,0.4);
  backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px);
  border-right: 1px solid rgba(255,255,255,0.4);
}
.sidebar.is-open { width: 240px; }
.menu-item {
  display: flex; align-items: center; height: 48px; padding: 0 20px;
  cursor: pointer; color: #475569; white-space: nowrap; transition: all 0.2s;
}
.menu-item:hover, .active-route { background-color: rgba(255,255,255,0.5); color: var(--theme-color) !important; }
.menu-item .icon { font-size: 20px; width: 24px; text-align: center; flex-shrink: 0; }
.menu-item .text {
  margin-left: 16px; opacity: 0; transform: translateX(-10px);
  transition: opacity 0.2s, transform 0.2s; font-size: 14px; font-weight: 600;
}
.sidebar.is-open .text { opacity: 1; transform: translateX(0); }

/* ===== 历史记录面板（仅侧边栏展开时可见） ===== */
.history-section {
  display: flex; flex-direction: column;
  margin: 8px 0; border-top: 1px solid rgba(0,0,0,0.06); padding-top: 8px;
}
.history-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 4px 20px; margin-bottom: 4px;
}
.history-label { font-size: 11px; font-weight: 700; color: #94a3b8; text-transform: uppercase; letter-spacing: 0.5px; }
.history-refresh-btn { background: transparent; border: none; font-size: 12px; cursor: pointer; padding: 2px 4px; opacity: 0.6; }
.history-refresh-btn:hover { opacity: 1; }

/* 滚动列表：max-height 控制，超出滚轮 */
.history-list {
  display: flex; flex-direction: column;
  max-height: 240px; overflow-y: auto;
  padding: 0 12px;
}
.history-list::-webkit-scrollbar { width: 3px; }
.history-list::-webkit-scrollbar-thumb { background: rgba(0,0,0,0.1); border-radius: 2px; }

/* 单条历史记录 */
.history-item {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 10px; border-radius: 10px; cursor: pointer;
  transition: background 0.15s;
  position: relative;
}
.history-item:hover { background: rgba(255,255,255,0.5); }
.history-item.active-session { background: rgba(255,255,255,0.6); }
.history-item-icon { font-size: 16px; flex-shrink: 0; width: 24px; text-align: center; }
.history-item-body { flex: 1; min-width: 0; }
.history-item-title {
  font-size: 13px; font-weight: 500; color: #1e293b;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.history-item-meta { font-size: 10px; color: #94a3b8; margin-top: 2px; }

/* ⋯ 按钮 */
.history-menu-wrapper { position: relative; flex-shrink: 0; }
.history-menu-btn {
  width: 28px; height: 28px; border-radius: 50%; border: none; background: transparent;
  font-size: 16px; font-weight: bold; color: #94a3b8; cursor: pointer;
  display: flex; align-items: center; justify-content: center; letter-spacing: 1px;
  transition: background 0.15s;
}
.history-menu-btn:hover { background: rgba(0,0,0,0.06); color: #475569; }

/* 向右展开的删除弹窗 */
.history-popup {
  position: absolute; left: 34px; top: 50%; transform: translateY(-50%);
  background: rgba(255,255,255,0.96); border-radius: 10px;
  box-shadow: 0 4px 20px rgba(0,0,0,0.12);
  border: 1px solid rgba(0,0,0,0.06); z-index: 300;
  white-space: nowrap; overflow: hidden; animation: popIn 0.12s ease;
  padding: 8px; display: flex; flex-direction: column; gap: 6px;
}
@keyframes popIn { from { opacity: 0; transform: translateY(-50%) scale(0.92); } to { opacity: 1; transform: translateY(-50%) scale(1); } }
.popup-input {
  width: 160px; padding: 6px 10px; border-radius: 6px; border: 1px solid rgba(0,0,0,0.12);
  background: rgba(255,255,255,0.6); font-size: 12px; outline: none;
}
.popup-btns { display: flex; gap: 4px; }
.popup-item {
  padding: 5px 8px; border: none; background: rgba(0,0,0,0.04); border-radius: 6px;
  font-size: 11px; cursor: pointer; color: #475569; white-space: nowrap;
}
.popup-item:hover { background: rgba(0,0,0,0.08); }
.popup-item.danger { color: #dc2626; }
.popup-item.danger:hover { background: rgba(220,38,38,0.1); }

/* 居中会话编辑弹窗 */
.session-edit-box {
  width: 320px; padding: 24px; border-radius: 16px;
  background: rgba(255,255,255,0.94); border: 1px solid rgba(255,255,255,0.6);
  box-shadow: 0 20px 50px rgba(0,0,0,0.15);
  backdrop-filter: blur(10px);
}
.session-edit-box h4 { margin: 0 0 4px; font-size: 16px; color: #1e293b; }
.session-edit-btns { display: flex; gap: 8px; }
.session-edit-btns .popup-item { flex: 1; text-align: center; padding: 8px 12px; font-size: 13px; }

.history-empty { padding: 12px 20px; text-align: center; }

/* ===== 主内容 ===== */
.main-content { flex: 1; margin-left: 64px; width: calc(100vw - 64px); display: flex; flex-direction: column; height: 100vh; position: relative; }

/* ===== 头部 ===== */
.main-header { height: 60px; display: flex; align-items: center; justify-content: flex-end; padding: 0 24px; gap: 12px; flex-shrink: 0; }
.offline-badge { font-size: 11px; font-weight: bold; color: #ea580c; background: rgba(234,88,12,0.1); padding: 4px 12px; border-radius: 12px; }
.login-btn { padding: 8px 20px; background: #1e3a8a; color: white; border: none; border-radius: 8px; cursor: pointer; font-weight: 600; font-size: 13px; }
.login-btn:hover { background: #1e40af; }
.user-menu-wrapper { position: relative; }
.avatar-circle.solo {
  width: 40px; height: 40px; border-radius: 50%; background: rgba(255,255,255,0.6); border: 2px solid;
  display: flex; align-items: center; justify-content: center;
  font-weight: bold; font-size: 18px; color: var(--theme-color); cursor: pointer; transition: transform 0.15s;
}
.avatar-circle.solo:hover { transform: scale(1.08); }
.user-dropdown {
  position: absolute; top: 48px; right: 0; width: 190px;
  background: rgba(255,255,255,0.95); border-radius: 12px;
  box-shadow: 0 8px 30px rgba(0,0,0,0.12); border: 1px solid rgba(0,0,0,0.06); z-index: 200;
  backdrop-filter: blur(12px); overflow: hidden;
}
.dropdown-header { display: flex; justify-content: space-between; align-items: center; padding: 12px 14px 8px; }
.dropdown-nickname { font-size: 14px; font-weight: 700; color: #1e293b; }
.role-tag { font-size: 10px; background: rgba(0,0,0,0.06); padding: 2px 8px; border-radius: 8px; color: #64748b; }
.dropdown-item { width: 100%; padding: 10px 14px; border: none; background: transparent; display: flex; align-items: center; gap: 8px; font-size: 13px; cursor: pointer; color: #475569; text-align: left; }
.dropdown-item:hover { background: rgba(0,0,0,0.04); }
.dd-icon { font-size: 14px; width: 20px; text-align: center; }
.logout-item { color: #dc2626; }
hr { margin: 0; border: none; border-top: 1px solid rgba(0,0,0,0.06); }

/* ===== 内容体 ===== */
.content-body { flex: 1; display: flex; align-items: center; justify-content: center; padding: 20px; width: 100%; box-sizing: border-box; overflow: hidden; }

/* ===== 聊天容器 ===== */
.chat-container { width: 100%; max-width: 800px; height: 100%; display: flex; flex-direction: column; background: rgba(255,255,255,0.35); border-radius: 20px; backdrop-filter: blur(16px); -webkit-backdrop-filter: blur(16px); border: 1px solid rgba(255,255,255,0.4); overflow: hidden; }
.chat-toolbar { display: flex; justify-content: space-between; align-items: center; padding: 12px 20px; border-bottom: 1px solid rgba(0,0,0,0.05); flex-shrink: 0; }
.chat-session-badge { font-size: 12px; font-weight: 600; color: #475569; background: rgba(0,0,0,0.04); padding: 4px 10px; border-radius: 10px; }
.chat-session-badge.muted { color: #94a3b8; }
.chat-clear-btn { font-size: 11px; padding: 4px 12px; background: transparent; border: 1px solid rgba(0,0,0,0.1); border-radius: 8px; cursor: pointer; color: #94a3b8; }
.chat-clear-btn:hover { color: #dc2626; border-color: rgba(220,38,38,0.3); }
.chat-messages { flex: 1; overflow-y: auto; padding: 16px 20px; display: flex; flex-direction: column; gap: 12px; }
.chat-messages::-webkit-scrollbar { width: 4px; }
.chat-messages::-webkit-scrollbar-thumb { background: rgba(0,0,0,0.1); border-radius: 2px; }
.chat-empty { text-align: center; padding: 40px 20px; color: #94a3b8; margin: auto; }
.chat-empty-icon { font-size: 48px; margin-bottom: 12px; }
.chat-empty p { margin: 4px 0; font-size: 14px; }

.chat-bubble { display: flex; gap: 10px; max-width: 85%; animation: fadeIn 0.2s ease; }
.chat-bubble.user { align-self: flex-end; flex-direction: row-reverse; }
.chat-bubble.assistant { align-self: flex-start; }
.chat-bubble.error { align-self: center; max-width: 90%; }
.bubble-avatar { width: 32px; height: 32px; border-radius: 50%; flex-shrink: 0; display: flex; align-items: center; justify-content: center; font-size: 13px; font-weight: bold; }
.chat-bubble.user .bubble-avatar { background: var(--theme-color, #1e3a8a); color: #fff; }
.chat-bubble.assistant .bubble-avatar { background: #e2e8f0; color: #475569; }
.chat-bubble.error .bubble-avatar { background: rgba(220,38,38,0.15); color: #dc2626; }
.bubble-body { display: flex; flex-direction: column; gap: 4px; }
.bubble-text { padding: 10px 16px; border-radius: 16px; font-size: 14px; line-height: 1.6; white-space: pre-wrap; word-break: break-word; }
.chat-bubble.user .bubble-text { background: var(--theme-color, #1e3a8a); color: #fff; border-bottom-right-radius: 4px; }
.chat-bubble.assistant .bubble-text { background: rgba(255,255,255,0.8); color: #1e293b; border-bottom-left-radius: 4px; }
.chat-bubble.error .bubble-text { background: rgba(220,38,38,0.08); color: #dc2626; font-size: 13px; border-radius: 10px; }
.bubble-meta { display: flex; flex-wrap: wrap; gap: 4px; padding-left: 4px; }
.meta-tag { font-size: 10px; padding: 2px 8px; border-radius: 6px; background: rgba(0,0,0,0.04); color: #94a3b8; }
.typing-indicator { display: flex; gap: 4px; padding: 12px 16px; }
.typing-indicator span { width: 8px; height: 8px; border-radius: 50%; background: #94a3b8; animation: typing 1.4s infinite ease-in-out both; }
.typing-indicator span:nth-child(2) { animation-delay: 0.16s; }
.typing-indicator span:nth-child(3) { animation-delay: 0.32s; }
@keyframes typing { 0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; } 40% { transform: scale(1); opacity: 1; } }
@keyframes fadeIn { from { opacity: 0; transform: translateY(4px); } to { opacity: 1; transform: translateY(0); } }

/* ===== 设置 ===== */
.settings-panel { width: 90%; max-width: 600px; padding: 28px; border-radius: 20px; background: rgba(255,255,255,0.5); backdrop-filter: blur(20px); border: 1px solid rgba(255,255,255,0.5); }
.styled-input { padding: 8px 12px; border-radius: 8px; border: 1px solid rgba(0,0,0,0.1); background: rgba(255,255,255,0.6); outline: none; font-size: 13px; }
.small-btn { padding: 8px 16px; background: #1e3a8a; color: white; border: none; border-radius: 8px; cursor: pointer; font-weight: 600; font-size: 12px; white-space: nowrap; }
.stats-row { display: flex; gap: 16px; flex-wrap: wrap; font-size: 12px; color: #64748b; margin: 12px 0; }
.btn-row { display: flex; gap: 8px; flex-wrap: wrap; }
.action-btn-secondary { padding: 8px 14px; background: rgba(255,255,255,0.7); border: 1px solid rgba(0,0,0,0.1); border-radius: 8px; cursor: pointer; font-size: 12px; }
.action-btn-secondary:hover { background: white; }
.action-btn-danger { padding: 8px 14px; background: rgba(220,38,38,0.08); border: 1px solid rgba(220,38,38,0.2); border-radius: 8px; cursor: pointer; font-size: 12px; color: #dc2626; }
.action-btn-danger:hover { background: rgba(220,38,38,0.15); }

/* ===== 弹窗复用 ===== */
.login-overlay { position: fixed; top: 0; left: 0; width: 100vw; height: 100vh; background: rgba(15,23,42,0.2); display: flex; align-items: center; justify-content: center; z-index: 400; backdrop-filter: blur(8px); -webkit-backdrop-filter: blur(8px); }
.login-box { width: 400px; padding: 28px; border-radius: 20px; background: rgba(255,255,255,0.92); border: 1px solid rgba(255,255,255,0.6); box-shadow: 0 20px 50px rgba(0,0,0,0.12); }
.login-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.login-header h3 { margin: 0; font-size: 20px; color: #1e293b; }
.close-btn { background: transparent; border: none; font-size: 20px; color: #94a3b8; cursor: pointer; }
.form-body { display: flex; flex-direction: column; gap: 14px; }
.form-item { display: flex; flex-direction: column; gap: 4px; }
.form-item label { font-size: 13px; font-weight: 600; color: #475569; }
.submit-btn { margin-top: 4px; padding: 12px; background: #1e3a8a; color: white; border: none; border-radius: 10px; cursor: pointer; font-weight: bold; font-size: 15px; }

/* ===== 底部输入 ===== */
.main-footer { padding: 24px 40px; display: flex; justify-content: center; width: 100%; box-sizing: border-box; flex-shrink: 0; }
.input-container-glass { display: flex; align-items: center; width: 100%; max-width: 800px; padding: 8px 16px; border-radius: 30px; background: rgba(255,255,255,0.5); backdrop-filter: blur(12px); -webkit-backdrop-filter: blur(12px); border: 1px solid rgba(255,255,255,0.4); box-shadow: 0 8px 32px rgba(0,0,0,0.04); }
.input-container-glass input { flex: 1; border: none; background: transparent; padding: 8px 12px; font-size: 15px; outline: none; }
.input-container-glass input:disabled { opacity: 0.5; }
.action-btn { background: transparent; border: none; font-size: 20px; cursor: pointer; padding: 4px 8px; }
.action-btn:disabled { opacity: 0.3; cursor: not-allowed; }
.send-btn { font-size: 18px; font-weight: bold; }
.overlay { position: absolute; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.01); z-index: 50; }
</style>
