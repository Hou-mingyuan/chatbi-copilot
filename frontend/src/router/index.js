import { createRouter, createWebHistory } from 'vue-router'
import { useAppStore } from '@/stores/app'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/layouts/WorkspaceLayout.vue'),
    children: [
      { path: '', redirect: '/chat' },
      {
        path: 'chat',
        name: 'chat',
        component: () => import('@/views/ChatView.vue'),
        meta: { title: '智能问数', icon: 'ChatDotRound', nav: true }
      },
      {
        path: 'history',
        name: 'history',
        component: () => import('@/views/HistoryView.vue'),
        meta: { title: '查询历史', icon: 'Clock', nav: true }
      },
      {
        path: 'favorites',
        name: 'favorites',
        component: () => import('@/views/FavoriteView.vue'),
        meta: { title: '收藏', icon: 'Star', nav: true }
      },
      {
        path: 'semantic',
        name: 'semantic',
        component: () => import('@/views/SemanticView.vue'),
        meta: { title: '语义层', icon: 'Collection', nav: true }
      },
      {
        path: 'datasources',
        name: 'datasources',
        component: () => import('@/views/DataSourceView.vue'),
        meta: { title: '数据源', icon: 'Coin', nav: true }
      },
      {
        path: 'admin',
        name: 'admin',
        component: () => import('@/views/AdminView.vue'),
        meta: { title: '权限与审计', icon: 'Lock', nav: true, roles: ['ADMIN'] }
      }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

export async function authorizeNavigation(to, store) {
  if (to.meta.public && !store.authReady) return true
  if (!store.authReady) {
    try {
      await store.restoreSession()
      await store.bootstrapWorkspace()
    } catch {
      store.clearAuth()
    }
  }
  if (to.meta.public) return store.authenticated ? '/chat' : true
  if (!store.authenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.roles && !to.meta.roles.some((role) => store.user.roles?.includes(role))) {
    return '/chat'
  }
  return true
}

router.beforeEach((to) => authorizeNavigation(to, useAppStore()))

export default router
