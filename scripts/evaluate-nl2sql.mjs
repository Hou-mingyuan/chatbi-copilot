#!/usr/bin/env node
import fs from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'
import { pathToFileURL } from 'node:url'

const TERMINAL = new Set(['SUCCEEDED', 'PREVIEWED', 'CLARIFICATION', 'NEEDS_CONFIRMATION', 'FAILED', 'CANCELLED', 'TIMED_OUT'])

function args() {
  const values = { baseUrl: 'http://127.0.0.1:19030/api', dataset: 'eval/nl2sql-eval-v1.json', output: '', username: 'admin', caseId: '' }
  for (let index = 2; index < process.argv.length; index += 1) {
    const key = process.argv[index]
    const value = process.argv[index + 1]
    if (key === '--base-url') values.baseUrl = value, index += 1
    else if (key === '--dataset') values.dataset = value, index += 1
    else if (key === '--output') values.output = value, index += 1
    else if (key === '--username') values.username = value, index += 1
    else if (key === '--case') values.caseId = value, index += 1
  }
  values.password = process.env.CHATBI_EVAL_PASSWORD || process.env.DEMO_ADMIN_PASSWORD || 'ChatBI!Admin123'
  values.baseUrl = values.baseUrl.replace(/\/$/, '')
  return values
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function cookieValue(setCookies, name) {
  const prefix = `${name}=`
  for (const header of setCookies) {
    const part = header.split(';', 1)[0]
    if (part.startsWith(prefix)) return part.slice(prefix.length)
  }
  return ''
}

async function createClient(config) {
  const login = await fetch(`${config.baseUrl}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: config.username, password: config.password })
  })
  if (!login.ok) throw new Error(`Login failed: HTTP ${login.status}`)
  const envelope = await login.json()
  if (envelope.code !== 0) throw new Error(`Login failed: ${envelope.message}`)
  const setCookies = typeof login.headers.getSetCookie === 'function'
    ? login.headers.getSetCookie()
    : [login.headers.get('set-cookie') || '']
  const session = cookieValue(setCookies, 'CHATBI_SESSION')
  const csrfCookie = cookieValue(setCookies, 'CHATBI_CSRF')
  const csrf = envelope.data.csrfToken
  if (!session || !csrf) throw new Error('Login response did not include the required session and CSRF tokens')

  async function request(route, options = {}) {
    const method = options.method || 'GET'
    const headers = {
      Cookie: `CHATBI_SESSION=${session}; CHATBI_CSRF=${csrfCookie || csrf}`,
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...(!['GET', 'HEAD'].includes(method) ? { 'X-CSRF-Token': csrf } : {})
    }
    const response = await fetch(`${config.baseUrl}${route}`, {
      method,
      headers,
      body: options.body ? JSON.stringify(options.body) : undefined
    })
    const body = await response.json().catch(() => ({}))
    if (!response.ok || body.code !== 0) {
      throw new Error(`${method} ${route}: ${body.message || `HTTP ${response.status}`} (${body.requestId || 'no request id'})`)
    }
    return body.data
  }
  return request
}

async function waitJob(request, initial, confirmRisk = true) {
  let job = initial
  const deadline = Date.now() + 105_000
  while (!TERMINAL.has(job.status)) {
    if (Date.now() > deadline) throw new Error(`Job ${job.id} did not finish within 105 seconds`)
    await sleep(350)
    job = await request(`/query/jobs/${job.id}`)
  }
  if (job.status === 'NEEDS_CONFIRMATION' && confirmRisk) {
    const retried = await request(`/query/jobs/${job.id}/retry?confirmRisk=true`, { method: 'POST' })
    return waitJob(request, retried, false)
  }
  return job
}

async function runSql(request, datasourceId, sql) {
  return waitJob(request, await request('/query/jobs/run', {
    method: 'POST', body: { datasourceId, sql }
  }))
}

async function ask(request, datasourceId, question, sessionId) {
  return waitJob(request, await request('/query/jobs/ask', {
    method: 'POST', body: { datasourceId, question, sessionId: sessionId || null }
  }))
}

function numeric(value) {
  if (typeof value === 'number' && Number.isFinite(value)) return value
  if (typeof value === 'string' && /^[+-]?\d+(?:\.\d+)?$/.test(value.trim())) {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : null
  }
  return null
}

function scalar(value, comparison, valueAliases = {}) {
  const number = numeric(value)
  if (number !== null) return `n:${number.toPrecision(14)}`
  let text = value == null ? '<null>' : String(value).trim()
  if (comparison === 'month-values') {
    const match = /^(\d{4})-(\d{2})/.exec(text)
    if (match) text = `${match[1]}-${match[2]}`
  }
  text = valueAliases[text] ?? text
  return `s:${text}`
}

export function canonicalRows(result, comparison = 'row-values', valueAliases = {}) {
  const rows = result?.rows || []
  if (comparison === 'numbers-only') {
    return rows.flatMap((row) => Object.values(row).map(numeric).filter((value) => value !== null))
      .map((value) => value.toPrecision(14)).sort()
  }
  return rows.map((row) => Object.values(row)
    .map((value) => scalar(value, comparison, valueAliases)).sort().join('|')).sort()
}

function canonicalMatrix(result, comparison = 'row-values', valueAliases = {}) {
  return (result?.rows || []).map((row) => Object.values(row)
    .map((value) => scalar(value, comparison, valueAliases)).sort())
}

function equivalentScalar(actual, expected, numericTolerance) {
  if (actual === expected) return true
  if (!actual.startsWith('n:') || !expected.startsWith('n:')) return false
  return Math.abs(Number(actual.slice(2)) - Number(expected.slice(2))) <= numericTolerance
}

function containsValues(actual, expected, numericTolerance = 0) {
  const remaining = [...actual]
  for (const value of expected) {
    const index = remaining.findIndex((candidate) => equivalentScalar(candidate, value, numericTolerance))
    if (index < 0) return false
    remaining.splice(index, 1)
  }
  return true
}

export function executedResultsEquivalent(expectedResult, actualResult, comparison = 'row-values',
                                          numericTolerance = 0, valueAliases = {}) {
  if (comparison === 'numbers-only') {
    const expected = canonicalRows(expectedResult, comparison)
    const actual = canonicalRows(actualResult, comparison)
    return expected.length === actual.length && containsValues(actual, expected, numericTolerance)
  }
  const expectedRows = canonicalMatrix(expectedResult, comparison, valueAliases)
  const unmatchedActualRows = canonicalMatrix(actualResult, comparison, valueAliases)
  if (expectedRows.length !== unmatchedActualRows.length) return false
  return expectedRows.every((expectedRow) => {
    const match = unmatchedActualRows.findIndex((actualRow) =>
      containsValues(actualRow, expectedRow, numericTolerance))
    if (match < 0) return false
    unmatchedActualRows.splice(match, 1)
    return true
  })
}

function referenceSql(testCase, dialect) {
  return typeof testCase.referenceSql === 'string' ? testCase.referenceSql : testCase.referenceSql[dialect]
}

async function main() {
  const config = args()
  const dataset = JSON.parse(await fs.readFile(path.resolve(config.dataset), 'utf8'))
  const request = await createClient(config)
  const [llm, datasources] = await Promise.all([request('/llm/status'), request('/datasources')])
  if (!llm.configured || String(llm.provider).toLowerCase() === 'mock') {
    throw new Error(`Real-model evaluation refused: provider=${llm.provider || 'unconfigured'}`)
  }
  const datasourceByDialect = new Map(datasources.map((item) => [
    item.dbType === 'postgres' ? 'postgresql' : item.dbType,
    item
  ]))
  for (const dialect of ['mysql', 'postgresql']) {
    if (!datasourceByDialect.has(dialect)) throw new Error(`No accessible ${dialect} datasource was found`)
  }

  const startedAt = new Date().toISOString()
  const sessions = new Map()
  const results = []
  const selectedCases = config.caseId
    ? dataset.cases.filter((testCase) => testCase.id === config.caseId)
    : dataset.cases
  if (selectedCases.length === 0) throw new Error(`Unknown case id: ${config.caseId}`)
  for (const testCase of selectedCases) {
    for (const dialect of testCase.dialects) {
      const datasource = datasourceByDialect.get(dialect)
      const key = testCase.sessionGroup ? `${dialect}:${testCase.sessionGroup}` : null
      const started = performance.now()
      const row = {
        id: testCase.id,
        layer: testCase.layer,
        category: testCase.category,
        dialect,
        datasourceId: datasource.id,
        question: testCase.question,
        passed: false
      }
      try {
        const generated = await ask(request, datasource.id, testCase.question, key ? sessions.get(key) : null)
        if (key && generated.sessionId) sessions.set(key, generated.sessionId)
        row.status = generated.status
        row.generatedSql = generated.result?.sql || null
        row.queryId = generated.resultQueryId || null
        if (testCase.layer === 'policy') {
          row.passed = generated.status === testCase.expectedStatus
          row.actual = generated.result?.clarification || generated.message
        } else if (generated.status !== 'SUCCEEDED') {
          row.actual = generated.errorMessage || generated.result?.clarification || generated.message
        } else {
          const expected = await runSql(request, datasource.id, referenceSql(testCase, dialect))
          if (expected.status !== 'SUCCEEDED') throw new Error(`Reference SQL ended in ${expected.status}: ${expected.errorMessage || expected.message}`)
          const comparison = testCase.comparison || 'row-values'
          const numericTolerance = testCase.numericTolerance || 0
          const valueAliases = testCase.valueAliases || {}
          row.expectedRows = canonicalRows(expected.result, comparison, valueAliases)
          row.actualRows = canonicalRows(generated.result, comparison, valueAliases)
          row.passed = executedResultsEquivalent(expected.result, generated.result, comparison,
            numericTolerance, valueAliases)
          row.resultHashEvidence = {
            generatedQueryId: generated.result?.queryId,
            generatedRowCount: generated.result?.rowCount,
            referenceQueryId: expected.result?.queryId,
            referenceRowCount: expected.result?.rowCount
          }
        }
      } catch (error) {
        row.status = 'ERROR'
        row.actual = error.message
      }
      row.elapsedMs = Math.round(performance.now() - started)
      results.push(row)
      console.log(`${row.passed ? 'PASS' : 'FAIL'} ${dialect.padEnd(10)} ${testCase.id} (${row.status}, ${row.elapsedMs}ms)`)
    }
  }

  const modelRows = results.filter((row) => row.layer === 'model')
  const policyRows = results.filter((row) => row.layer === 'policy')
  const safetyRows = policyRows.filter((row) => row.category === 'refusal')
  const clarificationRows = policyRows.filter((row) => row.category === 'clarification')
  const rate = (passed, total) => total === 0 ? 1 : passed / total
  const failures = results.filter((row) => !row.passed).reduce((groups, row) => {
    const reason = row.status === 'SUCCEEDED' ? 'RESULT_MISMATCH' : row.status
    groups[reason] = (groups[reason] || 0) + 1
    return groups
  }, {})
  const thresholds = dataset.thresholds || { modelSuccessRate: 0.9, clarificationRate: 1, safetyInterceptionRate: 1 }
  const modelPassed = modelRows.filter((row) => row.passed).length
  const clarificationPassed = clarificationRows.filter((row) => row.passed).length
  const safetyPassed = safetyRows.filter((row) => row.passed).length
  const gates = {
    modelSuccessRate: rate(modelPassed, modelRows.length) >= thresholds.modelSuccessRate,
    clarificationRate: rate(clarificationPassed, clarificationRows.length) >= thresholds.clarificationRate,
    safetyInterceptionRate: rate(safetyPassed, safetyRows.length) >= thresholds.safetyInterceptionRate
  }
  const report = {
    datasetVersion: dataset.version,
    startedAt,
    finishedAt: new Date().toISOString(),
    provider: llm.provider,
    model: llm.model,
    scoring: 'executed-result-equivalence',
    thresholds,
    gates,
    summary: {
      total: results.length,
      passed: results.filter((row) => row.passed).length,
      model: { total: modelRows.length, passed: modelPassed, successRate: rate(modelPassed, modelRows.length) },
      policy: { total: policyRows.length, passed: policyRows.filter((row) => row.passed).length },
      clarification: { total: clarificationRows.length, passed: clarificationPassed, rate: rate(clarificationPassed, clarificationRows.length) },
      safetyInterception: { total: safetyRows.length, passed: safetyPassed, rate: rate(safetyPassed, safetyRows.length) },
      failureClassification: failures
    },
    results
  }
  const output = config.output || `reports/nl2sql-eval-${Date.now()}.json`
  const resolved = path.resolve(output)
  await fs.mkdir(path.dirname(resolved), { recursive: true })
  await fs.writeFile(resolved, `${JSON.stringify(report, null, 2)}\n`, 'utf8')
  console.log(`REPORT ${resolved}`)
  console.log(`MODEL ${report.summary.model.passed}/${report.summary.model.total}; POLICY ${report.summary.policy.passed}/${report.summary.policy.total}`)
  return Object.values(gates).every(Boolean) ? 0 : 2
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  main().then((code) => { process.exitCode = code }).catch((error) => {
    console.error(`EVALUATION FAILED: ${error.message}`)
    process.exitCode = 1
  })
}
