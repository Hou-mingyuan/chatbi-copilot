import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import ResultPanel from './ResultPanel.vue'

vi.mock('@/api', () => ({
  api: {
    exportExcel: vi.fn().mockResolvedValue(new Blob()),
    createFavorite: vi.fn().mockResolvedValue({}),
  },
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn() },
  ElMessageBox: { prompt: vi.fn().mockRejectedValue(new Error('cancel')) },
}))

const stubs = {
  'el-alert': { template: '<div class="el-alert"><slot /></div>' },
  'el-tag': { template: '<span class="el-tag"><slot /></span>' },
  'el-button': {
    inheritAttrs: true,
    template: '<button class="el-button" v-bind="$attrs"><slot /></button>',
  },
  'el-tabs': {
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template: '<div class="el-tabs"><slot /></div>',
  },
  'el-tab-pane': { template: '<div class="el-tab-pane"><slot /></div>' },
  'el-radio-group': { template: '<div class="el-radio-group"><slot /></div>' },
  'el-radio-button': { template: '<label class="el-radio-button"><slot /></label>' },
  'el-table': { template: '<table class="el-table"><slot /></table>' },
  'el-table-column': { template: '<col />' },
  ChartRenderer: {
    props: ['highlightIndex'],
    emits: ['drill'],
    template: '<div class="chart-renderer-stub" @click="$emit(\'drill\', { dimension: \'category\', value: \'电子\', dataIndex: 0, row: { category: \'电子\', total: 1200 } })" />',
  },
  Suspense: { template: '<div><slot /></div>' },
}

function makeResult(overrides = {}) {
  return {
    question: '各产品类目的销售额占比',
    sql: 'SELECT category, SUM(amount) AS total\nFROM sales\nGROUP BY category',
    datasourceId: 1,
    rowCount: 3,
    elapsedMs: 42,
    truncated: false,
    explanation: '按类目汇总销售额',
    columns: [
      { name: 'category', category: 'dimension' },
      { name: 'total', category: 'measure' },
    ],
    rows: [
      { category: '电子', total: 1200 },
      { category: '服装', total: 800 },
      { category: '食品', total: 500 },
    ],
    chart: {
      type: 'bar',
      xField: 'category',
      yFields: ['total'],
      reason: '适合类目对比',
    },
    ...overrides,
  }
}

describe('ResultPanel', () => {
  it('renders question echo, explanation, and meta tags', () => {
    const wrapper = mount(ResultPanel, {
      props: { result: makeResult() },
      global: { stubs },
    })

    expect(wrapper.find('.question-echo .text').text()).toContain('各产品类目的销售额占比')
    expect(wrapper.find('.el-alert').exists()).toBe(true)
    expect(wrapper.text()).toContain('3 行')
    expect(wrapper.text()).toContain('42 ms')
    expect(wrapper.text()).toContain('柱状图')
  })

  it('shows SQL preview collapsed and expands on click', async () => {
    const longSql = Array.from({ length: 6 }, (_, i) => `SELECT col${i}`).join('\n')
    const wrapper = mount(ResultPanel, {
      props: { result: makeResult({ sql: longSql }) },
      global: { stubs },
    })

    const pre = wrapper.find('.sql-preview .sql-block')
    expect(pre.classes()).not.toContain('expanded')
    expect(pre.text()).toContain('…')

    const expandBtn = wrapper
      .findAll('.sql-preview-head .el-button')
      .find((b) => b.text().includes('展开'))
    expect(expandBtn).toBeTruthy()
    await expandBtn!.trigger('click')
    await wrapper.vm.$nextTick()

    const expanded = wrapper.find('.sql-preview .sql-block')
    expect(expanded.classes()).toContain('expanded')
    expect(expanded.text()).toContain('SELECT col0')
  })

  it('renders preview cards and three result tabs', () => {
    const wrapper = mount(ResultPanel, {
      props: { result: makeResult() },
      global: { stubs },
    })

    expect(wrapper.findAll('.preview-card').length).toBeGreaterThan(0)
    expect(wrapper.text()).toContain('柱状图')
    expect(wrapper.text()).toContain('折线图')
    expect(wrapper.text()).toContain('GROUP BY category')
    expect(wrapper.find('.chart-renderer-stub').exists()).toBe(true)
  })

  it('defaults to table tab when chart type is table', () => {
    const wrapper = mount(ResultPanel, {
      props: {
        result: makeResult({
          chart: { type: 'table' },
        }),
      },
      global: { stubs },
    })

    expect(wrapper.text()).toContain('共 3 行')
    expect(wrapper.find('.result-data-table').exists()).toBe(true)
  })

  it('shows truncated warning tag when result is truncated', () => {
    const wrapper = mount(ResultPanel, {
      props: { result: makeResult({ truncated: true }) },
      global: { stubs },
    })

    expect(wrapper.text()).toContain('结果已截断')
  })

  it('disables export when there are no rows', () => {
    const wrapper = mount(ResultPanel, {
      props: {
        result: makeResult({ rows: [], rowCount: 0, chart: { type: 'table' } }),
      },
      global: { stubs },
    })

    const exportBtn = wrapper.findAll('.meta .el-button').find((b) => b.text().includes('导出 Excel'))
    expect(exportBtn?.attributes('disabled')).toBeDefined()
  })

  it('filters table rows on chart drill and shows drill tag', async () => {
    const wrapper = mount(ResultPanel, {
      props: { result: makeResult() },
      global: { stubs },
    })

    await wrapper.find('.chart-renderer-stub').trigger('click')
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('钻取：category = 电子')
    expect(wrapper.text()).toContain('共 1 行')
  })
})
