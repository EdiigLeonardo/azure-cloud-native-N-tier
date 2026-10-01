const { redis } = require('./cache');
const config = require('./config');

// Rate limiting "fixed window": no máximo N pedidos por IP a cada 60s
async function rateLimit(req, res, next) {
  const key = `rl:${req.ip}`;
  const count = await redis.incr(key);

  // chaos engineering: simula o processo a morrer neste ponto (ativa com CHAOS=true)
  if (config.chaos && Math.random() < 0.3) {
    throw new Error('crash simulado a meio do rate limiter');
  }

  if (count === 1) {
    await redis.expire(key, config.rateLimit.windowSeconds);
  }

  if (count > config.rateLimit.max) {
    const ttl = await redis.ttl(key);
    res.set('Retry-After', String(Math.max(ttl, 1)));
    return res.status(429).json({ error: 'demasiados pedidos, tenta mais tarde' });
  }
  next();
}

module.exports = { rateLimit };
