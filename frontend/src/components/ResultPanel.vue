<script setup>
import { ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '@/api'
import { downloadBlob } from '@/utils/download'
import ChartRenderer from './ChartRenderer.vue'

const props = defineProps({
  result: { type: Object, required: true }
})

const hasRows = computed(() => (props.result.rows || []).length > 0)
const recommended = computed(() => props.result.chart || {})
const chartType = ref(
  recommended.value.type && recommended.value.type !== 'table' ? recommended.value.type : 'bar'
)
const activeTab = ref(
  recommended.value.type && recommended.value.type !== 'table' && hasRows.value ? 'chart' : 'table'
)

const xField = computed(() => recommended.value.xField || firstDimension())
const yFields = computed(() =>
  recommended.value.yFields && recommended.value.yFields.length
    ? recommended.value.yFields
    : firstMeasures()
)

function firstDimension() {
  const c = (props.result.columns || []).find((x) => x.category !== 'measure')
  return c ? c.name : (props.result.columns?.[0]?.name)
}
function firstMeasures() {
  const ms = (props.result.columns || []).filter((x) => x.category === 'measure').map((x) => x.name)
  return ms.length ? ms : (props.result.columns?.slice(1).map((x) => x.name) || [])
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
    <el-alert
      v-if="result.explanation"
      :title="result.explanation"
      type="info"
      :closable="false"
      show-icon
      class="explain"
    />

    <div class="meta">
      <el-tag size="small" type="info" effect="plain">{{ result.rowCount }} 行</el-tag>
      <el-tag size="small" type="info" effect="plain">{{ result.elapsedMs }} ms</el-tag>
      <el-tag v-if="result.truncated" size="small" type="warning" effect="plain">
        结果已截断（达到上限）
      </el-tag>
      <span class="spacer" />
      <el-button size="small" text :icon="'Star'" @click="saveFavorite">收藏</el-button>
      <el-button size="small" text :icon="'Download'" @click="exportExcel" :disabled="!hasRows">
        导出 Excel
      </el-button>
      <el-button size="small" text :icon="'DocumentCopy'" @click="copySql">复制 SQL</el-button>
    </div>

    <el-tabs v-model="activeTab" class="result-tabs">
      <el-tab-pane label="图表" name="chart" :disabled="!hasRows">
        <div class="chart-toolbar">
          <el-radio-group v-model="chartType" size="small">
            <el-radio-button value="bar">柱状图</el-radio-button>
            <el-radio-button value="line">折线图</el-radio-button>
            <el-radio-button value="pie">饼图</el-radio-button>
          </el-radio-group>
          <span v-if="recommended.reason" class="reason">推荐：{{ recommended.reason }}</span>
        </div>
        <ChartRenderer
          v-if="hasRows"
          :type="chartType"
          :x-field="xField"
          :y-fields="yFields"
          :rows="result.rows"
        />
      </el-tab-pane>

      <el-tab-pane label="表格" name="table">
        <el-table :data="result.rows" border stripe height="380" size="small">
          <el-table-column
            v-for="col in result.columns"
            :key="col.name"
            :prop="col.name"
            :label="col.name"
            show-overflow-tooltip
            min-width="120"
          />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="SQL" name="sql">
        <pre class="sql-block">{{ result.sql }}</pre>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.result-panel {
  margin-top: 6px;
}
.explain {
  margin-bottom: 10px;
}
.meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.spacer {
  flex: 1;
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
.sql-block {
  background: #0f172a;
  color: #e2e8f0;
  padding: 14px 16px;
  border-radius: 8px;
  overflow: auto;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
