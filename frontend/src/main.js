import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import { setUnauthorizedHandler } from './api'
import { useAppStore } from './stores/app'
import './assets/main.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)

const store = useAppStore(pinia)
setUnauthorizedHandler(() => {
  store.clearAuth()
  const current = router.currentRoute.value
  if (current.name !== 'login') {
    router.replace({ name: 'login', query: { redirect: current.fullPath } })
  }
})

app.mount('#app')
