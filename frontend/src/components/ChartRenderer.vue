<script setup>
import { ref, watch, onMounted, onBeforeUnmount, nextTick, shallowRef } from 'vue'

const props = defineProps({
  type: { type: String, default: 'bar' },
  xField: String,
  yFields: { type: Array, default: () => [] },
  seriesField: String,
  rows: { type: Array, default: () => [] },
  height: { type: String, default: '380px' },
  highlightIndex: { type: Number, default: null }
})

const emit = defineEmits(['drill'])

const el = ref(null)
const loading = ref(true)
const echarts = shallowRef(null)
const selectedIndex = ref(null)
let chart = null
let observer = null

async function loadEcharts() {
  if (echarts.value) return echarts.value
  const [core, charts, components, renderers] = await Promise.all([
    import('echarts/core'),
    import('echarts/charts'),
    import('echarts/components'),
    import('echarts/renderers')
  ])
  const { BarChart, LineChart, PieChart } = charts
  const { GridComponent, TooltipComponent, LegendComponent } = components
  const { CanvasRenderer } = renderers
  core.use([
    BarChart,
    LineChart,
    PieChart,
    GridComponent,
    TooltipComponent,
    LegendComponent,
    CanvasRenderer
  ])
  echarts.value = core
  return core
}

function toNumber(v) {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}

function buildOption() {
  const rows = props.rows || []
  const x = props.xField
  const yFields = props.yFields && props.yFields.length ? props.yFields : []
  const hi = props.highlightIndex ?? selectedIndex.value

  if (props.type === 'pie') {
    const y = yFields[0]
    const data = rows.map((r, i) => ({
      name: String(r[x]),
      value: toNumber(r[y]),
      selected: hi === i,
      itemStyle: hi === i ? { borderColor: '#4f46e5', borderWidth: 3, shadowBlur: 8, shadowColor: 'rgba(79,70,229,0.45)' } : undefined
    }))
    return {
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { type: 'scroll', bottom: 0 },
      series: [
        {
          type: 'pie',
          radius: ['42%', '68%'],
          selectedMode: 'single',
          avoidLabelOverlap: true,
          itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
          label: { formatter: '{b}\n{d}%' },
          emphasis: { scale: true, scaleSize: 8 },
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
    data: rows.map((r, i) => ({
      value: toNumber(r[f]),
      itemStyle: hi === i ? { color: '#4f46e5', shadowBlur: 6, shadowColor: 'rgba(79,70,229,0.4)' } : undefined
    })),
    emphasis: { focus: 'series' }
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

function handleChartClick(params) {
  const idx = params.dataIndex
  if (idx == null || idx < 0) return
  selectedIndex.value = idx
  const row = (props.rows || [])[idx]
  if (!row) return
  emit('drill', {
    index: idx,
    label: String(row[props.xField]),
    row,
    dimension: props.xField,
    chartType: props.type,
  })
  render()
}

function render() {
  if (!chart || !echarts.value) return
  chart.setOption(buildOption(), true)
  chart.off('click', handleChartClick)
  chart.on('click', handleChartClick)
  chart.resize()
}

onMounted(async () => {
  try {
    const ec = await loadEcharts()
    chart = ec.init(el.value)
    render()
    observer = new ResizeObserver(() => chart && chart.resize())
    observer.observe(el.value)
  } finally {
    loading.value = false
  }
})

onBeforeUnmount(() => {
  observer && observer.disconnect()
  chart && chart.dispose()
  chart = null
})

watch(
  () => [props.type, props.rows, props.xField, props.yFields, props.highlightIndex],
  () => nextTick(render),
  { deep: true }
)
</script>

<template>
  <div class="chart-wrap">
    <div v-if="loading" class="chart-loading">
      <el-icon class="is-loading"><Loading /></el-icon>
      加载图表组件…
    </div>
    <div ref="el" :style="{ width: '100%', height: height, visibility: loading ? 'hidden' : 'visible' }" />
  </div>
</template>

<style scoped>
.chart-wrap {
  position: relative;
  min-height: 200px;
}
.chart-loading {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #8a94a6;
  font-size: 13px;
}
</style>
