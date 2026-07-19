/** Preset Mock demo questions — matches MockLlmChatClient keyword routing. */
export const MOCK_DEMO_STEPS = [
  { question: '各产品类目的销售额占比', hint: '类目聚合 + 占比图表' },
  { question: '2024年每月销售额趋势', hint: '时间序列 → 折线图' },
  { question: '销售额最高的5个产品', hint: 'Top N 排名' },
  { question: '只看华东大区', hint: '多轮追问 · 区域过滤' }
]

export function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}
