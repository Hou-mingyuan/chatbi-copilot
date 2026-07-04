<script setup>
import { ref, onMounted, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '@/api'
import { useAppStore } from '@/stores/app'
import ResultPanel from '@/components/ResultPanel.vue'

const store = useAppStore()
const { currentDatasourceId } = storeToRefs(store)

const list = ref([])
const loading = ref(false)

const runDialog = ref(false)
const runResult = ref(null)
const running = ref(false)

async function load() {
  loading.value = true
  try {
    list.value = await api.listFavorites(currentDatasourceId.value || undefined)
  } finally {
    loading.value = false
  }
}

async function run(row) {
  runDialog.value = true
  running.value = true
  runResult.value = null
  try {
    runResult.value = await api.run({ datasourceId: row.datasourceId, sql: row.generatedSql })
  } catch (e) {
    runDialog.value = false
  } finally {
    running.value = false
  }
}

async function copySql(row) {
  await navigator.clipboard.writeText(row.generatedSql || '')
  ElMessage.success('SQL 已复制')
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`删除收藏「${row.title}」？`, '确认', { type: 'warning' })
    await api.deleteFavorite(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    /* cancelled */
  }
}

watch(currentDatasourceId, load)
onMounted(load)
</script>

<template>
  <div class="page-card">
    <div class="toolbar">
      <div>
        <h2 class="page-title">收藏</h2>
        <p class="page-subtitle">收藏常用查询，一键重跑</p>
      </div>
    </div>

    <el-empty v-if="!loading && !list.length" description="还没有收藏，去「智能问数」里收藏一个吧" />

    <el-row v-else :gutter="16">
      <el-col v-for="f in list" :key="f.id" :xs="24" :sm="12" :lg="8" style="margin-bottom: 16px">
        <el-card shadow="hover" class="fav-card">
          <template #header>
            <div class="fav-head">
              <el-icon color="#f59e0b"><StarFilled /></el-icon>
              <b>{{ f.title }}</b>
            </div>
          </template>
          <p class="fav-q">{{ f.question }}</p>
          <pre class="fav-sql">{{ f.generatedSql }}</pre>
          <div class="fav-actions">
            <el-button size="small" type="primary" text @click="run(f)">运行</el-button>
            <el-button size="small" text @click="copySql(f)">复制 SQL</el-button>
            <el-button size="small" text type="danger" @click="remove(f)">删除</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="runDialog" title="运行结果" width="70%" top="6vh">
      <div v-loading="running" style="min-height: 120px">
        <ResultPanel v-if="runResult" :result="runResult" />
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.fav-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.fav-q {
  color: #374151;
  font-size: 14px;
  margin: 0 0 8px;
}
.fav-sql {
  background: #f5f7fb;
  border-radius: 6px;
  padding: 8px 10px;
  font-size: 12px;
  color: #475569;
  max-height: 110px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0 0 10px;
}
.fav-actions {
  display: flex;
  gap: 4px;
}
</style>
