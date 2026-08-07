const DECIMAL_PATTERN = /^[+-]?(?:\d+\.?\d*|\.\d+)(?:e[+-]?\d+)?$/i

export function toChartNumber(value) {
  if (typeof value === 'number') return Number.isFinite(value) ? value : null
  if (typeof value !== 'string') return null
  const normalized = value.trim()
  if (!normalized || !DECIMAL_PATTERN.test(normalized)) return null
  const mantissa = normalized.split(/e/i)[0]
  const significantDigits = mantissa.replace(/^[+-]/, '').replace('.', '').replace(/^0+/, '').length
  if (significantDigits > 15) return null
  const number = Number(normalized)
  if (!Number.isFinite(number)) return null
  if (Number.isInteger(number) && !Number.isSafeInteger(number)) return null
  return number
}

export function hasChartableValues(rows, fields) {
  return Boolean(fields?.length) && rows.some((row) => fields.some((field) => toChartNumber(row[field]) !== null))
}

const CHART_NAMES = { bar: '柱状图', line: '折线图', pie: '饼图' }
const ARIA_MAX_ROWS = 20

export function buildChartAriaDescription({ type, xField, yFields, seriesField, rows }) {
  const safeRows = Array.isArray(rows) ? rows : []
  const safeFields = Array.isArray(yFields) ? yFields : []
  const visibleRows = safeRows.slice(0, ARIA_MAX_ROWS)
  const entries = visibleRows.map((row) => {
    const category = String(row?.[xField] ?? '未填写')
    const group = seriesField ? ` · ${String(row?.[seriesField] ?? '未填写')}` : ''
    const measures = safeFields.map((field) => {
      const value = row?.[field]
      return `${field}为 ${toChartNumber(value) === null ? '空值' : String(value)}`
    }).join('，')
    return `${category}${group}：${measures}`
  })
  const omitted = safeRows.length - visibleRows.length
  const suffix = omitted > 0 ? `；另有 ${omitted} 项未在描述中展开` : ''
  return `${CHART_NAMES[type] || '图表'}；维度 ${xField || '未指定'}；指标 ${safeFields.join('、') || '未指定'}。`
    + `${safeRows.length} 项数据：${entries.join('；') || '无数据'}${suffix}。`
}
