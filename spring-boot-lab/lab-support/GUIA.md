# 🛟 Lab Support: RiskCube

Uma app Java/Spring Boot que imita, em ponto pequeno, o produto da ActiveViam: um motor de **agregação de risco em memória** para bancos (desks → books → trades, P&L, exposição FX). Estás de turno no suporte e tens **10 tickets de clientes reais**, escritos em inglês, com logs, thread dumps e prazos. Cada ticket tem um teste de aceitação que começa vermelho.

Este lab treina exatamente o que a Joana listou: investigar em produção, ler logs, performance, "não consigo reproduzir", SLA, Spring Security, deploy a falhar, comunicação com o cliente e inglês. Não repete o lab anterior (Docker/K8s/Kafka): aqui o foco é **Java em produção e a cabeça de quem faz suporte**.

> **Regra de ouro:** em cada ticket segue o método da Joana, sempre pela mesma ordem, e diz os passos **em voz alta, em inglês**. Na sexta vais fazer exatamente isso com o Olivier.
>
> **Understand → Reproduce → Investigate → Root cause → Fix / Workaround → Test → Release → Communicate**

---

## ⏱️ Plano (a entrevista é sexta)

| Quando | O quê | Tempo |
|---|---|---|
| **Hoje à noite** | Setup · Passo 1 (triagem) · TCK-109 · TCK-101 · TCK-110 (logs) | ~2h30 |
| **Quinta de manhã** | TCK-106 · TCK-104 · TCK-107/108 · TCK-105 | ~3h |
| **Quinta à tarde** | `INTERVIEW-KIT.md`: preencher as tuas histórias e **dizê-las em voz alta** | ~2h |
| **Sexta de manhã** | Reler a cábula do kit, 2 perguntas para o Olivier, 1 ensaio | 45 min |
| Se sobrar tempo | TCK-103 · TCK-102 · FEAT-201 | |

Se só tiveres 3 horas no total: **TCK-110, TCK-106, TCK-104, TCK-109** e o kit. Estes quatro cobrem logs, deployment, performance, "não reproduz" e comunicação.

---

## Setup (20 min)

- **JDK 21** e **IntelliJ IDEA** (a Community chega; traz Maven incluído). Abre a pasta `riskcube/` como projeto Maven.
- Correr os testes: no IntelliJ, botão direito em `src/test/java` → *Run 'All Tests'*. Ou, com Maven instalado: `mvn test` / `mvn test -Dtest=Tck104*`.
- Arrancar a app: corre `RiskCubeApplication` (ou `mvn spring-boot:run`). Gera 20.000 trades no arranque.

```bash
curl -u analyst:analyst "localhost:8080/api/v1/aggregates?groupBy=DESK"
curl -u analyst:analyst  localhost:8080/api/v1/desks/RATES/summary
curl -u analyst:analyst  localhost:8080/api/v1/exposure/USD
curl -u analyst:analyst  localhost:8080/api/v1/trades/1
curl -u admin:admin      localhost:8080/actuator/metrics/jvm.memory.used
```
Utilizadores: `analyst/analyst` (só leitura) · `admin/admin` (escrita + actuator). Os logs mostram `[req=xxxx]` em cada linha, e a resposta traz o header `X-Request-Id`.

Primeira corrida dos testes: **~12 vermelhos**. O objetivo é pô-los todos verdes, mas o teste verde é só metade do trabalho: a outra metade é saberes **explicar** o que fizeste.

---

## Passo 1 · Triagem (15 min, antes de tocar em código)

Abre `tickets/README.md`. São 09:00 de segunda e tens 10 tickets. **Ordena-os** e atribui-lhes P1-P4 segundo esta tabela (é típica de contratos de suporte B2B):

| Prioridade | Definição | 1ª resposta | Objetivo de workaround |
|---|---|---|---|
| **P1 Critical** | Produção parada, dados errados, segurança | 1h | 4h |
| **P2 High** | Funcionalidade importante degradada, sem workaround | 4h | 1 dia útil |
| **P3 Medium** | Problema com workaround, impacto limitado | 1 dia útil | próxima release |
| **P4 Low** | Pergunta, cosmético, melhoria | 2 dias úteis | backlog |

Prioridade = **impacto** (quantos utilizadores, dinheiro, regulador, segurança) × **urgência** (há workaround? há prazo?). A prioridade que o cliente escreve é informação, não decisão: tu confirmas ou ajustas, e explicas porquê.

Escreve a tua ordem em `replies/TRIAGE.md` e compara com a do `SOLUCOES.md`.

🎤 *"How would you prioritise a critical ticket with an SLA?"*: é exatamente este exercício.

---

## Os tickets: como atacar cada um

Para cada ticket: lê → **reproduz** (curl ou teste) → investiga com a ferramenta indicada → corrige → teste verde → escreve a resposta ao cliente em `replies/TCK-xxx.md` (usa `replies/TEMPLATE.md`). Faz a resposta escrita pelo menos para o **109, 106 e 110**.

### TCK-109 · Deploy a falhar (20 min) · *"How would you investigate a failing deployment?"*
O cliente mandou o log: a resposta está lá. Aprende a ler um arranque falhado do Spring Boot: procura `APPLICATION FAILED TO START` e o **último** `Caused by:`. Depois compara o `CHANGELOG.md` com o `application-prod.yml`. Há duas soluções diferentes: um **workaround** que o cliente pode aplicar *hoje* (sem nova versão) e um **fix** no produto. Tens de dar as duas.
Reproduz localmente: arranca com `SPRING_PROFILES_ACTIVE=prod`.

### TCK-101 · Ler um erro (20 min)
Reproduz com curl. O log diz o que falhou, mas não *porquê*. Encontra de onde vem a chave `null`. Pergunta-te: foi o 2.4.0 ou foram os dados novos do cliente? (dica: o `DataSeeder` imita o sistema novo do cliente).

### TCK-110 · Forense de logs (45 min) ⭐ muito provável na entrevista
Responde às 9 perguntas do ticket usando só a linha de comandos. Kit de sobrevivência:
```bash
F=logs/riskcube-prod-2026-09-29.log
grep -c " ERROR " $F                                   # contar
grep " ERROR " $F | grep -oE '[a-z]\.[a-zA-Z.]+ +:' | sort | uniq -c   # erros por logger
grep "req=4be1c9d2" $F                                  # seguir UM pedido de ponta a ponta
awk '$1 >= "2026-09-29T14:30" && $1 < "2026-09-29T14:46"' $F | grep -c "status=500"   # janela temporal
grep "fetched in" $F | awk -F'fetched in ' '{print $1 " " $2}' | awk '$NF+0 > 3000' | head   # latências acima de 3s
grep -o "GET [^ ?]*" $F | sort | uniq -c | sort -rn | head   # tráfego por URL
grep "status=500" $F | grep -o "GET [^ ?]*" | sed -E 's#/[A-Z]+/summary#/*/summary#; s#/trades/[0-9]+#/trades/*#; s#/exposure/[A-Z]+#/exposure/*#' | sort | uniq -c   # 500s por endpoint
```
O que procurar num log, sempre: **o primeiro sinal** (muitas vezes um WARN, não um ERROR) · **o que mudou** (deploy, config, volume de dados) · **erros que já existiam antes** (ruído) · **causa vs. sintoma** (o erro mais frequente raramente é a causa) · **o percurso de um pedido concreto** (requestId) · **quando e porquê recuperou**.

### TCK-106 · Thread dump e o efeito dominó (40 min) ⭐
```bash
./scripts/slow-provider.sh           # simula a abertura de mercado
jcmd -l                              # PID da app
jcmd <pid> Thread.print > threads.txt
grep -c "HttpClientImpl.send" threads.txt      # threads à espera do fornecedor
grep -c "HikariPool.getConnection" threads.txt # threads à espera de ligação à BD
```
Ou `curl -u admin:admin localhost:8080/actuator/threaddump`. Pergunta-chave: **porque é que um endpoint que não usa FX também congela?** Há dois problemas. Um é óbvio (falta um timeout). O outro está numa anotação no `AggregationService`: o que é que um método `@Transactional` segura enquanto espera pela rede? (O TCK-110 é o mesmo incidente visto pelos logs.)

### TCK-104 · Performance (30 min) · *"How would you investigate a performance problem?"*
Primeiro **mede**, depois muda. Ativa o log de SQL e conta as queries de um pedido:
```yaml
# application.yml (temporário)
spring.jpa.show-sql: true
logging.level.org.hibernate.stat: DEBUG
spring.jpa.properties.hibernate.generate_statistics: true
```
Vais ver o padrão do `pg_stat_statements` do ticket. Nome do problema: **N+1 queries**. 3 ms de latência × 48.000 queries = ? Há várias soluções (`JOIN FETCH`, `@EntityGraph`, `@BatchSize`, ou deixar a BD fazer o `SUM ... GROUP BY`). Qual devolve menos dados?

### TCK-107 + TCK-108 · Spring Security (30 min)
Dois tickets, um só ficheiro (`SecurityConfig`), duas causas diferentes. Pistas: como é que `"/api/v1/trades"` compara com `/api/v1/trades/123`? Qual é a diferença entre `hasRole("ADMIN")` e `hasAuthority("ADMIN")`? O que acontece a um pedido que não bate em nenhuma regra específica?
Para o 107 a resposta ao cliente é delicada (é segurança): que versões estão afetadas? O que devem fazer **já** (workaround)? Que informação lhes pedes (logs de auditoria)?

### TCK-105 · Memória (40 min) · *"can't reproduce locally"*
Porque não se reproduz com o Postman? Olha para o access log do ticket e para a chave da cache no `AggregationController`.
```bash
./scripts/dashboard-sim.sh 3000
jcmd <pid> GC.class_histogram | head -15            # que objetos ocupam o heap? corre 2 vezes e compara
jcmd <pid> GC.heap_dump /tmp/heap.hprof             # abrir no Eclipse MAT / VisualVM → "Leak Suspects"
```
No lab cada entrada é pequena, por isso não vais ver um OOM: procura a **tendência** (o número de `ConcurrentHashMap$Node` só sobe). Nota que o cliente diz `OOMKilled / 137`, e não `java.lang.OutOfMemoryError`: sabes a diferença? (ver cábula). Há duas correções complementares: limitar a cache (a `caffeine` já está no `pom.xml`) e corrigir a chave.

### TCK-103 · "Em UAT funciona" (30 min)
Lê o ticket e o CHANGELOG. Que diferenças há entre UAT e PROD, e qual delas importa aqui? Nota: o teste pode passar **por sorte** numa máquina com poucos cores. Se passar sem mexeres em nada, isso é a lição: *um teste verde não prova a ausência de uma race condition*.

### TCK-102 + FEAT-201 (30 min, se sobrar tempo)
Erros como `application/problem+json` com o `requestId` (o cliente envia-o no ticket e tu encontras o stack trace em 5 segundos). Um `Timer` do Micrometer para o Grafana: vê em `/actuator/prometheus` depois de corrigires.

---

## 🧠 Cábula JVM para suporte (ActiveViam é Java *in-memory*: isto vai aparecer)

**Tipos de "falta de memória", que se distinguem pelo que vês:**

| Sintoma | Significa | Onde olhar |
|---|---|---|
| `java.lang.OutOfMemoryError: Java heap space` | heap cheio (objetos vivos a mais ou fuga) | heap dump, class histogram |
| `OutOfMemoryError: GC overhead limit exceeded` | a JVM passa >98% do tempo em GC | idem, está quase a morrer |
| `OutOfMemoryError: Direct buffer memory` | memória **off-heap** (NIO, bases in-memory usam muito) | `-XX:MaxDirectMemorySize`, NMT |
| `OutOfMemoryError: Metaspace` | classes carregadas a mais (classloader leak) | |
| **`OOMKilled`, exit code 137, sem stack trace Java** | o **kernel/Kubernetes** matou o processo: a memória *total* passou o limite do contentor | heap + off-heap + threads + metaspace > limit → `-XX:MaxRAMPercentage`, requests/limits |

**Ferramentas (vêm com o JDK):** `jcmd -l` · `jcmd <pid> Thread.print` (thread dump) · `jcmd <pid> GC.class_histogram` · `jcmd <pid> GC.heap_dump f.hprof` · `jcmd <pid> VM.native_memory summary` (precisa de `-XX:NativeMemoryTracking=summary`) · **JFR**: `jcmd <pid> JFR.start duration=60s filename=rec.jfr` → abrir no JDK Mission Control (profiling em produção com ~1% de overhead). Visuais: VisualVM, Eclipse MAT.

**Ler um thread dump:** tira 3 com 10 segundos de intervalo. Threads `RUNNABLE` sempre na mesma linha = loop / CPU. Muitas `WAITING`/`TIMED_WAITING` no mesmo sítio = à espera de um recurso (pool de ligações, HTTP, lock). `BLOCKED` = contenção num `synchronized`. O `jstack` também diz se há **deadlock**.

**Performance, por ordem:** mede (métricas p95/p99, não médias) → localiza (é CPU? GC? BD? rede? locks?) → hipótese → muda **uma** coisa → mede outra vez. Suspeitos habituais: N+1, falta de índice (`EXPLAIN ANALYZE`), falta de cache, pools pequenos, GC pauses (logs de GC com `-Xlog:gc*`), chamadas remotas sem timeout, locks.

**"Não consigo reproduzir":** compara ambientes (versão, config, volume de dados, nº de CPUs, SO, JDK, rede) · pede exatamente o input (request, dados, requestId, hora) · procura nos logs à volta dessa hora · **aumenta o logging de forma temporária e dirigida** (um package em DEBUG) · reproduz com dados de volume parecido · pensa em concorrência e em timing · se ainda assim não reproduzir, instrumenta e espera pela próxima ocorrência, e diz isso ao cliente com um plano.

---

## 🧹 Quando acabares
Faz `git diff` à tua versão e compara com o `SOLUCOES.md`. Cada diferença é uma conversa possível na entrevista: *"I solved it this way, an alternative would be... I chose this because..."*
