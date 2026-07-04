import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/chat' },
  {
    path: '/chat',
    name: 'chat',
    component: () => import('@/views/ChatView.vue'),
    meta: { title: '智能问数', icon: 'ChatDotRound' }
  },
  {
    path: '/datasources',
    name: 'datasources',
    component: () => import('@/views/DataSourceView.vue'),
    meta: { title: '数据源', icon: 'Coin' }
  },
  {
    path: '/semantic',
    name: 'semantic',
    component: () => import('@/views/SemanticView.vue'),
    meta: { title: '语义层', icon: 'Collection' }
  },
  {
    path: '/history',
    name: 'history',
    component: () => import('@/views/HistoryView.vue'),
    meta: { title: '查询历史', icon: 'Clock' }
  },
  {
    path: '/favorites',
    name: 'favorites',
    component: () => import('@/views/FavoriteView.vue'),
    meta: { title: '收藏', icon: 'Star' }
  }
]

export default createRouter({
  history: createWebHistory(),
  routes
})
