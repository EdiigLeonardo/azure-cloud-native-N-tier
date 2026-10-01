const { Pool } = require('pg');
const config = require('./config');

const pool = new Pool({ connectionString: config.databaseUrl });
let queryCount = 0;

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function normalize(row) {
  return row ? { ...row, amount: Number(row.amount) } : null;
}

async function init() {
  await pool.query(`
    CREATE TABLE IF NOT EXISTS orders (
      id          SERIAL PRIMARY KEY,
      customer    TEXT           NOT NULL,
      amount      NUMERIC(10, 2) NOT NULL,
      status      TEXT           NOT NULL DEFAULT 'CREATED',
      created_at  TIMESTAMPTZ    NOT NULL DEFAULT now()
    )`);
}

async function createOrder({ customer, amount }) {
  queryCount++;
  const { rows } = await pool.query(
    'INSERT INTO orders (customer, amount) VALUES ($1, $2) RETURNING *',
    [customer, amount]
  );
  return normalize(rows[0]);
}

async function getOrder(id) {
  queryCount++;
  // simula uma query pesada (join, relatório...) para tornar a cache relevante
  if (config.dbDelayMs > 0) await sleep(config.dbDelayMs);
  const { rows } = await pool.query('SELECT * FROM orders WHERE id = $1', [id]);
  return normalize(rows[0]);
}

async function updateStatus(id, status) {
  queryCount++;
  const { rows } = await pool.query(
    'UPDATE orders SET status = $2 WHERE id = $1 RETURNING *',
    [id, status]
  );
  return normalize(rows[0]);
}

async function ping() {
  await pool.query('SELECT 1');
}

module.exports = { init, createOrder, getOrder, updateStatus, ping, stats: () => ({ dbQueries: queryCount }) };
