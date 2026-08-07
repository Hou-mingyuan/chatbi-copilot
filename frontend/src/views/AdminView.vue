<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errorMessage } from '@/api'
import { useAppStore } from '@/stores/app'

const store = useAppStore()
const tab = ref('permissions')
const loading = ref(false)
const users = ref([])
const selectedUserId = ref(null)
const selectedDatasourceId = ref(null)
const schema = ref(null)
const globalSensitive = ref([])
const allowedSensitiveKeys = ref([])
const grant = reactive({ canQuery: false, canExport: false, canManageSemantic: false, tables: [] })

const userDialog = ref(false)
const editingUser = ref(null)
const savingUser = ref(false)
const userForm = reactive({ username: '', displayName: '', password: '', enabled: true, roles: ['VIEWER'] })

const auditRows = ref([])
const auditTotal = ref(0)
const auditPage = ref(1)
const auditLoading = ref(false)

const tables = computed(() => schema.value?.tables || [])
const selectedUser = computed(() => users.value.find((user) => user.id === selectedUserId.value))

function columnKey(column) {
  return `${column.table}.${column.column}`
}

function parseColumnKey(key) {
  const separator = key.indexOf('.')
  return { table: key.slice(0, separator), column: key.slice(separator + 1) }
}

async function loadBase() {
  loading.value = true
  try {
    const [userRows, datasourceRows] = await Promise.all([api.listUsers(), store.loadDatasources()])
    users.value = userRows
    selectedUserId.value ||= userRows.find((user) => !user.roles?.includes('ADMIN'))?.id || userRows[0]?.id
    selectedDatasourceId.value ||= datasourceRows[0]?.id
    await loadPermissionContext()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

async function loadPermissionContext() {
  if (!selectedUserId.value || !selectedDatasourceId.value) return
  loading.value = true
  try {
    const [currentGrant, currentSchema, sensitive] = await Promise.all([
      api.getGrant(selectedUserId.value, selectedDatasourceId.value),
      api.getSchema(selectedDatasourceId.value, false),
      api.getSensitiveColumns(selectedDatasourceId.value)
    ])
    schema.value = currentSchema
    globalSensitive.value = sensitive || []
    grant.canQuery = currentGrant.canQuery
    grant.canExport = currentGrant.canExport
    grant.canManageSemantic = currentGrant.canManageSemantic
    grant.tables = [...(currentGrant.tables || [])]
    allowedSensitiveKeys.value = (currentGrant.sensitiveColumns || []).map(columnKey)
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

async function saveGrant() {
  if (selectedUser.value?.roles?.includes('ADMIN')) {
    ElMessage.warning('管理员始终拥有全部权限，无需配置 ACL')
    return
  }
  loading.value = true
  try {
    await api.replaceGrant(selectedUserId.value, selectedDatasourceId.value, {
      canQuery: grant.canQuery,
      canExport: grant.canExport,
      canManageSemantic: grant.canManageSemantic,
      tables: grant.tables,
      sensitiveColumns: allowedSensitiveKeys.value.map(parseColumnKey)
    })
    ElMessage.success('权限矩阵已替换并立即生效')
    await loadPermissionContext()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

function openCreateUser() {
  editingUser.value = null
  Object.assign(userForm, { username: '', displayName: '', password: '', enabled: true, roles: ['VIEWER'] })
  userDialog.value = true
}

function openEditUser(user) {
  editingUser.value = user
  Object.assign(userForm, {
    username: user.username,
    displayName: user.displayName,
    password: '',
    enabled: user.enabled,
    roles: [...(user.roles || [])]
  })
  userDialog.value = true
}

async function saveUser() {
  if (!userForm.displayName.trim() || !userForm.roles.length || (!editingUser.value && (!userForm.username.trim() || userForm.password.length < 12))) {
    ElMessage.warning('请填写完整信息；新用户密码至少 12 位')
    return
  }
  savingUser.value = true
  try {
    if (editingUser.value) {
      await api.updateUser(editingUser.value.id, {
        displayName: userForm.displayName.trim(),
        enabled: userForm.enabled,
        roles: userForm.roles,
        password: userForm.password || null
      })
    } else {
      await api.createUser({
        username: userForm.username.trim(),
        displayName: userForm.displayName.trim(),
        password: userForm.password,
        roles: userForm.roles
      })
    }
    ElMessage.success(editingUser.value ? '用户已更新' : '用户已创建')
    userDialog.value = false
    users.value = await api.listUsers()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    savingUser.value = false
  }
}

async function loadSensitive() {
  if (!selectedDatasourceId.value) return
  loading.value = true
  try {
    const [currentSchema, sensitive] = await Promise.all([
      api.getSchema(selectedDatasourceId.value, false),
      api.getSensitiveColumns(selectedDatasourceId.value)
    ])
    schema.value = currentSchema
    globalSensitive.value = sensitive || []
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

function isGlobalSensitive(table, column) {
  return globalSensitive.value.some((item) => item.table === table && item.column === column)
}

async function toggleSensitive(table, column, checked) {
  const key = `${table}.${column}`
  const keys = new Set(globalSensitive.value.map(columnKey))
  if (checked) keys.add(key)
  else keys.delete(key)
  loading.value = true
  try {
    globalSensitive.value = await api.replaceSensitiveColumns(
      selectedDatasourceId.value,
      [...keys].map(parseColumnKey)
    )
    ElMessage.success('敏感列策略已更新')
  } catch (error) {
    ElMessage.error(errorMessage(error))
    await loadSensitive()
  } finally {
    loading.value = false
  }
}

async function loadAudit() {
  auditLoading.value = true
  try {
    const result = await api.listAudit({ page: auditPage.value, size: 20 })
    auditRows.value = result.records || []
    auditTotal.value = result.total || 0
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    auditLoading.value = false
  }
}

watch([selectedUserId, selectedDatasourceId], () => {
  if (tab.value === 'permissions') loadPermissionContext()
})
watch(tab, (value) => {
  if (value === 'audit') loadAudit()
  if (value === 'sensitive') loadSensitive()
})
onMounted(loadBase)
</script>

<template>
  <section class="page-card admin-page">
    <header class="toolbar">
      <div>
        <p class="eyebrow">系统管理</p>
        <h1 class="page-title">权限与审计</h1>
        <p class="page-subtitle">显式配置数据源、表和敏感列权限；空表集合表示不能访问任何表。</p>
      </div>
    </header>

    <el-tabs v-model="tab">
      <el-tab-pane label="权限矩阵" name="permissions">
        <div class="context-grid">
          <label>
            <span>用户</span>
            <el-select v-model="selectedUserId" filterable>
              <el-option v-for="user in users" :key="user.id" :value="user.id" :label="`${user.displayName} · ${user.username}`" />
            </el-select>
          </label>
          <label>
            <span>数据源</span>
            <el-select v-model="selectedDatasourceId" filterable>
              <el-option v-for="item in store.datasources" :key="item.id" :value="item.id" :label="`${item.name} · ${item.dbType}`" />
            </el-select>
          </label>
        </div>

        <el-alert
          v-if="selectedUser?.roles?.includes('ADMIN')"
          type="info"
          :closable="false"
          title="管理员权限不受 ACL 限制"
          description="请选择分析员或查看者账户来配置权限矩阵。"
        />

        <div v-else v-loading="loading" class="permission-layout">
          <section class="permission-section">
            <h3>能力</h3>
            <el-checkbox v-model="grant.canQuery">查询数据</el-checkbox>
            <el-checkbox v-model="grant.canExport">导出结果快照</el-checkbox>
            <el-checkbox v-model="grant.canManageSemantic">管理语义层</el-checkbox>
          </section>

          <section class="permission-section">
            <div class="section-head"><h3>允许访问的表</h3><span>{{ grant.tables.length }} / {{ tables.length }}</span></div>
            <el-checkbox-group v-model="grant.tables" class="check-grid">
              <el-checkbox v-for="table in tables" :key="table.name" :value="table.name">{{ table.name }}</el-checkbox>
            </el-checkbox-group>
            <el-empty v-if="!tables.length" description="该数据源没有可配置的表" />
          </section>

          <section class="permission-section">
            <div class="section-head"><h3>允许查看的敏感列</h3><span>{{ allowedSensitiveKeys.length }} / {{ globalSensitive.length }}</span></div>
            <el-checkbox-group v-model="allowedSensitiveKeys" class="check-grid">
              <el-checkbox v-for="column in globalSensitive" :key="columnKey(column)" :value="columnKey(column)">
                {{ column.table }}.{{ column.column }}
              </el-checkbox>
            </el-checkbox-group>
            <p v-if="!globalSensitive.length" class="muted-copy">尚未定义敏感列。</p>
          </section>

          <div class="save-row">
            <el-button type="primary" :loading="loading" @click="saveGrant">保存并替换权限</el-button>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="敏感列" name="sensitive">
        <div class="context-grid single">
          <label>
            <span>数据源</span>
            <el-select v-model="selectedDatasourceId" filterable @change="loadSensitive">
              <el-option v-for="item in store.datasources" :key="item.id" :value="item.id" :label="`${item.name} · ${item.dbType}`" />
            </el-select>
          </label>
        </div>
        <el-collapse v-loading="loading">
          <el-collapse-item v-for="table in tables" :key="table.name" :name="table.name" :title="table.name">
            <div class="column-policy-grid">
              <label v-for="column in table.columns" :key="column.name">
                <el-switch
                  :model-value="isGlobalSensitive(table.name, column.name)"
                  @change="(checked) => toggleSensitive(table.name, column.name, checked)"
                />
                <span>{{ column.name }}</span><small>{{ column.dataType }}</small>
              </label>
            </div>
          </el-collapse-item>
        </el-collapse>
      </el-tab-pane>

      <el-tab-pane label="用户" name="users">
        <div class="table-actions"><span class="spacer" /><el-button type="primary" @click="openCreateUser">新增用户</el-button></div>
        <el-table v-loading="loading" :data="users" border stripe>
          <el-table-column prop="username" label="用户名" min-width="130" />
          <el-table-column prop="displayName" label="显示名称" min-width="150" />
          <el-table-column label="角色" min-width="180">
            <template #default="{ row }"><el-tag v-for="role in row.roles" :key="role" size="small" effect="plain">{{ role }}</el-tag></template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '停用' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="操作" width="90"><template #default="{ row }"><el-button text size="small" @click="openEditUser(row)">编辑</el-button></template></el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="审计日志" name="audit">
        <el-table v-loading="auditLoading" :data="auditRows" border stripe>
          <el-table-column prop="createdAt" label="时间（UTC）" width="170" />
          <el-table-column prop="actorUserId" label="用户 ID" width="90" />
          <el-table-column prop="action" label="动作" min-width="170" />
          <el-table-column label="资源" min-width="150"><template #default="{ row }">{{ row.resourceType }} · {{ row.resourceId }}</template></el-table-column>
          <el-table-column label="结果" width="100"><template #default="{ row }"><el-tag size="small" :type="row.outcome === 'SUCCESS' ? 'success' : row.outcome === 'DENIED' ? 'warning' : 'danger'">{{ row.outcome }}</el-tag></template></el-table-column>
          <el-table-column prop="requestId" label="请求 ID" min-width="190" show-overflow-tooltip />
          <el-table-column prop="details" label="详情" min-width="220" show-overflow-tooltip />
        </el-table>
        <el-pagination
          class="pagination"
          layout="total, prev, pager, next"
          :total="auditTotal"
          :page-size="20"
          :current-page="auditPage"
          @current-change="(page) => { auditPage = page; loadAudit() }"
        />
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="userDialog" :title="editingUser ? '编辑用户' : '新增用户'" width="min(520px, 92vw)">
      <el-form :model="userForm" label-position="top">
        <el-form-item label="用户名" required><el-input v-model="userForm.username" :disabled="Boolean(editingUser)" /></el-form-item>
        <el-form-item label="显示名称" required><el-input v-model="userForm.displayName" /></el-form-item>
        <el-form-item :label="editingUser ? '重置密码（留空不修改）' : '密码（至少 12 位）'" :required="!editingUser">
          <el-input v-model="userForm.password" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="角色" required>
          <el-checkbox-group v-model="userForm.roles">
            <el-checkbox value="ADMIN">管理员</el-checkbox>
            <el-checkbox value="ANALYST">分析员</el-checkbox>
            <el-checkbox value="VIEWER">查看者</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item v-if="editingUser" label="账户状态"><el-switch v-model="userForm.enabled" active-text="启用" inactive-text="停用" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="userDialog = false">取消</el-button>
        <el-button type="primary" :loading="savingUser" @click="saveUser">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.context-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(220px, 360px));
  gap: 14px;
  margin-bottom: 18px;
}
.context-grid.single {
  grid-template-columns: minmax(220px, 360px);
}
.context-grid label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  color: var(--ink-650);
  font-size: 12px;
  font-weight: 650;
}
.permission-layout {
  display: grid;
  gap: 14px;
}
.permission-section {
  padding: 15px;
  background: var(--surface-soft);
  border: 1px solid var(--line);
  border-radius: 8px;
}
.permission-section h3 {
  margin: 0 0 11px;
  font-size: 14px;
}
.section-head {
  display: flex;
  justify-content: space-between;
  color: var(--ink-500);
  font-size: 11px;
}
.check-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 7px;
}
.save-row,
.table-actions,
.pagination {
  display: flex;
  justify-content: flex-end;
  margin: 14px 0;
}
.muted-copy {
  color: var(--ink-500);
  font-size: 12px;
}
.column-policy-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
  gap: 8px;
}
.column-policy-grid label {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 9px;
  padding: 8px 10px;
  background: var(--surface-soft);
  border: 1px solid var(--line);
  border-radius: 6px;
  font-size: 12px;
}
.column-policy-grid small {
  color: var(--ink-500);
}
@media (max-width: 640px) {
  .context-grid {
    grid-template-columns: 1fr;
  }
  .check-grid,
  .column-policy-grid {
    grid-template-columns: 1fr;
  }
}
</style>
