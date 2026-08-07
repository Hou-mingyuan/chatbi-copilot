import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ResultPanel from './ResultPanel.vue'

const mocks = vi.hoisted(() => ({
  exportExcel: vi.fn(),
  createFavorite: vi.fn(),
  prompt: vi.fn(),
  downloadBlob: vi.fn(),
}))

vi.mock('@/api', () => ({
  api: { exportExcel: mocks.exportExcel, createFavorite: mocks.createFavorite },
  errorMessage: (error: Error) => error.message,
}))
vi.mock('@/utils/download', () => ({ downloadBlob: mocks.downloadBlob }))
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
  ElMessageBox: { prompt: mocks.prompt },
}))

const stubs = {
  'el-tag': { template: '<span class="el-tag"><slot /></span>' },
  'el-empty': { props: ['description'], template: '<div class="el-empty">{{ description }}</div>' },
  'el-tooltip': { template: '<span class="el-tooltip"><slot /></span>' },
  'el-button': { inheritAttrs: true, template: '<button class="el-button" v-bind="$attrs"><slot /></button>' },
  'el-tabs': { props: ['modelValue'], template: '<div class="el-tabs"><slot /></div>' },
  'el-tab-pane': { props: ['disabled'], template: '<div class="el-tab-pane" :data-disabled="disabled"><slot /></div>' },
  'el-radio-group': { template: '<div><slot /></div>' },
  'el-radio-button': { template: '<label><slot /></label>' },
  'el-table': { template: '<table class="el-table"><slot /></table>' },
  'el-table-column': { template: '<col />' },
  ChartRenderer: {
    props: ['highlightIndex', 'seriesField'],
    emits: ['drill'],
    template: '<div class="chart-renderer-stub" :data-series="seriesField" @click="$emit(\'drill\', { dimension: \'category\', value: \'电子\', index: 0 })" />',
  },
  Suspense: { template: '<div><slot /></div>' },
}

function makeResult(overrides = {}) {
  return {
    queryId: 91,
    question: '各产品类目的销售额占比',
    sql: 'SELECT category, SUM(amount) AS total\nFROM sales\nGROUP BY category',
    datasourceId: 1,
    rowCount: 3,
    elapsedMs: 42,
    truncated: false,
    explanation: '按类目汇总销售额',
    summary: { text: '电子类目销售额最高。', facts: [{ label: '最大值', value: '1200', column: 'total', unit: '元' }] },
    columns: [
      { name: 'category', category: 'dimension' },
      { name: 'total', category: 'measure' },
    ],
    rows: [
      { category: '电子', total: 1200 },
      { category: '服装', total: 800 },
      { category: '食品', total: 500 },
    ],
    chart: { type: 'bar', xField: 'category', yFields: ['total'], seriesField: 'channel', reason: '适合类目对比' },
    risk: { level: 'LOW' },
    ...overrides,
  }
}

describe('ResultPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.exportExcel.mockResolvedValue({ data: new Blob(), headers: {} })
    mocks.createFavorite.mockResolvedValue({})
  })

  it('renders deterministic summary, facts, snapshot id and execution meta', () => {
    const wrapper = mount(ResultPanel, { props: { result: makeResult() }, global: { stubs } })
    expect(wrapper.text()).toContain('电子类目销售额最高')
    expect(wrapper.text()).toContain('最大值')
    expect(wrapper.text()).toContain('1200')
    expect(wrapper.text()).toContain('3 行')
    expect(wrapper.text()).toContain('42 ms')
    expect(wrapper.text()).toContain('查询 #91')
  })

  it('keeps SQL collapsed until the user expands it', async () => {
    const longSql = `SELECT ${'very_long_column, '.repeat(20)} id FROM sales`
    const wrapper = mount(ResultPanel, { props: { result: makeResult({ sql: longSql }) }, global: { stubs } })
    const sql = wrapper.find('.sql-card pre')
    expect(sql.classes()).not.toContain('expanded')
    expect(sql.text()).toContain('…')
    const button = wrapper.findAll('.sql-card button').find((item) => item.text().includes('展开'))!
    await button.trigger('click')
    expect(wrapper.find('.sql-card pre').classes()).toContain('expanded')
    expect(wrapper.find('.sql-card pre').text()).toBe(longSql)
  })

  it('passes the server-selected series field to the chart', async () => {
    const wrapper = mount(ResultPanel, { props: { result: makeResult() }, global: { stubs } })
    await flushPromises()
    expect(wrapper.find('.chart-renderer-stub').attributes('data-series')).toBe('channel')
  })

  it('refuses chart rendering for high precision string results', () => {
    const wrapper = mount(ResultPanel, {
      props: { result: makeResult({ rows: [{ category: '电子', total: '1234567890123456.78' }], rowCount: 1, chart: { type: 'table' } }) },
      global: { stubs },
    })
    expect(wrapper.find('.chart-renderer-stub').exists()).toBe(false)
    expect(wrapper.text()).toContain('无损表格展示')
  })

  it('exports and favorites only by the server query id', async () => {
    mocks.prompt.mockResolvedValueOnce({ value: '重点类目' })
    const wrapper = mount(ResultPanel, { props: { result: makeResult() }, global: { stubs } })
    const buttons = wrapper.findAll('.result-meta button')
    await buttons.find((item) => item.text().includes('导出'))!.trigger('click')
    await buttons.find((item) => item.text().includes('收藏'))!.trigger('click')
    await flushPromises()
    expect(mocks.exportExcel).toHaveBeenCalledWith(91)
    expect(mocks.downloadBlob).toHaveBeenCalled()
    expect(mocks.createFavorite).toHaveBeenCalledWith({ queryId: 91, title: '重点类目' })
  })

  it('disables export when the immutable snapshot has no rows', () => {
    const wrapper = mount(ResultPanel, {
      props: { result: makeResult({ rows: [], rowCount: 0, chart: { type: 'table' } }) },
      global: { stubs },
    })
    const button = wrapper.findAll('.result-meta button').find((item) => item.text().includes('导出'))!
    expect(button.attributes('disabled')).toBeDefined()
  })

  it('disables export before the request when datasource ACL denies it', async () => {
    const wrapper = mount(ResultPanel, {
      props: { result: makeResult(), canExport: false },
      global: { stubs },
    })
    const button = wrapper.findAll('.result-meta button').find((item) => item.text().includes('导出'))!
    expect(button.attributes('disabled')).toBeDefined()
    await button.trigger('click')
    expect(mocks.exportExcel).not.toHaveBeenCalled()
  })

  it('filters the table after a chart drill without changing source values', async () => {
    const wrapper = mount(ResultPanel, { props: { result: makeResult() }, global: { stubs } })
    await flushPromises()
    await wrapper.find('.chart-renderer-stub').trigger('click')
    expect(wrapper.text()).toContain('已筛选 category = 电子')
    expect(wrapper.text()).toContain('1 行')
  })
})
