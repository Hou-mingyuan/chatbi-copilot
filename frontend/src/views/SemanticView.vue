<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '@/api'
import { useAppStore } from '@/stores/app'

const store = useAppStore()
const { currentDatasourceId } = storeToRefs(store)

const list = ref([])
const schema = ref(null)
const loading = ref(false)

const dialogVisible = ref(false)
const editingId = ref(null)
const form = reactive({ tableName: '', columnName: '', businessAlias: '', description: '' })

const tables = computed(() => schema.value?.tables || [])
const columnsOfTable = computed(() => {
  const t = tables.value.find((x) => x.name === form.tableName)
  return t ? t.columns : []
})

async function load() {
  if (!currentDatasourceId.value) {
    list.value = []
    schema.value = null
    return
  }
  loading.value = true
  try {
    list.value = await api.listSemantic(currentDatasourceId.value)
    schema.value = await api.getSchema(currentDatasourceId.value, false)
  } catch (e) {
    /* handled */
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { tableName: '', columnName: '', businessAlias: '', description: '' })
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, {
    tableName: row.tableName,
    columnName: row.columnName || '',
    businessAlias: row.businessAlias || '',
    description: row.description || ''
  })
  dialogVisible.value = true
}

async function save() {
  if (!form.tableName) {
    ElMessage.warning('请选择表')
    return
  }
  const payload = { datasourceId: currentDatasourceId.value, ...form }
  try {
    if (editingId.value) {
      await api.updateSemantic(editingId.value, payload)
    } else {
      await api.createSemantic(payload)
    }
    ElMessage.success('已保存')
    dialogVisible.value = false
    await load()
  } catch (e) {
    /* handled */
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm('确定删除该语义配置？', '确认', { type: 'warning' })
    await api.deleteSemantic(row.id)
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
        <h2 class="page-title">语义层</h2>
        <p class="page-subtitle">为表 / 字段配置业务别名与描述，显著提升 Text2SQL 准确率</p>
      </div>
      <span class="spacer" />
      <el-button type="primary" :icon="'Plus'" :disabled="!currentDatasourceId" @click="openCreate">
        添加语义
      </el-button>
    </div>

    <el-alert
      v-if="!currentDatasourceId"
      type="info"
      show-icon
      :closable="false"
      title="请先在右上角选择数据源"
    />

    <el-table v-else :data="list" v-loading="loading" border stripe>
      <el-table-column prop="tableName" label="表" min-width="140" />
      <el-table-column label="字段" min-width="140">
        <template #default="{ row }">
          <span v-if="row.columnName">{{ row.columnName }}</span>
          <el-tag v-else size="small" type="info" effect="plain">整表</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="businessAlias" label="业务别名" min-width="140" />
      <el-table-column prop="description" label="业务描述" min-width="200" show-overflow-tooltip />
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button size="small" text @click="openEdit(row)">编辑</el-button>
          <el-button size="small" text type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑语义' : '添加语义'" width="520px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="表" required>
          <el-select v-model="form.tableName" filterable style="width: 100%" @change="form.columnName = ''">
            <el-option v-for="t in tables" :key="t.name" :label="t.name" :value="t.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="字段">
          <el-select v-model="form.columnName" clearable filterable placeholder="留空表示整表" style="width: 100%">
            <el-option v-for="c in columnsOfTable" :key="c.name" :label="c.name" :value="c.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务别名">
          <el-input v-model="form.businessAlias" placeholder="如：成交额 / 客户等级" />
        </el-form-item>
        <el-form-item label="业务描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="如：status=paid 表示已支付" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
