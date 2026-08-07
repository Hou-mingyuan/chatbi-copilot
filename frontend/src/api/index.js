import axios from 'axios'

const UNSAFE_METHODS = new Set(['post', 'put', 'patch', 'delete'])
let unauthorizedHandler = null

export class ApiError extends Error {
  constructor(message, options = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = options.status ?? 0
    this.code = options.code ?? this.status
    this.requestId = options.requestId ?? null
    this.offline = Boolean(options.offline)
    this.cancelled = Boolean(options.cancelled)
  }
}

export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler
}

export function readCookie(name) {
  if (typeof document === 'undefined') return ''
  const prefix = `${encodeURIComponent(name)}=`
  const value = document.cookie.split('; ').find((part) => part.startsWith(prefix))
  return value ? decodeURIComponent(value.slice(prefix.length)) : ''
}

const http = axios.create({
  baseURL: '/api',
  timeout: 30_000,
  withCredentials: true,
  xsrfCookieName: 'CHATBI_CSRF',
  xsrfHeaderName: 'X-CSRF-Token'
})

http.interceptors.request.use((config) => {
  const method = String(config.method || 'get').toLowerCase()
  if (UNSAFE_METHODS.has(method) && !config.headers?.['X-CSRF-Token']) {
    const token = readCookie('CHATBI_CSRF') || sessionStorage.getItem('chatbi_csrf')
    if (token) config.headers['X-CSRF-Token'] = token
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    if (response.config.responseType === 'blob') return response
    const body = response.data
    if (body && typeof body === 'object' && Object.hasOwn(body, 'code')) {
      if (body.code === 0) return body.data
      throw new ApiError(body.message || '请求失败', {
        status: response.status,
        code: body.code,
        requestId: body.requestId
      })
    }
    return body
  },
  (error) => {
    if (axios.isCancel(error) || error.code === 'ERR_CANCELED') {
      return Promise.reject(new ApiError('请求已取消', { cancelled: true }))
    }
    const status = error.response?.status ?? 0
    const body = error.response?.data
    const apiError = new ApiError(body?.message || (status ? `请求失败（${status}）` : '网络连接失败'), {
      status,
      code: body?.code,
      requestId: body?.requestId || error.response?.headers?.['x-request-id'],
      offline: !error.response
    })
    const isLogin = String(error.config?.url || '').includes('/auth/login')
    if (status === 401 && !isLogin && unauthorizedHandler) unauthorizedHandler(apiError)
    return Promise.reject(apiError)
  }
)

export function errorMessage(error) {
  if (error?.requestId) return `${error.message}（请求 ID：${error.requestId}）`
  return error?.message || '请求失败'
}

export const api = {
  login: (data) => http.post('/auth/login', data),
  me: () => http.get('/auth/me'),
  logout: () => http.post('/auth/logout'),

  listDatasources: () => http.get('/datasources'),
  getDatasource: (id) => http.get(`/datasources/${id}`),
  createDatasource: (data) => http.post('/datasources', data),
  updateDatasource: (id, data) => http.put(`/datasources/${id}`, data),
  deleteDatasource: (id) => http.delete(`/datasources/${id}`),
  testDatasource: (data) => http.post('/datasources/test', data),
  testDatasourceById: (id) => http.post(`/datasources/${id}/test`),
  getSchema: (id, refresh = false) => http.get(`/datasources/${id}/schema`, { params: { refresh } }),

  llmStatus: () => http.get('/llm/status'),

  createAskJob: (data) => http.post('/query/jobs/ask', data),
  createRunJob: (data) => http.post('/query/jobs/run', data),
  getQueryJob: (id, config = {}) => http.get(`/query/jobs/${id}`, config),
  listQueryJobs: (datasourceId) => http.get('/query/jobs', { params: { datasourceId } }),
  cancelQueryJob: (id) => http.delete(`/query/jobs/${id}`),
  retryQueryJob: (id, confirmRisk = false) =>
    http.post(`/query/jobs/${id}/retry`, null, { params: { confirmRisk } }),

  listSessions: (datasourceId) => http.get('/sessions', { params: { datasourceId } }),
  deleteSession: (id) => http.delete(`/sessions/${id}`),

  listHistory: (params) => http.get('/history', { params }),
  getHistoryResult: (id) => http.get(`/history/${id}/result`),
  deleteHistory: (id) => http.delete(`/history/${id}`),
  clearHistory: (datasourceId) => http.delete('/history', { params: { datasourceId } }),

  listFavorites: (datasourceId) => http.get('/favorites', { params: { datasourceId } }),
  createFavorite: (data) => http.post('/favorites', data),
  deleteFavorite: (id) => http.delete(`/favorites/${id}`),

  listSemantic: (datasourceId) => http.get('/semantic', { params: { datasourceId } }),
  createSemantic: (data) => http.post('/semantic', data),
  updateSemantic: (id, data) => http.put(`/semantic/${id}`, data),
  deleteSemantic: (id) => http.delete(`/semantic/${id}`),
  semanticRevisions: (id) => http.get(`/semantic/${id}/revisions`),
  semanticPromptPreview: (datasourceId, question) =>
    http.get('/semantic/prompt-preview', { params: { datasourceId, question } }),

  exportExcel: (queryId) => http.get(`/export/excel/${queryId}`, { responseType: 'blob', timeout: 60_000 }),

  listUsers: () => http.get('/admin/users'),
  createUser: (data) => http.post('/admin/users', data),
  updateUser: (id, data) => http.put(`/admin/users/${id}`, data),
  getGrant: (userId, datasourceId) =>
    http.get(`/admin/permissions/users/${userId}/datasources/${datasourceId}`),
  replaceGrant: (userId, datasourceId, data) =>
    http.put(`/admin/permissions/users/${userId}/datasources/${datasourceId}`, data),
  getSensitiveColumns: (datasourceId) =>
    http.get(`/admin/permissions/datasources/${datasourceId}/sensitive-columns`),
  replaceSensitiveColumns: (datasourceId, columns) =>
    http.put(`/admin/permissions/datasources/${datasourceId}/sensitive-columns`, { columns }),
  listAudit: (params) => http.get('/audit', { params })
}

export default http
