<script setup>
import { computed, defineAsyncComponent, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, errorMessage } from '@/api'
import { downloadBlob } from '@/utils/download'
import { hasChartableValues } from '@/utils/chartData'

const ChartRenderer = defineAsyncComponent(() => import('./ChartRenderer.vue'))
const props = defineProps({
  result: { type: Object, required: true },
  canExport: { type: Boolean, default: true }
})
const emit = defineEmits(['drilldown'])

const rows = computed(() => props.result.rows || [])
const columns = computed(() => props.result.columns || [])
const recommended = computed(() => props.result.chart || {})
const hasRows = computed(() => rows.value.length > 0)
const sqlExpanded = ref(false)
const drillFilter = ref(null)

function firstDimension() {
  return columns.value.find((column) => column.category !== 'measure')?.name || columns.value[0]?.name
}

function firstMeasures() {
  const measures = columns.value.filter((column) => column.category === 'measure').map((column) => column.name)
  return measures.length ? measures : columns.value.slice(1).map((column) => column.name)
}

const xField = computed(() => recommended.value.xField || firstDimension())
const yFields = computed(() => recommended.value.yFields?.length ? recommended.value.yFields : firstMeasures())
const chartable = computed(() =>
  recommended.value.type !== 'table' &&
  Boolean(xField.value) &&
  hasChartableValues(rows.value, yFields.value)
)
const chartType = ref(['bar', 'line', 'pie'].includes(recommended.value.type) ? recommended.value.type : 'bar')
const activeTab = ref(chartable.value ? 'chart' : 'table')
const displayRows = computed(() => {
  if (!drillFilter.value) return rows.value
  return rows.value.filter((row) => String(row[drillFilter.value.dimension]) === String(drillFilter.value.value))
})
const displayRowCount = computed(() => drillFilter.value ? displayRows.value.length : props.result.rowCount)
const sqlPreview = computed(() => {
  const sql = String(props.result.sql || '').trim()
  if (sql.length <= 220) return sql
  return `${sql.slice(0, 220)}…`
})

function formatCell(value, column) {
  if (value == null || value === '') return '—'
  if (column?.category === 'measure' && typeof value === 'number') {
    return value.toLocaleString('zh-CN', { maximumFractionDigits: 8 })
  }
  return value
}

function columnLabel(column) {
  if (column.category === 'measure') return `${column.name} · 指标`
  if (column.category === 'dimension') return `${column.name} · 维度`
  return column.name
}

function onChartDrill(payload) {
  const dimension = payload.dimension || xField.value
  const value = payload.label ?? payload.value
  drillFilter.value = { dimension, value, dataIndex: payload.index ?? null, label: String(value) }
  activeTab.value = 'table'
}

function clearDrill() {
  drillFilter.value = null
}

function confirmDrilldown() {
  if (!drillFilter.value) return
  emit('drilldown', { ...drillFilter.value, question: props.result.question })
}

async function copySql() {
  try {
    await navigator.clipboard.writeText(props.result.sql || '')
    ElMessage.success('SQL 已复制')
  } catch {
    ElMessage.warning('复制失败，请手动选择 SQL')
  }
}

async function exportExcel() {
  try {
    const response = await api.exportExcel(props.result.queryId)
    downloadBlob(response, `chatbi-query-${props.result.queryId}.xlsx`)
    ElMessage.success('已从结果快照导出 Excel')
  } catch (error) {
    ElMessage.error(errorMessage(error))
  }
}

async function saveFavorite() {
  try {
    const { value } = await ElMessageBox.prompt('输入收藏名称', '收藏查询', {
      confirmButtonText: '保存',
      cancelButtonText: '取消',
      inputValue: props.result.question || '',
      inputValidator: (text) => Boolean(text?.trim()) || '名称不能为空'
    })
    await api.createFavorite({ queryId: props.result.queryId, title: value.trim() })
    ElMessage.success('已收藏当前结果快照')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close' && error?.message !== 'cancel') {
      ElMessage.error(errorMessage(error))
    }
  }
}
</script>

<template>
  <div class="result-panel">
    <section v-if="result.summary?.text || result.explanation" class="insight-block">
      <p class="eyebrow">数据解读</p>
      <p class="insight-text">{{ result.summary?.text || result.explanation }}</p>
      <dl v-if="result.summary?.facts?.length" class="fact-grid">
        <div v-for="fact in result.summary.facts" :key="`${fact.label}-${fact.column}`">
          <dt>{{ fact.label }}</dt>
          <dd>{{ fact.value }}<small v-if="fact.unit"> {{ fact.unit }}</small></dd>
        </div>
      </dl>
    </section>

    <section class="sql-card">
      <header>
        <div>
          <span class="eyebrow">已校验只读 SQL</span>
          <el-tag v-if="result.risk?.level" size="small" effect="plain" :type="result.risk.level === 'LOW' ? 'success' : 'warning'">
            {{ result.risk.level }} 风险
          </el-tag>
        </div>
        <div>
          <el-button size="small" text @click="copySql">复制</el-button>
          <el-button size="small" text @click="sqlExpanded = !sqlExpanded">{{ sqlExpanded ? '收起' : '展开' }}</el-button>
        </div>
      </header>
      <pre :class="{ expanded: sqlExpanded }">{{ sqlExpanded ? result.sql : sqlPreview }}</pre>
    </section>

    <div class="result-meta">
      <span><strong>{{ displayRowCount }}</strong> 行</span>
      <span><strong>{{ columns.length }}</strong> 列</span>
      <span><strong>{{ result.elapsedMs ?? 0 }}</strong> ms</span>
      <span v-if="result.truncated" class="warning-text">结果已按安全上限截断</span>
      <span class="spacer" />
      <span v-if="result.queryId" class="query-id">查询 #{{ result.queryId }}</span>
      <el-button size="small" text :disabled="!result.queryId" @click="saveFavorite">收藏</el-button>
      <el-tooltip :disabled="canExport" content="当前账户没有该数据源的导出权限" placement="top">
        <span>
          <el-button size="small" text :disabled="!canExport || !result.queryId || !hasRows" @click="exportExcel">
            导出 Excel
          </el-button>
        </span>
      </el-tooltip>
    </div>

    <div v-if="drillFilter" class="drill-banner">
      <span>已筛选 {{ drillFilter.dimension }} = <strong>{{ drillFilter.label }}</strong></span>
      <el-button size="small" type="primary" @click="confirmDrilldown">基于此项追问</el-button>
      <el-button size="small" text @click="clearDrill">清除</el-button>
    </div>

    <el-tabs v-model="activeTab" class="result-tabs">
      <el-tab-pane label="图表" name="chart" :disabled="!chartable">
        <div v-if="chartable" class="chart-toolbar">
          <el-radio-group v-model="chartType" size="small">
            <el-radio-button value="bar">柱状图</el-radio-button>
            <el-radio-button value="line">折线图</el-radio-button>
            <el-radio-button value="pie">饼图</el-radio-button>
          </el-radio-group>
          <span>{{ recommended.reason || '图表数值直接取自结果快照' }}</span>
        </div>
        <Suspense v-if="chartable">
          <ChartRenderer
            :type="chartType"
            :x-field="xField"
            :y-fields="yFields"
            :series-field="recommended.seriesField"
            :rows="rows"
            :highlight-index="drillFilter?.dataIndex ?? null"
            @drill="onChartDrill"
          />
          <template #fallback><div class="chart-fallback">正在加载图表…</div></template>
        </Suspense>
        <el-empty v-else description="该结果包含空值、非数值或高精度数字，仅提供无损表格展示" />
      </el-tab-pane>

      <el-tab-pane label="数据表" name="table">
        <div class="table-wrap">
          <el-table :data="displayRows" border stripe height="380" size="small" class="result-table">
            <el-table-column
              v-for="column in columns"
              :key="column.name"
              :prop="column.name"
              :label="columnLabel(column)"
              show-overflow-tooltip
              :min-width="column.category === 'measure' ? 120 : 140"
              sortable
            >
              <template #default="{ row }">
                <span :class="{ 'number-cell': column.category === 'measure' }">{{ formatCell(row[column.name], column) }}</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.result-panel {
  min-width: 0;
}
.insight-block {
  margin-bottom: 12px;
  padding: 14px 16px;
  background: #eef7f4;
  border: 1px solid #cfe5df;
  border-left: 3px solid var(--accent);
  border-radius: 8px;
}
.insight-text {
  margin: 6px 0 0;
  color: var(--ink-800);
  font-size: 13px;
  line-height: 1.7;
}
.fact-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 12px 0 0;
}
.fact-grid div {
  min-width: 130px;
  padding: 8px 10px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid #d7e8e3;
  border-radius: 6px;
}
.fact-grid dt {
  color: var(--ink-500);
  font-size: 10px;
}
.fact-grid dd {
  margin: 3px 0 0;
  color: var(--ink-950);
  font-size: 16px;
  font-variant-numeric: tabular-nums;
  font-weight: 700;
}
.fact-grid small {
  color: var(--ink-500);
  font-size: 10px;
  font-weight: 500;
}
.sql-card {
  overflow: hidden;
  margin-bottom: 10px;
  background: #172522;
  border: 1px solid #30433f;
  border-radius: 8px;
}
.sql-card header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 7px 10px;
  background: #20312e;
  border-bottom: 1px solid #344844;
}
.sql-card header > div {
  display: flex;
  align-items: center;
  gap: 7px;
}
.sql-card .eyebrow {
  color: #96aaa5;
}
.sql-card :deep(.el-button) {
  color: #c7d6d2;
}
.sql-card pre {
  max-height: 4.7em;
  overflow: hidden;
  margin: 0;
  padding: 11px 13px;
  color: #dcebe7;
  font: 12px/1.65 ui-monospace, SFMono-Regular, Consolas, monospace;
  white-space: pre-wrap;
  word-break: break-word;
}
.sql-card pre.expanded {
  max-height: 320px;
  overflow: auto;
}
.result-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  min-height: 34px;
  color: var(--ink-500);
  font-size: 11px;
}
.result-meta strong {
  color: var(--ink-800);
  font-variant-numeric: tabular-nums;
}
.warning-text {
  color: var(--warning);
}
.query-id {
  font-family: ui-monospace, monospace;
}
.drill-banner {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin: 8px 0;
  padding: 8px 11px;
  color: var(--ink-800);
  background: var(--accent-soft);
  border: 1px solid #bbdbd5;
  border-radius: 7px;
  font-size: 12px;
}
.chart-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 8px;
  color: var(--ink-500);
  font-size: 11px;
}
.chart-fallback {
  display: grid;
  place-items: center;
  height: 260px;
  color: var(--ink-500);
  font-size: 12px;
}
.table-wrap {
  max-width: 100%;
  overflow: hidden;
}
.result-table :deep(.number-cell) {
  font-variant-numeric: tabular-nums;
  font-weight: 650;
}
@media (max-width: 640px) {
  .result-meta .spacer,
  .query-id {
    display: none;
  }
  .sql-card header {
    align-items: flex-start;
  }
  .chart-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
