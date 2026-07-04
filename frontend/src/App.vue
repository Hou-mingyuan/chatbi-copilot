<script setup>
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useAppStore } from '@/stores/app'

const store = useAppStore()
const { datasources, currentDatasourceId, llm } = storeToRefs(store)
const route = useRoute()
const router = useRouter()

const menus = computed(() =>
  router.getRoutes().filter((r) => r.meta && r.meta.title)
)

const activeMenu = computed(() => route.path)

function onDatasourceChange(id) {
  store.setCurrent(id)
}

onMounted(async () => {
  await Promise.all([store.loadDatasources(), store.loadLlm()])
})
</script>

<template>
  <el-container style="height: 100vh">
    <el-header class="app-header">
      <div class="brand">
        <span style="font-size: 22px">📊</span>
        <div>
          ChatBI Copilot
          <small>· 自然语言问数与可视化</small>
        </div>
      </div>
      <div class="header-right">
        <el-select
          :model-value="currentDatasourceId"
          placeholder="选择数据源"
          size="default"
          style="width: 220px"
          @change="onDatasourceChange"
        >
          <el-option
            v-for="d in datasources"
            :key="d.id"
            :label="`${d.name} (${d.dbType})`"
            :value="d.id"
          />
          <template #empty>
            <div style="padding: 8px; color: #999">暂无数据源，请先在「数据源」中添加</div>
          </template>
        </el-select>

        <el-tooltip :content="llm.configured ? `模型: ${llm.model}` : '未配置 LLM，请设置环境变量'">
          <el-tag :type="llm.configured ? 'success' : 'danger'" effect="dark" round>
            {{ llm.configured ? `LLM: ${llm.provider}` : 'LLM 未配置' }}
          </el-tag>
        </el-tooltip>

        <el-link href="/api/swagger-ui.html" target="_blank" style="color: #fff">API</el-link>
      </div>
    </el-header>

    <el-container>
      <el-aside width="200px" class="app-aside">
        <el-menu :default-active="activeMenu" router>
          <el-menu-item v-for="m in menus" :key="m.path" :index="m.path">
            <el-icon><component :is="m.meta.icon" /></el-icon>
            <span>{{ m.meta.title }}</span>
          </el-menu-item>
        </el-menu>
      </el-aside>

      <el-main class="app-main">
        <router-view v-slot="{ Component }">
          <keep-alive include="ChatView">
            <component :is="Component" />
          </keep-alive>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>
