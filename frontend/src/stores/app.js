import { defineStore } from 'pinia'
import { api } from '@/api'

export const useAppStore = defineStore('app', {
  state: () => ({
    datasources: [],
    currentDatasourceId: Number(localStorage.getItem('chatbi_ds') || 0) || null,
    llm: { configured: false, provider: '', model: '', hasApiKey: false }
  }),
  getters: {
    currentDatasource: (state) =>
      state.datasources.find((d) => d.id === state.currentDatasourceId) || null
  },
  actions: {
    async loadDatasources() {
      this.datasources = await api.listDatasources()
      const exists = this.datasources.find((d) => d.id === this.currentDatasourceId)
      if (!exists && this.datasources.length) {
        this.setCurrent(this.datasources[0].id)
      }
      if (!this.datasources.length) {
        this.currentDatasourceId = null
      }
      return this.datasources
    },
    setCurrent(id) {
      this.currentDatasourceId = id
      localStorage.setItem('chatbi_ds', String(id))
    },
    async loadLlm() {
      try {
        this.llm = await api.llmStatus()
      } catch (e) {
        this.llm = { configured: false }
      }
    }
  }
})
