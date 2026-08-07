#!/usr/bin/env node

const rawBase = process.argv[2] || process.env.CHATBI_SMOKE_BASE_URL || 'http://127.0.0.1:19030'
const base = rawBase.replace(/\/+$/, '')
const timeoutMs = Number.parseInt(process.env.CHATBI_SMOKE_TIMEOUT_MS || '15000', 10)
const attempts = Number.parseInt(process.env.CHATBI_SMOKE_ATTEMPTS || '180', 10)
const delayMs = Number.parseInt(process.env.CHATBI_SMOKE_RETRY_DELAY_MS || '2000', 10)
const username = process.env.CHATBI_SMOKE_USERNAME || 'admin'
const password = process.env.CHATBI_SMOKE_PASSWORD || process.env.DEMO_ADMIN_PASSWORD || 'ChatBI!Admin123'
const terminal = new Set(['SUCCEEDED', 'PREVIEWED', 'CLARIFICATION', 'NEEDS_CONFIRMATION', 'FAILED', 'CANCELLED', 'TIMED_OUT'])

try {
  await waitForReady()
  const request = await createClient()
  console.log('ok - authenticated session')

  const llm = await request('/llm/status')
  if (llm.provider !== 'mock' || llm.configured !== true) {
    throw new Error(`expected configured mock provider, got ${JSON.stringify(llm)}`)
  }
  console.log('ok - zero-key mock status')

  const datasources = await request('/datasources')
  for (const dbType of ['mysql', 'postgresql']) {
    const datasource = datasources.find((item) => item.dbType === dbType)
    if (!datasource?.verifiedReadOnly) {
      throw new Error(`verified ${dbType} demo datasource was not found`)
    }
    console.log(`ok - verified ${dbType} datasource ${datasource.id}`)

    const asked = await waitJob(request, await request('/query/jobs/ask', {
      method: 'POST',
      body: { datasourceId: datasource.id, question: '已支付订单数量和金额分别是多少？' }
    }))
    assertAggregate(asked, `${dbType} mock NL2SQL`)
    console.log(`ok - ${dbType} async mock NL2SQL result is 20 / 185551.00`)

    const regional = await waitJob(request, await request('/query/jobs/ask', {
      method: 'POST',
      body: { datasourceId: datasource.id, question: '各大区的已支付订单数量分别是多少？' }
    }))
    assertRegionalResult(regional.result, `${dbType} regional presentation`)
    await assertSnapshotFlow(request, datasource.id, regional.result, dbType)
    console.log(`ok - ${dbType} chart, summary, history, favorite and Excel snapshot flow`)

    const run = await waitJob(request, await request('/query/jobs/run', {
      method: 'POST',
      body: {
        datasourceId: datasource.id,
        sql: "SELECT COUNT(*) AS paid_order_count, SUM(total_amount) AS paid_order_amount FROM orders WHERE status='paid'"
      }
    }))
    assertAggregate(run, `${dbType} guarded SQL`)
    console.log(`ok - ${dbType} guarded SQL result is 20 / 185551.00`)
  }

  await request('/auth/logout', { method: 'POST' })
  console.log(`Mock demo smoke passed: ${base}`)
} catch (error) {
  console.error(`Mock demo smoke failed: ${error.message}`)
  process.exitCode = 1
}

async function waitForReady() {
  let lastError
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    try {
      const response = await fetchWithTimeout(`${base}/api/health/ready`)
      const body = await parseJson(response)
      if (body.code !== 0 || body.data?.status !== 'UP') throw new Error(body.message || 'not ready')
      console.log('ok - readiness')
      return
    } catch (error) {
      lastError = error
      if (attempt < attempts) await sleep(delayMs)
    }
  }
  throw lastError
}

async function createClient() {
  const response = await fetchWithTimeout(`${base}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  })
  const envelope = await parseJson(response)
  if (envelope.code !== 0) throw new Error(envelope.message || 'login failed')
  const setCookies = typeof response.headers.getSetCookie === 'function'
    ? response.headers.getSetCookie()
    : [response.headers.get('set-cookie') || '']
  const session = cookieValue(setCookies, 'CHATBI_SESSION')
  const csrfCookie = cookieValue(setCookies, 'CHATBI_CSRF')
  const csrf = envelope.data?.csrfToken
  if (!session || !csrf) throw new Error('login did not issue session and CSRF tokens')

  const request = async (route, options = {}) => {
    const res = await authenticatedFetch(route, options)
    const body = await parseJson(res)
    if (body.code !== 0) throw new Error(`${options.method || 'GET'} ${route}: ${body.message}`)
    return body.data
  }

  request.raw = async (route, options = {}) => authenticatedFetch(route, options)
  return request

  async function authenticatedFetch(route, options = {}) {
    const method = options.method || 'GET'
    const res = await fetchWithTimeout(`${base}/api${route}`, {
      method,
      headers: {
        Cookie: `CHATBI_SESSION=${session}; CHATBI_CSRF=${csrfCookie || csrf}`,
        ...(options.body ? { 'Content-Type': 'application/json' } : {}),
        ...(!['GET', 'HEAD'].includes(method) ? { 'X-CSRF-Token': csrf } : {})
      },
      body: options.body ? JSON.stringify(options.body) : undefined
    })
    return res
  }
}

async function waitJob(request, initial) {
  let job = initial
  const deadline = Date.now() + 30_000
  while (!terminal.has(job.status)) {
    if (Date.now() > deadline) throw new Error(`job ${job.id} timed out`)
    await sleep(100)
    job = await request(`/query/jobs/${job.id}`)
  }
  if (job.status !== 'SUCCEEDED') {
    throw new Error(`job ${job.id} ended in ${job.status}: ${job.errorMessage || job.message}`)
  }
  return job
}

function assertAggregate(job, label) {
  const row = job.result?.rows?.[0]
  if (!row) throw new Error(`${label} returned no rows`)
  const values = Object.values(row).map((value) => Number(value))
  if (!values.includes(20) || !values.includes(185551)) {
    throw new Error(`${label} returned unexpected values: ${JSON.stringify(row)}`)
  }
  const text = `${job.result?.summary?.text || ''} ${job.result?.explanation || ''}`
  if (!text.includes('20') || !text.includes('185551.00')) {
    throw new Error(`${label} summary does not trace the exact result: ${text}`)
  }
}

function assertRegionalResult(result, label) {
  if (!result?.queryId) throw new Error(`${label} has no immutable query id`)
  const actual = Object.fromEntries((result.rows || []).map((row) => [String(row.region), Number(row.paid_order_count)]))
  const expected = { '华东': 7, '华南': 6, '华北': 3, '华中': 2, '西南': 2 }
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw new Error(`${label} returned unexpected regional rows: ${JSON.stringify(actual)}`)
  }
  if (result.chart?.xField !== 'region' || !result.chart?.yFields?.includes('paid_order_count')) {
    throw new Error(`${label} chart does not reference the result columns: ${JSON.stringify(result.chart)}`)
  }
  const text = `${result.summary?.text || ''} ${result.explanation || ''}`
  if (!text.includes('华东') || !text.includes('7')) {
    throw new Error(`${label} summary does not trace the leading result: ${text}`)
  }
}

async function assertSnapshotFlow(request, datasourceId, result, dbType) {
  const queryId = result.queryId
  const history = await request(`/history?datasourceId=${encodeURIComponent(datasourceId)}&page=1&size=100`)
  const record = history.records?.find((item) => item.id === queryId)
  if (!record || record.status !== 'SUCCEEDED') {
    throw new Error(`${dbType} history does not contain successful query ${queryId}`)
  }

  const restored = await request(`/history/${queryId}/result`)
  assertRegionalResult(restored, `${dbType} restored history`)
  if (JSON.stringify(restored.rows) !== JSON.stringify(result.rows)) {
    throw new Error(`${dbType} restored history rows differ from query snapshot`)
  }

  const title = `Smoke ${dbType} regional orders`
  const favorite = await request('/favorites', { method: 'POST', body: { queryId, title } })
  const repeated = await request('/favorites', { method: 'POST', body: { queryId, title } })
  if (!favorite?.id || repeated.id !== favorite.id) {
    throw new Error(`${dbType} repeated favorite did not remain idempotent`)
  }
  const favorites = await request(`/favorites?datasourceId=${encodeURIComponent(datasourceId)}`)
  if (favorites.filter((item) => item.queryId === queryId).length !== 1) {
    throw new Error(`${dbType} favorite list does not contain exactly one snapshot reference`)
  }

  const excel = await request.raw(`/export/excel/${queryId}`)
  const contentType = excel.headers.get('content-type') || ''
  const disposition = excel.headers.get('content-disposition') || ''
  const bytes = new Uint8Array(await excel.arrayBuffer())
  if (!contentType.includes('application/vnd.openxmlformats-officedocument.spreadsheetml.sheet') ||
      !disposition.includes(`chatbi-query-${queryId}.xlsx`) ||
      bytes.length < 1000 || bytes[0] !== 0x50 || bytes[1] !== 0x4b) {
    throw new Error(`${dbType} Excel export is not a valid XLSX response`)
  }

  await request(`/favorites/${favorite.id}`, { method: 'DELETE' })
  const afterDelete = await request(`/favorites?datasourceId=${encodeURIComponent(datasourceId)}`)
  if (afterDelete.some((item) => item.queryId === queryId)) {
    throw new Error(`${dbType} favorite delete did not take effect`)
  }
}

function cookieValue(headers, name) {
  const prefix = `${name}=`
  for (const header of headers) {
    const part = header.split(';', 1)[0]
    if (part.startsWith(prefix)) return part.slice(prefix.length)
  }
  return ''
}

async function fetchWithTimeout(url, options = {}) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeoutMs)
  try {
    const response = await fetch(url, { ...options, signal: controller.signal })
    if (!response.ok) throw new Error(`HTTP ${response.status} from ${url}`)
    return response
  } finally {
    clearTimeout(timer)
  }
}

async function parseJson(response) {
  const text = await response.text()
  try {
    return JSON.parse(text)
  } catch {
    throw new Error(`expected JSON from ${response.url}, got ${text.slice(0, 160)}`)
  }
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}
