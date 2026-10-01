# 🔑 Soluções: RiskCube support lab

> Só depois de tentares. Em cada ticket lê primeiro **causa-raiz → workaround → fix**: é assim que respondes na entrevista.

---

## Passo 1 · Triagem

| # | Ticket | Prioridade | Porquê |
|---|---|---|---|
| 1 | **TCK-107** analyst apaga trades | **P1** (cliente disse High) | Segurança + integridade de dados. Resposta imediata; workaround: bloquear `DELETE` no gateway/proxy ou retirar temporariamente o acesso à API a contas ANALYST. |
| 2 | **TCK-103** totais mudam | **P1** | Números de risco errados num banco = risco regulatório. Workaround: rollback para 2.3.2. |
| 3 | **TCK-106** congela à abertura de mercado | **P1** | Produção indisponível todos os dias, à hora mais crítica. |
| 4 | **TCK-105** OOMKilled a cada 6h | **P1/P2** | Indisponibilidade recorrente mas recupera sozinho. Workaround: restart programado/limite de memória maior até ao fix. |
| 5 | **TCK-108** ingestão noturna 403 | **P2** | Dados em falta no relatório de risco, com prazo diário. |
| 6 | **TCK-110** relatório do incidente | **P2** | Não há nada em baixo, mas há um gestor à espera de uma explicação escrita. |
| 7 | **TCK-109** upgrade falhou | **P2** (cliente disse Critical) | Já fizeram rollback, a produção funciona e há workaround (definir `MARKET_DATA_URL`). Mas há um prazo (sexta): resposta rápida. |
| 8 | **TCK-104** desk summary lento | **P3** | Degradado mas utilizável. |
| 9 | **TCK-101** vista counterparty | **P3** | Uma vista falha, as outras funcionam. |
| 10 | **TCK-102** 404 vs 500 | **P3/P4** | Incómodo operacional, sem impacto nos dados. |
| - | FEAT-201 | backlog | Melhoria interna. |

Não há uma ordem "certa" única. O que conta na entrevista é **justificar com impacto × urgência** e dizer que *"security and wrong numbers come first"*. E também que um ticket de prioridade baixa recebe na mesma uma primeira resposta dentro do SLA.

---

## TCK-101 · Chave `null` na agregação
**Causa-raiz:** os trades do sistema novo do cliente não têm `counterparty` → `Dimension.keyOf()` devolve `null` → a chave `null` entra no `Map` → o **Jackson não consegue serializar chaves `null`** → 500. O bug estava latente desde o 2.3.0 e os dados novos ativaram-no. (Na entrevista: *"what changed? The data, not the code."*)
**Fix:** agrupar `null` em `UNKNOWN` (feito no motor, junto com o TCK-103).
**Workaround:** o cliente pode preencher a counterparty na origem.

<details><summary><code>src/main/java/com/riskcube/service/TradeAggregator.java</code></summary>

```diff
index 84d4eb9..f55cb55 100644
--- a/src/main/java/com/riskcube/service/TradeAggregator.java
+++ b/src/main/java/com/riskcube/service/TradeAggregator.java
@@ -5,29 +5,23 @@ import com.riskcube.domain.Trade;
 import org.springframework.stereotype.Component;
 
 import java.math.BigDecimal;
-import java.math.RoundingMode;
-import java.util.HashMap;
-import java.util.LinkedHashMap;
 import java.util.List;
 import java.util.Map;
+import java.util.Objects;
+import java.util.TreeMap;
+import java.util.stream.Collectors;
 
-/**
- * Aggregation engine. v2.4.0: parallelised for large books (#412).
- */
 @Component
 public class TradeAggregator {
 
-    public Map<String, BigDecimal> aggregate(List<Trade> trades, Dimension dimension) {
-        Map<String, Double> totals = new HashMap<>();
-
-        trades.parallelStream().forEach(trade -> {
-            String key = dimension.keyOf(trade);
-            totals.merge(key, trade.getPnl().doubleValue(), Double::sum);
-        });
+    public static final String UNKNOWN = "UNKNOWN";
 
-        Map<String, BigDecimal> result = new LinkedHashMap<>();
-        totals.forEach((key, total) ->
-                result.put(key, BigDecimal.valueOf(total).setScale(2, RoundingMode.HALF_UP)));
-        return result;
+    public Map<String, BigDecimal> aggregate(List<Trade> trades, Dimension dimension) {
+        // collect() is thread-safe with parallel streams: each thread fills its own map, then they are merged.
+        // BigDecimal: exact money arithmetic, no floating point drift.
+        return trades.parallelStream().collect(Collectors.groupingBy(
+                trade -> Objects.requireNonNullElse(dimension.keyOf(trade), UNKNOWN),
+                TreeMap::new,
+                Collectors.reducing(BigDecimal.ZERO, Trade::getPnl, BigDecimal::add)));
     }
 }
```

</details>


## TCK-102 · 500 → 404 Problem Details
**Causa-raiz:** `orElseThrow()` lança `NoSuchElementException`, que ninguém trata → 500.
**Fix:** `@RestControllerAdvice` que devolve `ProblemDetail` (RFC 7807, `application/problem+json`) com o `requestId`. Estende `ResponseEntityExceptionHandler` para não estragar os 400/405 do Spring. Os 500 são registados com o stack trace no log e **nunca** o expõem ao cliente.

<details><summary><code>src/main/java/com/riskcube/web/ApiExceptionHandler.java</code></summary>

```diff
new file mode 100644
index 0000000..1c176f2
--- /dev/null
+++ b/src/main/java/com/riskcube/web/ApiExceptionHandler.java
@@ -0,0 +1,52 @@
+package com.riskcube.web;
+
+import com.riskcube.marketdata.MarketDataUnavailableException;
+import jakarta.servlet.http.HttpServletRequest;
+import org.slf4j.Logger;
+import org.slf4j.LoggerFactory;
+import org.slf4j.MDC;
+import org.springframework.http.HttpStatus;
+import org.springframework.http.ProblemDetail;
+import org.springframework.web.bind.annotation.ExceptionHandler;
+import org.springframework.web.bind.annotation.RestControllerAdvice;
+import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
+
+import java.util.NoSuchElementException;
+
+/**
+ * Every error = RFC 7807 "problem+json" with the requestId.
+ * The customer sends us the requestId -> we grep the logs -> we find the stack trace.
+ * Extends ResponseEntityExceptionHandler so Spring's own errors (400 validation, 405...) keep their status.
+ */
+@RestControllerAdvice
+public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
+
+    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
+
+    @ExceptionHandler(NoSuchElementException.class)
+    ProblemDetail notFound(HttpServletRequest request) {
+        return problem(HttpStatus.NOT_FOUND, "Resource not found: " + request.getRequestURI());
+    }
+
+    @ExceptionHandler(IllegalArgumentException.class)
+    ProblemDetail badRequest(IllegalArgumentException ex) {
+        return problem(HttpStatus.BAD_REQUEST, ex.getMessage());
+    }
+
+    @ExceptionHandler(MarketDataUnavailableException.class)
+    ProblemDetail marketDataDown(MarketDataUnavailableException ex) {
+        return problem(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage() + ". Please retry in a few minutes.");
+    }
+
+    @ExceptionHandler(Exception.class)
+    ProblemDetail unexpected(Exception ex) {
+        log.error("Unhandled error", ex); // full stack trace in the logs, never in the response
+        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error. Please contact support with the requestId.");
+    }
+
+    private static ProblemDetail problem(HttpStatus status, String detail) {
+        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
+        problem.setProperty("requestId", MDC.get("requestId"));
+        return problem;
+    }
+}
```

</details>


## TCK-103 · Race condition (o "em UAT funciona")
**Causa-raiz:** 2.4.0 (#412) paralelizou a agregação com `parallelStream().forEach(...)` a escrever num **`HashMap` partilhado**, que não é thread-safe → as atualizações perdem-se (duas threads leem o mesmo valor e ambas escrevem). Com 2 CPUs quase nunca acontece; com 32 CPUs acontece sempre. Bónus: somar dinheiro em `double` acumula erros de vírgula flutuante; `BigDecimal` é exato.
**Fix:** `collect(groupingBy(..., reducing(BigDecimal.ZERO, ..., BigDecimal::add)))`. O `collect` é seguro em paralelo porque cada thread enche o seu próprio mapa e no fim são combinados. **Workaround:** rollback para 2.3.2.
**Frase:** *"Environment differences matter: CPUs, data volume. And a green test doesn't prove there's no race condition."*

## TCK-104 · N+1 queries
**Causa-raiz:** `desk.getBooks()` → 1 query; `book.getTrades()` para cada book → 1 query por book (coleções `LAZY`) → centenas de queries pequenas; carrega **todos** os trades para memória só para os somar. Com 3 ms de rede × 48.000 queries ≈ 144 s de espera acumulada entre pedidos.
**Fix:** deixar a base de dados fazer a soma: **uma** query `SUM ... GROUP BY` com projeção. Alternativas: `JOIN FETCH` / `@EntityGraph` (1 query, mas traz os trades todos), `@BatchSize`/`default_batch_fetch_size` (menos queries, mas continua a carregar tudo). A melhor aqui é a agregação na BD.

<details><summary><code>src/main/java/com/riskcube/domain/TradeRepository.java</code></summary>

```diff
index 07efef6..53dd432 100644
--- a/src/main/java/com/riskcube/domain/TradeRepository.java
+++ b/src/main/java/com/riskcube/domain/TradeRepository.java
@@ -3,7 +3,9 @@ package com.riskcube.domain;
 import org.springframework.data.jpa.repository.EntityGraph;
 import org.springframework.data.jpa.repository.JpaRepository;
 import org.springframework.data.jpa.repository.Query;
+import org.springframework.data.repository.query.Param;
 
+import java.math.BigDecimal;
 import java.util.List;
 
 public interface TradeRepository extends JpaRepository<Trade, Long> {
@@ -14,4 +16,14 @@ public interface TradeRepository extends JpaRepository<Trade, Long> {
     List<Trade> findAllWithBookAndDesk();
 
     List<Trade> findByCurrency(String currency);
+
+    @Query("""
+            select b.name as book, sum(t.pnl) as pnl
+            from Trade t join t.book b join b.desk d
+            where d.name = :desk
+            group by b.name""")
+    List<BookPnl> sumPnlByBookForDesk(@Param("desk") String desk);
+
+    @Query("select coalesce(sum(t.notional), 0) from Trade t where t.currency = :currency")
+    BigDecimal sumNotionalByCurrency(@Param("currency") String currency);
 }
```

</details>

<details><summary><code>src/main/java/com/riskcube/domain/BookPnl.java</code></summary>

```diff
new file mode 100644
index 0000000..5ff84d3
--- /dev/null
+++ b/src/main/java/com/riskcube/domain/BookPnl.java
@@ -0,0 +1,11 @@
+package com.riskcube.domain;
+
+import java.math.BigDecimal;
+
+/** Spring Data projection: one row of "select b.name as book, sum(t.pnl) as pnl ...". */
+public interface BookPnl {
+
+    String getBook();
+
+    BigDecimal getPnl();
+}
```

</details>

<details><summary><code>src/main/java/com/riskcube/domain/DeskRepository.java</code></summary>

```diff
index 2cb075b..94c088d 100644
--- a/src/main/java/com/riskcube/domain/DeskRepository.java
+++ b/src/main/java/com/riskcube/domain/DeskRepository.java
@@ -7,4 +7,6 @@ import java.util.Optional;
 public interface DeskRepository extends JpaRepository<Desk, Long> {
 
     Optional<Desk> findByName(String name);
+
+    boolean existsByName(String name);
 }
```

</details>

<details><summary><code>src/main/java/com/riskcube/service/AggregationService.java</code></summary>

```diff
index 17b94d1..ded052e 100644
--- a/src/main/java/com/riskcube/service/AggregationService.java
+++ b/src/main/java/com/riskcube/service/AggregationService.java
@@ -1,12 +1,13 @@
 package com.riskcube.service;
 
-import com.riskcube.domain.Book;
-import com.riskcube.domain.Desk;
+import com.riskcube.domain.BookPnl;
 import com.riskcube.domain.DeskRepository;
 import com.riskcube.domain.Dimension;
 import com.riskcube.domain.Trade;
 import com.riskcube.domain.TradeRepository;
 import com.riskcube.marketdata.MarketDataClient;
+import io.micrometer.core.instrument.MeterRegistry;
+import io.micrometer.core.instrument.Timer;
 import org.slf4j.Logger;
 import org.slf4j.LoggerFactory;
 import org.springframework.stereotype.Service;
@@ -16,6 +17,7 @@ import java.math.BigDecimal;
 import java.math.RoundingMode;
 import java.util.List;
 import java.util.Map;
+import java.util.NoSuchElementException;
 import java.util.TreeMap;
 
 @Service
@@ -27,45 +29,53 @@ public class AggregationService {
     private final DeskRepository deskRepository;
     private final TradeAggregator aggregator;
     private final MarketDataClient marketData;
+    private final MeterRegistry meterRegistry;
 
     public AggregationService(TradeRepository tradeRepository, DeskRepository deskRepository,
-                              TradeAggregator aggregator, MarketDataClient marketData) {
+                              TradeAggregator aggregator, MarketDataClient marketData, MeterRegistry meterRegistry) {
         this.tradeRepository = tradeRepository;
         this.deskRepository = deskRepository;
         this.aggregator = aggregator;
         this.marketData = marketData;
+        this.meterRegistry = meterRegistry;
     }
 
     @Transactional(readOnly = true)
     public Map<String, BigDecimal> aggregate(Dimension dimension) {
-        long start = System.nanoTime();
-        List<Trade> trades = tradeRepository.findAllWithBookAndDesk();
-        Map<String, BigDecimal> result = aggregator.aggregate(trades, dimension);
-        log.info("aggregate groupBy={} trades={} took={}ms", dimension, trades.size(), (System.nanoTime() - start) / 1_000_000);
-        return result;
+        Timer.Sample sample = Timer.start(meterRegistry);
+        try {
+            List<Trade> trades = tradeRepository.findAllWithBookAndDesk();
+            Map<String, BigDecimal> result = aggregator.aggregate(trades, dimension);
+            log.info("aggregate groupBy={} trades={}", dimension, trades.size());
+            return result;
+        } finally {
+            sample.stop(Timer.builder("riskcube.aggregation")
+                    .description("Time to aggregate the cube")
+                    .tag("dimension", dimension.name())
+                    .publishPercentileHistogram()
+                    .register(meterRegistry));
+        }
     }
 
     @Transactional(readOnly = true)
     public DeskSummary deskSummary(String deskName) {
-        Desk desk = deskRepository.findByName(deskName).orElseThrow();
+        if (!deskRepository.existsByName(deskName)) {
+            throw new NoSuchElementException("desk " + deskName);
+        }
+        // ONE query: the database does the SUM/GROUP BY instead of loading every trade into memory
         Map<String, BigDecimal> pnlByBook = new TreeMap<>();
         BigDecimal total = BigDecimal.ZERO;
-        for (Book book : desk.getBooks()) {
-            BigDecimal bookTotal = BigDecimal.ZERO;
-            for (Trade trade : book.getTrades()) {
-                bookTotal = bookTotal.add(trade.getPnl());
-            }
-            pnlByBook.put(book.getName(), bookTotal);
-            total = total.add(bookTotal);
+        for (BookPnl row : tradeRepository.sumPnlByBookForDesk(deskName)) {
+            pnlByBook.put(row.getBook(), row.getPnl());
+            total = total.add(row.getPnl());
         }
-        return new DeskSummary(desk.getName(), pnlByBook, total);
+        return new DeskSummary(deskName, pnlByBook, total);
     }
 
-    @Transactional(readOnly = true)
+    // NOT @Transactional: never hold a DB connection while waiting for a remote system
     public Exposure exposure(String currency) {
-        List<Trade> trades = tradeRepository.findByCurrency(currency);
-        BigDecimal notional = trades.stream().map(Trade::getNotional).reduce(BigDecimal.ZERO, BigDecimal::add);
         BigDecimal rate = marketData.fxRateToEur(currency);
+        BigDecimal notional = tradeRepository.sumNotionalByCurrency(currency);
         return new Exposure(currency, notional, rate, notional.multiply(rate).setScale(2, RoundingMode.HALF_UP));
     }
 }
```

</details>


## TCK-105 · Fuga de memória na cache
**Causa-raiz:** o dashboard do cliente acrescenta `&_=<timestamp>` (cache-busting) a cada refresh → a chave da cache (`request.getQueryString()`) é **sempre diferente** → 0% de hits e um `ConcurrentHashMap` **sem limite** que só cresce → heap sobe até o contentor passar o limite → **OOMKilled (137)**. Não reproduz com o Postman porque o Postman não envia `_=`.
**Fix duplo:** (1) cache **limitada** (Caffeine: `maximumSize` + `expireAfterWrite`); (2) chave só com o que muda o resultado (`groupBy`).
**Workaround:** restart programado / subir o limite de memória até ao fix. **Nota:** `OOMKilled` = o kernel matou o processo (memória total do contentor); `OutOfMemoryError` = a JVM ficou sem heap.

<details><summary><code>src/main/java/com/riskcube/service/QueryResultCache.java</code></summary>

```diff
index d369034..363a549 100644
--- a/src/main/java/com/riskcube/service/QueryResultCache.java
+++ b/src/main/java/com/riskcube/service/QueryResultCache.java
@@ -1,25 +1,30 @@
 package com.riskcube.service;
 
+import com.github.benmanes.caffeine.cache.Cache;
+import com.github.benmanes.caffeine.cache.Caffeine;
 import org.springframework.stereotype.Component;
 
-import java.util.Map;
-import java.util.concurrent.ConcurrentHashMap;
+import java.time.Duration;
 import java.util.function.Supplier;
 
-/**
- * Caches dashboard query results so repeated refreshes don't recompute the cube (#398).
- */
 @Component
 public class QueryResultCache {
 
-    private final Map<String, Object> cache = new ConcurrentHashMap<>();
+    // Bounded (max entries) + time-based expiry: memory can never grow forever.
+    private final Cache<String, Object> cache = Caffeine.newBuilder()
+            .maximumSize(1_000)
+            .expireAfterWrite(Duration.ofMinutes(5))
+            .executor(Runnable::run)
+            .recordStats()
+            .build();
 
     @SuppressWarnings("unchecked")
     public <T> T get(String key, Supplier<T> loader) {
-        return (T) cache.computeIfAbsent(key, k -> loader.get());
+        return (T) cache.get(key, k -> loader.get());
     }
 
     public long size() {
-        return cache.size();
+        cache.cleanUp();
+        return cache.estimatedSize();
     }
 }
```

</details>

<details><summary><code>src/main/java/com/riskcube/web/AggregationController.java</code></summary>

```diff
index b0646f2..870f95a 100644
--- a/src/main/java/com/riskcube/web/AggregationController.java
+++ b/src/main/java/com/riskcube/web/AggregationController.java
@@ -5,7 +5,6 @@ import com.riskcube.service.AggregationService;
 import com.riskcube.service.DeskSummary;
 import com.riskcube.service.Exposure;
 import com.riskcube.service.QueryResultCache;
-import jakarta.servlet.http.HttpServletRequest;
 import org.springframework.web.bind.annotation.GetMapping;
 import org.springframework.web.bind.annotation.PathVariable;
 import org.springframework.web.bind.annotation.RequestMapping;
@@ -29,8 +28,9 @@ public class AggregationController {
 
     /** Example: GET /api/v1/aggregates?groupBy=DESK */
     @GetMapping("/aggregates")
-    public Map<String, BigDecimal> aggregate(@RequestParam Dimension groupBy, HttpServletRequest request) {
-        return cache.get(request.getQueryString(), () -> service.aggregate(groupBy));
+    public Map<String, BigDecimal> aggregate(@RequestParam Dimension groupBy) {
+        // key = only what changes the result (ignores "_=<timestamp>" and other noise)
+        return cache.get("aggregate:" + groupBy, () -> service.aggregate(groupBy));
     }
 
     @GetMapping("/desks/{desk}/summary")
```

</details>


## TCK-106 · Thread pool / connection pool exhaustion
**Causa-raiz (duas):** (1) o `RestClient` não tem **timeout** → uma thread do Tomcat espera indefinidamente pelo fornecedor; (2) a chamada remota está dentro de um método **`@Transactional`** → cada pedido FX segura **uma ligação à BD** enquanto espera. Com 10 ligações no pool, 10 pedidos FX lentos bloqueiam a BD para **todos** os outros endpoints → `HikariPool ... Connection is not available` → a app inteira "congela" e o health check falha.
**Fix:** timeouts (connect 1 s, read 2 s) + exceção própria → **503** com mensagem clara (*fail fast*); a chamada remota sai da transação. Mais: circuit breaker (Resilience4j), cache do último rate conhecido, bulkhead (pool separado para o fornecedor).

<details><summary><code>src/main/java/com/riskcube/marketdata/MarketDataClient.java</code></summary>

```diff
index 7c4ff93..213bf5a 100644
--- a/src/main/java/com/riskcube/marketdata/MarketDataClient.java
+++ b/src/main/java/com/riskcube/marketdata/MarketDataClient.java
@@ -3,12 +3,15 @@ package com.riskcube.marketdata;
 import org.slf4j.Logger;
 import org.slf4j.LoggerFactory;
 import org.springframework.beans.factory.annotation.Value;
+import org.springframework.http.client.JdkClientHttpRequestFactory;
 import org.springframework.stereotype.Component;
 import org.springframework.web.client.RestClient;
+import org.springframework.web.client.RestClientException;
 
 import java.math.BigDecimal;
+import java.net.http.HttpClient;
+import java.time.Duration;
 
-/** Client for the external market data provider (#420: migrated to RestClient). */
 @Component
 public class MarketDataClient {
 
@@ -16,9 +19,16 @@ public class MarketDataClient {
 
     private final RestClient restClient;
 
-    public MarketDataClient(@Value("${riskcube.market-data.url}") String baseUrl) {
+    public MarketDataClient(@Value("${riskcube.market-data.url}") String baseUrl,
+                            @Value("${riskcube.market-data.timeout-ms:2000}") long timeoutMs) {
+        HttpClient httpClient = HttpClient.newBuilder()
+                .connectTimeout(Duration.ofSeconds(1))       // time to open the TCP connection
+                .build();
+        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
+        factory.setReadTimeout(Duration.ofMillis(timeoutMs)); // time to wait for the response
         this.restClient = RestClient.builder()
                 .baseUrl(baseUrl)
+                .requestFactory(factory)
                 .build();
     }
 
@@ -27,11 +37,16 @@ public class MarketDataClient {
             return BigDecimal.ONE;
         }
         long start = System.currentTimeMillis();
-        FxQuote quote = restClient.get()
-                .uri("/fx/{currency}", currency)
-                .retrieve()
-                .body(FxQuote.class);
-        log.info("fx rate {}->EUR fetched in {}ms", currency, System.currentTimeMillis() - start);
-        return quote.rate();
+        try {
+            FxQuote quote = restClient.get()
+                    .uri("/fx/{currency}", currency)
+                    .retrieve()
+                    .body(FxQuote.class);
+            log.info("fx rate {}->EUR fetched in {}ms", currency, System.currentTimeMillis() - start);
+            return quote.rate();
+        } catch (RestClientException e) {
+            log.warn("market data unavailable for {} after {}ms: {}", currency, System.currentTimeMillis() - start, e.getMessage());
+            throw new MarketDataUnavailableException(currency, e);
+        }
     }
 }
```

</details>

<details><summary><code>src/main/java/com/riskcube/marketdata/MarketDataUnavailableException.java</code></summary>

```diff
new file mode 100644
index 0000000..7b28295
--- /dev/null
+++ b/src/main/java/com/riskcube/marketdata/MarketDataUnavailableException.java
@@ -0,0 +1,8 @@
+package com.riskcube.marketdata;
+
+public class MarketDataUnavailableException extends RuntimeException {
+
+    public MarketDataUnavailableException(String currency, Throwable cause) {
+        super("Market data unavailable for " + currency, cause);
+    }
+}
```

</details>


## TCK-107 + TCK-108 · Spring Security
**107:** `requestMatchers(DELETE, "/api/v1/trades")` **não** apanha `/api/v1/trades/123` → o pedido cai em `anyRequest().authenticated()` → qualquer utilizador autenticado pode apagar. **108:** `.roles("ADMIN")` cria a authority `ROLE_ADMIN`; `hasAuthority("ADMIN")` procura `ADMIN` → 403. `hasRole("ADMIN")` acrescenta o prefixo `ROLE_` automaticamente.
**Fix:** uma regra para `/api/v1/trades` **e** `/api/v1/trades/**` com `hasRole("ADMIN")`. Boa prática extra: deny by default (terminar com `denyAll()`) e testes de segurança por endpoint, com os dois papéis.
**Resposta ao cliente (107):** confirmar que é defeito do produto; versões afetadas: **só a 2.4.0** (#425); workaround imediato; data do fix; pedir o log de auditoria completo para confirmarem o âmbito.

<details><summary><code>src/main/java/com/riskcube/config/SecurityConfig.java</code></summary>

```diff
index d7890e4..c7aded1 100644
--- a/src/main/java/com/riskcube/config/SecurityConfig.java
+++ b/src/main/java/com/riskcube/config/SecurityConfig.java
@@ -28,8 +28,7 @@ public class SecurityConfig {
                         .requestMatchers("/actuator/health/**", "/mock/**").permitAll()
                         .requestMatchers("/actuator/**").hasRole("ADMIN")
                         .requestMatchers(HttpMethod.GET, "/api/**").hasAnyRole("ANALYST", "ADMIN")
-                        .requestMatchers(HttpMethod.POST, "/api/v1/trades").hasAuthority("ADMIN")
-                        .requestMatchers(HttpMethod.DELETE, "/api/v1/trades").hasRole("ADMIN")
+                        .requestMatchers("/api/v1/trades", "/api/v1/trades/**").hasRole("ADMIN") // POST, DELETE, PUT...
                         .anyRequest().authenticated())
                 .httpBasic(Customizer.withDefaults());
         return http.build();
```

</details>


## TCK-109 · Upgrade em CrashLoopBackOff
**Causa-raiz:** no 2.4.0 (#421) a variável passou de `MD_URL` para `MARKET_DATA_URL` **e isso não foi documentado** como breaking change. O cliente mantém `MD_URL` → o placeholder `${MARKET_DATA_URL}` não resolve → o bean não arranca → a app não arranca → o Kubernetes reinicia o pod.
**Workaround (hoje, sem nova versão):** acrescentar `MARKET_DATA_URL` ao deployment com o mesmo valor. **Fix:** compatibilidade com o nome antigo (`${MARKET_DATA_URL:${MD_URL}}`) + nota de breaking change no CHANGELOG / upgrade guide. **Lição de processo:** alterações de configuração entram nas release notes.

<details><summary><code>src/main/resources/application-prod.yml</code></summary>

```diff
index 2ca845f..2ab95b8 100644
--- a/src/main/resources/application-prod.yml
+++ b/src/main/resources/application-prod.yml
@@ -1,7 +1,7 @@
 # Production profile (customers run with SPRING_PROFILES_ACTIVE=prod)
 riskcube:
   market-data:
-    url: ${MARKET_DATA_URL}
+    url: ${MARKET_DATA_URL:${MD_URL}}   # MD_URL = name used until 2.3.x (backward compatible)
 
 logging:
   level:
```

</details>


## FEAT-201 · Métrica
Ver o `Timer.Sample` no diff do `AggregationService` acima. Em `/actuator/prometheus` aparece `riskcube_aggregation_seconds_count{dimension="DESK"}` e os buckets do histograma → no Grafana: `histogram_quantile(0.95, sum by (le, dimension) (rate(riskcube_aggregation_seconds_bucket[5m])))`.

---

## TCK-110 · Respostas da análise de logs

1. **Início:** às **14:31:07** (a linha aparece às 14:31:11.873 com `fetched in 4870ms`: o pedido começou 4,87 s antes). O primeiro sinal é um **INFO com latência anormal** do `MarketDataClient` (normal: 60-190 ms), e não um ERROR. O primeiro ERROR do incidente só aparece às **14:32:50** (Hikari).
2. **O deploy não é a causa:** a 2.4.0 arrancou às 14:00:49 e houve **30 minutos de tráfego normal** depois disso. Correlação temporal não é causalidade.
3. **`ReportExporter`:** ruído. Falha a cada 5 minutos desde as 13:52 (antes do deploy e do incidente), por causa do SFTP do cliente. Vale a pena reportar em separado, mas não está relacionado.
4. **Pedido `4be1c9d2`:** às 14:36:22 pediu `GET /api/v1/desks/RATES/summary` → esperou **30 s** por uma ligação à BD → `HikariPool-1 - Connection is not available ... (total=10, active=10, idle=0, waiting=11)` → `CannotCreateTransactionException` → **500 após 30.007 ms**.
5. **229 respostas 500:** `/exposure/*` 112 · `/desks/*/summary` 75 · `/trades/*` 25 · `/aggregates` 17.
   `grep "status=500" $F | grep -o "GET [^ ?]*" | sed -E 's#/[A-Z]+/summary#/*/summary#; s#/trades/[0-9]+#/trades/*#; s#/exposure/[A-Z]+#/exposure/*#' | sort | uniq -c`
6. **Porque falhou o RATES:** os pedidos FX seguravam as 10 ligações do pool (chamada remota dentro da transação) enquanto esperavam 5-58 s pelo fornecedor → `active=10, idle=0` → qualquer endpoint que precise da BD espera 30 s e falha.
7. **Recuperação:** o fornecedor voltou ao normal por volta das **14:44:00** (`fetched in 98ms` às 14:44:02); o último 500 foi às **14:44:31** (pedidos que já estavam em fila); a partir de ~14:45 está tudo limpo. Recuperou sozinho porque a causa era externa. **Não houve nenhuma ação nossa**, e isso também se diz ao cliente.
8. **TCK-105:** todos os pedidos a `/aggregates` trazem `_=<timestamp>` diferente (853 pedidos → 853 chaves de cache diferentes).

**9. Resposta ao Martin (exemplo):**
> Hi Martin,
> Thank you for the request id, it let us trace the exact failure. **What happened:** between 14:31 and 14:45 UTC your market data provider became very slow (up to 58 s per FX request instead of ~0.1 s). **Root cause on our side:** RiskCube waited for the provider without a time limit, and while waiting it kept a database connection busy. After a few minutes all 10 database connections were blocked, so every screen that needs the database failed, including the RATES summary (request 4be1c9d2 waited 30 s for a connection and returned an error). **It was not caused by the 2.4.0 deployment:** the logs show 30 minutes of normal activity after it. The service recovered at 14:45 when the provider recovered.
> **What we are changing:** (1) a 2-second timeout on market data calls, so FX views fail fast with a clear message and all other views keep working; (2) market data calls no longer hold a database connection; (3) we recommend an alert on the connection pool usage. The fix is planned for 2.4.1 on [date].
> Separately, we noticed that your report export to sftp.northbridge.internal fails every 5 minutes (connection refused). It's unrelated, but you may want to check it.
> Best regards, Edig, RiskCube Support

---

## Exemplos de resposta ao cliente

**TCK-109 (Kestrel):**
> Subject: [TCK-109] 2.4.0 fails to start – workaround available
> Hi Anna, thank you for the logs: they show the cause clearly. In 2.4.0 the environment variable for the market data URL was renamed from `MD_URL` to `MARKET_DATA_URL`, and unfortunately this was missing from the release notes. I'm sorry for the disruption.
> **Workaround (no new version needed):** add `MARKET_DATA_URL` to your deployment with the same value as `MD_URL` (`http://md-gateway.kestrel.internal:8080`). We reproduced your setup in our lab and 2.4.0 starts correctly with it.
> **Fix:** 2.4.1 will accept both names, and we've updated the upgrade guide. Given your Friday deadline, I'm available today to join a call during your upgrade if that helps. Next update from me: tomorrow 10:00, or earlier once you confirm.

**TCK-106 (sem solução ainda, 1ª resposta):**
> Hi Priya, thank you, the thread dump is very useful. **What we know:** when the market data provider is slow, RiskCube waits for it without a limit, and those waiting requests also block database connections, which is why non-FX views freeze too. **Workaround for tomorrow's market open:** [e.g. temporarily reduce FX dashboard refresh / restart plan]. **Next steps:** we are testing a fix with a short timeout. Next update: today 17:00.
