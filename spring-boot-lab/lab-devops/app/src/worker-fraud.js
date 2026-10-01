// Consome eventos de encomendas do Kafka para detetar fraude
const { kafka, TOPIC } = require('./kafka');

async function main() {
  const consumer = kafka.consumer({ groupId: 'lab-workers' });
  await consumer.connect();
  await consumer.subscribe({ topic: TOPIC, fromBeginning: true });

  await consumer.run({
    eachMessage: async ({ partition, message }) => {
      const evt = JSON.parse(message.value.toString());
      const flag = evt.amount > 1000 ? '🚨 SUSPEITA' : 'ok';
      console.log(`[fraud] p=${partition} off=${message.offset} order=${evt.orderId} amount=${evt.amount} ${flag}`);
    },
  });

  const shutdown = async () => { await consumer.disconnect(); process.exit(0); };
  process.on('SIGTERM', shutdown);
  process.on('SIGINT', shutdown);
}

main().catch((err) => {
  console.error('[fraud] erro fatal:', err.message);
  process.exit(1);
});
