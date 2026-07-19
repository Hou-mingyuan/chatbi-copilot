#!/usr/bin/env node
/**
 * Mock-mode portfolio smoke: health + LLM status + NL2SQL ask + guarded SQL run (no API keys).
 */
const rawBase = process.argv[2] || process.env.CHATBI_SMOKE_BASE_URL || 'http://localhost:8080'
const timeoutMs = Number.parseInt(process.env.CHATBI_SMOKE_TIMEOUT_MS || '12000', 10)
const maxAttempts = Number.parseInt(process.env.CHATBI_SMOKE_ATTEMPTS || '24', 10)
const retryDelayMs = Number.parseInt(process.env.CHATBI_SMOKE_RETRY_DELAY_MS || '5000', 10)

const base = rawBase.replace(/\/+$/, '')

try {
  await runCheck('health', async () => {
    const body = await getJson(`${base}/api/health`)
    assertOk(body)
  })

  await runCheck('mock LLM status', async () => {
    const body = await getJson(`${base}/api/llm/status`)
    assertOk(body)
    const provider = body.data?.provider
    if (provider && provider !== 'mock') {
      throw new Error(`provider=${provider} — set LLM_PROVIDER=mock for zero-key demo`)
    }
    if (body.data?.configured !== true) {
      throw new Error('LLM not configured in mock mode')
    }
  })

  await runCheck('demo datasources', async () => {
    const body = await getJson(`${base}/api/datasources`)
    assertOk(body)
    if (!Array.isArray(body.data) || body.data.length === 0) {
      throw new Error('no datasources — ensure DEMO_DATASOURCE_ENABLED=true')
    }
  })

  await runCheck('mock NL2SQL ask', async () => {
    const body = await postJson(`${base}/api/query/ask`, {
      datasourceId: 1,
      question: '各产品类目的销售额占比',
    })
    assertOk(body)
    const result = body.data
    if (!result?.sql || !String(result.sql).toUpperCase().includes('SELECT')) {
      throw new Error(`missing SELECT sql: ${JSON.stringify(result)?.slice(0, 240)}`)
    }
    if (!Array.isArray(result.rows) || result.rows.length === 0) {
      throw new Error('ask returned no rows')
    }
  })

  await runCheck('guarded SQL run (no LLM)', async () => {
    const body = await postJson(`${base}/api/query/run`, {
      datasourceId: 1,
      sql: 'select p.category, count(*) as cnt from products p group by p.category limit 10',
    })
    assertOk(body)
    if (!Array.isArray(body.data?.rows) || body.data.rows.length === 0) {
      throw new Error('run returned no rows')
    }
  })

  console.log(`Mock demo smoke passed: ${base}`)
} catch (error) {
  console.error(`Mock demo smoke failed: ${error.message}`)
  process.exitCode = 1
}

async function runCheck(name, fn) {
  let lastError
  for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
    try {
      await fn()
      console.log(`ok - ${name}`)
      return
    } catch (error) {
      lastError = error
      if (attempt === maxAttempts) break
      await sleep(retryDelayMs)
    }
  }
  throw lastError
}

function assertOk(body) {
  if (body?.code !== 0) {
    throw new Error(`code ${body?.code}: ${body?.message ?? 'unknown'}`)
  }
}

async function getJson(url) {
  const response = await fetchWithTimeout(url)
  return parseJson(response)
}

async function postJson(url, payload) {
  const response = await fetchWithTimeout(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
  return parseJson(response)
}

async function fetchWithTimeout(url, options = {}) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeoutMs)
  try {
    return await fetch(url, { ...options, signal: controller.signal })
  } catch (error) {
    if (error.name === 'AbortError') {
      throw new Error(`${url} timed out after ${timeoutMs}ms`)
    }
    throw error
  } finally {
    clearTimeout(timer)
  }
}

async function parseJson(response) {
  const text = await response.text()
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${text.slice(0, 200)}`)
  }
  try {
    return JSON.parse(text)
  } catch {
    throw new Error(`expected JSON from ${response.url}, got: ${text.slice(0, 200)}`)
  }
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms))
}
