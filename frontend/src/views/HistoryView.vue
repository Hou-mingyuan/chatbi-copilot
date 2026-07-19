<script setup>
import { ref, onMounted, watch, defineAsyncComponent } from 'vue'
import { storeToRefs } from 'pinia'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '@/api'
import { useAppStore } from '@/stores/app'

const ResultPanel = defineAsyncComponent(() => import('@/components/ResultPanel.vue'))

const store = useAppStore()
const { currentDatasourceId } = storeToRefs(store)

const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)

const runDialog = ref(false)
const runResult = ref(null)
const running = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await api.listHistory({
      datasourceId: currentDatasourceId.value || undefined,
      page: page.value,
      size: size.value
    })
    rows.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

async function rerun(row) {
  runDialog.value = true
  running.value = true
  runResult.value = null
  try {
    runResult.value = await api.run({ datasourceId: row.datasourceId, sql: row.generatedSql })
  } catch (e) {
    runDialog.value = false
  } finally {
    running.value = false
  }
}

async function copySql(row) {
  await navigator.clipboard.writeText(row.generatedSql || '')
  ElMessage.success('SQL 已复制')
}

async function remove(row) {
  await api.deleteHistory(row.id)
  ElMessage.success('已删除')
  await load()
}

async function clearAll() {
  try {
    await ElMessageBox.confirm('确定清空当前数据源的历史记录？', '确认', { type: 'warning' })
    await api.clearHistory(currentDatasourceId.value || undefined)
    ElMessage.success('已清空')
    await load()
  } catch (e) {
    /* cancelled */
  }
}

watch([currentDatasourceId], () => {
  page.value = 1
  load()
})
onMounted(load)
</script>

<template>
  <div class="page-card">
    <div class="toolbar">
      <div>
        <h2 class="page-title">查询历史</h2>
        <p class="page-subtitle">记录每次提问、生成的 SQL、耗时与结果行数</p>
      </div>
      <span class="spacer" />
      <el-button :icon="'Delete'" @click="clearAll">清空</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="createdAt" label="时间" width="170" />
      <el-table-column prop="question" label="问题" min-width="200" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.success === 1 ? 'success' : 'danger'" size="small">
            {{ row.success === 1 ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="rowCount" label="行数" width="80" />
      <el-table-column prop="elapsedMs" label="耗时(ms)" width="100" />
      <el-table-column prop="generatedSql" label="SQL" min-width="220" show-overflow-tooltip />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button size="small" text type="primary" :disabled="!row.generatedSql" @click="rerun(row)">
            重跑
          </el-button>
          <el-button size="small" text @click="copySql(row)">复制</el-button>
          <el-button size="small" text type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div style="display: flex; justify-content: flex-end; margin-top: 14px">
      <el-pagination
        layout="total, prev, pager, next"
        :total="total"
        :page-size="size"
        :current-page="page"
        @current-change="(p) => { page = p; load() }"
      />
    </div>

    <el-dialog v-model="runDialog" title="重跑结果" width="70%" top="6vh">
      <div v-loading="running" style="min-height: 120px">
        <ResultPanel v-if="runResult" :result="runResult" />
      </div>
    </el-dialog>
  </div>
</template>
