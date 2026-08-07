import http from 'k6/http'
import { check, sleep } from 'k6'
import { Rate } from 'k6/metrics'

const BASE = (__ENV.BASE_URL || 'http://127.0.0.1:19030/api').replace(/\/$/, '')
const USERNAME = __ENV.CHATBI_PERF_USERNAME || 'admin'
const PASSWORD = __ENV.CHATBI_PERF_PASSWORD || 'ChatBI!Admin123'
const businessErrors = new Rate('business_errors')

export const options = {
  scenarios: {
    authenticatedSmoke: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 10),
      duration: __ENV.DURATION || '30s'
    }
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    business_errors: ['rate==0'],
    'http_req_duration{endpoint:read}': ['p(95)<300'],
    'http_req_duration{endpoint:write}': ['p(95)<800']
  }
}

export function setup() {
  const login = http.post(`${BASE}/auth/login`, JSON.stringify({ username: USERNAME, password: PASSWORD }), {
    headers: { 'Content-Type': 'application/json' }
  })
  const envelope = login.json()
  if (login.status !== 200 || envelope?.code !== 0) {
    throw new Error(`login failed: HTTP ${login.status} ${login.body}`)
  }
  const session = login.cookies.CHATBI_SESSION?.[0]?.value
  const csrfCookie = login.cookies.CHATBI_CSRF?.[0]?.value
  const csrf = envelope.data?.csrfToken
  if (!session || !csrf) throw new Error('login did not return session and CSRF tokens')
  const cookie = `CHATBI_SESSION=${session}; CHATBI_CSRF=${csrfCookie || csrf}`
  const datasources = http.get(`${BASE}/datasources`, { headers: { Cookie: cookie } }).json()
  const datasource = datasources?.data?.find((item) => item.dbType === 'mysql')
  if (!datasource?.verifiedReadOnly) throw new Error('verified MySQL datasource not found')
  return { cookie, csrf, datasourceId: datasource.id }
}

export default function (data) {
  const read = http.get(`${BASE}/auth/me`, {
    headers: { Cookie: data.cookie },
    tags: { endpoint: 'read' }
  })
  const readOk = check(read, {
    'authenticated read succeeds': (response) => response.status === 200 && response.json()?.code === 0
  })
  businessErrors.add(!readOk)

  const run = http.post(`${BASE}/query/run`, JSON.stringify({
    datasourceId: data.datasourceId,
    sql: "SELECT COUNT(*) AS paid_order_count, SUM(total_amount) AS paid_order_amount FROM orders WHERE status='paid'"
  }), {
    headers: {
      Cookie: data.cookie,
      'Content-Type': 'application/json',
      'X-CSRF-Token': data.csrf
    },
    tags: { endpoint: 'write' }
  })
  const runOk = check(run, {
    'guarded query succeeds with fixed result': (response) => {
      if (response.status !== 200) return false
      const body = response.json()
      const values = Object.values(body?.data?.rows?.[0] || {}).map(Number)
      return body?.code === 0 && values.includes(20) && values.includes(185551)
    }
  })
  businessErrors.add(!runOk)
  sleep(Number(__ENV.THINK_TIME_SECONDS || 0.2))
}

export function teardown(data) {
  http.post(`${BASE}/auth/logout`, null, {
    headers: { Cookie: data.cookie, 'X-CSRF-Token': data.csrf }
  })
}
