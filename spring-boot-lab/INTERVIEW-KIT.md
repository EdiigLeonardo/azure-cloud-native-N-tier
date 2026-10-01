# 🎤 Interview kit: ActiveViam, Technical Product Support (Friday)

**How to use this:** don't memorise sentences. Learn the **skeleton** (the bold words), then say each answer **out loud 3 times**, in your own words. Record yourself on the phone once and listen back. Short sentences are fine. Clear beats fancy.

Everything in `[brackets]` = fill in with YOUR real facts before Thursday evening. Never invent: if you didn't do something, use the "bridge" phrases at the end.

---

## 0 · What they do (know it in 20 seconds)

- ActiveViam builds **Atoti+** (built on **ActivePivot**): an **in-memory analytics / aggregation engine in Java**. Banks use it for market risk, credit risk, liquidity, P&L: big, fast-moving data, recalculated in real time, explored in dashboards.
- Deployed from Python notebooks up to large **Java cloud deployments** (AWS, Azure, GCP, Kubernetes).
- Support = customers raise **tickets**; you **collect the right information fast**, reproduce, solve what you can, escalate to L2/R&D with a clean analysis, and you are the **"voice of the customer"** towards product and engineering.
- So the job = **Java + JVM memory/performance + logs + clear communication with banks under time pressure.**

> *"My understanding of the role is: you are the first technical contact for customers who run critical risk calculations on Atoti. You collect the right information, reproduce the problem, find the root cause or a workaround, and you communicate clearly until it's solved. And you bring what you learn back to the product team."*

---

## 1 · Motivation

**Intro (60 s):**
> *"I'm a full-stack engineer with [X] years of experience, currently at Aubay. I've worked on [Beamian / B&R / SIBS]: [one line each]. Frontend is my strongest side, but in the last years I've done more and more backend: NestJS services for payments and invoices, AWS Step Functions orchestration with the SAGA pattern, PostgreSQL and Redis performance work, and CI/CD pipelines. I'm also doing a BSc at Universidade Aberta and I have a cybersecurity certification from IEFP. What I enjoy most is investigating problems: finding why something is slow or broken and fixing the root cause."*

**Why Java and CI/CD?**
> *"Because that's where the hardest and most interesting problems are for me: performance, concurrency, memory, how systems behave in production. Java is the standard for that in finance. And CI/CD, because a fix is only useful when it reaches the customer safely and quickly. I want to understand the whole path, from the code to production."*

**Why backend / technical product support?**
> *"When I look back at my projects, the moments I enjoyed most were [the Beamian performance issue / unblocking the React Native release pipeline]: investigating, understanding the root cause, and seeing the impact for users. Product support is exactly that, every day, on a technically advanced product. And I like talking to people: I prefer to understand the customer's real problem than to code in isolation."*

**Why Java, if you are stronger in frontend?** (they WILL ask, Joana said it)
> *"It's true, frontend is where I have the most years. But many skills transfer directly: debugging (the browser devtools and a thread dump are the same mindset: observe, form a hypothesis, test), REST APIs (I've built them on both sides), SQL and performance, CI/CD, and working with production incidents. On the backend I already have [NestJS, Spring Boot projects, Step Functions]. What I need to deepen is the JVM itself, and I'm actively doing it: recently I built a lab with a Spring Boot service and reproduced real production problems: N+1 queries, a thread pool exhaustion caused by a missing HTTP timeout, a memory leak in a cache, a race condition in a parallel stream. I fixed each one with a test."*

**Next 2-3 years:**
> *"First year: become autonomous on the product: know Atoti's architecture, its logs, its common issues, and solve most tickets without escalating. Second year: go deep on JVM performance and memory: GC tuning, profiling with JFR, off-heap memory, because that's the core of an in-memory product. Third year: be the person the team goes to for complex performance or deployment issues, and contribute to the product itself, for example better diagnostics or documentation that prevents tickets."*

---

## 2 · The support method (use it for EVERY technical question)

**Understand → Reproduce → Investigate → Root cause → Fix / workaround → Test → Release → Communicate / document**

**"How would you investigate a problem in production?"**
> *"First I **understand** the impact: who is affected, since when, what changed: a deploy, config, data volume? I ask for the version, logs, the exact request or requestId, and the time. Then I try to **reproduce** it with the same version and similar data. I **investigate** with logs, metrics and, for Java, thread dumps or heap dumps. When I find the **root cause**, I first give the customer a **workaround** if possible, because they need to work today, and then a proper **fix** with a test that proves it. After the **release** I confirm with the customer and I **document** it, so the next person solves it in five minutes."*

**"What information do you look for in logs?"**
> *"The first sign, which is often a WARN and not an ERROR. What changed around that time. Errors that already existed before, so I can ignore the noise. The difference between cause and symptoms: the most frequent error is usually a symptom. The path of one specific request, using the request id. And when it recovered and why. Also stack traces: I read the last 'Caused by'."*
> Example (lab TCK-110): *"The customer blamed our deploy at 14:00, but the logs were clean for 30 minutes after it. The first sign was at 14:31: the market data provider became slow. Because the FX call was inside a database transaction, every slow call kept a DB connection, the pool was exhausted, and unrelated screens failed too. The root cause was a missing timeout plus a remote call inside a transaction."*

**"How would you investigate a performance problem?"**
> *"Measure first, never guess. p95 and p99, not averages. Then locate the time: CPU, GC, database, network, locks? Tools: metrics in Grafana or CloudWatch, the SQL logs or pg_stat_statements, EXPLAIN ANALYZE, a profiler like JFR, thread dumps. Then I change one thing and measure again."*
> Example: Beamian (section 5) + lab TCK-104 (N+1: 48,000 small queries × 3 ms of latency; fixed with one aggregate query).

**"How would you handle a problem you can't reproduce locally?"**
> *"I compare the environments: version, configuration, data volume, number of CPUs, JDK, network. Most 'works on my machine' problems come from one of those. I ask for the exact input and time and look at the logs around it. If needed, I add targeted logging, for example DEBUG on one package, or metrics, and I wait for the next occurrence with a plan. And I tell the customer honestly what I'm doing and when they'll hear from me."*
> Example (lab TCK-103): *"Totals changed between refreshes only in PROD: 32 CPUs and 2 million trades versus 2 CPUs in UAT. It was a race condition: a parallel stream writing to a non-thread-safe HashMap. More cores meant more collisions."*

**"How do you prioritise a critical ticket with an SLA?"**
> *"Priority is impact times urgency. Production down, wrong numbers, or a security problem is P1, especially in a bank, because wrong risk numbers can mean a regulatory issue. For a P1: acknowledge fast within the SLA, even if I don't know the answer yet, give an update time, look for a workaround first, involve L2 or engineering early, not late. The priority the customer writes is information; I confirm it or explain why I adjust it."*
> Example (lab triage): *"A customer marked a failed upgrade as critical, but they had already rolled back and had a workaround, so it was P2. A read-only user who could delete trades was marked 'high', but it was a security issue, so P1."*

---

## 3 · Java / Spring Boot (short and concrete)

- **My experience:** *"[Spring Boot projects: which, what they did]. I've also built REST APIs with NestJS, which is very close to Spring in concepts: modules, dependency injection, decorators like annotations."*
- **Dependency Injection:** *"Objects don't create their dependencies; the Spring container creates beans and injects them, preferably by constructor. It makes code testable (you can inject a fake) and loosely coupled. Scopes: singleton by default, so beans must be stateless or thread-safe."*
- **REST APIs:** *"Resources as URLs, HTTP verbs with meaning, correct status codes (201 created, 204, 400, 401 vs 403, 404, 409, 503), idempotency for PUT and DELETE, pagination, versioning like /api/v1, and consistent errors, e.g. RFC 7807 Problem Details with a request id that the customer can send to support."*
- **Spring Security:** *"A chain of filters in front of the controllers. Authentication: who are you (basic, JWT, OAuth2). Authorization: what can you do, with request matchers and roles. Two classic bugs I reproduced: a matcher '/api/trades' doesn't match '/api/trades/123', so the request falls into a more permissive rule; and hasRole('ADMIN') checks 'ROLE_ADMIN' while hasAuthority('ADMIN') checks 'ADMIN'. Also, 401 means not authenticated and 403 means authenticated but not allowed."*
- **MVC vs hexagonal:** *"Layered MVC is controller → service → repository: simple, but the business logic often depends on the framework and the database. Hexagonal puts the domain in the center, with **ports** (interfaces) and **adapters** (REST, JPA, Kafka, an external API). The domain doesn't know Spring or the database, so it's easy to test and to replace an adapter. For a small CRUD, layered is fine; for complex domains or many integrations, hexagonal pays off."*
- **Architecture of one of my projects:** *"[Project]: [Angular/React] frontend → Spring Boot REST API → [PostgreSQL / Redis / queue]. Packages by [layer or feature]. Auth with [...]. Tests: [unit with JUnit/Mockito, integration with @SpringBootTest]. Deployed with [Docker, pipeline, cloud]. One thing I'd change today: [...]."*
- **What I want to deepen:** *"The JVM in production: memory (heap vs off-heap), garbage collection, profiling with JFR, concurrency. For an in-memory product like Atoti, that's where the real problems are."*
- **Transactions (bonus, from the lab):** *"Never keep a transaction open while calling a remote system: the DB connection stays blocked and one slow provider can exhaust the pool for everyone."*

---

## 4 · CI/CD & Cloud

- **Jenkins:** *"[What you did: used / edited / created pipelines]. Declarative Jenkinsfile: agent, stages (build and test, image, deploy staging, manual approval for production), `post` for test reports and notifications, credentials from the Jenkins store, never in the code."* (see `riskcube/Jenkinsfile`)
- **CI vs CD:** *"CI: every change is built and tested automatically, so problems are found in minutes. Continuous Delivery: every build *can* go to production, with a manual approval. Continuous Deployment: it goes automatically."*
- **Docker:** *"Image = packaged app plus runtime; container = running instance. For Java: multi-stage build, small JRE image, non-root user, and size the heap from the container limit with MaxRAMPercentage, otherwise you get OOMKilled."*
- **AWS ECS / Step Functions:** *"At B&R I worked with Step Functions to orchestrate IoT microservices with the SAGA pattern: each step has a compensation if a later step fails, instead of a distributed transaction. [Your ECS experience]."*
- **Azure / AKS:** *"AKS is managed Kubernetes on Azure: Azure manages the control plane, you manage nodes and workloads. Images in ACR, identity with managed identities, logs in Azure Monitor. At SIBS I used [VMs, Storage...]."*
- **Monitoring:** *"CloudWatch or Azure Monitor for logs and infrastructure metrics. **Sentry** for application errors with stack traces, grouped, with release info. **Prometheus + Grafana** for application metrics and dashboards: p95 latency, error rate, JVM heap, GC, pool usage, with alerts. Logs tell you *what* happened, metrics tell you *when and how much*, traces tell you *where*."*
- **A deployment that's failing:** *"Which step fails: build, image, or rollout? On Kubernetes: `kubectl get pods`, `describe` (events), `logs --previous`. Common causes: missing config or secret, image pull error, failing readiness probe, not enough memory, database migration. For a Spring Boot app I look for 'APPLICATION FAILED TO START' and the last 'Caused by'. And I check the release notes: what changed in configuration? First action for the customer: roll back, or a workaround like setting the new variable."* (lab TCK-109)

---

## 5 · My stories (STAR) · prepare 3, tell each in ~2 minutes

### Story A · Beamian: API from ~16 s to < 200 ms ⭐ (Joana asked for this one)
- **Situation:** *"At Beamian, [what the product does], the [endpoint/screen] took around 16 seconds. [Impact: users / events / complaints]."*
- **Task:** *"I was responsible for [finding and fixing it]."*
- **Action (the core, be specific):**
  1. *"I measured first: [how: logs with timings, APM, the network tab, SQL logging]."*
  2. *"The time was in the database: [what you saw: full table scans, EXPLAIN ANALYZE, slow query log, N+1...]."*
  3. *"I added [composite] indexes on [columns used in WHERE / JOIN / ORDER BY]. That took it to [X ms]."*
  4. *"Then Redis for [data read often, changes rarely], with a TTL of [x] and invalidation when [event]."*
  5. *"I validated in [staging, with realistic data / load]."*
- **Result:** *"From ~16 s to under 200 ms, about 80 times faster. [Business result]."*
- **Learning:** *"Measure before changing anything. The index fixed the query; the cache avoided repeating it."*
- **Follow-ups to prepare:** How did you know it was the database? · Which kind of index, and why those columns? · How did you invalidate the cache? · What if Redis goes down? (*the app falls back to the DB, slower but correct*) · Why not only the cache? (*the first request and every cache miss would still take 16 s*).

### Story B · A critical problem / hotfix
Candidates from your CV: the **Expo migration that unblocked a stalled React Native release pipeline** at B&R, or [a production bug].
- S: what was blocked and why it mattered (a release that couldn't ship = [impact]).
- T/A: how you diagnosed it, what you changed, how you tested it under pressure, who you communicated with.
- R: shipped on [date], [result]. L: [what you'd do earlier next time].

### Story C · L2/L3 support experience
[Where did you handle tickets or production issues: SIBS backoffice? Aubay clients?] If you never had a formal L2/L3 role, say it honestly and bridge:
> *"I haven't had a formal L2/L3 support title, but in [project] I regularly handled production issues reported by [users / the client]: [example]. I'd reproduce, find the cause, hotfix, and explain it to the client."*

---

## 6 · Communication

**Explain a technical problem to someone who doesn't know the code** → impact first, then one analogy, no jargon.
> *"Think of a restaurant where one supplier is late. Instead of serving other dishes, every waiter stood at the door waiting for that supplier. So the whole restaurant stopped. We now tell waiters to wait at most two seconds, and the other dishes are served normally."* (= TCK-106)

**How do you communicate when you don't have a solution yet?** → never silence; facts + next step + time of next update.
> *"I say what we know, what we don't know yet, what we're doing now, and when they'll hear from me, and I keep that time even if I have nothing new. 'We have reproduced the issue, we're analysing the thread dumps, next update at 15:00.'"*

**How do you manage expectations in a critical incident?**
> *"Acknowledge fast and show I understand their impact. Give a workaround as early as possible. Don't promise a fix time I can't control: I promise update times. One clear channel and one owner. After the incident, a written summary: timeline, root cause, and what we change so it doesn't happen again."*

---

## 7 · Phrases that save you (English under pressure)

- To think: *"Good question, let me think about it for a second."*
- To check: *"Just to make sure I understood: you mean ... ?"*
- To structure: *"I'd approach it in three steps. First... then... and finally..."*
- Don't know: *"I haven't worked with that directly, but here's how I would approach it: ..."*
- No production experience with X: *"I haven't done it in production yet, but I reproduced it in a lab: ..."*
- Lost a word: *"How do you say... the thing that ..."* → totally fine, keep going.
- To finish: *"...so in short: [one sentence]."*

## 8 · Questions for Olivier (pick 2)
1. *"What would you expect from someone in this role during the first three to six months?"*
2. *"What are the main technical challenges the support team is facing right now, and where would you expect the new person to contribute?"*
3. *"What does a typical escalation look like: when does a ticket go from support to R&D, and how involved is support after that?"*
4. *"What kind of issues come up most often with Atoti in production: memory, performance, data loading, deployment?"*
