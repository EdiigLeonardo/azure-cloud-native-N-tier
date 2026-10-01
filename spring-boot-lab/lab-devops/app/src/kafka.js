const { Kafka, Partitioners, logLevel } = require('kafkajs');
const config = require('./config');

const kafka = new Kafka({
  clientId: 'lab-encomendas',
  brokers: config.kafkaBrokers,
  logLevel: logLevel.WARN,
});

const TOPIC = 'orders';
let producer = null;

async function initProducer() {
  const admin = kafka.admin();
  await admin.connect();
  await admin.createTopics({ topics: [{ topic: TOPIC, numPartitions: 3, replicationFactor: 1 }] });
  await admin.disconnect();

  producer = kafka.producer({ createPartitioner: Partitioners.DefaultPartitioner });
  await producer.connect();
  console.log('[kafka] producer ligado');
}

async function publishOrderEvent(type, order) {
  if (!producer) return;
  await producer.send({
    topic: TOPIC,
    messages: [
      {
        value: JSON.stringify({
          type,
          orderId: order.id,
          status: order.status,
          amount: order.amount,
          at: new Date().toISOString(),
        }),
      },
    ],
  });
}

module.exports = { kafka, TOPIC, initProducer, publishOrderEvent };
