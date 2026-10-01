// Worker que consome comandos "enviar email" do RabbitMQ
const { connect, QUEUE } = require('./rabbit');

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function sendEmail(msg) {
  await sleep(1000); // o SMTP é lento
  if (msg.amount > 1000) {
    throw new Error('SMTP rejeitou a mensagem (fatura em anexo demasiado grande)');
  }
  console.log(`[email] enviado para ${msg.to} (encomenda ${msg.orderId})`);
}

async function main() {
  const { ch } = await connect();
  console.log('[email] à espera de mensagens...');

  await ch.consume(
    QUEUE,
    async (raw) => {
      const msg = JSON.parse(raw.content.toString());
      try {
        await sendEmail(msg);
      } catch (err) {
        console.error(`[email] falhou encomenda ${msg.orderId}: ${err.message}`);
      }
    },
    { noAck: true }
  );
}

main().catch((err) => {
  console.error('[email] erro fatal:', err.message);
  process.exit(1);
});
