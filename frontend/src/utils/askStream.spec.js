import { describe, expect, it } from 'vitest'
import {
  ASK_STREAM_STAGES,
  buildDrilldownQuestion,
  nextAskStageIndex,
  pickStreamFinishText,
} from './askStream'

describe('askStream utils', () => {
  it('cycles ask stage indices without overflowing', () => {
    expect(ASK_STREAM_STAGES.length).toBeGreaterThan(1)
    expect(nextAskStageIndex(0)).toBe(1)
    expect(nextAskStageIndex(ASK_STREAM_STAGES.length - 1)).toBe(ASK_STREAM_STAGES.length - 1)
  })

  it('pickStreamFinishText prefers explanation and clarification', () => {
    expect(pickStreamFinishText({ explanation: '按类目汇总' })).toBe('按类目汇总')
    expect(
      pickStreamFinishText({ needClarification: true, clarification: '请指定年份' })
    ).toContain('请指定年份')
  })

  it('buildDrilldownQuestion includes label and original question', () => {
    const q = buildDrilldownQuestion('华东', {
      dimension: 'region',
      question: '各大区客户数量',
    })
    expect(q).toContain('华东')
    expect(q).toContain('各大区客户数量')
  })
})
