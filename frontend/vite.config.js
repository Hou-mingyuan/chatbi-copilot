import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { visualizer } from 'rollup-plugin-visualizer'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

const analyze = process.env.ANALYZE === '1'

export default defineConfig({
  plugins: [
    vue(),
    Components({
      dts: false,
      resolvers: [ElementPlusResolver({ directives: true })]
    }),
    analyze &&
      visualizer({
        filename: 'dist/stats.html',
        gzipSize: true,
        brotliSize: true,
        open: false
      })
  ].filter(Boolean),
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    host: '127.0.0.1',
    port: 19031,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:19030',
        changeOrigin: true
      }
    }
  },
  build: {
    chunkSizeWarningLimit: 1500,
    rollupOptions: {
      output: {
        manualChunks(id) {
          const normalized = id.replaceAll('\\', '/')
          if (normalized.includes('/node_modules/echarts/')) return 'echarts'
          if (
            normalized.includes('/node_modules/vue/') ||
            normalized.includes('/node_modules/vue-router/') ||
            normalized.includes('/node_modules/pinia/')
          ) return 'vue'
        }
      }
    }
  }
})
