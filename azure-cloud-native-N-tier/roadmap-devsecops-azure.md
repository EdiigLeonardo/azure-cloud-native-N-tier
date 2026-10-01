# Roadmap DevSecOps em Azure — TDD First

**Arquitetura alvo:**
```
Azure API Management (Gateway)
        │
   NGINX/Envoy (Proxy)
        │
   ┌────┴────┐
   │         │
  Vue 3 FE   Spring Boot 3 BE (JPA)
                   │
             ┌─────┴─────┐
             │           │
      Azure Blob      PostgreSQL
      Storage         (Flexible Server)
```

Princípio geral: **cada fase só avança quando a anterior tem testes verdes e roda localmente**. Nada sobe para Azure sem estar containerizado e testado.

---

## Fase 0 — Setup & Fundamentos (2-3 dias)
- [ ] Conta Azure (free tier ou subscrição existente), `az cli` instalado e autenticado
- [ ] Estrutura de repo: monorepo com `/backend`, `/frontend`, `/proxy`, `/infra`, `/pipelines`
- [ ] Decidir convenção de commits (Conventional Commits) — vai facilitar depois nos pipelines (semantic versioning)
- [ ] Instalar: Docker Desktop, Java 21 + Maven, Node 20+, PostgreSQL client (psql ou DBeaver)

---

## Fase 1 — Backend Spring Boot (TDD puro, sem Azure) (semana 1)
Objetivo: 2 rotas REST + JPA, 100% via TDD (red-green-refactor).

- [ ] `POST /api/items` e `GET /api/items/{id}` (ou domínio à tua escolha — algo que depois faça sentido ligar a um ficheiro no Blob, ex: upload de comprovativo/imagem associado a um registo)
- [ ] Testes unitários: JUnit 5 + Mockito (mock do repository)
- [ ] Testes de integração: `@DataJpaTest` com Testcontainers (PostgreSQL real em container — evita "funciona no H2 mas não em prod")
- [ ] Camadas: Controller → Service → Repository (JPA), com DTOs e mapper (MapStruct opcional)
- [ ] Validação (Bean Validation) com testes a casos inválidos
- [ ] Tratamento de erros centralizado (`@ControllerAdvice`) — importante para depois testares no DAST

**Ponto de estudo DevSecOps:** já aqui introduzir *dependency scanning* local — `mvn org.owasp:dependency-check-maven:check` — para apanhares o hábito de correr scans antes de sequer haver pipeline.

---

## Fase 2 — Frontend Vue 3 simples (semana 1-2)
- [ ] Vue 3 + Vite, Composition API
- [ ] Testes: Vitest + Vue Test Utils (TDD também aqui — componente de listagem + formulário)
- [ ] Chamada à API do BE via `fetch`/`axios`, com `.env` para URL configurável (importante para depois ligar ao proxy)
- [ ] Build estático (`npm run build`) — vais servir isto via NGINX no proxy, não via `npm run dev`

---

## Fase 3 — Containerização + Proxy local (semana 2-3)
- [ ] `Dockerfile` multi-stage para o BE (build Maven → imagem `eclipse-temurin` slim)
- [ ] `Dockerfile` para o FE (build Vite → servido por NGINX)
- [ ] Container NGINX dedicado como **proxy reverso**: recebe tudo em `:80`, faz `/api/*` → BE, resto → FE estático
- [ ] `docker-compose.yml` a juntar: proxy + FE + BE + PostgreSQL, tudo local
- [ ] Testar o fluxo completo localmente antes de tocar em Azure

**Ponto de estudo DevSecOps:** correr `docker scout` ou `trivy image` sobre as tuas imagens locais — apanhar CVEs nas base images antes de subires nada.

---

## Fase 4 — Infra base em Azure (semana 3-4)
- [ ] Resource Group dedicado (nomenclatura clara: `rg-devsecops-lab`)
- [ ] Azure Container Registry (ACR) — `az acr build` para já testar builds remotos
- [ ] Azure Database for PostgreSQL Flexible Server (**Burstable B1ms** para não gastar — depois falamos de scaling)
- [ ] Azure Blob Storage — Storage Account + container privado, `SAS token` ou Managed Identity para acesso do BE (evita guardar connection strings em claro)
- [ ] Container Apps Environment + 2 Container Apps (BE e proxy+FE, ou proxy separado — a decidir consoante quiseres estudar Dapr/service-to-service dentro do Container Apps Environment)
- [ ] Ligar tudo via variáveis de ambiente / **Key Vault references** (nunca secrets hardcoded)

---

## Fase 5 — API Gateway (Azure APIM) (semana 4)
- [ ] Criar instância APIM (tier **Consumption**, é o mais barato para lab)
- [ ] Importar a tua API (via OpenAPI spec exportada do Spring Boot — `springdoc-openapi`)
- [ ] Policies básicas: rate limiting, CORS, JWT validation (mesmo que mockado) — isto é o "S" de segurança a começar a aparecer no Gateway
- [ ] Ligar APIM → Container Apps (backend URL)

Neste ponto tens a arquitetura completa a correr em Azure, ainda sem CI/CD.

---

## Fase 6 — CI/CD: GitHub Actions vs Azure DevOps (semana 5)
Fazer os **dois em paralelo** para comparares diferenças (ótimo talking point em entrevista).

**Pipeline (ambos):**
1. Lint + testes unitários (falha rápido)
2. Build + testes de integração (Testcontainers)
3. Build de imagem Docker
4. Scan de imagem (Trivy) — falha o pipeline se houver CVE crítico
5. Push para ACR
6. Deploy para Container Apps (staging slot/revision)
7. Smoke test pós-deploy

- [ ] GitHub Actions: workflow YAML, usar `azure/login` + OIDC (sem secrets estáticos — federated credentials)
- [ ] Azure DevOps: Pipeline YAML, Service Connection com Workload Identity Federation
- [ ] Comparar: sintaxe, gestão de secrets, integração nativa com Azure (Azure DevOps ganha aqui), ecossistema de actions/marketplace (GitHub ganha aqui)

---

## Fase 7 — DevSecOps "a sério": Shift-Left Security (semana 5-6)
Aqui é onde o projeto deixa de ser "CI/CD" e passa a ser "DevSecOps":

- [ ] **SAST**: SonarCloud (grátis para repos públicos) ou Semgrep, integrado no pipeline, com quality gate a bloquear merge
- [ ] **Dependency scanning**: Dependabot (GitHub) ou OWASP Dependency-Check, com falha automática em vulnerabilidades HIGH/CRITICAL
- [ ] **Secret scanning**: gitleaks ou GitHub Advanced Security, pre-commit hook local também (husky no FE, ou git hooks genéricos)
- [ ] **Container scanning**: Trivy ou Microsoft Defender for Containers no ACR (scan automático em cada push)
- [ ] **DAST**: OWASP ZAP a correr contra o ambiente de staging depois do deploy, no próprio pipeline
- [ ] **IaC scanning**: Checkov ou tfsec sobre os teus templates (Fase 8) antes de aplicar
- [ ] **Secrets management**: tudo o que hoje está em env vars passa a vir do Azure Key Vault via Managed Identity

---

## Fase 8 — Infrastructure as Code (semana 6-7)
- [ ] Escolher Bicep (nativo Azure, mais simples de aprender) — Terraform como extra opcional se quiseres comparar
- [ ] Templates para: Resource Group, ACR, Container Apps Environment, PostgreSQL Flexible Server, Storage Account, APIM, Key Vault
- [ ] Pipeline dedicado de infra: `what-if` (dry-run) em PR, `apply` só após aprovação manual
- [ ] Ambientes: `dev` e `staging` a partir do mesmo template, parametrizados

---

## Fase 9 — Observabilidade & Scaling (semana 7-8)
- [ ] Application Insights ligado ao BE (Spring Boot Actuator + Micrometer)
- [ ] Azure Monitor dashboards básicos: latência, erros, throughput
- [ ] Scaling rules no Container Apps (KEDA — scale por HTTP concurrency ou CPU)
- [ ] Teste de carga simples (k6 ou Locust) para forçares o scale-out e veres acontecer
- [ ] Health checks / liveness / readiness probes configurados corretamente

---

## Fase 10 — Hardening final & storytelling para entrevistas (semana 8)
- [ ] Revisão de todas as policies de segurança aplicadas (checklist final)
- [ ] Documentar decisões de arquitetura (ADRs curtos — 1 página por decisão)
- [ ] Preparar 2-3 minutos de explicação do projeto em inglês (ligando com a tua prep atual para VWDS/ACENSI)
- [ ] Diagrama final da arquitetura (draw.io ou Excalidraw) para levares a entrevistas

---

## Notas
- Podes correr Fases 1-3 sem custos Azure nenhuns — só entras em Azure na Fase 4.
- PostgreSQL: começas em container (Fase 3), sobes para Flexible Server (Fase 4) — a app não deve mudar, só a connection string (isto valida que o teu BE está bem desenhado/escalável desde o início).
- Sempre que possível, desliga/apaga recursos Azure fora das sessões de estudo para poupar (Container Apps escala a zero, mas APIM e PostgreSQL Flexible Server não são grátis parados).
