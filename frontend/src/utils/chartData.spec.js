import { describe, expect, it } from 'vitest'
import { buildChartAriaDescription, hasChartableValues, toChartNumber } from './chartData'

describe('chart data integrity', () => {
  it('keeps valid finite values numerically identical', () => {
    expect(toChartNumber(185551)).toBe(185551)
    expect(toChartNumber('185551.00')).toBe(185551)
    expect(toChartNumber(-0.25)).toBe(-0.25)
  })

  it('never turns null, blank or invalid values into zero', () => {
    expect(toChartNumber(null)).toBeNull()
    expect(toChartNumber('')).toBeNull()
    expect(toChartNumber('not-a-number')).toBeNull()
    expect(toChartNumber(Number.NaN)).toBeNull()
  })

  it('rejects numbers that would lose JavaScript precision', () => {
    expect(toChartNumber('1234567890123456.78')).toBeNull()
    expect(toChartNumber('9007199254740992')).toBeNull()
    expect(hasChartableValues([{ value: '1234567890123456.78' }], ['value'])).toBe(false)
  })

  it('describes only the plotted values without adding an axis baseline', () => {
    const description = buildChartAriaDescription({
      type: 'bar',
      xField: '大区',
      yFields: ['已支付订单数'],
      rows: [{ 大区: '华东', 已支付订单数: 7 }]
    })
    expect(description).toContain('华东：已支付订单数为 7')
    expect(description).not.toContain('0，7')
  })

  it('keeps decimal text aligned with the query snapshot', () => {
    const description = buildChartAriaDescription({
      type: 'pie',
      xField: 'region',
      yFields: ['total_sales'],
      rows: [{ region: '华东', total_sales: '60682.00' }]
    })
    expect(description).toContain('华东：total_sales为 60682.00')
  })
})
