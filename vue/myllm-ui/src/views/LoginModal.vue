<script setup lang="ts">
import { ref } from 'vue'
import { login, register, syncLocalData } from '../services/auth'
import { hasLocalData } from '../services/localStorage'

const emit = defineEmits(['close', 'login-success'])

const tab = ref<'login' | 'register'>('login')
const loading = ref(false)
const error = ref('')
const syncPrompt = ref(false)

// Login
const loginUsername = ref('')
const loginPassword = ref('')

// Register
const regUsername = ref('')
const regPassword = ref('')
const regPasswordConfirm = ref('')
const regEmail = ref('')
const regNickname = ref('')

const doLogin = async () => {
  error.value = ''
  if (!loginUsername.value.trim() || !loginPassword.value.trim()) {
    error.value = '请填写用户名和密码'
    return
  }
  loading.value = true
  try {
    await login({ username: loginUsername.value.trim(), password: loginPassword.value })
    emit('login-success')
    if (hasLocalData()) {
      syncPrompt.value = true
    } else {
      emit('close')
    }
  } catch (e: any) {
    error.value = e.response?.data?.error || '登录失败，请重试'
  } finally {
    loading.value = false
  }
}

const doRegister = async () => {
  error.value = ''
  if (!regUsername.value.trim()) {
    error.value = '请填写用户名'
    return
  }
  if (regPassword.value.length < 6) {
    error.value = '密码至少需要6个字符'
    return
  }
  if (regPassword.value !== regPasswordConfirm.value) {
    error.value = '两次输入的密码不一致'
    return
  }
  loading.value = true
  try {
    await register({
      username: regUsername.value.trim(),
      password: regPassword.value,
      email: regEmail.value.trim() || undefined,
      nickname: regNickname.value.trim() || undefined
    })
    emit('login-success')
    emit('close')
  } catch (e: any) {
    error.value = e.response?.data?.error || '注册失败，请重试'
  } finally {
    loading.value = false
  }
}

const doSync = async () => {
  loading.value = true
  try {
    await syncLocalData()
    syncPrompt.value = false
    emit('close')
  } catch (e) {
    error.value = '同步失败，请重试'
  } finally {
    loading.value = false
  }
}

const skipSync = () => {
  syncPrompt.value = false
  emit('close')
}
</script>

<template>
  <Teleport to="body">
    <div class="login-overlay" @click.self="emit('close')">
      <!-- 登录/注册 -->
      <div v-if="!syncPrompt" class="login-box">
        <div class="login-header">
          <h3>{{ tab === 'login' ? '登录' : '注册' }}</h3>
          <button class="close-btn" @click="emit('close')">✕</button>
        </div>

        <div class="tab-bar">
          <button :class="['tab-btn', { active: tab === 'login' }]" @click="tab = 'login'">登录</button>
          <button :class="['tab-btn', { active: tab === 'register' }]" @click="tab = 'register'">注册</button>
        </div>

        <div v-if="error" class="error-msg">{{ error }}</div>

        <!-- 登录表单 -->
        <div v-if="tab === 'login'" class="form-body">
          <div class="form-item">
            <label>用户名</label>
            <input v-model="loginUsername" type="text" placeholder="请输入用户名" class="styled-input"
              @keyup.enter="doLogin" />
          </div>
          <div class="form-item">
            <label>密码</label>
            <input v-model="loginPassword" type="password" placeholder="请输入密码" class="styled-input"
              @keyup.enter="doLogin" />
          </div>
          <button class="submit-btn" :disabled="loading" @click="doLogin">
            {{ loading ? '登录中...' : '登 录' }}
          </button>
        </div>

        <!-- 注册表单 -->
        <div v-if="tab === 'register'" class="form-body">
          <div class="form-item">
            <label>用户名 <span class="required">*</span></label>
            <input v-model="regUsername" type="text" placeholder="请输入用户名" class="styled-input" />
          </div>
          <div class="form-item">
            <label>昵称</label>
            <input v-model="regNickname" type="text" placeholder="取个名字吧" class="styled-input" />
          </div>
          <div class="form-item">
            <label>邮箱</label>
            <input v-model="regEmail" type="email" placeholder="可选" class="styled-input" />
          </div>
          <div class="form-item">
            <label>密码 <span class="required">*</span></label>
            <input v-model="regPassword" type="password" placeholder="至少6个字符" class="styled-input" />
          </div>
          <div class="form-item">
            <label>确认密码 <span class="required">*</span></label>
            <input v-model="regPasswordConfirm" type="password" placeholder="再次输入密码" class="styled-input" />
          </div>
          <button class="submit-btn" :disabled="loading" @click="doRegister">
            {{ loading ? '注册中...' : '注 册' }}
          </button>
        </div>
      </div>

      <!-- 同步提示 -->
      <div v-else class="login-box">
        <div class="login-header">
          <h3>📦 导入本地数据</h3>
        </div>
        <p style="color: #475569; font-size: 14px; line-height: 1.6; margin: 12px 0;">
          检测到浏览器中有本地存储的数据。是否导入到服务器账户中？
        </p>
        <div class="sync-actions">
          <button class="submit-btn" :disabled="loading" @click="doSync">
            {{ loading ? '导入中...' : '导入到服务器' }}
          </button>
          <button class="skip-btn" @click="skipSync">跳过，保留在本地</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.login-overlay {
  position: fixed; top: 0; left: 0; width: 100vw; height: 100vh;
  background: rgba(15, 23, 42, 0.2); display: flex;
  align-items: center; justify-content: center; z-index: 400;
  backdrop-filter: blur(8px); -webkit-backdrop-filter: blur(8px);
}
.login-box {
  width: 400px; padding: 28px; border-radius: 20px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(255, 255, 255, 0.6);
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.12);
}
.login-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.login-header h3 { margin: 0; font-size: 20px; color: #1e293b; }
.close-btn { background: transparent; border: none; font-size: 20px; color: #94a3b8; cursor: pointer; }
.tab-bar { display: flex; gap: 0; margin: 16px 0; border-radius: 10px; overflow: hidden; border: 1px solid rgba(0,0,0,0.08); }
.tab-btn {
  flex: 1; padding: 10px; border: none; background: rgba(0,0,0,0.03); cursor: pointer;
  font-size: 14px; font-weight: 600; color: #64748b; transition: all 0.2s;
}
.tab-btn.active { background: white; color: #1e3a8a; }
.error-msg {
  background: rgba(220, 38, 38, 0.08); color: #dc2626;
  padding: 10px 14px; border-radius: 8px; font-size: 13px; margin-bottom: 8px;
}
.form-body { display: flex; flex-direction: column; gap: 14px; }
.form-item { display: flex; flex-direction: column; gap: 4px; }
.form-item label { font-size: 13px; font-weight: 600; color: #475569; }
.form-item .required { color: #dc2626; }
.styled-input {
  padding: 10px 12px; border-radius: 8px; border: 1px solid rgba(0,0,0,0.12);
  background: rgba(255,255,255,0.65); outline: none; font-size: 14px; box-sizing: border-box;
}
.submit-btn {
  margin-top: 4px; padding: 12px; background: #1e3a8a; color: white;
  border: none; border-radius: 10px; cursor: pointer; font-weight: bold; font-size: 15px;
}
.submit-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.sync-actions { display: flex; flex-direction: column; gap: 10px; margin-top: 16px; }
.skip-btn {
  padding: 10px; background: transparent; border: 1px solid rgba(0,0,0,0.1);
  border-radius: 10px; cursor: pointer; font-size: 14px; color: #64748b;
}
</style>
