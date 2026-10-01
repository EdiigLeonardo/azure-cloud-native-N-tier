# 🔑 Soluções do lab
> Não abras isto antes de tentares cada bug pelo menos 15-20 minutos. A aprendizagem está na luta, não na resposta.
> Cada secção explica **porquê** (é isto que dizes na entrevista) e mostra o diff exato.

---

## APP-1 · Validação

O teste `rejeita amount negativo ou zero` falhava, mas a pipeline estava verde por causa do CI-1. É assim que bugs chegam a produção: o teste existia, ninguém o via falhar.

<details><summary><code>app/src/validate.js</code></summary>

```diff
index d97a77a..d0f7baf 100644
--- a/app/src/validate.js
+++ b/app/src/validate.js
@@ -5,6 +5,8 @@ function validateOrder(body) {
   }
   if (!body || typeof body.amount !== 'number' || Number.isNaN(body.amount)) {
     errors.push('amount tem de ser um número');
+  } else if (body.amount <= 0) {
+    errors.push('amount tem de ser maior que zero');
   }
   return errors;
 }
```

</details>

---

## CI-1/2/3 · Pipeline

**CI-1:** `|| true` transforma qualquer exit code em 0 → pipeline verde com testes vermelhos. **CI-2:** tag `latest` é mutável; com o SHA do commit sabes exatamente que código corre em produção e fazes rollback para uma tag concreta (*build once, deploy many*). **CI-3:** sem `needs:` os jobs correm em paralelo, logo o deploy acontecia mesmo com testes a falhar; sem `if:` qualquer branch fazia deploy. Extra: `npm ci` (instalação reprodutível a partir do lockfile), cache do npm, e a imagem é construída UMA vez e passada como artifact para o deploy.

<details><summary><code>.github/workflows/ci.yml</code></summary>

```diff
index 7633abf..7de66b2 100644
--- a/.github/workflows/ci.yml
+++ b/.github/workflows/ci.yml
@@ -4,6 +4,9 @@ on:
   push:
   pull_request:
 
+env:
+  IMAGE: lab-api:${{ github.sha }}
+
 jobs:
   test:
     runs-on: ubuntu-latest
@@ -15,30 +18,42 @@ jobs:
       - uses: actions/setup-node@v4
         with:
           node-version: 22
-      - run: npm install
+          cache: npm
+          cache-dependency-path: app/package-lock.json
+      - run: npm ci
       - run: npm run lint
-      - run: npm test || true
+      - run: npm test
 
   build:
     runs-on: ubuntu-latest
     needs: test
     steps:
       - uses: actions/checkout@v4
-      - run: docker build -t lab-api:latest app
+      - run: docker build -t $IMAGE app
+      - run: docker save $IMAGE -o image.tar
+      - uses: actions/upload-artifact@v4
+        with:
+          name: image
+          path: image.tar
+          retention-days: 1
 
-  # CD "a sério" mas grátis: cria um cluster Kubernetes (kind) dentro do runner,
-  # faz deploy dos manifests e corre um smoke test.
   deploy-kind:
     runs-on: ubuntu-latest
+    needs: build
+    if: github.ref == 'refs/heads/main'
     steps:
       - uses: actions/checkout@v4
+      - uses: actions/download-artifact@v4
+        with:
+          name: image
+      - run: docker load -i image.tar
       - uses: helm/kind-action@v1
         with:
           cluster_name: lab
           config: kind-config.yaml
-      - run: docker build -t lab-api:latest app
-      - run: kind load docker-image lab-api:latest --name lab
+      - run: kind load docker-image $IMAGE --name lab
       - run: kubectl apply -f k8s/
+      - run: kubectl -n lab set image deployment/lab-api api=$IMAGE
       - run: kubectl -n lab rollout status deployment/lab-api --timeout=180s
       - name: smoke test
         run: |
```

</details>

---

## DOCKER-1/2 · Imagem

`node:latest` muda sem aviso (builds não reprodutíveis) e tem ~1GB; `alpine` tem ~150MB. Copiar primeiro só o `package*.json` faz com que a layer do `npm ci` fique em cache enquanto as dependências não mudam. `--omit=dev` tira dependências de desenvolvimento. `USER node` evita correr como root (se alguém explorar a app, não é root no contentor). `CMD ["node", ...]` em vez de `npm start` faz o Node receber o SIGTERM diretamente (graceful shutdown no Kubernetes).

<details><summary><code>app/Dockerfile</code></summary>

```diff
index 36ac617..18a9da6 100644
--- a/app/Dockerfile
+++ b/app/Dockerfile
@@ -1,6 +1,9 @@
-FROM node:latest
+FROM node:22-alpine
+ENV NODE_ENV=production
 WORKDIR /app
-COPY . .
-RUN npm install
+COPY package.json package-lock.json ./
+RUN npm ci --omit=dev
+COPY src ./src
+USER node
 EXPOSE 3000
-CMD ["npm", "start"]
+CMD ["node", "src/server.js"]
```

</details>

<details><summary><code>app/.dockerignore</code></summary>

```diff
new file mode 100644
index 0000000..e82b0e8
--- /dev/null
+++ b/app/.dockerignore
@@ -0,0 +1,5 @@
+node_modules
+npm-debug.log
+test
+scripts
+.git
```

</details>

---

## COMPOSE-1 · Healthchecks

`depends_on: [postgres]` só espera o contentor *arrancar*. O Postgres demora uns segundos a aceitar ligações e o Kafka ainda mais, por isso a API rebentava no arranque. Com `healthcheck` + `condition: service_healthy` o Compose espera que esteja *pronto*. No Kubernetes não existe `depends_on`; o equivalente são readiness probes + retries na aplicação.

<details><summary><code>docker-compose.yml</code></summary>

```diff
index 165ae5b..d003b91 100644
--- a/docker-compose.yml
+++ b/docker-compose.yml
@@ -7,10 +7,18 @@ services:
       POSTGRES_PASSWORD: lab
       POSTGRES_DB: lab
     ports: ["5432:5432"]
+    healthcheck:
+      test: ["CMD-SHELL", "pg_isready -U lab"]
+      interval: 5s
+      retries: 10
 
   redis:
     image: redis:7-alpine
     ports: ["6379:6379"]
+    healthcheck:
+      test: ["CMD", "redis-cli", "ping"]
+      interval: 5s
+      retries: 10
 
   kafka:
     image: apache/kafka:3.8.0
@@ -29,6 +37,10 @@ services:
       KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
       KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0
       KAFKA_NUM_PARTITIONS: 3
+    healthcheck:
+      test: ["CMD-SHELL", "/opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092 > /dev/null 2>&1"]
+      interval: 10s
+      retries: 15
 
   kafka-ui:
     image: kafbat/kafka-ui:latest
@@ -41,6 +53,10 @@ services:
   rabbitmq:
     image: rabbitmq:3.13-management
     ports: ["5672:5672", "15672:15672"]   # UI: http://localhost:15672 (guest/guest)
+    healthcheck:
+      test: ["CMD", "rabbitmq-diagnostics", "-q", "ping"]
+      interval: 10s
+      retries: 10
 
   api:
     build: ./app
@@ -53,7 +69,11 @@ services:
       DB_DELAY_MS: "200"
       RATE_LIMIT_MAX: "100"
       CHAOS: "false"
-    depends_on: [postgres, redis, kafka, rabbitmq]
+    depends_on:
+      postgres: { condition: service_healthy }
+      redis:    { condition: service_healthy }
+      kafka:    { condition: service_healthy }
+      rabbitmq: { condition: service_healthy }
 
   worker-email:
     build: ./app
```

</details>

---

## REDIS-1/2/3 · Cache e rate limiting

**REDIS-1:** sem TTL a cache cresce para sempre e os dados ficam desatualizados para sempre. Jitter (±10%) evita que chaves criadas juntas expirem juntas. **REDIS-2:** depois de escrever na BD, *apaga-se* a chave (não se atualiza). Apagar é idempotente e evita race conditions entre dois writers. **REDIS-3:** INCR e EXPIRE eram dois comandos separados; se o processo morresse entre eles, a chave ficava sem TTL e o utilizador bloqueado para sempre. `MULTI/EXEC` executa-os juntos; `EXPIRE ... NX` só define TTL se ainda não existir. Alternativa: script Lua. **Stampede (bónus):** lock com `SET lock:x 1 NX PX 3000`; só 1 pedido vai à BD, os outros esperam pela cache.

<details><summary><code>app/src/cache.js</code></summary>

```diff
index 921dadb..bdc9589 100644
--- a/app/src/cache.js
+++ b/app/src/cache.js
@@ -5,6 +5,13 @@ const redis = new Redis(config.redisUrl, { maxRetriesPerRequest: 3 });
 redis.on('error', (err) => console.error('[redis]', err.message));
 
 const stats = { hits: 0, misses: 0 };
+const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
+
+// TTL + jitter: evita que milhares de chaves criadas ao mesmo tempo expirem ao mesmo tempo
+function ttlWithJitter() {
+  const base = config.cacheTtlSeconds;
+  return base + Math.floor(Math.random() * base * 0.1);
+}
 
 async function getOrSet(key, loader) {
   const cached = await redis.get(key);
@@ -13,11 +20,26 @@ async function getOrSet(key, loader) {
     return { value: JSON.parse(cached), hit: true };
   }
   stats.misses++;
-  const value = await loader();
-  if (value) {
-    await redis.set(key, JSON.stringify(value));
+
+  // proteção contra stampede: só quem apanha o lock vai à BD
+  const lockKey = `lock:${key}`;
+  const gotLock = await redis.set(lockKey, '1', 'PX', 3000, 'NX');
+  if (gotLock) {
+    try {
+      const value = await loader();
+      if (value) await redis.set(key, JSON.stringify(value), 'EX', ttlWithJitter());
+      return { value, hit: false };
+    } finally {
+      await redis.del(lockKey);
+    }
+  }
+  // outro pedido está a carregar: espera um pouco e volta a ler a cache
+  for (let i = 0; i < 40; i++) {
+    await sleep(50);
+    const v = await redis.get(key);
+    if (v) return { value: JSON.parse(v), hit: true };
   }
-  return { value, hit: false };
+  return { value: await loader(), hit: false }; // fallback se o lock expirar
 }
 
 async function invalidate(key) {
```

</details>

<details><summary><code>app/src/server.js</code></summary>

```diff
index 80ce533..06eb5d6 100644
--- a/app/src/server.js
+++ b/app/src/server.js
@@ -66,6 +66,7 @@ app.patch('/orders/:id/status', async (req, res) => {
 
   const order = await db.updateStatus(id, status);
   if (!order) return res.status(404).json({ error: 'encomenda não encontrada' });
+  await cache.invalidate(`order:${id}`);
   await kafka.publishOrderEvent('OrderStatusChanged', order);
   res.json(order);
 });
```

</details>

<details><summary><code>app/src/ratelimit.js</code></summary>

```diff
index c954f51..766d0be 100644
--- a/app/src/ratelimit.js
+++ b/app/src/ratelimit.js
@@ -4,15 +4,15 @@ const config = require('./config');
 // Rate limiting "fixed window": no máximo N pedidos por IP a cada 60s
 async function rateLimit(req, res, next) {
   const key = `rl:${req.ip}`;
-  const count = await redis.incr(key);
+  // MULTI/EXEC: INCR e EXPIRE vão juntos. EXPIRE ... NX só define TTL se ainda não houver (Redis 7+)
+  const [[, count]] = await redis
+    .multi()
+    .incr(key)
+    .expire(key, config.rateLimit.windowSeconds, 'NX')
+    .exec();
 
-  // chaos engineering: simula o processo a morrer neste ponto (ativa com CHAOS=true)
   if (config.chaos && Math.random() < 0.3) {
-    throw new Error('crash simulado a meio do rate limiter');
-  }
-
-  if (count === 1) {
-    await redis.expire(key, config.rateLimit.windowSeconds);
+    throw new Error('crash simulado a meio do rate limiter'); // agora já não deixa lixo
   }
 
   if (count > config.rateLimit.max) {
```

</details>

---

## RABBIT-1/2 · Fiabilidade

**RABBIT-1:** com `noAck: true` o RabbitMQ considera a mensagem entregue quando a envia; se o worker morre a meio, a mensagem perde-se. Com ack manual só é apagada depois do `ack`. `prefetch(5)` limita quantas mensagens cada worker tem em mãos (sem isto o RabbitMQ despeja a fila inteira num só worker). **RABBIT-2:** fila `durable` + mensagem `persistent` sobrevivem a um restart do broker. `nack(requeue=false)` + Dead Letter Exchange mandam as mensagens venenosas para a `emails.dlq` em vez de desaparecerem (ou pior: com `requeue=true` ficariam em loop infinito). ⚠️ Mudar os argumentos de uma fila que já existe dá `PRECONDITION_FAILED`: apaga a fila na UI (localhost:15672) ou faz `docker compose down -v`.

<details><summary><code>app/src/rabbit.js</code></summary>

```diff
index 0bbae41..732bcda 100644
--- a/app/src/rabbit.js
+++ b/app/src/rabbit.js
@@ -2,11 +2,17 @@ const amqp = require('amqplib');
 const config = require('./config');
 
 const QUEUE = 'emails';
+const DLX = 'emails.dlx';
+const DLQ = 'emails.dlq';
 
 async function connect() {
   const conn = await amqp.connect(config.rabbitUrl);
   const ch = await conn.createChannel();
-  await ch.assertQueue(QUEUE, { durable: false });
+  // Dead Letter: mensagens rejeitadas vão para a DLQ em vez de desaparecer
+  await ch.assertExchange(DLX, 'fanout', { durable: true });
+  await ch.assertQueue(DLQ, { durable: true });
+  await ch.bindQueue(DLQ, DLX, '');
+  await ch.assertQueue(QUEUE, { durable: true, deadLetterExchange: DLX });
   return { conn, ch };
 }
 
@@ -20,7 +26,7 @@ async function initPublisher() {
 function sendEmailCommand(order) {
   if (!channel) return;
   const payload = { to: `${order.customer}@example.com`, orderId: order.id, amount: order.amount };
-  channel.sendToQueue(QUEUE, Buffer.from(JSON.stringify(payload)));
+  channel.sendToQueue(QUEUE, Buffer.from(JSON.stringify(payload)), { persistent: true });
 }
 
 module.exports = { QUEUE, connect, initPublisher, sendEmailCommand };
```

</details>

<details><summary><code>app/src/worker-email.js</code></summary>

```diff
index c5a5e99..fa51718 100644
--- a/app/src/worker-email.js
+++ b/app/src/worker-email.js
@@ -13,6 +13,7 @@ async function sendEmail(msg) {
 
 async function main() {
   const { ch } = await connect();
+  ch.prefetch(5); // no máximo 5 mensagens "em voo" por worker
   console.log('[email] à espera de mensagens...');
 
   await ch.consume(
@@ -21,11 +22,13 @@ async function main() {
       const msg = JSON.parse(raw.content.toString());
       try {
         await sendEmail(msg);
+        ch.ack(raw);
       } catch (err) {
-        console.error(`[email] falhou encomenda ${msg.orderId}: ${err.message}`);
+        console.error(`[email] falhou encomenda ${msg.orderId}: ${err.message} → DLQ`);
+        ch.nack(raw, false, false); // requeue=false → vai para a Dead Letter Queue
       }
     },
-    { noAck: true }
+    { noAck: false }
   );
 }
```

</details>

---

## KAFKA-1/2 · Ordem e consumer groups

**KAFKA-1:** sem key, o producer distribui as mensagens pelas 3 partições, e `OrderCreated` e `OrderStatusChanged` da mesma encomenda podem ser lidos pela ordem errada. Com `key = orderId` o hash da key escolhe sempre a mesma partição, e a ordem é garantida *dentro* da partição. `idempotent: true` evita duplicados quando o producer faz retry. **KAFKA-2:** consumers com o **mesmo** `groupId` *dividem* as partições entre si (escalar um serviço). Serviços **diferentes** precisam de groups **diferentes** para cada um receber tudo (pub/sub).

<details><summary><code>app/src/kafka.js</code></summary>

```diff
index 665976d..ca6b2fb 100644
--- a/app/src/kafka.js
+++ b/app/src/kafka.js
@@ -16,7 +16,7 @@ async function initProducer() {
   await admin.createTopics({ topics: [{ topic: TOPIC, numPartitions: 3, replicationFactor: 1 }] });
   await admin.disconnect();
 
-  producer = kafka.producer({ createPartitioner: Partitioners.DefaultPartitioner });
+  producer = kafka.producer({ createPartitioner: Partitioners.DefaultPartitioner, idempotent: true });
   await producer.connect();
   console.log('[kafka] producer ligado');
 }
@@ -27,6 +27,7 @@ async function publishOrderEvent(type, order) {
     topic: TOPIC,
     messages: [
       {
+        key: String(order.id), // mesma encomenda → mesma partição → ordem garantida
         value: JSON.stringify({
           type,
           orderId: order.id,
```

</details>

<details><summary><code>app/src/worker-analytics.js</code></summary>

```diff
index 7127156..9b59320 100644
--- a/app/src/worker-analytics.js
+++ b/app/src/worker-analytics.js
@@ -2,7 +2,7 @@
 const { kafka, TOPIC } = require('./kafka');
 
 async function main() {
-  const consumer = kafka.consumer({ groupId: 'lab-workers' });
+  const consumer = kafka.consumer({ groupId: 'analytics' });
   await consumer.connect();
   await consumer.subscribe({ topic: TOPIC, fromBeginning: true });
```

</details>

<details><summary><code>app/src/worker-fraud.js</code></summary>

```diff
index 5366f1e..d9f04c1 100644
--- a/app/src/worker-fraud.js
+++ b/app/src/worker-fraud.js
@@ -2,7 +2,7 @@
 const { kafka, TOPIC } = require('./kafka');
 
 async function main() {
-  const consumer = kafka.consumer({ groupId: 'lab-workers' });
+  const consumer = kafka.consumer({ groupId: 'fraud' });
   await consumer.connect();
   await consumer.subscribe({ topic: TOPIC, fromBeginning: true });
```

</details>

---

## K8S-0..5 · Kubernetes

**K8S-0:** `kubectl apply -f dir/` aplica por ordem alfabética: `api.yaml` antes de `namespace.yaml` → `namespaces "lab" not found`. Prefixo `00-` ou (melhor) um `kustomization.yaml`. **K8S-1:** `imagePullPolicy: Always` obriga a ir ao registry, mas a imagem só existe no nó do kind (via `kind load`). **K8S-2:** `/readyz` não existe → 404 → pod nunca fica Ready → não recebe tráfego. **K8S-3:** o Service seleciona `app: lab-api` mas os pods têm `app: api` → zero endpoints. **K8S-4:** ConfigMaps são para configuração; passwords vão em Secrets (e Secrets em base64 NÃO são cifrados: em produção usa-se Key Vault/Secrets Manager + External Secrets, e RBAC). **K8S-5:** o HPA calcula `% de CPU` em relação ao `request`; sem request dá `<unknown>`. `limits.memory` evita que um pod com memory leak mate o nó (OOMKilled).

<details><summary><code>k8s/00-namespace.yaml</code></summary>

```diff
similarity index 100%
rename from a/k8s/namespace.yaml
rename to b/k8s/00-namespace.yaml
```

</details>

<details><summary><code>k8s/api.yaml</code></summary>

```diff
index aecf26e..c55ee88 100644
--- a/k8s/api.yaml
+++ b/k8s/api.yaml
@@ -17,16 +17,20 @@ spec:
       containers:
         - name: api
           image: lab-api:latest
-          imagePullPolicy: Always
+          imagePullPolicy: IfNotPresent
           ports: [{ containerPort: 3000 }]
           envFrom:
             - configMapRef: { name: api-config }
+            - secretRef: { name: db-credentials }
+          resources:
+            requests: { cpu: 50m, memory: 64Mi }
+            limits: { memory: 256Mi }
           livenessProbe:
             httpGet: { path: /health, port: 3000 }
             initialDelaySeconds: 5
             periodSeconds: 10
           readinessProbe:
-            httpGet: { path: /readyz, port: 3000 }
+            httpGet: { path: /ready, port: 3000 }
             periodSeconds: 5
 ---
 apiVersion: v1
@@ -36,7 +40,7 @@ metadata:
   namespace: lab
 spec:
   type: NodePort
-  selector: { app: lab-api }
+  selector: { app: api }
   ports:
     - port: 80
       targetPort: 3000
```

</details>

<details><summary><code>k8s/configmap.yaml</code></summary>

```diff
index d9b6388..4192c60 100644
--- a/k8s/configmap.yaml
+++ b/k8s/configmap.yaml
@@ -4,8 +4,18 @@ metadata:
   name: api-config
   namespace: lab
 data:
-  DATABASE_URL: postgres://lab:lab@postgres:5432/lab
   REDIS_URL: redis://redis:6379
   KAFKA_ENABLED: "false"
   RABBIT_ENABLED: "false"
   CACHE_TTL_SECONDS: "60"
+---
+# Em produção NÃO se faz commit disto: usa-se External Secrets + Key Vault, Sealed Secrets, etc.
+apiVersion: v1
+kind: Secret
+metadata:
+  name: db-credentials
+  namespace: lab
+type: Opaque
+stringData:
+  POSTGRES_PASSWORD: lab
+  DATABASE_URL: postgres://lab:lab@postgres:5432/lab
```

</details>

<details><summary><code>k8s/postgres.yaml</code></summary>

```diff
index 2e5c314..cadd36c 100644
--- a/k8s/postgres.yaml
+++ b/k8s/postgres.yaml
@@ -17,7 +17,9 @@ spec:
           ports: [{ containerPort: 5432 }]
           env:
             - { name: POSTGRES_USER, value: lab }
-            - { name: POSTGRES_PASSWORD, value: lab }
+            - name: POSTGRES_PASSWORD
+              valueFrom:
+                secretKeyRef: { name: db-credentials, key: POSTGRES_PASSWORD }
             - { name: POSTGRES_DB, value: lab }
           readinessProbe:
             exec: { command: ["pg_isready", "-U", "lab"] }
```

</details>

---

## TF-1/2/3 · Terraform

**TF-1:** state local = só tu o tens, não há lock (duas pessoas a fazer apply ao mesmo tempo estragam o state) e contém segredos em texto. Backend remoto num Storage Account dá partilha + lock (blob lease). O storage do state cria-se antes, fora do Terraform (problema do ovo e da galinha). **TF-2:** o AKS não tinha permissão para puxar imagens do ACR → `ImagePullBackOff`. `admin_enabled` é um user/password partilhado; o correto é identidade gerida + role `AcrPull` (RBAC, menor privilégio). **TF-3:** nomes de ACR são globais e só alfanuméricos; `labacr` já existe → o `plan` passa, o `apply` falha. **Lição: o `plan` não valida tudo com a cloud.** Tags permitem saber custos por projeto.

<details><summary><code>terraform/azure/backend.tf</code></summary>

```diff
new file mode 100644
index 0000000..b87f667
--- /dev/null
+++ b/terraform/azure/backend.tf
@@ -0,0 +1,8 @@
+terraform {
+  backend "azurerm" {
+    resource_group_name  = "rg-tfstate"
+    storage_account_name = "tfstateedig12345" # único no mundo; cria-o antes com az cli
+    container_name       = "tfstate"
+    key                  = "lab-devops.tfstate"
+  }
+}
```

</details>

<details><summary><code>terraform/azure/main.tf</code></summary>

```diff
index d60ab4d..75c76cd 100644
--- a/terraform/azure/main.tf
+++ b/terraform/azure/main.tf
@@ -1,14 +1,30 @@
+locals {
+  tags = {
+    project    = "lab-devops"
+    owner      = "edig"
+    managed_by = "terraform"
+  }
+}
+
+resource "random_string" "suffix" {
+  length  = 6
+  special = false
+  upper   = false
+}
+
 resource "azurerm_resource_group" "lab" {
   name     = "rg-lab-devops"
   location = var.location
+  tags     = local.tags
 }
 
 resource "azurerm_container_registry" "acr" {
-  name                = "labacr"
+  name                = "labacr${random_string.suffix.result}"
   resource_group_name = azurerm_resource_group.lab.name
   location            = azurerm_resource_group.lab.location
   sku                 = "Basic"
-  admin_enabled       = true
+  admin_enabled       = false
+  tags                = local.tags
 }
 
 resource "azurerm_kubernetes_cluster" "aks" {
@@ -17,6 +33,7 @@ resource "azurerm_kubernetes_cluster" "aks" {
   resource_group_name = azurerm_resource_group.lab.name
   dns_prefix          = "aks-lab"
   sku_tier            = "Free"
+  tags                = local.tags
 
   default_node_pool {
     name       = "system"
@@ -28,3 +45,11 @@ resource "azurerm_kubernetes_cluster" "aks" {
     type = "SystemAssigned"
   }
 }
+
+# A identidade dos nós (kubelet) precisa de AcrPull para puxar imagens do ACR
+resource "azurerm_role_assignment" "aks_acr_pull" {
+  principal_id                     = azurerm_kubernetes_cluster.aks.kubelet_identity[0].object_id
+  role_definition_name             = "AcrPull"
+  scope                            = azurerm_container_registry.acr.id
+  skip_service_principal_aad_check = true
+}
```

</details>

<details><summary><code>terraform/azure/versions.tf</code></summary>

```diff
index 8953ed0..697f264 100644
--- a/terraform/azure/versions.tf
+++ b/terraform/azure/versions.tf
@@ -6,6 +6,10 @@ terraform {
       source  = "hashicorp/azurerm"
       version = "~> 4.0"
     }
+    random = {
+      source  = "hashicorp/random"
+      version = "~> 3.6"
+    }
   }
 }
```

</details>
