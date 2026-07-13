/**
 * MyLLM 用户认证服务（Vue Composable）
 * ---------------
 * 全局单例响应式状态，所有组件通过 useAuth() 共享。
 *
 * 核心变量：
 *   IsLogin  (= isLoggedIn) — 整个前端判断本地/服务器的开关
 *   isOffline                 — 浏览器是否断网
 *
 * 行为：
 *   - IsLogin=false → 所有数据 CRUD 走 localStorage（离线模式）
 *   - IsLogin=true  → 立即从后端拉取数据，同时自动上传本地数据到服务器
 *   - 网络恢复时自动推送未同步的本地数据
 */

import { ref, computed } from 'vue'
import { login as apiLogin, register as apiRegister, getMe, syncData } from '../api'
import { hasLocalData, getAllLocalData, clearAll } from './localStorage'
import type { UserInfo, LoginRequest, RegisterRequest, SyncData } from '../api'

// ==================== 全局响应式状态 ====================

/** IsLogin — 前端判断本地/服务器模式的总开关 */
export const isLoggedIn = ref(false)

/** 当前登录用户信息 */
export const user = ref<UserInfo | null>(null)

/** JWT token */
export const token = ref<string | null>(null)

/** 浏览器是否离线 */
export const isOffline = ref(!navigator.onLine)

const username = computed(() => user.value?.username || null)
const nickname = computed(() => user.value?.nickname || user.value?.username || '用户')

// ==================== 网络监听 ====================

window.addEventListener('online', () => {
  isOffline.value = false
  console.log('[Auth] 网络已恢复')
  if (isLoggedIn.value) {
    syncLocalData()
  }
})

window.addEventListener('offline', () => {
  isOffline.value = true
  console.log('[Auth] 网络已断开，切换到离线模式')
})

// ==================== 认证函数 ====================

/**
 * 登录
 *  1. POST /api/auth/login 获取 JWT
 *  2. 存入 localStorage + 内存
 *  3. 立刻自动同步本地数据到服务器（无需用户确认）
 *  4. 返回用户信息
 */
export async function login(data: LoginRequest): Promise<UserInfo> {
  const res = await apiLogin(data)
  const u = res.data

  token.value = u.token || null
  if (token.value) {
    localStorage.setItem('myllm_token', token.value)
  }
  user.value = u
  localStorage.setItem('myllm_user', JSON.stringify(u))
  isLoggedIn.value = true

  // 登录成功 → 静默自动上传本地数据（失败不阻断）

  return u
}

/**
 * 注册
 *  流程同 login，调 POST /api/auth/register
 */
export async function register(data: RegisterRequest): Promise<UserInfo> {
  const res = await apiRegister(data)
  const u = res.data

  token.value = u.token || null
  if (token.value) {
    localStorage.setItem('myllm_token', token.value)
  }
  user.value = u
  localStorage.setItem('myllm_user', JSON.stringify(u))
  isLoggedIn.value = true

  return u
}

/** 注销：清除 token + 用户信息，回到本地模式 */
export function logout() {
  token.value = null
  user.value = null
  isLoggedIn.value = false
  localStorage.removeItem('myllm_token')
  localStorage.removeItem('myllm_user')
}

/**
 * 页面刷新时恢复登录态
 *  从 localStorage 取 token → GET /api/auth/me 验证 → 有效则恢复
 */
export async function checkAuth(): Promise<boolean> {
  const storedToken = localStorage.getItem('myllm_token')
  if (!storedToken) {
    isLoggedIn.value = false
    return false
  }

  token.value = storedToken

  try {
    const res = await getMe()
    user.value = res.data
    isLoggedIn.value = true
    return true
  } catch (e) {
    logout()
    return false
  }
}

// ==================== 内部：自动同步（不暴露给 LoginModal 弹窗） ====================

async function doSyncLocalData(): Promise<boolean> {
  if (!isLoggedIn.value || !hasLocalData()) return false

  const localData = getAllLocalData()
  const syncPayload: SyncData = {
    models: localData.models,
    memories: localData.memories,
    rags: localData.rags
  }

  await syncData(syncPayload)
  clearAll()
  console.log('[Auth] 本地数据已自动同步到服务器')
  return true
}

/**
 * 手动同步（暴露给外部调用）
 * 网络恢复时调用，静默执行
 */
export async function syncLocalData(): Promise<boolean> {
  return doSyncLocalData()
}

// ==================== 导出组合式函数 ====================

export function useAuth() {
  return {
    isLoggedIn,    // IsLogin — 本地/服务器模式开关
    user,
    token,
    isOffline,
    username,
    nickname,
    login,
    register,
    logout,
    checkAuth,
    syncLocalData
  }
}
