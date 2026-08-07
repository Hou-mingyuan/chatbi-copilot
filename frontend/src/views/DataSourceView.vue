<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, errorMessage } from '@/api'
import { useAppStore } from '@/stores/app'

const store = useAppStore()
const list = ref([])
const loading = ref(false)
const loadError = ref('')
const dialogVisible = ref(false)
const editingId = ref(null)
const testing = ref(false)
const saving = ref(false)
const connectionResult = ref(null)
const resultDialog = ref(false)
const form = reactive({
  name: '', dbType: 'mysql', host: '127.0.0.1', port: 3306,
  databaseName: '', username: '', password: '', jdbcParams: '', remark: ''
})
const schemaDrawer = ref(false)
const schemaLoading = ref(false)
const schema = ref(null)

watch(() => form.dbType, (type) => {
  if (type === 'postgresql' && form.port === 3306) form.port = 5432
  if (type === 'mysql' && form.port === 5432) form.port = 3306
})

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    list.value = await api.listDatasources()
    await store.loadDatasources()
  } catch (error) {
    loadError.value = errorMessage(error)
  } finally {
    loading.value = false
  }
}

function resetForm() {
  Object.assign(form, {
    name: '', dbType: 'mysql', host: '127.0.0.1', port: 3306,
    databaseName: '', username: '', password: '', jdbcParams: '', remark: ''
  })
  connectionResult.value = null
}

function openCreate() {
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, {
    name: row.name, dbType: row.dbType, host: row.host, port: row.port,
    databaseName: row.databaseName, username: row.username, password: '',
    jdbcParams: row.jdbcParams || '', remark: row.remark || ''
  })
  connectionResult.value = null
  dialogVisible.value = true
}

function valid() {
  return form.name.trim() && form.host.trim() && form.databaseName.trim() && form.username.trim()
}

async function testConnection() {
  if (!valid() || (editingId.value && !form.password)) {
    ElMessage.warning(editingId.value ? '测试修改后的连接时需要重新输入密码' : '请填写名称、主机、数据库名和用户名')
    return
  }
  testing.value = true
  connectionResult.value = null
  try {
    connectionResult.value = await api.testDatasource({ ...form })
    ElMessage.success('连接成功且只读权限已核验')
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    testing.value = false
  }
}

async function save() {
  if (!valid()) {
    ElMessage.warning('请填写名称、主机、数据库名和用户名')
    return
  }
  saving.value = true
  try {
    if (editingId.value) await api.updateDatasource(editingId.value, { ...form })
    else await api.createDatasource({ ...form })
    ElMessage.success(editingId.value ? '数据源已更新并重新核验' : '数据源已创建并核验只读权限')
    dialogVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    saving.value = false
  }
}

async function testById(row) {
  testing.value = true
  try {
    connectionResult.value = await api.testDatasourceById(row.id)
    resultDialog.value = true
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    testing.value = false
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`删除数据源“${row.name}”？其历史快照不会转移。`, '确认删除', { type: 'warning' })
    await api.deleteDatasource(row.id)
    ElMessage.success('数据源已删除')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(errorMessage(error))
  }
}

async function viewSchema(row, refresh = false) {
  schemaDrawer.value = true
  schemaLoading.value = true
  schema.value = null
  try {
    schema.value = await api.getSchema(row.id, refresh)
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    schemaLoading.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="page-card">
    <header class="toolbar">
      <div>
        <p class="eyebrow">连接管理</p>
        <h1 class="page-title">数据源</h1>
        <p class="page-subtitle">仅通过独立只读账号连接 MySQL / PostgreSQL；通过核验后才允许执行查询。</p>
      </div>
      <span class="spacer" />
      <el-button v-if="store.isAdmin" type="primary" @click="openCreate">新增数据源</el-button>
    </header>

    <el-alert v-if="loadError" type="error" :closable="false" :title="loadError" class="state-alert">
      <template #default><el-button size="small" @click="load">重试</el-button></template>
    </el-alert>
    <el-empty v-else-if="!loading && !list.length" description="当前账户没有可访问的数据源" />

    <div v-else class="table-scroll">
      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="name" label="名称" min-width="150" />
        <el-table-column prop="dbType" label="类型" width="110"><template #default="{ row }"><el-tag size="small" effect="plain">{{ row.dbType }}</el-tag></template></el-table-column>
        <el-table-column label="连接" min-width="240"><template #default="{ row }"><span class="mono">{{ row.host }}:{{ row.port }}/{{ row.databaseName }}</span></template></el-table-column>
        <el-table-column prop="username" label="只读账号" min-width="130" />
        <el-table-column label="只读核验" min-width="150">
          <template #default="{ row }">
            <el-tag :type="row.verifiedReadOnly ? 'success' : 'danger'" size="small">{{ row.verifiedReadOnly ? '已通过' : '未通过' }}</el-tag>
            <small v-if="row.lastVerifiedAt" class="verified-time">{{ row.lastVerifiedAt }}</small>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
        <el-table-column label="操作" :width="store.isAdmin ? 280 : 100" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="viewSchema(row)">结构</el-button>
            <template v-if="store.isAdmin">
              <el-button size="small" text type="primary" :loading="testing" @click="testById(row)">核验</el-button>
              <el-button size="small" text @click="openEdit(row)">编辑</el-button>
              <el-button size="small" text type="danger" @click="remove(row)">删除</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑数据源' : '新增数据源'" width="min(600px, 94vw)" destroy-on-close>
      <el-form :model="form" label-position="top" class="datasource-form">
        <el-form-item label="名称" required><el-input v-model="form.name" maxlength="128" /></el-form-item>
        <el-form-item label="数据库类型" required>
          <el-select v-model="form.dbType"><el-option label="MySQL" value="mysql" /><el-option label="PostgreSQL" value="postgresql" /></el-select>
        </el-form-item>
        <el-form-item label="主机" required><el-input v-model="form.host" placeholder="IP 或可解析主机名" /></el-form-item>
        <el-form-item label="端口" required><el-input-number v-model="form.port" :min="1" :max="65535" controls-position="right" /></el-form-item>
        <el-form-item label="数据库名" required><el-input v-model="form.databaseName" /></el-form-item>
        <el-form-item label="只读用户名" required><el-input v-model="form.username" autocomplete="off" /></el-form-item>
        <el-form-item label="密码" :required="!editingId">
          <el-input v-model="form.password" type="password" show-password autocomplete="new-password" :placeholder="editingId ? '留空保留现有密码' : '数据库只读账号密码'" />
        </el-form-item>
        <el-form-item label="JDBC 参数"><el-input v-model="form.jdbcParams" placeholder="仅支持安全白名单参数，如 serverTimezone=UTC" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" maxlength="512" /></el-form-item>
      </el-form>
      <div v-if="connectionResult" class="verification-box">
        <strong>{{ connectionResult.databaseProduct }} {{ connectionResult.databaseVersion }}</strong>
        <span>只读权限：{{ connectionResult.readOnlyVerified ? '通过' : '未通过' }}</span>
        <ul><li v-for="item in connectionResult.evidence" :key="item">{{ item }}</li></ul>
      </div>
      <template #footer>
        <el-button :loading="testing" @click="testConnection">测试并核验</el-button>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resultDialog" title="只读核验证据" width="min(660px, 94vw)">
      <div v-if="connectionResult" class="verification-box standalone">
        <strong>{{ connectionResult.databaseProduct }} {{ connectionResult.databaseVersion }}</strong>
        <el-tag :type="connectionResult.readOnlyVerified ? 'success' : 'danger'">{{ connectionResult.readOnlyVerified ? '只读核验通过' : '只读核验失败' }}</el-tag>
        <ul><li v-for="item in connectionResult.evidence" :key="item">{{ item }}</li></ul>
      </div>
    </el-dialog>

    <el-drawer v-model="schemaDrawer" title="可访问的库表结构" size="min(760px, 94vw)">
      <div v-loading="schemaLoading">
        <el-empty v-if="!schemaLoading && !schema?.tables?.length" description="没有可访问的表" />
        <el-collapse v-else>
          <el-collapse-item v-for="table in schema?.tables || []" :key="table.name" :name="table.name">
            <template #title><strong>{{ table.name }}</strong><span class="table-comment">{{ table.businessAlias || table.comment }}</span></template>
            <el-table :data="table.columns" size="small" border>
              <el-table-column prop="name" label="字段" min-width="150"><template #default="{ row }">{{ row.name }} <el-tag v-if="row.primaryKey" size="small" type="warning" effect="plain">PK</el-tag></template></el-table-column>
              <el-table-column prop="dataType" label="类型" width="120" />
              <el-table-column label="业务含义" min-width="180"><template #default="{ row }">{{ row.businessAlias || row.businessDescription || row.comment || '—' }}</template></el-table-column>
            </el-table>
          </el-collapse-item>
        </el-collapse>
      </div>
    </el-drawer>
  </section>
</template>

<style scoped>
.state-alert { margin-bottom: 14px; }
.table-scroll { max-width: 100%; overflow: hidden; }
.mono { font-family: ui-monospace, SFMono-Regular, Consolas, monospace; font-size: 11px; }
.verified-time { display: block; margin-top: 4px; color: var(--ink-500); font-size: 9px; }
.datasource-form { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); column-gap: 16px; }
.datasource-form :deep(.el-select), .datasource-form :deep(.el-input-number) { width: 100%; }
.verification-box { margin-top: 8px; padding: 12px; color: var(--ink-650); background: var(--surface-soft); border: 1px solid var(--line); border-radius: 7px; font-size: 12px; }
.verification-box.standalone { margin: 0; }
.verification-box strong, .verification-box span { display: block; margin-bottom: 6px; }
.verification-box ul { margin: 8px 0 0; padding-left: 18px; font-family: ui-monospace, monospace; line-height: 1.7; }
.table-comment { margin-left: 10px; color: var(--ink-500); font-size: 11px; font-weight: 400; }
@media (max-width: 640px) { .datasource-form { grid-template-columns: 1fr; } }
</style>
