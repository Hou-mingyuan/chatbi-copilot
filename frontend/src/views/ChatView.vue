<script setup>
import { ref, nextTick, computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '@/api'
import { useAppStore } from '@/stores/app'
import ResultPanel from '@/components/ResultPanel.vue'

defineOptions({ name: 'ChatView' })

const store = useAppStore()
const { currentDatasourceId, llm } = storeToRefs(store)
const router = useRouter()

const question = ref('')
const messages = ref([])
const turns = ref([])
const sending = ref(false)
const listRef = ref(null)

const examples = [
  '各产品类目的销售额占比',
  '2024年每月销售额趋势',
  '销售额最高的5个产品',
  '各大区的客户数量',
  '各渠道的订单数量对比',
  'VIP客户贡献了多少销售额'
]

const noDatasource = computed(() => !currentDatasourceId.value)

function scrollBottom() {
  nextTick(() => {
    const el = listRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

async function send(text) {
  const q = (typeof text === 'string' ? text : question.value).trim()
  if (!q) return
  if (noDatasource.value) {
    ElMessage.warning('请先在右上角选择数据源')
    return
  }
  messages.value.push({ role: 'user', content: q })
  messages.value.push({ role: 'assistant', loading: true })
  const am = messages.value[messages.value.length - 1]
  question.value = ''
  sending.value = true
  scrollBottom()

  try {
    const res = await api.ask({
      datasourceId: currentDatasourceId.value,
      question: q,
      history: turns.value.slice(-5)
    })
    am.loading = false
    if (res.needClarification) {
      am.clarification = res.clarification
      am.explanation = res.explanation
    } else {
      am.result = res
      turns.value.push({ question: q, sql: res.sql })
    }
  } catch (e) {
    am.loading = false
    am.error = e?.message || '查询失败'
  } finally {
    sending.value = false
    scrollBottom()
  }
}

function clearChat() {
  messages.value = []
  turns.value = []
}
</script>

<template>
  <div class="chat">
    <div class="chat-head">
      <div>
        <h2 class="page-title">智能问数</h2>
        <p class="page-subtitle">用自然语言提问，自动生成只读 SQL、执行并可视化</p>
      </div>
      <el-button v-if="messages.length" text :icon="'Delete'" @click="clearChat">清空对话</el-button>
    </div>

    <el-alert
      v-if="!llm.configured"
      type="warning"
      show-icon
      :closable="false"
      title="LLM 未配置"
      description="请在后端 .env 中配置 LLM_API_KEY / LLM_BASE_URL / LLM_MODEL 后重启服务。"
      style="margin-bottom: 12px"
    />

    <div ref="listRef" class="chat-list">
      <div v-if="!messages.length" class="welcome">
        <div class="welcome-emoji">📊</div>
        <h3>欢迎使用 ChatBI Copilot</h3>
        <p v-if="noDatasource" class="hint">
          还没有数据源，
          <el-link type="primary" @click="router.push('/datasources')">去添加一个</el-link>
          （或用 docker-compose 一键启动演示库）
        </p>
        <p v-else class="hint">试试下面的示例问题：</p>
        <div v-if="!noDatasource" class="examples">
          <el-tag
            v-for="ex in examples"
            :key="ex"
            class="example-chip"
            effect="plain"
            round
            @click="send(ex)"
          >
            {{ ex }}
          </el-tag>
        </div>
      </div>

      <template v-for="(m, i) in messages" :key="i">
        <div v-if="m.role === 'user'" class="row user">
          <div class="bubble user-bubble">{{ m.content }}</div>
          <el-avatar class="avatar" :size="34">我</el-avatar>
        </div>

        <div v-else class="row assistant">
          <el-avatar class="avatar bot" :size="34">AI</el-avatar>
          <div class="bubble bot-bubble">
            <div v-if="m.loading" class="loading">
              <el-icon class="is-loading"><Loading /></el-icon>
              正在思考并生成 SQL…
            </div>
            <el-alert
              v-else-if="m.error"
              type="error"
              :closable="false"
              show-icon
              :title="m.error"
            />
            <el-alert
              v-else-if="m.clarification"
              type="warning"
              :closable="false"
              show-icon
              title="需要澄清"
              :description="m.clarification"
            />
            <ResultPanel v-else-if="m.result" :result="m.result" />
          </div>
        </div>
      </template>
    </div>

    <div class="composer">
      <el-input
        v-model="question"
        type="textarea"
        :autosize="{ minRows: 1, maxRows: 4 }"
        resize="none"
        placeholder="例如：各产品类目的销售额占比（Enter 发送，Shift+Enter 换行）"
        :disabled="noDatasource"
        @keydown.enter.exact.prevent="send()"
      />
      <el-button
        type="primary"
        :loading="sending"
        :disabled="noDatasource || !question.trim()"
        :icon="'Promotion'"
        @click="send()"
      >
        发送
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.chat {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.chat-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}
.chat-list {
  flex: 1;
  overflow: auto;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(16, 24, 40, 0.06);
  padding: 18px;
  margin-bottom: 12px;
}
.welcome {
  text-align: center;
  color: #6b7280;
  padding: 48px 12px;
}
.welcome-emoji {
  font-size: 44px;
}
.welcome h3 {
  margin: 10px 0 6px;
  color: #374151;
}
.hint {
  font-size: 13px;
}
.examples {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
  margin-top: 14px;
}
.example-chip {
  cursor: pointer;
  padding: 8px 14px;
  font-size: 13px;
}
.example-chip:hover {
  color: var(--brand-1);
  border-color: var(--brand-1);
}
.row {
  display: flex;
  gap: 10px;
  margin-bottom: 18px;
  align-items: flex-start;
}
.row.user {
  justify-content: flex-end;
}
.avatar {
  flex-shrink: 0;
  background: #e5e7eb;
  color: #374151;
  font-size: 13px;
}
.avatar.bot {
  background: linear-gradient(135deg, var(--brand-1), var(--brand-2));
  color: #fff;
}
.bubble {
  max-width: 82%;
}
.user-bubble {
  background: linear-gradient(135deg, var(--brand-1), var(--brand-2));
  color: #fff;
  padding: 10px 14px;
  border-radius: 12px 12px 2px 12px;
  white-space: pre-wrap;
  word-break: break-word;
}
.bot-bubble {
  background: #f7f8fb;
  border: 1px solid #eef0f4;
  padding: 12px 14px;
  border-radius: 12px 12px 12px 2px;
  width: 100%;
  max-width: 92%;
}
.loading {
  color: #6b7280;
  display: flex;
  align-items: center;
  gap: 8px;
}
.composer {
  display: flex;
  gap: 10px;
  align-items: flex-end;
}
.composer .el-textarea {
  flex: 1;
}
</style>
