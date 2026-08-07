<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { buildChartAriaDescription, toChartNumber } from '@/utils/chartData'

const props = defineProps({
  type: { type: String, default: 'bar' },
  xField: { type: String, default: '' },
  yFields: { type: Array, default: () => [] },
  seriesField: { type: String, default: '' },
  rows: { type: Array, default: () => [] },
  height: { type: String, default: '360px' },
  highlightIndex: { type: Number, default: null }
})
const emit = defineEmits(['drill'])

const element = ref(null)
const loading = ref(true)
const echarts = shallowRef(null)
const selectedIndex = ref(null)
let chart = null
let observer = null
let initializing = false
let disposed = false
let sourceIndexMatrix = []

async function loadEcharts() {
  if (echarts.value) return echarts.value
  const [core, charts, components, renderers] = await Promise.all([
    import('echarts/core'),
    import('echarts/charts'),
    import('echarts/components'),
    import('echarts/renderers')
  ])
  core.use([
    charts.BarChart,
    charts.LineChart,
    charts.PieChart,
    components.GridComponent,
    components.TooltipComponent,
    components.LegendComponent,
    components.AriaComponent,
    renderers.CanvasRenderer
  ])
  echarts.value = core
  return core
}

function highlightedStyle(sourceIndex) {
  const highlighted = props.highlightIndex ?? selectedIndex.value
  return highlighted === sourceIndex
    ? { color: '#0f766e', borderColor: '#0b5f59', borderWidth: 2, shadowBlur: 7, shadowColor: 'rgba(15,118,110,.25)' }
    : undefined
}

function cartesianData() {
  const rows = props.rows || []
  if (!props.seriesField) {
    return {
      categories: rows.map((row) => row[props.xField]),
      sourceIndexes: props.yFields.map(() => rows.map((_, sourceIndex) => sourceIndex)),
      series: props.yFields.map((field) => ({
        name: field,
        type: props.type === 'line' ? 'line' : 'bar',
        smooth: false,
        connectNulls: false,
        showSymbol: props.type === 'line',
        barMaxWidth: 38,
        data: rows.map((row, sourceIndex) => {
          const value = toChartNumber(row[field])
          const itemStyle = highlightedStyle(sourceIndex)
          return itemStyle ? { value, itemStyle } : value
        }),
        emphasis: { focus: 'series' }
      }))
    }
  }

  const categoryKeys = [...new Set(rows.map((row) => String(row[props.xField])))]
  const groups = [...new Set(rows.map((row) => String(row[props.seriesField])))]
  const categoryLabel = new Map(rows.map((row) => [String(row[props.xField]), row[props.xField]]))
  const series = []
  const sourceIndexes = []
  for (const field of props.yFields) {
    for (const group of groups) {
      const indexes = []
      series.push({
        name: props.yFields.length > 1 ? `${group} · ${field}` : group,
        type: props.type === 'line' ? 'line' : 'bar',
        smooth: false,
        connectNulls: false,
        showSymbol: props.type === 'line',
        barMaxWidth: 38,
        data: categoryKeys.map((category) => {
          const sourceIndex = rows.findIndex((row) =>
            String(row[props.xField]) === category && String(row[props.seriesField]) === group
          )
          indexes.push(sourceIndex)
          if (sourceIndex < 0) return null
          const value = toChartNumber(rows[sourceIndex][field])
          const itemStyle = highlightedStyle(sourceIndex)
          return itemStyle ? { value, itemStyle } : value
        }),
        emphasis: { focus: 'series' }
      })
      sourceIndexes.push(indexes)
    }
  }
  return { categories: categoryKeys.map((key) => categoryLabel.get(key)), series, sourceIndexes }
}

function buildOption() {
  const rows = props.rows || []
  const palette = ['#0f766e', '#d18a25', '#3f719b', '#7a8f52', '#b85c4a', '#55736c']
  const common = {
    color: palette,
    animationDuration: 280,
    aria: {
      enabled: true,
      label: {
        description: buildChartAriaDescription({
          type: props.type,
          xField: props.xField,
          yFields: props.yFields,
          seriesField: props.seriesField,
          rows
        })
      },
      decal: { show: false }
    },
    textStyle: { color: '#4b5d59', fontFamily: 'Inter, PingFang SC, sans-serif' }
  }

  if (props.type === 'pie') {
    const field = props.yFields[0]
    sourceIndexMatrix = [rows.map((_, sourceIndex) => sourceIndex)]
    return {
      ...common,
      tooltip: { trigger: 'item' },
      legend: { type: 'scroll', bottom: 0, textStyle: { color: '#596b67' } },
      series: [{
        name: field,
        type: 'pie',
        radius: ['44%', '69%'],
        selectedMode: 'single',
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 3, borderColor: '#fff', borderWidth: 2 },
        label: { formatter: '{b}\n{d}%' },
        data: rows.map((row, sourceIndex) => ({
          name: props.seriesField
            ? `${row[props.xField]} · ${row[props.seriesField]}`
            : String(row[props.xField] ?? '未填写'),
          value: toChartNumber(row[field]),
          selected: (props.highlightIndex ?? selectedIndex.value) === sourceIndex,
          itemStyle: highlightedStyle(sourceIndex)
        }))
      }]
    }
  }

  const { categories, series, sourceIndexes } = cartesianData()
  sourceIndexMatrix = sourceIndexes
  return {
    ...common,
    tooltip: { trigger: 'axis', confine: true },
    legend: { top: 0, type: 'scroll', textStyle: { color: '#596b67' } },
    grid: { left: 58, right: 20, top: 38, bottom: categories.length > 7 ? 74 : 42 },
    xAxis: {
      type: 'category',
      data: categories,
      axisLine: { lineStyle: { color: '#cbd3cd' } },
      axisTick: { show: false },
      axisLabel: { color: '#596b67', rotate: categories.length > 7 ? 32 : 0, interval: 0 }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#71817d' },
      splitLine: { lineStyle: { color: '#e8ece8' } }
    },
    series
  }
}

function handleClick(params) {
  // Keep drill metadata outside ECharts data items. Numeric custom fields are
  // interpreted as extra measures by ECharts ARIA and would make its spoken
  // values disagree with the table (for example "0, 7" instead of "7").
  const sourceIndex = sourceIndexMatrix[params.seriesIndex]?.[params.dataIndex] ?? params.dataIndex
  if (sourceIndex == null || sourceIndex < 0) return
  const row = props.rows[sourceIndex]
  if (!row) return
  selectedIndex.value = sourceIndex
  emit('drill', {
    index: sourceIndex,
    label: String(row[props.xField]),
    row,
    dimension: props.xField,
    seriesField: props.seriesField,
    chartType: props.type
  })
  render()
}

function render() {
  if (!chart || !echarts.value) return
  chart.setOption(buildOption(), true)
  chart.off('click', handleClick)
  chart.on('click', handleClick)
  chart.resize()
}

async function initializeWhenSized() {
  if (chart || initializing || disposed || !element.value) return
  if (element.value.clientWidth <= 0 || element.value.clientHeight <= 0) return
  initializing = true
  try {
    const library = await loadEcharts()
    if (disposed || !element.value || element.value.clientWidth <= 0 || element.value.clientHeight <= 0) return
    chart = library.init(element.value)
    render()
    loading.value = false
  } finally {
    initializing = false
  }
}

onMounted(() => {
  observer = new ResizeObserver(() => {
    if (chart) chart.resize()
    else void initializeWhenSized()
  })
  observer.observe(element.value)
  void initializeWhenSized()
})

onBeforeUnmount(() => {
  disposed = true
  observer?.disconnect()
  chart?.dispose()
  chart = null
})

watch(
  () => [props.type, props.rows, props.xField, props.yFields, props.seriesField, props.highlightIndex],
  () => nextTick(render),
  { deep: true }
)
</script>

<template>
  <div class="chart-wrap" role="group" :aria-label="`${type} 图，横轴 ${xField}，指标 ${yFields.join('、')}`">
    <div v-if="loading" class="chart-loading">正在加载图表…</div>
    <div ref="element" :style="{ width: '100%', height, visibility: loading ? 'hidden' : 'visible' }" />
  </div>
</template>

<style scoped>
.chart-wrap {
  position: relative;
  min-height: 220px;
}
.chart-loading {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: var(--ink-500);
  font-size: 12px;
}
</style>
