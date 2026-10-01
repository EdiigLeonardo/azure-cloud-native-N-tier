const express = require('express');
const config = require('./config');
const db = require('./db');
const cache = require('./cache');
const { rateLimit } = require('./ratelimit');
const kafka = require('./kafka');
const rabbit = require('./rabbit');
const { validateOrder } = require('./validate');

const app = express();
app.use(express.json());

const STATUSES = ['CREATED', 'PAID', 'SHIPPED', 'CANCELLED'];

// liveness: o processo está vivo?
app.get('/health', (req, res) => res.json({ status: 'ok' }));

// readiness: consigo servir tráfego? (dependências OK)
app.get('/ready', async (req, res) => {
  try {
    await db.ping();
    await cache.redis.ping();
    res.json({ status: 'ready' });
  } catch (err) {
    res.status(503).json({ status: 'not-ready', error: err.message });
  }
});

app.get('/stats', (req, res) => {
  res.json({ ...db.stats(), cacheHits: cache.stats.hits, cacheMisses: cache.stats.misses, pod: process.env.HOSTNAME });
});

function parseId(req, res) {
  const id = Number(req.params.id);
  if (!Number.isInteger(id) || id <= 0) {
    res.status(400).json({ error: 'id inválido' });
    return null;
  }
  return id;
}

app.post('/orders', rateLimit, async (req, res) => {
  const errors = validateOrder(req.body);
  if (errors.length) return res.status(400).json({ errors });

  const order = await db.createOrder(req.body);
  await kafka.publishOrderEvent('OrderCreated', order);
  rabbit.sendEmailCommand(order);
  res.status(201).json(order);
});

app.get('/orders/:id', async (req, res) => {
  const id = parseId(req, res);
  if (id === null) return;
  const { value, hit } = await cache.getOrSet(`order:${id}`, () => db.getOrder(id));
  res.set('X-Cache', hit ? 'HIT' : 'MISS');
  if (!value) return res.status(404).json({ error: 'encomenda não encontrada' });
  res.json(value);
});

app.patch('/orders/:id/status', async (req, res) => {
  const id = parseId(req, res);
  if (id === null) return;
  const { status } = req.body || {};
  if (!STATUSES.includes(status)) return res.status(400).json({ error: `status tem de ser um de ${STATUSES.join(', ')}` });

  const order = await db.updateStatus(id, status);
  if (!order) return res.status(404).json({ error: 'encomenda não encontrada' });
  await kafka.publishOrderEvent('OrderStatusChanged', order);
  res.json(order);
});

app.use((err, req, res, next) => {
  console.error('[api]', err.message);
  res.status(500).json({ error: 'erro interno' });
});

async function start() {
  await db.init();
  if (config.kafkaEnabled) await kafka.initProducer();
  if (config.rabbitEnabled) await rabbit.initPublisher();
  app.listen(config.port, () => console.log(`[api] a ouvir na porta ${config.port}`));
}

if (require.main === module) {
  start().catch((err) => {
    console.error('[api] falhou no arranque:', err.message);
    process.exit(1);
  });
}

module.exports = { app };
