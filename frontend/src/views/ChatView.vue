<script setup>
import { computed, defineAsyncComponent, nextTick, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowRight } from '@element-plus/icons-vue'
import { api, errorMessage } from '@/api'
import { useAppStore } from '@/stores/app'
import { buildDrilldownQuestion } from '@/utils/askStream'
import { jobStatusLabel, pollQueryJob } from '@/utils/queryJobs'

const ResultPanel = defineAsyncComponent(() => import('@/components/ResultPanel.vue'))

defineOptions({ name: 'ChatView' })

const store = useAppStore()
const router = useRouter()
const { currentDatasourceId, currentDatasource, llm, online } = storeToRefs(store)
const question = ref('')
const messages = ref([])
const sessionId = ref(null)
const activeJobId = ref(null)
const pollingController = ref(null)
const listRef = ref(null)

const busy = computed(() => Boolean(activeJobId.value))
const noDatasource = computed(() => !currentDatasourceId.value)
const isMockMode = computed(() => llm.value?.provider === 'mock')
const examples = [
  '已支付订单的数量和销售额是多少？',
  '按月查看已支付订单销售额趋势',
  '销售额最高的 5 个产品是什么？',
  '各产品类目的销售额占比',
  '各大区的客户数量',
  'VIP 客户贡献了多少销售额？'
]

function scrollBottom() {
  nextTick(() => {
    if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight
  })
}

function updateAssistant(assistant, job) {
  assistant.job = job
  assistant.loading = !['SUCCEEDED', 'PREVIEWED', 'CLARIFICATION', 'NEEDS_CONFIRMATION', 'FAILED', 'CANCELLED', 'TIMED_OUT'].includes(job.status)
  if (job.sessionId) sessionId.value = job.sessionId
  scrollBottom()
}

function applyTerminal(assistant, job) {
  assistant.loading = false
  const result = job.result
  if (job.status === 'SUCCEEDED' || job.status === 'PREVIEWED') {
    assistant.result = { ...result, question: assistant.question }
    return
  }
  if (job.status === 'CLARIFICATION') {
    assistant.clarification = result?.clarification || '请补充时间范围、指标口径或分析维度。'
    return
  }
  if (job.status === 'NEEDS_CONFIRMATION') {
    assistant.confirmation = result
    return
  }
  assistant.error = job.errorMessage || job.message || jobStatusLabel(job.status)
  assistant.retryable = ['FAILED', 'CANCELLED', 'TIMED_OUT'].includes(job.status)
}

async function followJob(created, assistant) {
  const controller = new AbortController()
  pollingController.value = controller
  activeJobId.value = created.id
  updateAssistant(assistant, created)
  try {
    const terminal = await pollQueryJob(created.id, {
      fetchJob: api.getQueryJob,
      signal: controller.signal,
      onUpdate: (job) => updateAssistant(assistant, job)
    })
    applyTerminal(assistant, terminal)
  } catch (error) {
    if (!error.cancelled && error.name !== 'AbortError' && assistant.job?.status !== 'CANCELLED') {
      assistant.loading = false
      assistant.error = errorMessage(error)
      assistant.retryable = true
    }
  } finally {
    if (activeJobId.value === created.id) activeJobId.value = null
    if (pollingController.value === controller) pollingController.value = null
    scrollBottom()
  }
}

async function send(text) {
  const value = (typeof text === 'string' ? text : question.value).trim()
  if (!value || busy.value) return
  if (!online.value) {
    ElMessage.warning('网络已断开，请恢复连接后重试')
    return
  }
  if (noDatasource.value) {
    ElMessage.warning('请先选择可访问的数据源')
    return
  }

  const assistant = {
    role: 'assistant',
    question: value,
    loading: true,
    job: { status: 'QUEUED', progress: 0, message: '正在提交查询' }
  }
  messages.value.push({ role: 'user', content: value }, assistant)
  question.value = ''
  scrollBottom()

  try {
    const created = await api.createAskJob({
      datasourceId: currentDatasourceId.value,
      question: value,
      sessionId: sessionId.value
    })
    await followJob(created, assistant)
  } catch (error) {
    assistant.loading = false
    assistant.error = errorMessage(error)
    assistant.retryable = false
    scrollBottom()
  }
}

async function cancel(assistant) {
  const id = assistant.job?.id
  if (!id) return
  try {
    const job = await api.cancelQueryJob(id)
    updateAssistant(assistant, job)
    applyTerminal(assistant, job)
    pollingController.value?.abort()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  }
}

async function retry(assistant, confirmRisk = false) {
  const id = assistant.job?.id
  if (!id || busy.value) return
  assistant.error = null
  assistant.confirmation = null
  assistant.result = null
  assistant.retryable = false
  assistant.loading = true
  try {
    const created = await api.retryQueryJob(id, confirmRisk)
    await followJob(created, assistant)
  } catch (error) {
    assistant.loading = false
    assistant.error = errorMessage(error)
    assistant.retryable = true
  }
}

function onDrilldown(payload) {
  const value = payload.label ?? payload.value
  send(buildDrilldownQuestion(value, {
    dimension: payload.dimension,
    question: payload.question
  }))
}

async function newConversation() {
  if (busy.value) {
    const assistant = [...messages.value].reverse().find((item) => item.role === 'assistant' && item.loading)
    if (assistant) await cancel(assistant)
  }
  pollingController.value?.abort()
  messages.value = []
  sessionId.value = null
  question.value = ''
}

watch(currentDatasourceId, (next, previous) => {
  if (previous != null && next !== previous) newConversation()
})
</script>

<template>
  <section class="chat-page">
    <header class="chat-header">
      <div>
        <p class="eyebrow">自然语言问数</p>
        <h1 class="page-title">{{ currentDatasource?.name || '智能问数' }}</h1>
        <p class="page-subtitle">服务端校验权限与只读 SQL，图表、解读和导出均来自同一结果快照。</p>
      </div>
      <div class="chat-actions">
        <span v-if="sessionId" class="session-label" :title="sessionId">会话 {{ sessionId.slice(0, 8) }}</span>
        <el-button :disabled="busy" @click="newConversation">新对话</el-button>
      </div>
    </header>

    <el-alert
      v-if="isMockMode"
      class="mode-notice"
      type="warning"
      :closable="false"
      show-icon
      title="当前为 Mock 确定性回归模式"
      description="它用于验证流程和安全边界，不代表真实模型的问数准确率。"
    />
    <el-alert
      v-else-if="!llm.configured"
      class="mode-notice"
      type="error"
      :closable="false"
      show-icon
      title="真实模型尚未配置"
      description="管理员需配置兼容接口后重启服务；已有历史结果仍可查看。"
    />

    <div ref="listRef" class="chat-stream" aria-live="polite">
      <div v-if="!messages.length" class="chat-empty">
        <div class="empty-monogram">SQL</div>
        <h2>{{ noDatasource ? '还没有可用数据源' : '从一个明确的业务问题开始' }}</h2>
        <p v-if="noDatasource">
          当前账户没有可查询的数据源。管理员可在数据源与权限页面完成配置。
        </p>
        <template v-else>
          <p>写明指标、维度和时间范围；存在关键歧义时系统会先向你澄清。</p>
          <div class="example-grid">
            <button v-for="item in examples" :key="item" type="button" @click="send(item)">
              <span>{{ item }}</span><el-icon><ArrowRight /></el-icon>
            </button>
          </div>
        </template>
        <el-button v-if="noDatasource && store.isAdmin" type="primary" @click="router.push('/datasources')">
          配置数据源
        </el-button>
      </div>

      <template v-for="(message, index) in messages" :key="index">
        <article v-if="message.role === 'user'" class="message-row user-row">
          <div class="message-label">你</div>
          <div class="user-message">{{ message.content }}</div>
        </article>

        <article v-else class="message-row assistant-row">
          <div class="message-label assistant-label">BI</div>
          <div class="assistant-message">
            <div v-if="message.loading" class="job-progress">
              <div class="job-progress-head">
                <div>
                  <strong>{{ message.job?.message || jobStatusLabel(message.job?.status) }}</strong>
                  <span>{{ jobStatusLabel(message.job?.status) }}</span>
                </div>
                <el-button size="small" plain @click="cancel(message)">取消</el-button>
              </div>
              <el-progress
                :percentage="message.job?.progress || 0"
                :show-text="false"
                :stroke-width="5"
              />
            </div>

            <div v-else-if="message.error" class="terminal-state error-state">
              <div>
                <strong>{{ jobStatusLabel(message.job?.status) }}</strong>
                <p>{{ message.error }}</p>
              </div>
              <el-button v-if="message.retryable" size="small" type="primary" plain @click="retry(message)">
                重试
              </el-button>
            </div>

            <div v-else-if="message.clarification" class="clarification-state">
              <p class="eyebrow">需要澄清</p>
              <strong>{{ message.clarification }}</strong>
              <span>直接在下方补充，答案会保留在当前服务端会话中。</span>
            </div>

            <div v-else-if="message.confirmation" class="risk-state">
              <div>
                <p class="eyebrow">执行计划确认</p>
                <strong>该查询可能扫描较多数据，尚未执行。</strong>
                <ul v-if="message.confirmation.risk?.reasons?.length">
                  <li v-for="reason in message.confirmation.risk.reasons" :key="reason">{{ reason }}</li>
                </ul>
                <p v-if="message.confirmation.risk?.estimatedRows != null">
                  预计扫描 {{ Number(message.confirmation.risk.estimatedRows).toLocaleString('zh-CN') }} 行
                </p>
              </div>
              <div class="risk-actions">
                <el-button size="small" type="primary" @click="retry(message, true)">确认并执行</el-button>
                <el-button size="small" @click="message.error = '已放弃执行'; message.confirmation = null">放弃</el-button>
              </div>
            </div>

            <Suspense v-else-if="message.result">
              <ResultPanel
                :result="message.result"
                :can-export="Boolean(currentDatasource?.canExport)"
                @drilldown="onDrilldown"
              />
              <template #fallback><div class="panel-loading">正在加载结果组件…</div></template>
            </Suspense>
          </div>
        </article>
      </template>
    </div>

    <form class="composer" @submit.prevent="send()">
      <el-input
        v-model="question"
        type="textarea"
        :autosize="{ minRows: 1, maxRows: 5 }"
        resize="none"
        maxlength="1000"
        placeholder="输入指标、维度和时间范围；Enter 发送，Shift + Enter 换行"
        :disabled="noDatasource || busy || !online"
        @keydown.enter.exact.prevent="send()"
      />
      <el-button
        native-type="submit"
        type="primary"
        :disabled="noDatasource || busy || !question.trim() || !online"
      >
        {{ busy ? '查询中' : '发送' }}
      </el-button>
    </form>
  </section>
</template>

<style scoped>
.chat-page {
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr) auto;
  width: min(100%, 1320px);
  height: 100%;
  min-height: 520px;
  margin: 0 auto;
}
.chat-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.chat-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.session-label {
  max-width: 150px;
  overflow: hidden;
  color: var(--ink-500);
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mode-notice {
  margin-bottom: 12px;
}
.chat-stream {
  min-height: 0;
  overflow: auto;
  padding: 20px clamp(14px, 3vw, 42px);
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 10px;
}
.chat-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100%;
  padding: 32px 12px;
  text-align: center;
}
.empty-monogram {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  color: var(--accent);
  background: var(--accent-soft);
  border: 1px solid #beded8;
  border-radius: 12px;
  font: 750 12px/1 ui-monospace, monospace;
  letter-spacing: 0.08em;
}
.chat-empty h2 {
  margin: 18px 0 7px;
  font-size: 20px;
}
.chat-empty > p {
  max-width: 590px;
  margin: 0 0 22px;
  color: var(--ink-500);
  font-size: 13px;
  line-height: 1.65;
}
.example-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  width: min(100%, 700px);
}
.example-grid button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 44px;
  padding: 10px 13px;
  color: var(--ink-800);
  background: var(--surface-soft);
  border: 1px solid var(--line);
  border-radius: 7px;
  cursor: pointer;
  font-size: 12px;
  text-align: left;
}
.example-grid button:hover {
  color: var(--accent-strong);
  border-color: #8fc5bd;
}
.message-row {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  gap: 11px;
  margin-bottom: 22px;
}
.user-row {
  grid-template-columns: minmax(0, 1fr) 32px;
}
.message-label {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  color: var(--ink-650);
  background: #edf0ec;
  border-radius: 8px;
  font-size: 11px;
  font-weight: 750;
}
.assistant-label {
  color: #e9fffb;
  background: var(--accent);
}
.user-message {
  justify-self: end;
  max-width: min(78%, 760px);
  padding: 9px 13px;
  color: #fff;
  background: var(--ink-800);
  border-radius: 9px 9px 2px 9px;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
}
.user-row .message-label {
  grid-column: 2;
  grid-row: 1;
}
.user-row .user-message {
  grid-column: 1;
  grid-row: 1;
}
.assistant-message {
  min-width: 0;
}
.job-progress,
.terminal-state,
.clarification-state,
.risk-state {
  max-width: 760px;
  padding: 14px 16px;
  background: var(--surface-soft);
  border: 1px solid var(--line);
  border-radius: 8px;
}
.job-progress-head,
.terminal-state,
.risk-state {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}
.job-progress-head {
  margin-bottom: 11px;
}
.job-progress-head div {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.job-progress-head strong,
.terminal-state strong,
.clarification-state strong,
.risk-state strong {
  color: var(--ink-800);
  font-size: 13px;
}
.job-progress-head span,
.terminal-state p,
.clarification-state span,
.risk-state p,
.risk-state li {
  margin: 0;
  color: var(--ink-500);
  font-size: 12px;
  line-height: 1.6;
}
.error-state {
  border-color: #efcbc6;
  background: #fff8f7;
}
.clarification-state {
  display: flex;
  flex-direction: column;
  gap: 7px;
  border-left: 3px solid #d29a36;
}
.risk-state {
  border-left: 3px solid #c77d16;
}
.risk-state ul {
  margin: 8px 0;
  padding-left: 18px;
}
.risk-actions {
  display: flex;
  flex: 0 0 auto;
  gap: 6px;
}
.panel-loading {
  color: var(--ink-500);
  font-size: 12px;
}
.composer {
  display: flex;
  align-items: flex-end;
  gap: 9px;
  padding-top: 12px;
}
.composer .el-textarea {
  flex: 1;
}
.composer :deep(.el-textarea__inner) {
  min-height: 42px !important;
  padding: 10px 12px;
  border-radius: 8px;
}
.composer .el-button {
  min-width: 78px;
  height: 42px;
}
@media (max-width: 640px) {
  .chat-page {
    min-height: 0;
  }
  .chat-header .page-subtitle,
  .session-label {
    display: none;
  }
  .chat-stream {
    padding: 14px 10px;
  }
  .example-grid {
    grid-template-columns: 1fr;
  }
  .message-row {
    grid-template-columns: 28px minmax(0, 1fr);
    gap: 8px;
  }
  .user-row {
    grid-template-columns: minmax(0, 1fr) 28px;
  }
  .message-label {
    width: 28px;
    height: 28px;
  }
  .user-message {
    max-width: 88%;
  }
  .risk-state,
  .terminal-state {
    flex-direction: column;
  }
  .composer .el-button {
    min-width: 62px;
  }
}
</style>
