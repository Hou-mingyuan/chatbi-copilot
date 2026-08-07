export const TERMINAL_JOB_STATUSES = new Set([
  'SUCCEEDED',
  'PREVIEWED',
  'CLARIFICATION',
  'NEEDS_CONFIRMATION',
  'FAILED',
  'CANCELLED',
  'TIMED_OUT'
])

export function isTerminalJob(status) {
  return TERMINAL_JOB_STATUSES.has(status)
}

export function delay(ms, signal) {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) {
      reject(signal.reason || new DOMException('Aborted', 'AbortError'))
      return
    }
    const timer = setTimeout(resolve, ms)
    signal?.addEventListener('abort', () => {
      clearTimeout(timer)
      reject(signal.reason || new DOMException('Aborted', 'AbortError'))
    }, { once: true })
  })
}

export async function pollQueryJob(jobId, options) {
  const {
    fetchJob,
    onUpdate = () => {},
    signal,
    intervalMs = 350,
    maxIntervalMs = 1_200,
    timeoutMs = 100_000
  } = options
  const startedAt = Date.now()
  let interval = intervalMs

  while (true) {
    if (signal?.aborted) throw signal.reason || new DOMException('Aborted', 'AbortError')
    const job = await fetchJob(jobId, { signal })
    onUpdate(job)
    if (isTerminalJob(job.status)) return job
    if (Date.now() - startedAt >= timeoutMs) {
      throw new Error('等待查询任务超时，请检查任务列表后重试')
    }
    await delay(interval, signal)
    interval = Math.min(maxIntervalMs, Math.round(interval * 1.25))
  }
}

export function jobStatusLabel(status) {
  return {
    QUEUED: '查询已排队',
    PREPARING: '准备数据上下文',
    GENERATING_SQL: '生成查询语句',
    VALIDATING_SQL: '校验 SQL 与权限',
    ANALYZING_PLAN: '评估执行计划',
    EXECUTING: '执行只读查询',
    INTERPRETING: '生成图表与解读',
    SUCCEEDED: '查询完成',
    PREVIEWED: '预览完成',
    CLARIFICATION: '需要补充信息',
    NEEDS_CONFIRMATION: '等待风险确认',
    FAILED: '查询失败',
    CANCELLED: '查询已取消',
    TIMED_OUT: '查询超时'
  }[status] || status || '处理中'
}
