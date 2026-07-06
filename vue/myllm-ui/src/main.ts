/**
 * MyLLM 前端入口文件
 * -----------------
 * 1. 创建 Vue 3 应用实例
 * 2. 注册 vue-router（所有页面统一走 Layout.vue）
 * 3. 挂载到 #app 节点
 */

// import './assets/main.css'  // 全局样式文件（暂未启用）

import { createApp } from 'vue'
import App from './App.vue'         // 根组件，仅包含 <RouterView />
import router from './router'       // 路由配置（单路径 "/" → Layout）

const app = createApp(App)

app.use(router)  // 安装路由插件

app.mount('#app')  // 挂载到 public/index.html 中的 <div id="app">
