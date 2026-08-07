/**
 * Build a natural-language drill-down follow-up question.
 */
export function buildDrilldownQuestion(label, ctx = {}) {
  const dim = ctx.dimension || '维度'
  const base = (ctx.question || '').trim()
  const prefix = `请针对${dim}「${label}」做明细钻取`
  return base ? `${prefix}（原问题：${base}）` : `${prefix}，展示相关明细数据`
}
