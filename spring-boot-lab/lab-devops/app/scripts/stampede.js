// Simula um "cache stampede": a chave expira e chegam 200 pedidos em simultâneo.
// Sem proteção, TODOS vão à base de dados. Com proteção, só ~1 vai.
const Redis = require('ioredis');
const API = process.env.API_URL || 'http://localhost:3000';
const CONCURRENCY = Number(process.argv[2] || 200);

(async () => {
  const redis = new Redis(process.env.REDIS_URL || 'redis://localhost:6379');
  const created = await fetch(`${API}/orders`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ customer: 'stampede', amount: 99 }),
  }).then((r) => r.json());
  const id = created.id;
  if (!id) { console.error('não consegui criar encomenda:', created); process.exit(1); }

  await redis.del(`order:${id}`); // simula a chave a expirar
  const before = await fetch(`${API}/stats`).then((r) => r.json());

  const t0 = Date.now();
  await Promise.all(Array.from({ length: CONCURRENCY }, () => fetch(`${API}/orders/${id}`)));
  const ms = Date.now() - t0;

  const after = await fetch(`${API}/stats`).then((r) => r.json());
  const dbHits = after.dbQueries - before.dbQueries;
  console.log(`${CONCURRENCY} pedidos concorrentes em ${ms}ms`);
  console.log(`queries à BD: ${dbHits}`);
  console.log(dbHits <= 2 ? '✅ stampede controlado' : '❌ stampede! a BD levou com quase tudo');
  await redis.quit();
})();
