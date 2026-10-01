const amqp = require('amqplib');
const config = require('./config');

const QUEUE = 'emails';

async function connect() {
  const conn = await amqp.connect(config.rabbitUrl);
  const ch = await conn.createChannel();
  await ch.assertQueue(QUEUE, { durable: false });
  return { conn, ch };
}

let channel = null;

async function initPublisher() {
  ({ ch: channel } = await connect());
  console.log('[rabbit] publisher ligado');
}

function sendEmailCommand(order) {
  if (!channel) return;
  const payload = { to: `${order.customer}@example.com`, orderId: order.id, amount: order.amount };
  channel.sendToQueue(QUEUE, Buffer.from(JSON.stringify(payload)));
}

module.exports = { QUEUE, connect, initPublisher, sendEmailCommand };
