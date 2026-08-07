<script setup>
import { onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, errorMessage } from '@/api'
import { useAppStore } from '@/stores/app'
import ResultPanel from '@/components/ResultPanel.vue'
import QueryJobDialog from '@/components/QueryJobDialog.vue'

const store = useAppStore()
const { currentDatasourceId, currentDatasource } = storeToRefs(store)
const list = ref([])
const loading = ref(false)
const loadError = ref('')
const detailDialog = ref(false)
const detailLoading = ref(false)
const detailResult = ref(null)
const runDialog = ref(false)
const runner = ref(null)

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    list.value = await api.listFavorites(currentDatasourceId.value || undefined)
  } catch (error) {
    loadError.value = errorMessage(error)
  } finally {
    loading.value = false
  }
}

async function view(row) {
  detailDialog.value = true
  detailLoading.value = true
  detailResult.value = null
  try {
    detailResult.value = await api.getHistoryResult(row.queryId)
  } catch (error) {
    detailDialog.value = false
    ElMessage.error(errorMessage(error))
  } finally {
    detailLoading.value = false
  }
}

function run(row) {
  runDialog.value = true
  runner.value?.run({
    datasourceId: row.datasourceId,
    sql: row.generatedSql,
    question: row.question
  })
}

async function copySql(row) {
  try {
    await navigator.clipboard.writeText(row.generatedSql || '')
    ElMessage.success('SQL 已复制')
  } catch {
    ElMessage.warning('复制失败，请打开详情手动复制')
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`删除收藏“${row.title}”？原查询历史不受影响。`, '确认删除', { type: 'warning' })
    await api.deleteFavorite(row.id)
    ElMessage.success('收藏已删除')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(errorMessage(error))
  }
}

watch(currentDatasourceId, load)
onMounted(load)
</script>

<template>
  <section class="page-card">
    <header class="toolbar">
      <div>
        <p class="eyebrow">个人工作集</p>
        <h1 class="page-title">收藏查询</h1>
        <p class="page-subtitle">收藏只绑定属于你的成功查询；打开时重新检查当前数据权限。</p>
      </div>
    </header>

    <el-alert v-if="loadError" type="error" :closable="false" :title="loadError"><template #default><el-button size="small" @click="load">重试</el-button></template></el-alert>
    <el-empty v-else-if="!loading && !list.length" description="还没有收藏；在成功查询的结果面板中添加" />

    <el-table v-else v-loading="loading" :data="list" border stripe>
      <el-table-column prop="title" label="名称" min-width="170" />
      <el-table-column prop="question" label="原问题" min-width="220" show-overflow-tooltip />
      <el-table-column prop="generatedSql" label="已校验 SQL" min-width="280" show-overflow-tooltip><template #default="{ row }"><span class="sql-inline">{{ row.generatedSql }}</span></template></el-table-column>
      <el-table-column prop="createdAt" label="收藏时间（UTC）" width="175" />
      <el-table-column label="操作" width="225" fixed="right">
        <template #default="{ row }">
          <el-button size="small" text type="primary" @click="view(row)">原结果</el-button>
          <el-button size="small" text @click="run(row)">重新执行</el-button>
          <el-button size="small" text @click="copySql(row)">复制</el-button>
          <el-button size="small" text type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="detailDialog" title="收藏的原始结果快照" width="min(980px, 96vw)" top="4vh">
      <div v-loading="detailLoading" class="detail-body">
        <ResultPanel
          v-if="detailResult"
          :result="detailResult"
          :can-export="Boolean(currentDatasource?.canExport)"
        />
      </div>
    </el-dialog>
    <QueryJobDialog ref="runner" v-model="runDialog" title="重新执行收藏查询" @completed="load" />
  </section>
</template>

<style scoped>
.sql-inline { font-family: ui-monospace, SFMono-Regular, Consolas, monospace; font-size: 11px; }
.detail-body { min-height: 160px; }
</style>
