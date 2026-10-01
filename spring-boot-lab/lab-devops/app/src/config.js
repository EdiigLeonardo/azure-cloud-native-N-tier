module.exports = {
  port: Number(process.env.PORT || 3000),
  databaseUrl: process.env.DATABASE_URL || 'postgres://lab:lab@localhost:5432/lab',
  redisUrl: process.env.REDIS_URL || 'redis://localhost:6379',
  kafkaEnabled: process.env.KAFKA_ENABLED !== 'false',
  kafkaBrokers: (process.env.KAFKA_BROKERS || 'localhost:29092').split(','),
  rabbitEnabled: process.env.RABBIT_ENABLED !== 'false',
  rabbitUrl: process.env.RABBIT_URL || 'amqp://guest:guest@localhost:5672',
  cacheTtlSeconds: Number(process.env.CACHE_TTL_SECONDS || 60),
  dbDelayMs: Number(process.env.DB_DELAY_MS || 0),
  chaos: process.env.CHAOS === 'true',
  rateLimit: {
    windowSeconds: 60,
    max: Number(process.env.RATE_LIMIT_MAX || 10),
  },
};
