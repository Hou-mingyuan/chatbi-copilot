/** Progressive status labels shown while /api/query/ask is in flight. */
export const ASK_STREAM_STAGES = [
  '正在理解您的问题…',
  '正在读取 Schema 并生成 SQL…',
  '正在安全校验并执行查询…',
  '正在推荐图表类型…',
]

export function nextAskStageIndex(current) {
  if (current >= ASK_STREAM_STAGES.length - 1) return current
  return current + 1
}

/**
 * Resolve a typewriter target string from an ask response.
 */
export function pickStreamFinishText(res) {
  if (!res) return ''
  if (res.needClarification) return res.explanation || res.clarification || '需要进一步澄清'
  return res.explanation || '查询完成，正在展示结果…'
}

/**
 * Build a natural-language drill-down follow-up question.
 */
export function buildDrilldownQuestion(label, ctx = {}) {
  const dim = ctx.dimension || '维度'
  const base = (ctx.question || '').trim()
  const prefix = `请针对${dim}「${label}」做明细钻取`
  return base ? `${prefix}（原问题：${base}）` : `${prefix}，展示相关明细数据`
}
