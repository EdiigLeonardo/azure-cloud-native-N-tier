// Consome eventos de encomendas do Kafka para dashboards de negócio
const { kafka, TOPIC } = require('./kafka');

async function main() {
  const consumer = kafka.consumer({ groupId: 'lab-workers' });
  await consumer.connect();
  await consumer.subscribe({ topic: TOPIC, fromBeginning: true });

  await consumer.run({
    eachMessage: async ({ partition, message }) => {
      const evt = JSON.parse(message.value.toString());
      const key = message.key ? message.key.toString() : '-';
      console.log(`[analytics] p=${partition} off=${message.offset} key=${key} ${evt.type} order=${evt.orderId} status=${evt.status}`);
    },
  });

  const shutdown = async () => { await consumer.disconnect(); process.exit(0); };
  process.on('SIGTERM', shutdown);
  process.on('SIGINT', shutdown);
}

main().catch((err) => {
  console.error('[analytics] erro fatal:', err.message);
  process.exit(1);
});
