import { defineStore } from 'pinia'
import { api } from '@/api'

export const useAppStore = defineStore('app', {
  state: () => ({
    user: null,
    authReady: false,
    authLoading: false,
    online: typeof navigator === 'undefined' ? true : navigator.onLine,
    datasources: [],
    currentDatasourceId: Number(localStorage.getItem('chatbi_ds') || 0) || null,
    llm: { configured: false, provider: '', model: '', hasApiKey: false }
  }),
  getters: {
    authenticated: (state) => Boolean(state.user),
    isAdmin: (state) => Boolean(state.user?.roles?.includes('ADMIN')),
    currentDatasource: (state) =>
      state.datasources.find((item) => item.id === state.currentDatasourceId) || null
  },
  actions: {
    async restoreSession() {
      if (this.authReady || this.authLoading) return this.user
      this.authLoading = true
      try {
        this.user = await api.me()
      } catch (error) {
        if (error.status !== 401) throw error
        this.user = null
      } finally {
        this.authReady = true
        this.authLoading = false
      }
      return this.user
    },
    async login(credentials) {
      const result = await api.login(credentials)
      this.user = result.user
      this.authReady = true
      sessionStorage.setItem('chatbi_csrf', result.csrfToken)
      await Promise.all([this.loadDatasources(), this.loadLlm()])
      return result.user
    },
    async logout() {
      try {
        await api.logout()
      } finally {
        this.clearAuth()
      }
    },
    clearAuth() {
      this.user = null
      this.authReady = true
      this.datasources = []
      this.currentDatasourceId = null
      sessionStorage.removeItem('chatbi_csrf')
    },
    async bootstrapWorkspace() {
      if (!this.user) return
      await Promise.all([this.loadDatasources(), this.loadLlm()])
    },
    async loadDatasources() {
      if (!this.user) return []
      this.datasources = await api.listDatasources()
      const exists = this.datasources.some((item) => item.id === this.currentDatasourceId)
      if (!exists) this.setCurrent(this.datasources[0]?.id ?? null)
      return this.datasources
    },
    setCurrent(id) {
      this.currentDatasourceId = id ?? null
      if (id == null) localStorage.removeItem('chatbi_ds')
      else localStorage.setItem('chatbi_ds', String(id))
    },
    async loadLlm() {
      if (!this.user) return
      try {
        this.llm = await api.llmStatus()
      } catch {
        this.llm = { configured: false, provider: '', model: '', hasApiKey: false }
      }
    },
    setOnline(value) {
      this.online = value
    }
  }
})
