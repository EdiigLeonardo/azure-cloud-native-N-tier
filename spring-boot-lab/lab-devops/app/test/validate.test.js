const test = require('node:test');
const assert = require('node:assert');
const { validateOrder } = require('../src/validate');

test('aceita uma encomenda válida', () => {
  assert.deepStrictEqual(validateOrder({ customer: 'ana', amount: 25.5 }), []);
});

test('rejeita encomenda sem customer', () => {
  assert.ok(validateOrder({ amount: 10 }).length > 0);
});

test('rejeita amount que não é número', () => {
  assert.ok(validateOrder({ customer: 'ana', amount: '10' }).length > 0);
});

test('rejeita amount negativo ou zero', () => {
  assert.ok(validateOrder({ customer: 'ana', amount: -5 }).length > 0, 'amount negativo devia falhar');
  assert.ok(validateOrder({ customer: 'ana', amount: 0 }).length > 0, 'amount zero devia falhar');
});
