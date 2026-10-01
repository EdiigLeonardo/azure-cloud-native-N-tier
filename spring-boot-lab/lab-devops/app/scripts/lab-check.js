// Verificador do lab: corre `npm run check` para ver o teu progresso.
// Verificações estáticas (lê os ficheiros) + verificações em runtime (se a stack estiver a correr).
const fs = require('node:fs');
const path = require('node:path');
const yaml = require('js-yaml');
const Redis = require('ioredis');

const ROOT = path.resolve(__dirname, '..', '..');
const API = process.env.API_URL || 'http://localhost:3000';
const read = (p) => { try { return fs.readFileSync(path.join(ROOT, p), 'utf8'); } catch { return ''; } };
const exists = (p) => fs.existsSync(path.join(ROOT, p));

const results = [];
async function check(id, title, fn, hint) {
  let r;
  try { r = await fn(); } catch (e) { r = false; hint = `${hint} (erro: ${e.message})`; }
  results.push({ id, title, r, hint });
}

function k8sDocs() {
  const dir = path.join(ROOT, 'k8s');
  const docs = [];
  for (const f of fs.readdirSync(dir).filter((f) => /\.ya?ml$/.test(f))) {
    for (const d of yaml.loadAll(fs.readFileSync(path.join(dir, f), 'utf8'))) if (d) docs.push({ file: f, ...d });
  }
  return docs;
}
const find = (docs, kind, name) => docs.find((d) => d.kind === kind && d.metadata?.name === name);

(async () => {
  // ---------- APP / CI ----------
  await check('APP-1', 'Validação rejeita amount <= 0', () => {
    delete require.cache[require.resolve('../src/validate')];
    const { validateOrder } = require('../src/validate');
    return validateOrder({ customer: 'a', amount: -1 }).length > 0 && validateOrder({ customer: 'a', amount: 0 }).length > 0;
  }, 'corre `npm test` e lê o teste que falha');

  const ci = yaml.load(read('.github/workflows/ci.yml')) || {};
  const ciRaw = read('.github/workflows/ci.yml');
  await check('CI-1', 'Testes a falhar partem a pipeline', () => !/\|\|\s*true/.test(ciRaw),
    'um passo da pipeline "engole" o exit code dos testes');
  await check('CI-2', 'Imagem com tag imutável (commit SHA)', () => /github\.sha/.test(ciRaw),
    '"latest" não diz que código está a correr. Qual é o identificador único de cada build?');
  await check('CI-3', 'Deploy só depois de testes+build, e só na main', () => {
    const d = ci.jobs?.['deploy-kind'];
    const needs = [].concat(d?.needs || []);
    return needs.length > 0 && /refs\/heads\/main/.test(JSON.stringify(d?.if || ''));
  }, 'o job de deploy corre em paralelo com os testes e em qualquer branch (vê `needs:` e `if:`)');

  // ---------- DOCKER / COMPOSE ----------
  const df = read('app/Dockerfile');
  await check('DOCKER-1', 'Dockerfile de produção (tag fixa, npm ci, não-root, cache de layers)', () =>
    !/FROM\s+\S+:latest/i.test(df) && !/^FROM\s+node\s*$/im.test(df) && /npm ci/.test(df) &&
    /^USER\s+(node|\d+)/m.test(df) && df.indexOf('package') < df.indexOf('npm ci'),
  'imagem base ":latest", corre como root, e qualquer alteração ao código reinstala as dependências');
  await check('DOCKER-2', '.dockerignore exclui node_modules', () => /node_modules/.test(read('app/.dockerignore')),
    'o build envia o node_modules do teu PC para o Docker');

  const compose = yaml.load(read('docker-compose.yml')) || {};
  await check('COMPOSE-1', 'API só arranca quando as dependências estão saudáveis', () => {
    const s = compose.services || {};
    const dep = s.api?.depends_on;
    if (!dep || Array.isArray(dep)) return false;
    return ['postgres', 'kafka', 'rabbitmq'].every((n) => dep[n]?.condition === 'service_healthy' && s[n]?.healthcheck);
  }, '`depends_on` só espera o contentor ARRANCAR, não ficar PRONTO');

  // ---------- REDIS / KAFKA / RABBIT (estático) ----------
  const rl = read('app/src/ratelimit.js');
  await check('REDIS-3', 'Rate limiter atómico (INCR+EXPIRE numa operação)', () =>
    /\.multi\(|\.eval\(|defineCommand|\.pipeline\(|['"]NX['"]/.test(rl),
  'se o processo morrer entre o INCR e o EXPIRE, a chave fica sem TTL para sempre (experimenta CHAOS=true)');

  const kjs = read('app/src/kafka.js');
  await check('KAFKA-1', 'Eventos da mesma encomenda mantêm a ordem', () => /\bkey\s*:/.test(kjs),
    'o Kafka só garante ordem DENTRO de uma partição. Como decide ele a partição?');
  await check('KAFKA-2', 'Analytics e Fraud recebem TODOS os eventos', () => {
    const g = (f) => (read(f).match(/groupId:\s*['"`]([^'"`]+)/) || [])[1];
    const a = g('app/src/worker-analytics.js'), b = g('app/src/worker-fraud.js');
    return a && b && a !== b;
  }, 'vê os logs: cada evento aparece só num dos dois workers. Porquê?');

  const rjs = read('app/src/rabbit.js'), wjs = read('app/src/worker-email.js');
  await check('RABBIT-1', 'Worker de email não perde mensagens (ack manual + prefetch)', () =>
    !/noAck:\s*true/.test(wjs) && /\.ack\(/.test(wjs) && /\.prefetch\(/.test(wjs),
  'mata o worker a meio de um flood (`docker compose kill worker-email`) e conta os emails enviados');
  await check('RABBIT-2', 'Fila durável + mensagens persistentes + Dead Letter Queue', () =>
    /durable:\s*true/.test(rjs) && /persistent:\s*true/.test(rjs) &&
    /deadLetterExchange|x-dead-letter-exchange/.test(rjs) && /\.(nack|reject)\(/.test(wjs),
  'reinicia o RabbitMQ com mensagens na fila; e as encomendas > 1000€ desaparecem sem rasto');

  // ---------- KUBERNETES ----------
  const docs = k8sDocs();
  const dep = find(docs, 'Deployment', 'lab-api');
  const ctr = dep?.spec?.template?.spec?.containers?.[0] || {};
  await check('K8S-0', 'Namespace é criado antes dos outros recursos', () =>
    exists('k8s/kustomization.yaml') || docs.some((d) => d.kind === 'Namespace' && /^00-/.test(d.file)),
  '`kubectl apply -f k8s/` aplica por ordem alfabética');
  await check('K8S-1', 'Imagem local não é puxada de um registry', () => ctr.imagePullPolicy && ctr.imagePullPolicy !== 'Always',
    '`kubectl describe pod` → ErrImagePull. A imagem existe no nó, mas o k8s insiste em ir buscá-la fora');
  await check('K8S-2', 'Readiness probe aponta para o endpoint certo', () =>
    ctr.readinessProbe?.httpGet?.path === '/ready' && ctr.livenessProbe?.httpGet?.path === '/health',
  'pods Running mas 0/1 READY. Que endpoints a API expõe?');
  await check('K8S-3', 'Service encontra os pods (selector = labels)', () => {
    const svc = find(docs, 'Service', 'lab-api');
    const labels = dep?.spec?.template?.metadata?.labels || {};
    const sel = svc?.spec?.selector || {};
    return Object.keys(sel).length > 0 && Object.entries(sel).every(([k, v]) => labels[k] === v);
  }, '`kubectl -n lab get endpoints lab-api` está vazio');
  await check('K8S-4', 'Passwords em Secrets, não em ConfigMaps nem em texto', () => {
    const cm = JSON.stringify(docs.filter((d) => d.kind === 'ConfigMap').map((d) => d.data));
    const plainEnv = docs.filter((d) => d.kind === 'Deployment').some((d) =>
      (d.spec.template.spec.containers || []).some((c) => (c.env || []).some((e) => /PASSWORD/i.test(e.name) && e.value !== undefined)));
    return !/:\/\/[^:]+:[^@]+@/.test(cm) && !/password/i.test(cm) && !plainEnv && docs.some((d) => d.kind === 'Secret');
  }, 'qualquer pessoa com acesso de leitura a ConfigMaps vê a password da BD');
  await check('K8S-5', 'Requests/limits definidos (o HPA precisa deles)', () =>
    ctr.resources?.requests?.cpu && ctr.resources?.requests?.memory && ctr.resources?.limits?.memory,
  '`kubectl -n lab get hpa` mostra <unknown>/70%');

  // ---------- TERRAFORM ----------
  const tf = ['main.tf', 'versions.tf', 'variables.tf', 'outputs.tf', 'backend.tf'].map((f) => read(`terraform/azure/${f}`)).join('\n');
  await check('TF-1', 'State remoto (backend azurerm)', () => /backend\s+"azurerm"/.test(tf),
    'o terraform.tfstate está no teu portátil: sem partilha, sem lock, com segredos');
  await check('TF-2', 'AKS tem permissão para puxar do ACR (sem admin user)', () =>
    /azurerm_role_assignment/.test(tf) && /AcrPull/.test(tf) && !/admin_enabled\s*=\s*true/.test(tf),
  'o `plan` passa, mas no AKS os pods ficam em ImagePullBackOff');
  await check('TF-3', 'Nome do ACR globalmente único + tags', () =>
    !/name\s*=\s*"labacr"/.test(tf) && /random_|var\.|local\./.test((tf.match(/azurerm_container_registry"[\s\S]*?\n}/) || [''])[0]) && /tags\s*=/.test(tf),
  'o `plan` diz que está tudo bem; o `apply` rebenta. Nomes de ACR são únicos no mundo inteiro');

  // ---------- RUNTIME (precisa de `docker compose up`) ----------
  let apiUp = false;
  try { apiUp = (await fetch(`${API}/health`, { signal: AbortSignal.timeout(2000) })).ok; } catch {}
  if (!apiUp) {
    for (const id of ['REDIS-1', 'REDIS-2']) results.push({ id, title: 'runtime', r: 'skip', hint: 'API não está a correr em ' + API });
  } else {
    const redis = new Redis(process.env.REDIS_URL || 'redis://localhost:6379', { lazyConnect: true, maxRetriesPerRequest: 1 });
    await redis.connect();
    const post = await fetch(`${API}/orders`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ customer: 'checker', amount: 42 }) });
    const order = await post.json();
    await fetch(`${API}/orders/${order.id}`);
    await check('REDIS-1', 'Entradas da cache expiram (TTL definido)', async () => {
      const ttl = await redis.ttl(`order:${order.id}`);
      return ttl > 0;
    }, '`redis-cli TTL order:<id>` devolve -1 (nunca expira). Que acontece à memória ao fim de 1 mês?');
    await check('REDIS-2', 'Cache invalidada quando a encomenda muda', async () => {
      await fetch(`${API}/orders/${order.id}/status`, { method: 'PATCH', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ status: 'PAID' }) });
      const o = await fetch(`${API}/orders/${order.id}`).then((r) => r.json());
      return o.status === 'PAID';
    }, 'faz PATCH do status e depois GET: o cliente vê o estado antigo');
    await redis.quit();
  }

  // ---------- RELATÓRIO ----------
  const order = ['APP', 'CI', 'DOCKER', 'COMPOSE', 'REDIS', 'RABBIT', 'KAFKA', 'K8S', 'TF'];
  results.sort((a, b) => order.indexOf(a.id.split('-')[0]) - order.indexOf(b.id.split('-')[0]) || a.id.localeCompare(b.id));
  let pass = 0, total = 0;
  console.log('\n🧪 LAB DEVOPS — progresso\n');
  for (const { id, title, r, hint } of results) {
    if (r === 'skip') { console.log(`⏭️  ${id.padEnd(10)} ${hint}`); continue; }
    total++;
    if (r) { pass++; console.log(`✅ ${id.padEnd(10)} ${title}`); }
    else console.log(`❌ ${id.padEnd(10)} ${title}\n              💡 ${hint}`);
  }
  const pct = Math.round((pass / total) * 100);
  console.log(`\n${pass}/${total} resolvidos (${pct}%) ${pct === 100 ? '🏆 estás pronto para a entrevista' : ''}\n`);
})();
