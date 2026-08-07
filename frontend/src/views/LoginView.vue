<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { errorMessage } from '@/api'
import { useAppStore } from '@/stores/app'

const store = useAppStore()
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const feedback = ref('')
const form = reactive({ username: '', password: '' })

async function submit() {
  feedback.value = ''
  if (!form.username.trim() || !form.password) {
    feedback.value = '请输入用户名和密码'
    return
  }
  loading.value = true
  try {
    await store.login({ username: form.username.trim(), password: form.password })
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/')
      ? route.query.redirect
      : '/chat'
    await router.replace(redirect)
  } catch (error) {
    feedback.value = errorMessage(error)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-intro">
      <div class="login-brand"><span>BI</span> ChatBI Copilot</div>
      <div class="intro-copy">
        <p class="eyebrow">可信问数工作台</p>
        <h1>从业务问题到可追溯的数据答案</h1>
        <p>权限裁剪、只读 SQL 校验、执行计划和结果快照共同保护每一次查询。</p>
      </div>
      <dl class="trust-list">
        <div><dt>01</dt><dd>数据权限在服务端强制执行</dd></div>
        <div><dt>02</dt><dd>图表、解读和导出共用查询快照</dd></div>
        <div><dt>03</dt><dd>每次请求均有可核查的审计记录</dd></div>
      </dl>
    </section>

    <section class="login-panel">
      <form class="login-card" @submit.prevent="submit">
        <div>
          <p class="eyebrow">账户验证</p>
          <h2>登录工作区</h2>
          <p class="login-help">使用管理员分配的账户。演示账户说明见项目 README。</p>
        </div>
        <label>
          <span>用户名</span>
          <input
            v-model="form.username"
            class="login-input"
            autocomplete="username"
            autofocus
            placeholder="请输入用户名"
            :disabled="loading"
            @input="feedback = ''"
          />
        </label>
        <label>
          <span>密码</span>
          <input
            v-model="form.password"
            class="login-input"
            type="password"
            autocomplete="current-password"
            placeholder="请输入密码"
            :disabled="loading"
            @input="feedback = ''"
          />
        </label>
        <p v-if="feedback" class="login-error" role="alert">{{ feedback }}</p>
        <button class="login-submit" type="submit" :aria-busy="loading" :disabled="loading || !store.online">
          <span v-if="loading" class="login-spinner" aria-hidden="true" />
          {{ loading ? '登录中' : store.online ? '登录' : '等待网络恢复' }}
        </button>
        <p class="login-footnote">会话仅保存在安全 Cookie 中；退出后立即失效。</p>
      </form>
    </section>
  </main>
</template>
