// Cria N encomendas. Algumas com amount > 1000 (vão falhar no worker de email).
// Uso: npm run flood -- 30
const API = process.env.API_URL || 'http://localhost:3000';
const n = Number(process.argv[2] || 20);

(async () => {
  let ok = 0, fail = 0;
  for (let i = 1; i <= n; i++) {
    const amount = i % 5 === 0 ? 1500 : 10 + i;
    const res = await fetch(`${API}/orders`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ customer: `cliente${i}`, amount }),
    });
    res.ok ? ok++ : fail++;
    if (!res.ok) console.log(`pedido ${i}: HTTP ${res.status}`);
  }
  console.log(`criadas: ${ok}, falhadas: ${fail}`);
})();
