/**
 * Vue Router 路由配置
 * ---------------
 * 整个应用只有一个路由入口：
 *   "/" → Layout.vue（主布局组件）
 *
 * 子页面切换不通过路由，而由 Layout.vue 内部的 currentView 状态控制。
 * 这样做的好处：切换页面不需要重新创建 Layout，侧边栏和主题状态不丢失。
 */
import { createRouter, createWebHistory } from 'vue-router'
import Layout from '../views/Layout.vue'

const router = createRouter({
  // 使用 HTML5 History 模式（无 # 号的干净 URL）
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'root',
      component: Layout  // 全局唯一的顶层路由，挂载带侧边栏+毛玻璃的主视图
    }
  ]
})

export default router
