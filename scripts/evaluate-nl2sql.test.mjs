import test from 'node:test'
import assert from 'node:assert/strict'

import { executedResultsEquivalent } from './evaluate-nl2sql.mjs'

test('accepts harmless additional columns while matching every reference row', () => {
  const expected = { rows: [{ name: 'MacBook Pro', price: 12999 }, { name: 'iPhone 15', price: 5999 }] }
  const actual = { rows: [
    { name: 'iPhone 15', category: '手机', price: 5999 },
    { name: 'MacBook Pro', category: '电脑', price: 12999 }
  ] }
  assert.equal(executedResultsEquivalent(expected, actual), true)
})

test('rejects wrong values, missing rows, and missing requested fields', () => {
  const expected = { rows: [{ region: '华东', amount: 100 }, { region: '华南', amount: 50 }] }
  assert.equal(executedResultsEquivalent(expected, { rows: [{ region: '华东', amount: 99 }, { region: '华南', amount: 50 }] }), false)
  assert.equal(executedResultsEquivalent(expected, { rows: [{ region: '华东', amount: 100 }] }), false)
  assert.equal(executedResultsEquivalent(expected, { rows: [{ amount: 100 }, { amount: 50 }] }), false)
})

test('numbers-only comparison supports a follow-up that omits the inherited dimension', () => {
  const expected = { rows: [{ paid_order_count: 8 }] }
  const actual = { rows: [{ region: '华东', count: '8' }] }
  assert.equal(executedResultsEquivalent(expected, actual, 'numbers-only'), true)
})

test('accepts configured enum aliases without treating unrelated labels as equal', () => {
  const expected = { rows: [{ status: 'paid', count: 20 }] }
  const localized = { rows: [{ status: '已支付', count: 20 }] }
  const wrong = { rows: [{ status: '待支付', count: 20 }] }
  const aliases = { 已支付: 'paid', 待支付: 'pending' }
  assert.equal(executedResultsEquivalent(expected, localized, 'row-values', 0, aliases), true)
  assert.equal(executedResultsEquivalent(expected, wrong, 'row-values', 0, aliases), false)
})

test('accepts only numeric differences inside an explicit tolerance', () => {
  const expected = { rows: [{ average: 10739.692308 }] }
  const rounded = { rows: [{ average: 10739.69 }] }
  assert.equal(executedResultsEquivalent(expected, rounded, 'row-values', 0.01), true)
  assert.equal(executedResultsEquivalent(expected, rounded, 'row-values', 0.001), false)
})
