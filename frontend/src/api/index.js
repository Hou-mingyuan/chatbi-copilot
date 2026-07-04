import axios from 'axios'
import { ElMessage } from 'element-plus'

const http = axios.create({
  baseURL: '/api',
  timeout: 120000
})

http.interceptors.response.use(
  (resp) => {
    if (resp.config.responseType === 'blob') {
      return resp
    }
    const body = resp.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) {
        return body.data
      }
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body
  },
  (error) => {
    const msg = error.response?.data?.message || error.message || '网络错误'
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export const api = {
  // datasources
  listDatasources: () => http.get('/datasources'),
  getDatasource: (id) => http.get(`/datasources/${id}`),
  createDatasource: (data) => http.post('/datasources', data),
  updateDatasource: (id, data) => http.put(`/datasources/${id}`, data),
  deleteDatasource: (id) => http.delete(`/datasources/${id}`),
  testDatasource: (data) => http.post('/datasources/test', data),
  testDatasourceById: (id) => http.post(`/datasources/${id}/test`),
  getSchema: (id, refresh = false) => http.get(`/datasources/${id}/schema`, { params: { refresh } }),

  // llm
  llmStatus: () => http.get('/llm/status'),

  // query
  ask: (data) => http.post('/query/ask', data),
  run: (data) => http.post('/query/run', data),

  // history
  listHistory: (params) => http.get('/history', { params }),
  deleteHistory: (id) => http.delete(`/history/${id}`),
  clearHistory: (datasourceId) => http.delete('/history', { params: { datasourceId } }),

  // favorites
  listFavorites: (datasourceId) => http.get('/favorites', { params: { datasourceId } }),
  createFavorite: (data) => http.post('/favorites', data),
  deleteFavorite: (id) => http.delete(`/favorites/${id}`),

  // semantic layer
  listSemantic: (datasourceId) => http.get('/semantic', { params: { datasourceId } }),
  createSemantic: (data) => http.post('/semantic', data),
  updateSemantic: (id, data) => http.put(`/semantic/${id}`, data),
  deleteSemantic: (id) => http.delete(`/semantic/${id}`),

  // export
  exportExcel: (data) => http.post('/export/excel', data, { responseType: 'blob' })
}

export default http
