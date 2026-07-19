/** 异步打字机效果：逐字回调 onUpdate，返回完整文本。 */
export async function typewriterText(fullText, onUpdate, opts = {}) {
  const { charDelayMs = 16, chunkSize = 2 } = opts
  if (!fullText) {
    onUpdate('')
    return ''
  }
  let acc = ''
  for (let i = 0; i < fullText.length; i += chunkSize) {
    acc += fullText.slice(i, i + chunkSize)
    onUpdate(acc)
    if (i + chunkSize < fullText.length) {
      await sleep(charDelayMs)
    }
  }
  return acc
}

export function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

/** 流式加载阶段文案 */
export const ASK_LOADING_PHASES = [
  '正在理解您的问题…',
  '正在生成 SQL 并校验只读护栏…',
  '即将返回分析结果…'
]
