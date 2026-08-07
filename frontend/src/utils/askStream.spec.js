import { describe, expect, it } from 'vitest'
import { buildDrilldownQuestion } from './askStream'

describe('drill-down question', () => {
  it('preserves the selected dimension, value and original question', () => {
    expect(buildDrilldownQuestion('华东', {
      dimension: 'region',
      question: '各区域销售额是多少？',
    })).toBe('请针对region「华东」做明细钻取（原问题：各区域销售额是多少？）')
  })

  it('builds a standalone follow-up when there is no original question', () => {
    expect(buildDrilldownQuestion('电子')).toBe('请针对维度「电子」做明细钻取，展示相关明细数据')
  })
})
