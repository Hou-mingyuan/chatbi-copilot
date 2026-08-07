<script setup>
import { onBeforeUnmount, onMounted } from 'vue'
import { useAppStore } from '@/stores/app'

const store = useAppStore()

function setOnline() {
  store.setOnline(navigator.onLine)
}

onMounted(() => {
  window.addEventListener('online', setOnline)
  window.addEventListener('offline', setOnline)
})

onBeforeUnmount(() => {
  window.removeEventListener('online', setOnline)
  window.removeEventListener('offline', setOnline)
})
</script>

<template>
  <router-view />
</template>
