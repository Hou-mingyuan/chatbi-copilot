<script setup>
import { ref, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  type: { type: String, default: 'bar' },
  xField: String,
  yFields: { type: Array, default: () => [] },
  seriesField: String,
  rows: { type: Array, default: () => [] },
  height: { type: String, default: '380px' }
})

const el = ref(null)
let chart = null
let observer = null

function toNumber(v) {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}

function buildOption() {
  const rows = props.rows || []
  const x = props.xField
  const yFields = props.yFields && props.yFields.length ? props.yFields : []

  if (props.type === 'pie') {
    const y = yFields[0]
    const data = rows.map((r) => ({ name: String(r[x]), value: toNumber(r[y]) }))
    return {
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { type: 'scroll', bottom: 0 },
      series: [
        {
          type: 'pie',
          radius: ['42%', '68%'],
          avoidLabelOverlap: true,
          itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
          label: { formatter: '{b}\n{d}%' },
          data
        }
      ]
    }
  }

  const categories = rows.map((r) => r[x])
  const series = yFields.map((f) => ({
    name: f,
    type: props.type === 'line' ? 'line' : 'bar',
    smooth: props.type === 'line',
    areaStyle: props.type === 'line' ? { opacity: 0.08 } : undefined,
    barMaxWidth: 42,
    data: rows.map((r) => toNumber(r[f]))
  }))

  return {
    color: ['#4f46e5', '#06b6d4', '#f59e0b', '#ef4444', '#10b981', '#8b5cf6'],
    tooltip: { trigger: 'axis' },
    legend: { top: 0 },
    grid: { left: 56, right: 24, top: 34, bottom: categories.length > 6 ? 70 : 40 },
    xAxis: {
      type: 'category',
      data: categories,
      axisLabel: { rotate: categories.length > 6 ? 32 : 0, interval: 0 }
    },
    yAxis: { type: 'value' },
    series
  }
}

function render() {
  if (!chart) return
  chart.setOption(buildOption(), true)
  chart.resize()
}

onMounted(() => {
  chart = echarts.init(el.value)
  render()
  observer = new ResizeObserver(() => chart && chart.resize())
  observer.observe(el.value)
})

onBeforeUnmount(() => {
  observer && observer.disconnect()
  chart && chart.dispose()
  chart = null
})

watch(
  () => [props.type, props.rows, props.xField, props.yFields],
  () => nextTick(render),
  { deep: true }
)
</script>

<template>
  <div ref="el" :style="{ width: '100%', height: height }"></div>
</template>
