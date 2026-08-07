import { describe, expect, it, vi } from 'vitest'
import { isTerminalJob, pollQueryJob } from './queryJobs'

describe('query job polling', () => {
  it('reports real server stages and stops at success', async () => {
    const stages = [
      { id: 'j1', status: 'PREPARING', progress: 5 },
      { id: 'j1', status: 'EXECUTING', progress: 75 },
      { id: 'j1', status: 'SUCCEEDED', progress: 100, result: { queryId: 7 } },
    ]
    const fetchJob = vi.fn().mockImplementation(() => Promise.resolve(stages.shift()))
    const updates = []
    const result = await pollQueryJob('j1', { fetchJob, onUpdate: (job) => updates.push(job.status), intervalMs: 0 })
    expect(updates).toEqual(['PREPARING', 'EXECUTING', 'SUCCEEDED'])
    expect(result.result.queryId).toBe(7)
    expect(fetchJob).toHaveBeenCalledTimes(3)
  })

  it('treats clarification, confirmation, cancellation and timeout as terminal', () => {
    for (const status of ['CLARIFICATION', 'NEEDS_CONFIRMATION', 'CANCELLED', 'TIMED_OUT']) {
      expect(isTerminalJob(status)).toBe(true)
    }
    expect(isTerminalJob('EXECUTING')).toBe(false)
  })

  it('honors browser cancellation', async () => {
    const controller = new AbortController()
    controller.abort()
    await expect(pollQueryJob('j1', {
      fetchJob: vi.fn(), signal: controller.signal, intervalMs: 0,
    })).rejects.toMatchObject({ name: 'AbortError' })
  })
})
