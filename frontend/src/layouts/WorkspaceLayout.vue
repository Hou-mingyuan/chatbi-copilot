<script setup>
import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowDown,
  ChatDotRound,
  Clock,
  Coin,
  Collection,
  Lock,
  Star
} from '@element-plus/icons-vue'
import 'element-plus/theme-chalk/el-message.css'
import 'element-plus/theme-chalk/el-message-box.css'
import { useAppStore } from '@/stores/app'

const store = useAppStore()
const router = useRouter()
const { user, datasources, currentDatasourceId, llm, online } = storeToRefs(store)
const navIcons = { ChatDotRound, Clock, Star, Collection, Coin, Lock }

const menus = computed(() =>
  router.getRoutes().filter((item) => {
    if (!item.meta?.nav) return false
    return !item.meta.roles || item.meta.roles.some((role) => user.value?.roles?.includes(role))
  })
)
const modelLabel = computed(() => {
  if (llm.value?.provider === 'mock') return 'Mock 回归'
  if (!llm.value?.configured) return '模型未配置'
  return llm.value.model || llm.value.provider || '模型已连接'
})

async function logout() {
  try {
    await store.logout()
    await router.replace('/login')
  } catch (error) {
    ElMessage.error(error.message || '退出失败')
  }
}
</script>

<template>
  <div class="app-shell">
    <aside class="side-rail" aria-label="主导航">
      <router-link class="brand" to="/chat" aria-label="ChatBI Copilot 首页">
        <span class="brand-mark">BI</span>
        <span class="brand-copy">
          <strong>ChatBI</strong>
          <small>Copilot</small>
        </span>
      </router-link>

      <nav class="primary-nav">
        <router-link v-for="item in menus" :key="item.path" :to="item.path" class="nav-item">
          <el-icon><component :is="navIcons[item.meta.icon]" /></el-icon>
          <span>{{ item.meta.title }}</span>
        </router-link>
      </nav>

      <div class="side-foot">
        <div class="model-state" :class="{ warning: !llm.configured || llm.provider === 'mock' }">
          <span class="state-dot" />
          <span class="model-text" :title="modelLabel">{{ modelLabel }}</span>
        </div>
      </div>
    </aside>

    <section class="workspace" :class="{ 'workspace--offline': !online }">
      <div v-if="!online" class="offline-banner" role="status">
        网络已断开。页面数据可能不是最新状态，恢复连接后可重试。
      </div>

      <header class="topbar">
        <div class="topbar-context">
          <span class="eyebrow">当前数据源</span>
          <el-select
            :model-value="currentDatasourceId"
            class="datasource-select"
            placeholder="选择可访问的数据源"
            @change="store.setCurrent"
          >
            <el-option
              v-for="item in datasources"
              :key="item.id"
              :value="item.id"
              :label="`${item.name} · ${item.dbType}`"
            />
          </el-select>
        </div>

        <el-dropdown trigger="click">
          <button class="user-menu" type="button" aria-label="账户菜单">
            <span class="user-avatar">{{ user?.displayName?.slice(0, 1) || user?.username?.slice(0, 1) }}</span>
            <span class="user-copy">
              <strong>{{ user?.displayName || user?.username }}</strong>
              <small>{{ user?.roles?.join(' · ') }}</small>
            </span>
            <el-icon><ArrowDown /></el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </header>

      <main class="app-main">
        <router-view v-slot="{ Component }">
          <keep-alive include="ChatView">
            <component :is="Component" />
          </keep-alive>
        </router-view>
      </main>
    </section>

    <nav class="mobile-nav" aria-label="移动端主导航">
      <router-link v-for="item in menus" :key="item.path" :to="item.path" class="mobile-nav-item">
        <el-icon><component :is="navIcons[item.meta.icon]" /></el-icon>
        <span>{{ item.meta.title }}</span>
      </router-link>
    </nav>
  </div>
</template>
