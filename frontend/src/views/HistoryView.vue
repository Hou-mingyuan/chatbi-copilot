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
const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = 20
const loading = ref(false)
const loadError = ref('')
const detailDialog = ref(false)
const detailLoading = ref(false)
const detailResult = ref(null)
const detailRow = ref(null)
const runDialog = ref(false)
const runner = ref(null)

const statusMeta = {
  SUCCEEDED: ['成功', 'success'], PREVIEWED: ['预览', 'info'], CLARIFICATION: ['待澄清', 'warning'],
  NEEDS_CONFIRMATION: ['待确认', 'warning'], BLOCKED: ['已阻止', 'danger'], FAILED: ['失败', 'danger']
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const result = await api.listHistory({
      datasourceId: currentDatasourceId.value || undefined,
      page: page.value,
      size
    })
    rows.value = result.records || []
    total.value = result.total || 0
  } catch (error) {
    loadError.value = errorMessage(error)
  } finally {
    loading.value = false
  }
}

async function showDetail(row) {
  detailDialog.value = true
  detailLoading.value = true
  detailResult.value = null
  detailRow.value = row
  try {
    detailResult.value = await api.getHistoryResult(row.id)
  } catch (error) {
    ElMessage.error(errorMessage(error))
    detailDialog.value = false
  } finally {
    detailLoading.value = false
  }
}

function rerun(row) {
  runDialog.value = true
  runner.value?.run({
    datasourceId: row.datasourceId,
    sql: row.editedSql || row.generatedSql,
    sessionId: row.sessionId,
    question: row.question
  })
}

async function copySql(row) {
  try {
    await navigator.clipboard.writeText(row.editedSql || row.generatedSql || '')
    ElMessage.success('SQL 已复制')
  } catch {
    ElMessage.warning('复制失败，请打开详情手动复制')
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm('删除这条个人历史记录？', '确认删除', { type: 'warning' })
    await api.deleteHistory(row.id)
    ElMessage.success('历史记录已删除')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(errorMessage(error))
  }
}

async function clearAll() {
  if (!currentDatasourceId.value) {
    ElMessage.warning('请选择一个数据源后再清空')
    return
  }
  try {
    await ElMessageBox.confirm('清空当前数据源下属于你的全部历史记录？该操作不可撤销。', '确认清空', { type: 'warning' })
    await api.clearHistory(currentDatasourceId.value)
    ElMessage.success('当前数据源的个人历史已清空')
    page.value = 1
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(errorMessage(error))
  }
}

watch(currentDatasourceId, () => { page.value = 1; load() })
onMounted(load)
</script>

<template>
  <section class="page-card">
    <header class="toolbar">
      <div>
        <p class="eyebrow">可追溯记录</p>
        <h1 class="page-title">查询历史</h1>
        <p class="page-subtitle">仅展示当前用户可访问的数据；成功结果可从完整性校验后的快照恢复。</p>
      </div>
      <span class="spacer" />
      <el-button :disabled="!currentDatasourceId || !rows.length" @click="clearAll">清空当前数据源</el-button>
    </header>

    <el-alert v-if="loadError" type="error" :closable="false" :title="loadError"><template #default><el-button size="small" @click="load">重试</el-button></template></el-alert>
    <el-empty v-else-if="!loading && !rows.length" description="还没有查询历史" />

    <el-table v-else v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="createdAt" label="时间（UTC）" width="170" />
      <el-table-column prop="question" label="问题" min-width="220" show-overflow-tooltip />
      <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="(statusMeta[row.status] || ['未知','info'])[1]" size="small">{{ (statusMeta[row.status] || [row.status,'info'])[0] }}</el-tag></template></el-table-column>
      <el-table-column prop="rowCount" label="行数" width="80" />
      <el-table-column label="耗时" width="100"><template #default="{ row }">{{ row.elapsedMs || 0 }} ms</template></el-table-column>
      <el-table-column prop="riskLevel" label="风险" width="90"><template #default="{ row }">{{ row.riskLevel || '—' }}</template></el-table-column>
      <el-table-column prop="generatedSql" label="SQL" min-width="240" show-overflow-tooltip />
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <el-button size="small" text type="primary" @click="showDetail(row)">详情</el-button>
          <el-button size="small" text :disabled="!row.generatedSql" @click="rerun(row)">重跑</el-button>
          <el-button size="small" text :disabled="!row.generatedSql" @click="copySql(row)">复制</el-button>
          <el-button size="small" text type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-if="total > size"
      class="pagination"
      layout="total, prev, pager, next"
      :total="total"
      :page-size="size"
      :current-page="page"
      @current-change="(value) => { page = value; load() }"
    />

    <el-dialog v-model="detailDialog" title="历史详情" width="min(980px, 96vw)" top="4vh">
      <div v-loading="detailLoading" class="detail-body">
        <ResultPanel
          v-if="detailResult && (detailResult.rows?.length || detailRow?.status === 'SUCCEEDED')"
          :result="detailResult"
          :can-export="Boolean(currentDatasource?.canExport)"
        />
        <div v-else-if="detailResult" class="history-state">
          <strong>{{ (statusMeta[detailRow.status] || [detailRow.status])[0] }}</strong>
          <p v-if="detailResult.clarification">{{ detailResult.clarification }}</p>
          <p v-else-if="detailRow.errorMsg">{{ detailRow.errorMsg }}</p>
          <pre v-if="detailResult.sql">{{ detailResult.sql }}</pre>
        </div>
      </div>
    </el-dialog>

    <QueryJobDialog ref="runner" v-model="runDialog" title="安全重跑" @completed="load" />
  </section>
</template>

<style scoped>
.pagination { display: flex; justify-content: flex-end; margin-top: 14px; }
.detail-body { min-height: 160px; }
.history-state { padding: 16px; background: var(--surface-soft); border: 1px solid var(--line); border-radius: 8px; }
.history-state p { color: var(--ink-650); font-size: 13px; }
.history-state pre { overflow: auto; padding: 12px; color: #dbe8e5; background: #172522; border-radius: 7px; font: 11px/1.6 ui-monospace, monospace; white-space: pre-wrap; }
</style>
