import { ref, computed } from 'vue'
import { login as apiLogin, register as apiRegister, getMe, syncData } from '../api'
import { hasLocalData, getAllLocalData, clearAll } from './localStorage'
import type { UserInfo, LoginRequest, RegisterRequest, SyncData } from '../api'

// ========== 响应式状态 ==========

export const isLoggedIn = ref(false)
export const user = ref<UserInfo | null>(null)
export const token = ref<string | null>(null)
export const isOffline = ref(!navigator.onLine)

const username = computed(() => user.value?.username || null)
const nickname = computed(() => user.value?.nickname || user.value?.username || '用户')

// ========== 网络监听 ==========

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

// ========== 认证函数 ==========

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

  // 检查是否有本地数据需要导入
  if (hasLocalData()) {
    // 返回标志让调用方决定是否弹窗
  }

  return u
}

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

export function logout() {
  token.value = null
  user.value = null
  isLoggedIn.value = false
  localStorage.removeItem('myllm_token')
  localStorage.removeItem('myllm_user')
}

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
    // Token 无效，清除
    logout()
    return false
  }
}

export async function syncLocalData(): Promise<boolean> {
  if (!isLoggedIn.value || !hasLocalData()) return false

  try {
    const localData = getAllLocalData()
    const syncPayload: SyncData = {
      models: localData.models,
      memories: localData.memories,
      rags: localData.rags
    }

    await syncData(syncPayload)
    clearAll()
    console.log('[Auth] 本地数据已同步到服务器')
    return true
  } catch (e) {
    console.error('[Auth] 同步失败:', e)
    return false
  }
}

// ========== 工具函数 ==========

export function useAuth() {
  return {
    isLoggedIn,
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
