<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errorMessage } from '@/api'
import { jobStatusLabel, pollQueryJob } from '@/utils/queryJobs'
import ResultPanel from './ResultPanel.vue'

defineProps({ modelValue: Boolean, title: { type: String, default: '查询结果' } })
const emit = defineEmits(['update:modelValue', 'completed'])
const job = ref(null)
const result = ref(null)
const error = ref('')
const question = ref('')
const controller = ref(null)
const running = computed(() => Boolean(job.value) && !['SUCCEEDED', 'PREVIEWED', 'CLARIFICATION', 'NEEDS_CONFIRMATION', 'FAILED', 'CANCELLED', 'TIMED_OUT'].includes(job.value.status))

async function follow(created) {
  controller.value = new AbortController()
  job.value = created
  try {
    const terminal = await pollQueryJob(created.id, {
      fetchJob: api.getQueryJob,
      signal: controller.value.signal,
      onUpdate: (value) => { job.value = value }
    })
    job.value = terminal
    if (['SUCCEEDED', 'PREVIEWED'].includes(terminal.status)) {
      result.value = { ...terminal.result, question: question.value || terminal.result?.question }
      emit('completed', result.value)
    } else if (!['NEEDS_CONFIRMATION', 'CLARIFICATION'].includes(terminal.status)) {
      error.value = terminal.errorMessage || terminal.message || jobStatusLabel(terminal.status)
    }
  } catch (caught) {
    if (!caught.cancelled && caught.name !== 'AbortError') error.value = errorMessage(caught)
  }
}

async function run(payload) {
  emit('update:modelValue', true)
  job.value = null
  result.value = null
  error.value = ''
  question.value = payload.question || ''
  try {
    const created = await api.createRunJob({
      datasourceId: payload.datasourceId,
      sql: payload.sql,
      sessionId: payload.sessionId || null
    })
    await follow(created)
  } catch (caught) {
    error.value = errorMessage(caught)
  }
}

async function retry(confirmRisk = false) {
  if (!job.value?.id) return
  error.value = ''
  result.value = null
  try {
    const created = await api.retryQueryJob(job.value.id, confirmRisk)
    await follow(created)
  } catch (caught) {
    error.value = errorMessage(caught)
  }
}

async function cancel() {
  if (!job.value?.id) return
  try {
    job.value = await api.cancelQueryJob(job.value.id)
    error.value = job.value.message
    controller.value?.abort()
  } catch (caught) {
    ElMessage.error(errorMessage(caught))
  }
}

function close(done) {
  if (running.value) {
    ElMessage.warning('查询仍在运行，请先取消')
    return
  }
  done()
}

onBeforeUnmount(() => {
  if (running.value && job.value?.id) api.cancelQueryJob(job.value.id).catch(() => {})
  controller.value?.abort()
})

defineExpose({ run })
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="min(980px, 96vw)"
    top="4vh"
    :close-on-click-modal="!running"
    :before-close="close"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-if="running" class="dialog-progress">
      <div><strong>{{ job.message || jobStatusLabel(job.status) }}</strong><span>{{ jobStatusLabel(job.status) }}</span></div>
      <el-progress :percentage="job.progress || 0" :stroke-width="6" />
      <el-button size="small" @click="cancel">取消查询</el-button>
    </div>

    <div v-else-if="job?.status === 'NEEDS_CONFIRMATION'" class="dialog-state warning">
      <div><strong>执行计划需要确认</strong><p>该 SQL 可能扫描较多数据，尚未执行。</p><ul v-if="job.result?.risk?.reasons"><li v-for="reason in job.result.risk.reasons" :key="reason">{{ reason }}</li></ul></div>
      <el-button type="primary" @click="retry(true)">确认并执行</el-button>
    </div>

    <div v-else-if="job?.status === 'CLARIFICATION'" class="dialog-state warning">
      <strong>该查询需要补充信息</strong><p>{{ job.result?.clarification }}</p>
    </div>

    <div v-else-if="error" class="dialog-state error">
      <div><strong>{{ jobStatusLabel(job?.status) }}</strong><p>{{ error }}</p></div>
      <el-button v-if="['FAILED','CANCELLED','TIMED_OUT'].includes(job?.status)" type="primary" plain @click="retry(false)">重试</el-button>
    </div>

    <ResultPanel v-else-if="result" :result="result" />
    <el-empty v-else description="正在创建查询任务" />
  </el-dialog>
</template>

<style scoped>
.dialog-progress { display: grid; grid-template-columns: minmax(0, 1fr) 130px auto; align-items: center; gap: 14px; min-height: 120px; }
.dialog-progress > div { display: flex; flex-direction: column; gap: 5px; }
.dialog-progress span, .dialog-state p, .dialog-state li { margin: 0; color: var(--ink-500); font-size: 12px; line-height: 1.6; }
.dialog-state { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; padding: 16px; background: var(--surface-soft); border: 1px solid var(--line); border-left: 3px solid var(--warning); border-radius: 8px; }
.dialog-state.error { border-left-color: var(--danger); background: #fff8f7; }
.dialog-state ul { margin: 8px 0 0; padding-left: 18px; }
@media (max-width: 640px) { .dialog-progress { grid-template-columns: 1fr; } .dialog-state { flex-direction: column; } }
</style>
