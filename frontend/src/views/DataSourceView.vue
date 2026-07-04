<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '@/api'
import { useAppStore } from '@/stores/app'

const store = useAppStore()
const list = ref([])
const loading = ref(false)

const dialogVisible = ref(false)
const editingId = ref(null)
const testing = ref(false)
const saving = ref(false)
const form = reactive({
  name: '',
  dbType: 'mysql',
  host: 'localhost',
  port: 3306,
  databaseName: '',
  username: '',
  password: '',
  jdbcParams: '',
  remark: ''
})

const schemaDrawer = ref(false)
const schemaLoading = ref(false)
const schema = ref(null)

watch(
  () => form.dbType,
  (t) => {
    if (t === 'postgresql') form.port = form.port === 3306 ? 5432 : form.port
    if (t === 'mysql') form.port = form.port === 5432 ? 3306 : form.port
  }
)

async function load() {
  loading.value = true
  try {
    list.value = await api.listDatasources()
    await store.loadDatasources()
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    name: '', dbType: 'mysql', host: 'localhost', port: 3306,
    databaseName: '', username: '', password: '', jdbcParams: '', remark: ''
  })
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, {
    name: row.name, dbType: row.dbType, host: row.host, port: row.port,
    databaseName: row.databaseName, username: row.username, password: '',
    jdbcParams: row.jdbcParams, remark: row.remark
  })
  dialogVisible.value = true
}

async function testConnection() {
  testing.value = true
  try {
    await api.testDatasource({ ...form })
    ElMessage.success('连接成功')
  } catch (e) {
    /* handled */
  } finally {
    testing.value = false
  }
}

async function save() {
  if (!form.name || !form.host || !form.databaseName) {
    ElMessage.warning('请填写名称、主机、数据库名')
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await api.updateDatasource(editingId.value, { ...form })
      ElMessage.success('已更新')
    } else {
      await api.createDatasource({ ...form })
      ElMessage.success('已创建')
    }
    dialogVisible.value = false
    await load()
  } catch (e) {
    /* handled */
  } finally {
    saving.value = false
  }
}

async function testById(row) {
  try {
    await api.testDatasourceById(row.id)
    ElMessage.success(`「${row.name}」连接正常`)
  } catch (e) {
    /* handled */
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确定删除数据源「${row.name}」？`, '确认', { type: 'warning' })
    await api.deleteDatasource(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    /* cancelled */
  }
}

async function viewSchema(row) {
  schemaDrawer.value = true
  schemaLoading.value = true
  schema.value = null
  try {
    schema.value = await api.getSchema(row.id, true)
  } catch (e) {
    /* handled */
  } finally {
    schemaLoading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page-card">
    <div class="toolbar">
      <div>
        <h2 class="page-title">数据源管理</h2>
        <p class="page-subtitle">连接 MySQL / PostgreSQL，自动读取库表结构与字段注释</p>
      </div>
      <span class="spacer" />
      <el-button type="primary" :icon="'Plus'" @click="openCreate">新增数据源</el-button>
    </div>

    <el-table :data="list" v-loading="loading" border stripe>
      <el-table-column prop="name" label="名称" min-width="140" />
      <el-table-column prop="dbType" label="类型" width="110">
        <template #default="{ row }">
          <el-tag size="small" :type="row.dbType === 'mysql' ? 'primary' : 'success'">
            {{ row.dbType }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="地址" min-width="220">
        <template #default="{ row }">{{ row.host }}:{{ row.port }}/{{ row.databaseName }}</template>
      </el-table-column>
      <el-table-column prop="username" label="用户名" width="120" />
      <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
      <el-table-column label="操作" width="300" fixed="right">
        <template #default="{ row }">
          <el-button size="small" text type="primary" @click="testById(row)">测试</el-button>
          <el-button size="small" text type="primary" @click="viewSchema(row)">查看结构</el-button>
          <el-button size="small" text @click="openEdit(row)">编辑</el-button>
          <el-button size="small" text type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑数据源' : '新增数据源'"
      width="560px"
    >
      <el-form :model="form" label-width="96px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="如：生产销售库" />
        </el-form-item>
        <el-form-item label="数据库类型">
          <el-select v-model="form.dbType" style="width: 100%">
            <el-option label="MySQL" value="mysql" />
            <el-option label="PostgreSQL" value="postgresql" />
          </el-select>
        </el-form-item>
        <el-form-item label="主机" required>
          <el-input v-model="form.host" placeholder="localhost 或 IP" />
        </el-form-item>
        <el-form-item label="端口" required>
          <el-input-number v-model="form.port" :min="1" :max="65535" controls-position="right" />
        </el-form-item>
        <el-form-item label="数据库名" required>
          <el-input v-model="form.databaseName" placeholder="如：chatbi_demo" />
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="form.username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :placeholder="editingId ? '留空则不修改' : ''"
          />
        </el-form-item>
        <el-form-item label="JDBC参数">
          <el-input v-model="form.jdbcParams" placeholder="可选，如 useSSL=false" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :loading="testing" @click="testConnection">测试连接</el-button>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="schemaDrawer" title="库表结构" size="42%">
      <div v-loading="schemaLoading">
        <el-empty v-if="!schema || !schema.tables?.length" description="暂无表" />
        <el-collapse v-else>
          <el-collapse-item
            v-for="t in schema.tables"
            :key="t.name"
            :name="t.name"
          >
            <template #title>
              <b>{{ t.name }}</b>
              <span v-if="t.comment" style="color: #8a94a6; margin-left: 8px">{{ t.comment }}</span>
            </template>
            <el-table :data="t.columns" size="small" border>
              <el-table-column prop="name" label="字段" min-width="140">
                <template #default="{ row }">
                  {{ row.name }}
                  <el-tag v-if="row.primaryKey" size="small" type="warning" effect="plain">PK</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="dataType" label="类型" width="110" />
              <el-table-column prop="comment" label="注释" min-width="140" show-overflow-tooltip />
            </el-table>
          </el-collapse-item>
        </el-collapse>
      </div>
    </el-drawer>
  </div>
</template>
