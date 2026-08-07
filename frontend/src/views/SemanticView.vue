<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, errorMessage } from '@/api'
import { useAppStore } from '@/stores/app'

const store = useAppStore()
const { currentDatasourceId, currentDatasource } = storeToRefs(store)
const list = ref([])
const schema = ref(null)
const loading = ref(false)
const loadError = ref('')
const dialogVisible = ref(false)
const editingId = ref(null)
const saving = ref(false)
const revisionDrawer = ref(false)
const revisions = ref([])
const previewDrawer = ref(false)
const previewQuestion = ref('')
const preview = ref(null)
const previewLoading = ref(false)

const emptyForm = () => ({
  definitionType: 'COLUMN', tableName: '', columnName: '', businessAlias: '', description: '',
  metricExpression: '', aggregation: 'SUM', unit: '', timeGrain: 'MONTH', enumValue: '', enumLabel: '',
  relatedTable: '', relatedColumn: '', joinType: 'LEFT', active: true
})
const form = reactive(emptyForm())
const tables = computed(() => schema.value?.tables || [])
const columnsOfTable = computed(() => tables.value.find((table) => table.name === form.tableName)?.columns || [])
const relatedColumns = computed(() => tables.value.find((table) => table.name === form.relatedTable)?.columns || [])
const mayManage = computed(() => Boolean(currentDatasource.value?.canManageSemantic))
const typeLabel = {
  TABLE: '表定义', COLUMN: '字段定义', METRIC: '指标', ENUM: '枚举', TIME: '时间', JOIN: '关联'
}

async function load() {
  if (!currentDatasourceId.value) {
    list.value = []
    schema.value = null
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    ;[list.value, schema.value] = await Promise.all([
      api.listSemantic(currentDatasourceId.value),
      api.getSchema(currentDatasourceId.value, false)
    ])
  } catch (error) {
    loadError.value = errorMessage(error)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, emptyForm())
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, emptyForm(), row, { active: row.active === 1 || row.active === true })
  dialogVisible.value = true
}

function onTypeChange() {
  form.columnName = ''
  form.metricExpression = ''
  form.enumValue = ''
  form.enumLabel = ''
  form.relatedTable = ''
  form.relatedColumn = ''
}

function validate() {
  if (!form.tableName) return '请选择表'
  if (['COLUMN', 'ENUM', 'TIME', 'JOIN'].includes(form.definitionType) && !form.columnName) return '请选择字段'
  if (form.definitionType === 'TABLE' && !form.businessAlias.trim() && !form.description.trim()) return '表定义需填写别名或描述'
  if (form.definitionType === 'COLUMN' && !form.businessAlias.trim() && !form.description.trim()) return '字段定义需填写别名或描述'
  if (form.definitionType === 'METRIC' && (!form.businessAlias.trim() || !form.metricExpression.trim())) return '指标需填写名称和表达式'
  if (form.definitionType === 'ENUM' && (!form.enumValue.trim() || !form.enumLabel.trim())) return '枚举需填写原值和业务含义'
  if (form.definitionType === 'JOIN' && (!form.relatedTable || !form.relatedColumn)) return '关联需选择目标表和字段'
  return ''
}

async function save() {
  const message = validate()
  if (message) {
    ElMessage.warning(message)
    return
  }
  saving.value = true
  try {
    const payload = { datasourceId: currentDatasourceId.value, ...form }
    if (editingId.value) await api.updateSemantic(editingId.value, payload)
    else await api.createSemantic(payload)
    ElMessage.success(editingId.value ? '语义定义已更新并生成新版本' : '语义定义已创建')
    dialogVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`删除“${row.businessAlias || row.tableName}”的当前定义？历史版本仍保留。`, '确认删除', { type: 'warning' })
    await api.deleteSemantic(row.id)
    ElMessage.success('语义定义已删除')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(errorMessage(error))
  }
}

async function showRevisions(row) {
  try {
    revisions.value = await api.semanticRevisions(row.id)
    revisionDrawer.value = true
  } catch (error) {
    ElMessage.error(errorMessage(error))
  }
}

async function runPreview() {
  if (!previewQuestion.value.trim()) {
    ElMessage.warning('请输入用于检索上下文的问题')
    return
  }
  previewLoading.value = true
  try {
    preview.value = await api.semanticPromptPreview(currentDatasourceId.value, previewQuestion.value.trim())
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    previewLoading.value = false
  }
}

watch(currentDatasourceId, load)
onMounted(load)
</script>

<template>
  <section class="page-card">
    <header class="toolbar">
      <div>
        <p class="eyebrow">业务口径</p>
        <h1 class="page-title">语义层</h1>
        <p class="page-subtitle">维护表、字段、指标、枚举、时间粒度和 JOIN；每次变更都保留版本快照。</p>
      </div>
      <span class="spacer" />
      <el-button :disabled="!currentDatasourceId" @click="previewDrawer = true">Prompt 预览</el-button>
      <el-button v-if="mayManage" type="primary" :disabled="!currentDatasourceId" @click="openCreate">新增定义</el-button>
    </header>

    <el-alert v-if="!currentDatasourceId" type="info" :closable="false" title="请先选择数据源" />
    <el-alert v-else-if="loadError" type="error" :closable="false" :title="loadError"><template #default><el-button size="small" @click="load">重试</el-button></template></el-alert>
    <el-empty v-else-if="!loading && !list.length" description="该数据源还没有语义定义" />

    <el-table v-else v-loading="loading" :data="list" border stripe>
      <el-table-column label="类型" width="105"><template #default="{ row }"><el-tag size="small" effect="plain">{{ typeLabel[row.definitionType] || row.definitionType }}</el-tag></template></el-table-column>
      <el-table-column prop="tableName" label="表" min-width="130" />
      <el-table-column label="字段 / 关联" min-width="175"><template #default="{ row }"><span class="mono">{{ row.columnName || '—' }}</span><small v-if="row.relatedTable"> → {{ row.relatedTable }}.{{ row.relatedColumn }}</small></template></el-table-column>
      <el-table-column label="业务定义" min-width="210"><template #default="{ row }"><strong>{{ row.businessAlias || row.enumLabel || '—' }}</strong><small class="block-copy">{{ row.description || row.metricExpression || (row.enumValue ? `${row.enumValue} → ${row.enumLabel}` : '') }}</small></template></el-table-column>
      <el-table-column label="口径" min-width="150"><template #default="{ row }">{{ row.aggregation || row.timeGrain || row.joinType || '—' }}<span v-if="row.unit"> · {{ row.unit }}</span></template></el-table-column>
      <el-table-column label="版本" width="90"><template #default="{ row }">v{{ row.version }} <span :class="row.active ? 'active-dot' : 'inactive-dot'" /></template></el-table-column>
      <el-table-column label="操作" :width="mayManage ? 190 : 90" fixed="right">
        <template #default="{ row }">
          <el-button size="small" text @click="showRevisions(row)">版本</el-button>
          <template v-if="mayManage"><el-button size="small" text @click="openEdit(row)">编辑</el-button><el-button size="small" text type="danger" @click="remove(row)">删除</el-button></template>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑语义定义' : '新增语义定义'" width="min(680px, 94vw)" destroy-on-close>
      <el-form :model="form" label-position="top" class="semantic-form">
        <el-form-item label="定义类型" required>
          <el-select v-model="form.definitionType" @change="onTypeChange">
            <el-option v-for="(label, value) in typeLabel" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属表" required>
          <el-select v-model="form.tableName" filterable @change="form.columnName = ''"><el-option v-for="table in tables" :key="table.name" :value="table.name" :label="table.name" /></el-select>
        </el-form-item>
        <el-form-item v-if="['COLUMN', 'ENUM', 'TIME', 'JOIN'].includes(form.definitionType)" label="字段" required>
          <el-select v-model="form.columnName" filterable><el-option v-for="column in columnsOfTable" :key="column.name" :value="column.name" :label="`${column.name} · ${column.dataType}`" /></el-select>
        </el-form-item>
        <el-form-item v-if="['TABLE', 'COLUMN', 'METRIC'].includes(form.definitionType)" :label="form.definitionType === 'METRIC' ? '指标名称' : '业务别名'" :required="form.definitionType === 'METRIC'">
          <el-input v-model="form.businessAlias" maxlength="255" placeholder="例如：成交额" />
        </el-form-item>
        <el-form-item v-if="['TABLE', 'COLUMN', 'METRIC'].includes(form.definitionType)" label="业务描述">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="1000" />
        </el-form-item>
        <template v-if="form.definitionType === 'METRIC'">
          <el-form-item label="SQL 指标表达式" required><el-input v-model="form.metricExpression" type="textarea" :rows="2" placeholder="例如：SUM(total_amount)" /></el-form-item>
          <el-form-item label="聚合方式" required><el-select v-model="form.aggregation"><el-option v-for="item in ['SUM','COUNT','COUNT_DISTINCT','AVG','MIN','MAX']" :key="item" :value="item" :label="item" /></el-select></el-form-item>
          <el-form-item label="单位"><el-input v-model="form.unit" placeholder="元、单、人" /></el-form-item>
        </template>
        <template v-if="form.definitionType === 'ENUM'">
          <el-form-item label="数据库原值" required><el-input v-model="form.enumValue" /></el-form-item>
          <el-form-item label="业务含义" required><el-input v-model="form.enumLabel" /></el-form-item>
        </template>
        <el-form-item v-if="form.definitionType === 'TIME'" label="时间粒度" required>
          <el-select v-model="form.timeGrain"><el-option v-for="item in ['DAY','WEEK','MONTH','QUARTER','YEAR']" :key="item" :value="item" :label="item" /></el-select>
        </el-form-item>
        <template v-if="form.definitionType === 'JOIN'">
          <el-form-item label="关联表" required><el-select v-model="form.relatedTable" filterable @change="form.relatedColumn = ''"><el-option v-for="table in tables" :key="table.name" :value="table.name" :label="table.name" /></el-select></el-form-item>
          <el-form-item label="关联字段" required><el-select v-model="form.relatedColumn" filterable><el-option v-for="column in relatedColumns" :key="column.name" :value="column.name" :label="`${column.name} · ${column.dataType}`" /></el-select></el-form-item>
          <el-form-item label="JOIN 类型"><el-select v-model="form.joinType"><el-option v-for="item in ['INNER','LEFT','RIGHT']" :key="item" :value="item" :label="item" /></el-select></el-form-item>
        </template>
        <el-form-item label="状态"><el-switch v-model="form.active" active-text="启用" inactive-text="停用" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>

    <el-drawer v-model="revisionDrawer" title="定义版本" size="min(720px, 94vw)">
      <el-timeline>
        <el-timeline-item v-for="item in revisions" :key="item.id" :timestamp="item.createdAt" placement="top">
          <el-card shadow="never"><strong>v{{ item.version }} · {{ item.changeType }}</strong><pre>{{ item.snapshot }}</pre></el-card>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-if="!revisions.length" description="暂无版本记录" />
    </el-drawer>

    <el-drawer v-model="previewDrawer" title="Prompt 上下文预览" size="min(760px, 96vw)">
      <div class="preview-search"><el-input v-model="previewQuestion" placeholder="输入一个业务问题，查看会送入模型的有限上下文" @keydown.enter="runPreview" /><el-button type="primary" :loading="previewLoading" @click="runPreview">检索</el-button></div>
      <template v-if="preview">
        <div class="preview-stats"><span>表 {{ preview.selectedTables.length }}/{{ preview.totalTables }}</span><span>字段 {{ preview.selectedColumns }}/{{ preview.totalColumns }}</span><span>字符 {{ preview.renderedCharacters }}</span><el-tag v-if="preview.truncated" type="warning" size="small">已截断</el-tag></div>
        <p class="preview-label">命中定义：{{ preview.semanticDefinitions.join('、') || '无' }}</p>
        <pre class="prompt-preview">{{ preview.renderedContext }}</pre>
      </template>
      <el-empty v-else description="输入问题后查看轻量检索结果" />
    </el-drawer>
  </section>
</template>

<style scoped>
.mono { font-family: ui-monospace, monospace; font-size: 11px; }
.block-copy { display: block; margin-top: 3px; color: var(--ink-500); font-size: 10px; }
.active-dot, .inactive-dot { display: inline-block; width: 7px; height: 7px; margin-left: 5px; border-radius: 50%; background: #2f9b6d; }
.inactive-dot { background: #aab3af; }
.semantic-form { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); column-gap: 16px; }
.semantic-form :deep(.el-select) { width: 100%; }
.semantic-form :deep(.el-form-item:has(textarea)) { grid-column: 1 / -1; }
.el-timeline pre, .prompt-preview { overflow: auto; padding: 11px; color: #dbe8e5; background: #172522; border-radius: 7px; font: 11px/1.65 ui-monospace, monospace; white-space: pre-wrap; word-break: break-word; }
.preview-search { display: flex; gap: 8px; margin-bottom: 14px; }
.preview-stats { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; color: var(--ink-650); font-size: 12px; }
.preview-label { color: var(--ink-500); font-size: 11px; }
.prompt-preview { max-height: calc(100vh - 260px); }
@media (max-width: 640px) { .semantic-form { grid-template-columns: 1fr; } .semantic-form :deep(.el-form-item:has(textarea)) { grid-column: auto; } }
</style>
