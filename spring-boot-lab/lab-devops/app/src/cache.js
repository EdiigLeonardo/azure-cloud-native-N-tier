const Redis = require('ioredis');
const config = require('./config');

const redis = new Redis(config.redisUrl, { maxRetriesPerRequest: 3 });
redis.on('error', (err) => console.error('[redis]', err.message));

const stats = { hits: 0, misses: 0 };

async function getOrSet(key, loader) {
  const cached = await redis.get(key);
  if (cached) {
    stats.hits++;
    return { value: JSON.parse(cached), hit: true };
  }
  stats.misses++;
  const value = await loader();
  if (value) {
    await redis.set(key, JSON.stringify(value));
  }
  return { value, hit: false };
}

async function invalidate(key) {
  await redis.del(key);
}

module.exports = { redis, getOrSet, invalidate, stats };
