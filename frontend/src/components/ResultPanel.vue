<script setup>
import { ref, computed, defineAsyncComponent } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '@/api'
import { downloadBlob } from '@/utils/download'

const ChartRenderer = defineAsyncComponent(() => import('./ChartRenderer.vue'))

const props = defineProps({
  result: { type: Object, required: true }
})

const emit = defineEmits(['drilldown'])

const hasRows = computed(() => (props.result.rows || []).length > 0)
const recommended = computed(() => props.result.chart || {})
const chartType = ref(
  recommended.value.type && recommended.value.type !== 'table' ? recommended.value.type : 'bar'
)
const activeTab = ref(
  recommended.value.type && recommended.value.type !== 'table' && hasRows.value ? 'chart' : 'table'
)
const sqlExpanded = ref(false)
const drillFilter = ref(null)

const displayRows = computed(() => {
  const rows = props.result.rows || []
  if (!drillFilter.value) return rows
  const { dimension, value } = drillFilter.value
  return rows.filter((r) => String(r[dimension]) === String(value))
})

const displayRowCount = computed(() =>
  drillFilter.value ? displayRows.value.length : props.result.rowCount
)

function onChartDrill(payload) {
  const dimension = payload.dimension || xField.value
  const value = payload.label ?? payload.value
  drillFilter.value = {
    dimension,
    value,
    dataIndex: payload.index ?? payload.dataIndex ?? null,
    label: String(value),
  }
  activeTab.value = 'table'
}

function clearDrill() {
  drillFilter.value = null
}

function confirmDrilldown() {
  if (!drillFilter.value) return
  emit('drilldown', {
    ...drillFilter.value,
    question: props.result.question,
  })
}

const chartTypeLabels = { bar: '柱状图', line: '折线图', pie: '饼图', table: '表格' }

const recommendedLabel = computed(() => {
  const t = recommended.value.type || 'table'
  return chartTypeLabels[t] || t
})

const sqlPreview = computed(() => {
  const sql = (props.result.sql || '').trim()
  if (!sql) return ''
  const lines = sql.split('\n')
  return lines.length <= 3 && sql.length <= 180 ? sql : lines.slice(0, 3).join('\n') + (lines.length > 3 ? '\n…' : '')
})

const previewRows = computed(() => (props.result.rows || []).slice(0, 4))

function formatCell(value, col) {
  if (value == null || value === '') return '—'
  if (col?.category === 'measure' && typeof value === 'number') {
    return value.toLocaleString('zh-CN', { maximumFractionDigits: 4 })
  }
  return value
}

function columnLabel(col) {
  if (col.category === 'measure') return `${col.name} · 指标`
  if (col.category === 'dimension') return `${col.name} · 维度`
  return col.name
}

const xField = computed(() => recommended.value.xField || firstDimension())
const yFields = computed(() =>
  recommended.value.yFields && recommended.value.yFields.length
    ? recommended.value.yFields
    : firstMeasures()
)

function firstDimension() {
  const c = (props.result.columns || []).find((x) => x.category !== 'measure')
  return c ? c.name : props.result.columns?.[0]?.name
}
function firstMeasures() {
  const ms = (props.result.columns || []).filter((x) => x.category === 'measure').map((x) => x.name)
  return ms.length ? ms : props.result.columns?.slice(1).map((x) => x.name) || []
}

async function copySql() {
  try {
    await navigator.clipboard.writeText(props.result.sql || '')
    ElMessage.success('SQL 已复制')
  } catch (e) {
    ElMessage.warning('复制失败，请手动选择')
  }
}

async function exportExcel() {
  try {
    const resp = await api.exportExcel({
      datasourceId: props.result.datasourceId,
      sql: props.result.sql
    })
    downloadBlob(resp, 'chatbi-export.xlsx')
    ElMessage.success('已导出 Excel')
  } catch (e) {
    /* handled by interceptor */
  }
}

async function saveFavorite() {
  try {
    const { value } = await ElMessageBox.prompt('给这个查询取个名字', '收藏查询', {
      confirmButtonText: '保存',
      cancelButtonText: '取消',
      inputValue: props.result.question || ''
    })
    await api.createFavorite({
      datasourceId: props.result.datasourceId,
      title: value,
      question: props.result.question,
      sql: props.result.sql
    })
    ElMessage.success('已收藏')
  } catch (e) {
    /* cancelled or handled */
  }
}
</script>

<template>
  <div class="result-panel">
    <div v-if="result.question" class="question-echo">
      <span class="label">问</span>
      <span class="text">{{ result.question }}</span>
    </div>

    <el-alert
      v-if="result.explanation"
      :title="result.explanation"
      type="info"
      :closable="false"
      show-icon
      class="explain"
    />

    <div class="sql-preview">
      <div class="sql-preview-head">
        <span class="sql-label">生成 SQL</span>
        <el-tag size="small" type="primary" effect="plain">{{ recommendedLabel }}</el-tag>
        <span class="spacer" />
        <el-button size="small" text :icon="'DocumentCopy'" @click="copySql">复制</el-button>
        <el-button size="small" text @click="sqlExpanded = !sqlExpanded">
          {{ sqlExpanded ? '收起' : '展开' }}
        </el-button>
      </div>
      <pre class="sql-block" :class="{ expanded: sqlExpanded }">{{ sqlExpanded ? result.sql : sqlPreview }}</pre>
    </div>

    <div class="meta">
      <el-tag size="small" type="success" effect="plain">{{ displayRowCount }} 行</el-tag>
      <el-tag v-if="drillFilter" size="small" type="warning" effect="plain" closable @close="clearDrill">
        钻取：{{ drillFilter.dimension }} = {{ drillFilter.value }}
      </el-tag>
      <el-tag size="small" type="info" effect="plain">{{ result.elapsedMs }} ms</el-tag>
      <el-tag v-if="result.truncated" size="small" type="warning" effect="plain">
        结果已截断
      </el-tag>
      <span class="spacer" />
      <el-button size="small" text :icon="'Star'" @click="saveFavorite">收藏</el-button>
      <el-button size="small" text :icon="'Download'" @click="exportExcel" :disabled="!hasRows">
        导出 Excel
      </el-button>
    </div>

    <div v-if="hasRows && previewRows.length" class="preview-cards">
      <div v-for="(row, idx) in previewRows" :key="idx" class="preview-card">
        <div v-for="col in result.columns?.slice(0, 3)" :key="col.name" class="preview-cell">
          <span class="k">{{ col.name }}</span>
          <span class="v">{{ row[col.name] }}</span>
        </div>
      </div>
      <span v-if="result.rowCount > previewRows.length" class="more-hint">
        还有 {{ result.rowCount - previewRows.length }} 行，见下方表格
      </span>
    </div>

    <div v-if="drillFilter" class="drill-banner">
      <span>已选 <strong>{{ drillFilter.label }}</strong> · 表格已筛选，可继续追问明细</span>
      <el-button size="small" type="primary" @click="confirmDrilldown">追问钻取</el-button>
      <el-button size="small" text @click="clearDrill">清除</el-button>
    </div>

    <el-tabs v-model="activeTab" class="result-tabs">
      <el-tab-pane label="图表" name="chart" :disabled="!hasRows">
        <div class="chart-toolbar">
          <el-radio-group v-model="chartType" size="small">
            <el-radio-button value="bar">柱状图</el-radio-button>
            <el-radio-button value="line">折线图</el-radio-button>
            <el-radio-button value="pie">饼图</el-radio-button>
          </el-radio-group>
          <span class="reason">{{ drillFilter ? '已钻取选中项，表格已同步筛选' : '点击图表元素可钻取查看明细' }}</span>
          <span v-if="!drillFilter && recommended.reason" class="reason muted">{{ recommended.reason }}</span>
        </div>
        <Suspense v-if="hasRows">
          <ChartRenderer
            :type="chartType"
            :x-field="xField"
            :y-fields="yFields"
            :rows="result.rows"
            :highlight-index="drillFilter?.dataIndex ?? null"
            @drill="onChartDrill"
          />
          <template #fallback>
            <div class="chart-fallback">加载图表…</div>
          </template>
        </Suspense>
      </el-tab-pane>

      <el-tab-pane label="表格" name="table">
        <div class="table-toolbar">
          <span class="table-summary">共 {{ displayRowCount }} 行 · {{ result.columns?.length || 0 }} 列</span>
          <span v-if="result.truncated" class="table-truncated">（已截断，完整结果请导出 Excel）</span>
        </div>
        <el-table
          :data="displayRows"
          border
          stripe
          height="380"
          size="small"
          class="result-data-table"
          :default-sort="{ prop: result.columns?.[0]?.name, order: 'ascending' }"
        >
          <el-table-column
            v-for="col in result.columns"
            :key="col.name"
            :prop="col.name"
            :label="columnLabel(col)"
            show-overflow-tooltip
            :min-width="col.category === 'measure' ? 100 : 120"
            sortable
          >
            <template #default="{ row }">
              <span :class="{ 'num-cell': col.category === 'measure' }">
                {{ formatCell(row[col.name], col) }}
              </span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="SQL" name="sql">
        <pre class="sql-block expanded">{{ result.sql }}</pre>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.result-panel {
  margin-top: 4px;
}
.question-echo {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  margin-bottom: 10px;
  font-size: 13px;
  color: #4b5563;
}
.question-echo .label {
  flex-shrink: 0;
  background: #eef2ff;
  color: var(--brand-1);
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
}
.question-echo .text {
  line-height: 1.5;
}
.explain {
  margin-bottom: 10px;
}
.sql-preview {
  margin-bottom: 10px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  overflow: hidden;
  background: #0f172a;
}
.sql-preview-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  background: #1e293b;
  border-bottom: 1px solid #334155;
}
.sql-label {
  color: #94a3b8;
  font-size: 12px;
  font-weight: 600;
}
.meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.spacer {
  flex: 1;
}
.preview-cards {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}
.preview-card {
  flex: 1 1 140px;
  min-width: 120px;
  max-width: 200px;
  background: #fff;
  border: 1px solid #eef0f4;
  border-radius: 8px;
  padding: 8px 10px;
}
.preview-cell {
  display: flex;
  justify-content: space-between;
  gap: 6px;
  font-size: 12px;
  line-height: 1.6;
}
.preview-cell .k {
  color: #8a94a6;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.preview-cell .v {
  font-weight: 600;
  color: #374151;
  text-align: right;
}
.more-hint {
  align-self: center;
  font-size: 12px;
  color: #8a94a6;
}
.chart-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}
.reason {
  color: #8a94a6;
  font-size: 12px;
}
.reason.muted {
  opacity: 0.85;
}
.drill-banner {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
  padding: 8px 12px;
  border-radius: 8px;
  background: #eef2ff;
  border: 1px solid #c7d2fe;
  font-size: 12px;
  color: #4338ca;
}
.chart-fallback {
  height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #8a94a6;
  font-size: 13px;
}
.table-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 12px;
  color: #6b7280;
}
.table-summary {
  font-weight: 600;
  color: #374151;
}
.table-truncated {
  color: #d97706;
}
.result-data-table :deep(.num-cell) {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}
.sql-block {
  margin: 0;
  color: #e2e8f0;
  padding: 10px 12px;
  overflow: hidden;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 4.6em;
}
.sql-block.expanded {
  max-height: none;
  overflow: auto;
  padding: 14px 16px;
  font-size: 13px;
  line-height: 1.6;
}
</style>
