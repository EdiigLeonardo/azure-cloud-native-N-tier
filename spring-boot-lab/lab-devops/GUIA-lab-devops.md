# 🧪 Lab DevOps: de "sempre tive ajuda" a "fiz isto de A a Z"

Um sistema de encomendas **propositadamente estragado**. Tem 21 bugs reais (do tipo que aparece em produção) espalhados por Docker, Redis, RabbitMQ, Kafka, Kubernetes, CI/CD e Terraform. Tu corriges, o verificador confirma.

**Custo: 0 €.** Tudo corre no teu PC (Docker + kind) e no GitHub Actions (grátis). A Azure só entra no fim, e é opcional (menos de 1 € se destruíres tudo no mesmo dia).

```
                    ┌──────────── Kafka (topic "orders", 3 partições) ─────┬──► worker-analytics
  cliente ──► API ──┤                                                      └──► worker-fraud
   (Express)  │     └──────────── RabbitMQ (fila "emails") ───────────────────► worker-email
              ├──► Redis (cache-aside + rate limiting)
              └──► PostgreSQL
```

Porquê os dois brokers? Porque é exatamente a diferença que te vão perguntar: **Kafka = log de eventos** (vários serviços leem o mesmo evento, dá para reler), **RabbitMQ = fila de tarefas** (cada tarefa é feita por um worker e desaparece).

---

## 0. Setup (30 min, uma vez)

| Ferramenta | Para quê | Linux (Ubuntu/Debian) | macOS | Windows |
|---|---|---|---|---|
| **Docker** + Compose | contentores | `sudo apt install docker.io docker-compose-v2` ou [Docker Engine](https://docs.docker.com/engine/install/) | [Docker Desktop](https://docker.com) / `brew install --cask docker` | Docker Desktop (WSL2) |
| **Node.js 22+** | verificador e scripts | `nvm install 22` ou [NodeSource](https://github.com/nodesource/distributions) | `brew install node` | `winget install OpenJS.NodeJS.LTS` |
| **kind** | Kubernetes local no Docker | Ver comando abaixo | `brew install kind` | `winget install Kubernetes.kind` |
| **kubectl** | CLI do Kubernetes | Ver comando abaixo | `brew install kubectl` | `winget install Kubernetes.kubectl` |
| **Terraform** | IaC | `sudo snap install terraform --classic` ou HashiCorp repo | `brew install terraform` | `winget install Hashicorp.Terraform` |
| **Azure CLI** | só no M7 | `curl -sL https://aka.ms/InstallAzureCLIDeb \| sudo bash` | `brew install azure-cli` | `winget install Microsoft.AzureCLI` |

### 🐧 Instalação rápida no Linux (kind + kubectl):

```bash
# 1. Instalar kind (x86_64 / amd64)
curl -Lo ./kind https://kind.sigs.k8s.io/dl/v0.27.0/kind-linux-amd64
chmod +x ./kind
sudo mv ./kind /usr/local/bin/kind

# 2. Instalar kubectl
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
sudo install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl

# 3. Garantir permissões do Docker sem sudo (se necessário)
sudo usermod -aG docker $USER
# Nota: se adicionares o grupo agora, faz 'newgrp docker' ou reinicia a sessão.
```

> **Dica de recursos:** Garante que o Docker tem acesso a pelo menos 6 GB de RAM disponível (o Kafka e o cluster kind necessitam de folga).

```bash
git init && git add . && git commit -m "lab inicial"   # para veres os teus diffs no fim
cd app && npm install && npm run check                 # 0/21: vamos a isto
```

## Como jogar

1. Lê a missão, reproduz o **sintoma** primeiro (ver o bug a acontecer é metade da aprendizagem).
2. Corrige. Usa só a documentação oficial e as dicas do `npm run check`.
3. `npm run check` até ficar ✅.
4. Só abres o `SOLUCOES.md` depois de 15-20 min encravado. Lê o **porquê**, é isso que vais dizer na entrevista.
5. No fim de cada módulo, responde **em voz alta** às perguntas de entrevista. Se gaguejares, repete.

---

## ⏱️ Plano de estudo

**Rota expresso (~6h, se a entrevista é em 1-2 dias):** M1 → M2 → M4 (só KAFKA-2 + experiência de escalar) → M5 → M6. Lê a cábula toda na véspera.

**Rota completa (~12h, em 3 dias):**

| Dia | Módulos | Resultado |
|---|---|---|
| 1 | M1 Docker/Compose · M2 Redis · M3 RabbitMQ | stack a correr, 7 bugs |
| 2 | M4 Kafka · M5 Kubernetes | 10 bugs, sabes debugar pods |
| 3 | M6 CI/CD · M7 Terraform (+ Azure opcional) · cábula | 21/21, discurso preparado |

---

## M1 · Docker & Compose (45 min) · `DOCKER-1` `DOCKER-2` `COMPOSE-1`

```bash
docker compose up -d --build
docker compose ps                 # a api está "Exited"? Porquê?
docker compose logs api
```

**Sintoma:** a API morre no arranque com `ECONNREFUSED` porque o Postgres ainda não aceita ligações. O Kafka demora ainda mais. Se relançares com `docker compose up -d api` funciona, e é este tipo de coisa que faz um deploy falhar "às vezes".

**Missões:**
- Faz a API só arrancar quando as dependências estão **saudáveis** (pesquisa: `healthcheck`, `condition: service_healthy`). Para o Kafka: `/opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092`.
- Transforma o Dockerfile numa imagem de produção. Compara `docker images` antes e depois (tamanho!). Altera uma linha de `src/` e reconstrói: o `npm install` voltou a correr? Não devia.

**UIs úteis:** Kafka UI em http://localhost:8080 · RabbitMQ em http://localhost:15672 (guest/guest).

**Teste rápido da API:**
```bash
curl -s -XPOST localhost:3000/orders -H 'content-type: application/json' -d '{"customer":"ana","amount":30}'
curl -si localhost:3000/orders/1 | grep -i x-cache    # MISS, depois HIT
curl -s -XPATCH localhost:3000/orders/1/status -H 'content-type: application/json' -d '{"status":"PAID"}'
```

🎤 *"Qual a diferença entre imagem e contentor? Porque é que a ordem das instruções no Dockerfile importa? O que é multi-stage build? Porquê não correr como root?"*

---

## M2 · Redis (1h30) · `REDIS-1` `REDIS-2` `REDIS-3` + stampede

```bash
docker compose exec redis redis-cli
> KEYS *                 # nunca em produção! (bloqueia). Usa SCAN 0 MATCH order:* COUNT 100
> TTL order:1            # -1 = sem expiração, -2 = não existe
> MONITOR                # vê todos os comandos em tempo real (Ctrl+C para sair)
```

**Missões:**
1. **REDIS-1:** faz as entradas expirarem. Usa `config.cacheTtlSeconds` e adiciona **jitter**.
2. **REDIS-2:** muda o status de uma encomenda e faz GET. O cliente vê o estado antigo. Corrige.
3. **REDIS-3:** põe `CHAOS: "true"` no compose, faz 30 POSTs, depois `SCAN 0 MATCH rl:*` e `TTL` nessa chave. Vês `-1`? Esse utilizador está bloqueado para sempre. Torna o rate limiter atómico.
4. **Stampede (bónus, muito valorizado):**
   ```bash
   npm run stampede      # 200 pedidos ao mesmo tempo após a chave expirar
   ```
   Sem proteção: ~200 queries à BD. Implementa um lock com `SET lock:<key> 1 NX PX 3000` até dar ≤ 2.
5. **Eviction (experiência, 10 min):**
   ```bash
   docker compose exec redis redis-cli CONFIG SET maxmemory 2mb
   docker compose exec redis redis-cli CONFIG SET maxmemory-policy noeviction
   docker compose exec redis redis-benchmark -t set -n 50000 -r 50000 -d 200 -q   # erros OOM
   docker compose exec redis redis-cli CONFIG SET maxmemory-policy allkeys-lru
   docker compose exec redis redis-benchmark -t set -n 50000 -r 50000 -d 200 -q   # funciona
   docker compose exec redis redis-cli INFO stats | grep evicted_keys
   ```

### Cábula Redis: "algoritmos para expirar a cache"

**Como o próprio Redis expira chaves com TTL** (2 mecanismos em simultâneo):
- **Passivo (lazy):** quando alguém lê uma chave expirada, é apagada nesse momento.
- **Ativo:** ~10x por segundo, testa uma amostra aleatória de chaves com TTL e apaga as expiradas; se mais de 25% da amostra estava expirada, repete. Por isso a memória não enche de chaves mortas que ninguém lê.

**Eviction (quando chega ao `maxmemory`):**

| Política | Comportamento | Quando usar |
|---|---|---|
| `noeviction` | recusa escritas (erro OOM) | Redis como BD/fila, não podes perder dados |
| `allkeys-lru` | remove as menos usadas recentemente | **cache genérica (default sensato)** |
| `allkeys-lfu` | remove as menos usadas em frequência | há chaves "quentes" estáveis |
| `volatile-lru` / `volatile-lfu` | igual, mas só entre chaves com TTL | mistura cache + dados persistentes |
| `volatile-ttl` | remove as que expiram mais cedo | |
| `allkeys-random` / `volatile-random` | aleatório | acesso uniforme |

Detalhe que impressiona: o LRU do Redis é **aproximado**. Não mantém uma lista ordenada, amostra `maxmemory-samples` chaves (5 por defeito) e remove a mais antiga. O LFU usa um contador logarítmico (Morris counter) com decaimento no tempo.

**Estratégias de expiração/invalidação na aplicação:**
- **TTL fixo:** simples, aceita dados até X segundos desatualizados.
- **TTL + jitter:** evita *avalanche* (milhares de chaves a expirar no mesmo segundo).
- **Invalidação on-write (delete):** apagar depois de escrever na BD. Apagar > atualizar (evita races). Variante: *delayed double delete*.
- **Versioned keys:** `order:42:v7`; mudar a versão invalida sem apagar.
- **Stale-while-revalidate:** serve o valor velho e recarrega em background.
- **Probabilistic early expiration (XFetch):** cada pedido tem uma probabilidade crescente de recarregar *antes* do TTL acabar. Sem locks.
- **Lock / single-flight:** só 1 pedido recarrega (o que fizeste no stampede).
- **Negative caching:** guardar também o "não existe" (com TTL curto) para evitar *cache penetration* (ataques a IDs inexistentes).

**Padrões de cache:** *cache-aside* (este lab: app lê a cache, se falhar lê a BD e preenche) · *read-through* (a cache vai à BD sozinha) · *write-through* (escreve na cache e na BD na mesma operação) · *write-behind* (escreve na cache, a BD é atualizada depois, é rápido mas pode perder dados).

**Outros temas que surgem:** persistência RDB (snapshots) vs AOF (log de comandos) · Replicação + Sentinel (failover) vs Redis Cluster (sharding em 16384 hash slots) · Redis é single-threaded na execução de comandos, por isso `KEYS *` e chaves enormes bloqueiam tudo · Rate limiting: fixed window (este lab), sliding window (sorted sets), token bucket (Lua).

🎤 *"Como evitas dados desatualizados na cache? O que é cache stampede e como resolves? Que política de eviction escolhias para uma cache? Porque é que INCR+EXPIRE separados é um problema?"*

---

## M3 · RabbitMQ (1h) · `RABBIT-1` `RABBIT-2`

**Experiência 1: perda de mensagens**
```bash
npm run flood -- 30
docker compose kill worker-email          # a meio!
docker compose up -d worker-email
docker compose logs worker-email | grep -c enviado    # quantos dos 24 válidos?
```
Vê na UI (Queues → emails) as colunas *Ready* vs *Unacked*. Com `noAck: true` nada fica "Unacked": o RabbitMQ já considerou tudo entregue.

**Experiência 2: mensagens venenosas.** As encomendas > 1000 € falham sempre. Hoje desaparecem. Quero-as numa **Dead Letter Queue** para alguém investigar.

**Experiência 3: competing consumers**
```bash
docker compose up -d --scale worker-email=3
npm run flood -- 30 && docker compose logs -f worker-email    # round-robin entre os 3
```

⚠️ Quando mudares os argumentos da fila vais ver `PRECONDITION_FAILED`. É normal e acontece em produção: uma fila existente não muda de argumentos. Apaga-a na UI ou faz `docker compose down -v`.

**Conceitos:** Producer → **Exchange** → (binding + routing key) → **Queue** → Consumer. Tipos de exchange: **direct** (routing key exata), **topic** (padrões `order.*.eu`), **fanout** (todas as filas), **headers**. **Ack/nack**, **prefetch** (QoS), **DLX**, **TTL** por mensagem/fila (retry com atraso = fila com TTL + DLX de volta), **quorum queues** (replicadas, Raft, o standard atual para alta disponibilidade).

🎤 *"O que acontece se um consumer morre a meio? Como fazes retries com atraso? O que é prefetch?"*

---

## M4 · Kafka (1h15) · `KAFKA-1` `KAFKA-2`

```bash
docker compose logs -f worker-analytics worker-fraud
npm run flood -- 10
```
**Sintoma KAFKA-2:** cada evento aparece em *um* dos workers, nunca nos dois. O de fraude está a perder metade das encomendas!

```bash
K="docker compose exec kafka /opt/kafka/bin"
$K/kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic orders
$K/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --all-groups   # vê o LAG
$K/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic orders --from-beginning \
   --property print.key=true --property print.partition=true
```

**Sintoma KAFKA-1:** cria uma encomenda e muda o status 3 vezes seguidas. Nos logs, os eventos da mesma encomenda estão em partições diferentes (`p=0`, `p=2`...). Um consumer por partição = podem ser processados fora de ordem.

**Experiência "o superpoder do Kafka": replay** (depois de corrigires os groupIds)
```bash
docker compose stop worker-analytics
$K/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group analytics \
   --topic orders --reset-offsets --to-earliest --execute
docker compose start worker-analytics     # reprocessa TODO o histórico
```
Com o RabbitMQ isto é impossível: a mensagem já foi apagada.

**Experiência: limite de paralelismo**
```bash
docker compose up -d --scale worker-analytics=4
$K/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group analytics
```
3 partições → 1 dos 4 consumers fica **sem nada**. Máximo de consumers úteis num grupo = nº de partições.

**Conceitos:** **topic** dividido em **partições** (log append-only, ordenado) · **offset** = posição no log, cada consumer group guarda o seu · **consumer group** = partições divididas entre membros; grupos diferentes leem tudo de forma independente · **retention** por tempo/tamanho (a mensagem não é apagada ao ser lida) · **log compaction** (guarda o último valor por key) · **rebalance** · **replication factor** + ISR · Producer `acks=all` + `idempotent` · Semânticas: *at-most-once*, *at-least-once* (default, consumers devem ser **idempotentes**), *exactly-once* (transações).

### Kafka vs RabbitMQ (a pergunta clássica)

| | **Kafka** | **RabbitMQ** |
|---|---|---|
| Modelo | log distribuído (streaming) | message broker (filas) |
| Depois de lida | fica (retenção configurável) | apagada após ack |
| Replay | ✅ reset de offsets | ❌ |
| Ordem | por partição (via key) | por fila (com 1 consumer) |
| Vários serviços, mesmo evento | natural (consumer groups) | fanout exchange (1 fila por serviço) |
| Routing | simples (topic/partição) | rico (direct, topic, headers) |
| Throughput | muito alto (milhões/s) | alto (dezenas de milhares/s) |
| Consumo | pull | push (com prefetch) |
| Casos típicos | event sourcing, CDC, analytics, logs, integração entre muitos serviços | tarefas assíncronas, jobs, RPC, workflows com retries e prioridades |

**Frase para a entrevista:** *"Uso Kafka quando o evento é um facto que vários serviços querem ler, possivelmente mais tarde ou de novo. Uso RabbitMQ quando é uma tarefa que um worker tem de fazer uma vez, com routing, retries e DLQ. Neste lab usei os dois: `OrderCreated` vai para Kafka (analytics e fraude leem ambos), 'envia email' vai para RabbitMQ."*

---

## M5 · Kubernetes (2h30), o módulo mais importante · `K8S-0` … `K8S-5`

```bash
kind create cluster --name lab --config kind-config.yaml
docker build -t lab-api:latest ./app
kind load docker-image lab-api:latest --name lab     # "empurra" a imagem para o nó
kubectl apply -f k8s/
```

Vais bater em vários erros seguidos. **O fluxo de debug (decora isto, é o que te vão pedir):**
```bash
kubectl -n lab get pods -o wide              # 1. estado (Pending? CrashLoopBackOff? 0/1 Ready?)
kubectl -n lab describe pod <pod>            # 2. Events no fundo: a resposta está quase sempre aqui
kubectl -n lab logs <pod> [--previous]       # 3. logs (--previous = do contentor que crashou)
kubectl -n lab get events --sort-by=.lastTimestamp
kubectl -n lab get endpoints lab-api         # 4. o Service encontra pods?
kubectl -n lab exec -it <pod> -- sh          # 5. entrar no pod
kubectl -n lab port-forward svc/lab-api 8080:80
```

| Estado | Causa típica |
|---|---|
| `ErrImagePull` / `ImagePullBackOff` | nome/tag errado, registry privado sem permissão, pull policy |
| `CrashLoopBackOff` | app crasha no arranque → `logs --previous` |
| `Running` mas `0/1 READY` | readiness probe a falhar |
| `Pending` | sem recursos no nó, PVC não montado, nodeSelector |
| `OOMKilled` | passou o `limits.memory` |
| Service sem endpoints | selector ≠ labels, ou pods não Ready |

**Quando estiver tudo verde, faz estes exercícios (são perguntas de entrevista em forma de prática):**
```bash
curl localhost:30080/stats                                    # chama várias vezes: o "pod" muda (load balancing)
kubectl -n lab delete pod <um-pod-api>                        # self-healing: o ReplicaSet cria outro
kubectl -n lab scale deploy/lab-api --replicas=4
docker build -t lab-api:v2 ./app && kind load docker-image lab-api:v2 --name lab
kubectl -n lab set image deploy/lab-api api=lab-api:v2 && kubectl -n lab rollout status deploy/lab-api
kubectl -n lab rollout history deploy/lab-api
kubectl -n lab rollout undo deploy/lab-api                    # rollback
kubectl -n lab set image deploy/lab-api api=lab-api:naoexiste # deploy partido: a app continua a responder? (maxUnavailable: 0)
```

**HPA (K8S-5):**
```bash
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
kubectl -n kube-system patch deploy metrics-server --type=json \
  -p '[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]'
kubectl -n lab get hpa -w
kubectl -n lab run load --rm -it --image=busybox -- sh -c "while true; do wget -q -O- http://lab-api/stats >/dev/null; done"
```

**Conceitos:** Pod (1+ contentores, IP próprio, efémero) · ReplicaSet (mantém N pods) · **Deployment** (gere ReplicaSets, rolling updates, rollback) · **StatefulSet** (identidade e disco estáveis: BDs, Kafka; o Postgres deste lab devia ser um, com PVC) · DaemonSet (1 por nó: agentes de logs) · Job/CronJob · **Service**: ClusterIP (interno), NodePort, LoadBalancer (cloud) · **Ingress** (HTTP routing por host/path, TLS) · ConfigMap / Secret · **Probes**: liveness (reinicia), readiness (tira do load balancer), startup (apps lentas a arrancar) · requests (scheduling) vs limits (teto) · HPA · Namespaces · RBAC · control plane (api-server, etcd, scheduler, controller-manager) vs worker nodes (kubelet, kube-proxy, container runtime).

🎤 *"Um pod está em CrashLoopBackOff, o que fazes?" "Diferença entre liveness e readiness?" "Deployment vs StatefulSet?" "Como fazes um deploy sem downtime?" "Como geres segredos?"*

---

## M6 · CI/CD (1h30) · `APP-1` `CI-1` `CI-2` `CI-3`

```bash
cd app && npm test        # um teste falha. E a pipeline? Estava verde...
```

1. Cria um repositório **público** no GitHub (Actions grátis e ilimitado) e faz push.
2. Vê a pipeline correr em *Actions*. Repara: ela passa, **mas os testes falham**. E o deploy corre em paralelo com os testes.
3. Corrige o CI primeiro (CI-1). A pipeline fica vermelha: **é bom!** Agora corrige o código (APP-1).
4. CI-2 e CI-3: imagem com tag do commit, construída uma vez e reutilizada (artifact), deploy só depois do build e só na `main`.
5. Faz uma branch `feature/x` com um bug: a pipeline corre os testes mas não faz deploy. Abre um PR.

O job `deploy-kind` cria um **cluster Kubernetes dentro do runner**, faz deploy dos teus manifests e um smoke test, ou seja, um CD verdadeiro sem gastar um cêntimo. Se falhar, o passo "debug se falhar" mostra os eventos (é o fluxo do M5 automatizado).

**Conceitos:**
- **CI:** cada push → build + testes automáticos, feedback rápido, main sempre verde.
- **Continuous Delivery:** cada commit na main *pode* ir para produção (há um botão/aprovação). **Continuous Deployment:** vai automaticamente.
- **Build once, deploy many:** o mesmo artefacto (imagem com tag SHA) passa por dev → staging → prod; só muda a configuração.
- Estágios típicos: lint → testes unitários → build → scan de segurança (Trivy, SAST, dependências) → push para registry → deploy staging → testes e2e/smoke → aprovação → deploy prod.
- **Estratégias de deploy:** rolling (default do k8s) · blue/green (2 ambientes, troca instantânea, rollback instantâneo) · canary (5% do tráfego primeiro, métricas, depois 100%) · feature flags.
- **GitOps** (Argo CD, Flux): o cluster puxa o estado desejado de um repo Git, em vez de a pipeline fazer `kubectl apply`.
- **Segredos:** nunca no repo; GitHub Secrets / Azure Key Vault; **OIDC** (a pipeline autentica-se na cloud sem password guardada).
- Equivalentes: GitHub Actions ≈ Azure DevOps Pipelines ≈ GitLab CI ≈ Jenkins.

🎤 *"Descreve uma pipeline que montaste." "Como fazes rollback?" "Onde guardas segredos da pipeline?" "Diferença entre delivery e deployment?"*

---

## M7 · Terraform + Azure (1h30) · `TF-1` `TF-2` `TF-3`

**Parte grátis** (não cria nada, só compara o código com a Azure):
```bash
az login
cd terraform/azure
cp terraform.tfvars.example terraform.tfvars    # mete o teu subscription_id
terraform init
terraform fmt -check && terraform validate
terraform plan
```

**Lê o plan com atenção** (`+` criar, `~` alterar in-place, `-/+` destruir e recriar: perigo em produção!).

Para TF-1 precisas de criar o storage do state **fora** do Terraform (custa cêntimos por mês):
```bash
az group create -n rg-tfstate -l westeurope
az storage account create -n tfstate<nome-unico> -g rg-tfstate --sku Standard_LRS
az storage container create -n tfstate --account-name tfstate<nome-unico>
terraform init -migrate-state
```

**Parte opcional com créditos (~1h, < 1 €):** AKS free tier + 1 nó B2s + ACR Basic.
```bash
terraform apply                                          # TF-3 vai rebentar aqui se não o corrigiste
$(terraform output -raw aks_get_credentials)
az acr build -r <nome-do-acr> -t lab-api:v1 ../../app   # build na cloud, sem Docker local
# nos manifests: image: <acr>.azurecr.io/lab-api:v1 e o Service type: LoadBalancer
kubectl apply -f ../../k8s/ && kubectl -n lab get svc -w # espera pelo EXTERNAL-IP
terraform destroy                                        # ⚠️ OBRIGATÓRIO no fim
```
Se não resolveste o TF-2, vês o `ImagePullBackOff` na cloud. É o bug mais comum em AKS/EKS reais. Define também um **budget alert** na subscrição (Cost Management → Budgets).

**Conceitos:** providers · resources vs data sources · **state** (mapa código ↔ recursos reais) · **remote backend + locking** · `plan` / `apply` / `destroy` · variables, outputs, locals · **modules** (reutilizar) · **drift** (alguém mudou no portal: o `plan` mostra) · `terraform import` · workspaces vs pastas por ambiente · `lifecycle { prevent_destroy = true }` · Terraform é **declarativo** (descreves o estado final, não os passos).

### AWS ↔ Azure (tu já sabes AWS, é só mudar os nomes)

| AWS | Azure | GCP |
|---|---|---|
| EKS | **AKS** | GKE |
| ECS / Fargate · App Runner | **Container Apps** | Cloud Run |
| ECR | **ACR** | Artifact Registry |
| Lambda | **Functions** | Cloud Functions |
| Step Functions | **Logic Apps / Durable Functions** | Workflows |
| API Gateway | **API Management** | API Gateway |
| S3 | **Blob Storage** | Cloud Storage |
| RDS PostgreSQL | **Database for PostgreSQL Flexible Server** | Cloud SQL |
| ElastiCache | **Azure Managed Redis** | Memorystore |
| MSK / Kinesis | **Event Hubs** (tem API Kafka) | Pub/Sub |
| SQS / SNS | **Service Bus** / Event Grid | Pub/Sub |
| CloudWatch | **Azure Monitor / Log Analytics / App Insights** | Cloud Monitoring |
| IAM Roles | **Managed Identity + RBAC (Entra ID)** | IAM / Service Accounts |
| Secrets Manager | **Key Vault** | Secret Manager |
| CloudFormation | **Bicep / ARM** | Deployment Manager |

🎤 *"O que é o state e porque é remoto?" "Alguém mudou um recurso no portal, o que acontece?" "Como organizas Terraform para dev/staging/prod?"*

---

## 🎤 Como contar isto na entrevista

Não digas "fiz em produção". Diz a verdade, que é mais forte: **"Montei um lab pessoal de ponta a ponta para consolidar isto"**, e depois conta **um bug como uma história (STAR)**:

> *"Tinha pods em `Running` mas o Service não respondia. Fui ao `get endpoints` e estava vazio: o selector do Service não batia com as labels dos pods. Corrigi, e depois tinha 0/1 Ready porque a readiness probe apontava para um endpoint que não existia. Aprendi a seguir sempre o mesmo fluxo: get, describe, logs, events, endpoints."*

> *"Com o Kafka, tinha dois serviços com o mesmo consumer group e cada um recebia só metade dos eventos, o que para detetar fraude é grave. Grupos diferentes resolveram. Também percebi que sem key os eventos da mesma encomenda podiam chegar fora de ordem."*

Um entrevistador sénior valoriza muito mais quem sabe **porque é que as coisas partem** do que quem decorou definições. É exatamente isso que este lab te dá.

## 🧹 Limpeza
```bash
docker compose down -v
kind delete cluster --name lab
cd terraform/azure && terraform destroy
```
