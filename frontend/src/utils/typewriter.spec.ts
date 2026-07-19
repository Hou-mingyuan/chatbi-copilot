import { describe, expect, it, vi } from 'vitest'
import { typewriterText, ASK_LOADING_PHASES } from './typewriter'

describe('typewriterText', () => {
  it('reveals text incrementally via onUpdate', async () => {
    vi.useFakeTimers()
    const updates = []
    const promise = typewriterText('你好', (t) => updates.push(t), { charDelayMs: 10, chunkSize: 1 })

    await vi.runAllTimersAsync()
    await promise

    expect(updates).toEqual(['你', '你好'])
    vi.useRealTimers()
  })

  it('exports three ask loading phase labels', () => {
    expect(ASK_LOADING_PHASES).toHaveLength(3)
    expect(ASK_LOADING_PHASES[0]).toContain('理解')
  })
})
