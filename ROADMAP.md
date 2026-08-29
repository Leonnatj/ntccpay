# ccpay — Card Payment Authorization Platform

A production-style card payment authorization platform, built from scratch as a
learning vehicle for modern cloud development.

**Domain purpose:** high transaction volume, strict latency (auth decision on the
synchronous critical path), and absolute money correctness. Everything else
(ledger, fraud, notifications, settlement) runs on an asynchronous event pipeline.

---

## 1. Target Architecture

```
                                  SYNC CRITICAL PATH (~p99 < 150ms)
 ┌─────────┐   HTTPS    ┌──────────────────┐          ┌───────────────────┐
 │  Card   │──────────▶ │  auth-api         │─────────▶│ decision-service  │
 │ Network │            │  (REST, AuthN,    │  (REST)  │ rules, BIN, velo- │
 │ client  │            │  idempotency)     │◀─────────│ city, limits      │
 └─────────┘            └───────┬──────────┘  Redis   └───────────────────┘
                                │ publish auth events (OUTBOX)
                                ▼
                     ┌─────────────────────┐
                     │        Kafka        │  topics: auths, captures,
                     └──┬─────┬─────┬─────┘  settlements, dlq.*
                        │     │     │
        ┌───────────────┘     │     └───────────────┐
        ▼                     ▼                     ▼
┌───────────────┐   ┌─────────────────┐   ┌────────────────────┐
│ ledger-service│   │  fraud-service  │   │ notification-svc   │
│ double-entry, │   │ velocity, rules,│   │ webhooks/emails    │
│ idempotent,   │   │ model scoring   │   │ (mock providers)   │
│ Postgres      │   └─────────────────┘   └────────────────────┘
└──────┬────────┘
       │ end-of-day batch
       ▼
┌──────────────────┐
│settlement-service│  batching, reconciliation, settlement files
└──────────────────┘
```

**Key principles**

| Principle | How it shows up |
|---|---|
| Money correctness | amounts as `long` minor units (cents) + ISO 4217 currency; never floats |
| Idempotency everywhere | idempotency keys on the API, idempotent Kafka consumers (processed-event table) |
| Reliable eventing | **transactional outbox** pattern (never "write DB + publish Kafka" as two independent steps) |
| Latency budget | auth path touches only Redis + in-memory rules; heavy work is async |
| Database-per-service | each service owns its schema; no shared DB |
| Observability first | metrics + traces + structured logs from Phase 5 onward |

---

## 2. Tech Stack

- **Java 25 (LTS)** + **Spring Boot 4.x** (Web, Data JPA, Kafka, Actuator, Validation)
- **PostgreSQL** (ledger, settlement) + **Redis** (velocity counters — cache only)
- **Apache Kafka** — event backbone
- **Docker + Docker Compose** — local infra; **Kubernetes (kind)** — local orchestration
- **Micrometer + Prometheus + Grafana + Loki + Tempo/OpenTelemetry** — observability
- **Testcontainers, JUnit 5, Mockito, AssertJ** — testing
- **Cucumber JVM** (`cucumber-spring` + `cucumber-junit-platform-engine`) — BDD acceptance tests for business behavior
- **k6 or Gatling** — load testing
- **Terraform + AWS (EKS, MSK, RDS, ECR)** — cloud; **GitHub Actions** — CI/CD

---

## 2.1 Database choices (ADR: why PostgreSQL for everything)

**Decision: PostgreSQL is the primary store for every service. Redis is a cache only.**

The ledger demands ACID transactions (double-entry debit+credit must be atomic),
strong consistency ("was this auth captured?"), enforced constraints (unique
idempotency keys, optimistic locking), and SQL for reconciliation. PostgreSQL
provides all of it — this is why real fintech stacks are relational at the core.

| Rejected | Why | Would fit if... |
|---|---|---|
| MongoDB | document model; cross-document ACID across shards is complex; wrong tool for money | we had document-shaped config/profile data |
| Cassandra | eventual consistency, query-first model (no joins/ad-hoc queries) — a ledger's reconciliation is the opposite of its design; "unique key" must be hand-rolled CAS | we needed planetary-scale event archival |
| Elasticsearch | a search index, not a source of truth | optional Phase 9+ CQRS read model: merchant transaction search, fed from Kafka events |

**Rule: polyglot persistence is earned, not chosen.** One database done well first;
add a specialty store only when a measured workload demands it — and write the ADR
when you do. Keep the distinction sharp: Redis is disposable (derived state only),
Postgres is truth.

---

## 2.2 Domain-Driven Design (DDD)

DDD is the design backbone of ccpay: the architecture (Phase 1) and the service
split (Phase 4) both *derive from* the domain model instead of being chosen ad hoc.

### Ubiquitous language (from day 1)
Maintain `docs/ubiquitous-language.md` — a glossary of payment terms as the business
uses them: *Authorization, Capture, Decline, Reason Code, BIN, Cardholder, Merchant,
Settlement, Reversal*. Code, tests, Gherkin features, and Kafka topics all use these
exact words. If a test name can't be read aloud to a payments person, rename it.

### Strategic design → the service map (Phase 4)
- **Bounded contexts = microservices.** Auth Context (auth-api + decision-service),
  Ledger Context, Fraud Context, Notification Context, Settlement Context. Each
  context owns its own model of shared concepts (the ledger's "transaction" is not
  the auth's "authorization").
- **Context mapping:** Kafka event schemas are the *published language* between
  contexts. fraud-service and ledger-service keep *anti-corruption layers* (ACLs) —
  they translate inbound events into their own model and never import auth-api's
  types. Exercise: run a lightweight **event storming** session (orange stickies →
  domain events; blue → commands; yellow → aggregates) before splitting services.
- **One aggregate = one transaction = one DB write.** Cross-aggregate consistency
  happens through domain events (→ outbox, Phase 3), never by reaching into another
  aggregate's tables.

### Tactical patterns (Phase 1–3)
- **Value objects** (immutable, validated at construction): `Money` (minor units +
  ISO 4217), `CardNumber` (self-masking `toString()`), `Bin`, `IdempotencyKey`,
  `ReasonCode`, `MerchantId`. No bare `long amount` or `String pan` anywhere.
- **Aggregates with invariants:** `Authorization` (aggregate root) enforces "a
  decision, once made, is immutable" and "one decision per idempotency key";
  `LedgerTransaction` enforces "debits must equal credits". Invariants live *inside*
  the aggregate, not in services.
- **Domain events:** `AuthorizationRequested`, `AuthorizationApproved`,
  `AuthorizationDeclined` — raised by the aggregate, consumed internally first
  (Phase 1), relayed to Kafka via the outbox (Phase 3).
- **Repository ports:** the domain defines `AuthorizationRepository` as an
  interface; JPA lives in the infrastructure adapter only (persistence model ≠
  domain model).
- **Domain services:** the rule engine is a pure domain service — no Spring, no I/O,
  fully testable.

### Learning sources
- *Learning Domain-Driven Design* — Vlad Khononov (start here; pragmatic)
- *Implementing Domain-Driven Design* — Vaughn Vernon (tactical depth)
- *Domain-Driven Design* — Eric Evans (the original; skim for strategic chapters)

---

## 3. Phased Roadmap

Each phase has a **goal**, concrete **tasks**, and an **exit checklist**. Do not
skip exit criteria — they are the actual skill being learned.

### Phase 0 — Tooling (day 1)

**Core — install now:**

- [x] **JDK 25 (LTS)** — Eclipse Temurin; verify: `java -version`
- [ ] **PostgreSQL — containerized (decision: no native installs)**: run Postgres only via Docker/Testcontainers. Uninstall the native install now — Phase 1 needs no DB, and this avoids port-5432 conflicts and "which DB am I hitting?" confusion while keeping dev at version parity with RDS in Phase 7. `psql` access happens via `docker exec -it <pg-container> psql` — no Windows client install needed
- [x] **IntelliJ IDEA** — Lombok plugin enabled, Project SDK = 25 (bundled HTTP Client covers
      all API testing; no Postman needed)
- [ ] **Git repo + GitHub** (repo was lost — reinitialize): `git init -b main` (explicit `main`, not the 2.33 default `master`), add `.gitignore` (Maven/Gradle + `.idea/` + `target/` + `*.iml`), then `git add ROADMAP.md && git commit -m "docs: add ccpay learning roadmap"` and push to GitHub. Docs and scaffolding go straight to `main` — branches start in Phase 1
- [ ] **Docker Desktop** — required by Phase 2 (Testcontainers + docker-compose):
      `winget install Docker.DockerDesktop`, then verify `docker run hello-world`.
      Last blocker before Phase 2; nothing else on this list needs it sooner

**Deferred — each is installed by its phase, don't install manually now:**

| Tool | Phase | How it arrives (Windows note) |
|---|---|---|
| **Redis** | 4 | **No official native Windows build.** Run it in Docker (`redis:7-alpine` in docker-compose) or in WSL2. Memurai is a Windows-native Redis-compatible option if you must avoid Docker — but Docker Desktop will exist by then, so don't bother |
| **Kafka** | 3 | docker-compose (KRaft mode) — never installed natively |
| **Prometheus / Grafana / Tempo / Loki** | 5 | docker-compose observability stack |
| **k6** | 5 | `winget install k6` (load testing CLI) |
| **kubectl + kind** | 6 | Layering: containers → Docker (Ph2) → **kind** = a real k8s cluster inside Docker (Ph6) → **EKS** = managed k8s (Ph7). Install only the `kind` + `kubectl` CLIs; kind creates the cluster |
| **Helm** | 6 | `winget install Helm.Helm` |
| **Terraform CLI + AWS CLI** | 7 | `winget install Hashicorp.Terraform Amazon.AWSCLI` |

**Sanity check before Phase 1:** `java -version` → 25, `git status` clean, IntelliJ compiles a
hello-world Spring Boot app. Docker can wait until Phase 2 — but install it before starting that
phase, not after (and uninstall the native PostgreSQL first, per above).

- [ ] Skim: what a Maven/Gradle build is, what a Spring Boot "fat jar" is

### Phase 1 — The auth decision, in-memory (Week 1–2)
> Learn: Spring Boot fundamentals, hexagonal architecture + DDD, TDD/BDD, REST, idempotency.

- [ ] Generate project on Spring Initializr → **auth-api**: Web, Validation, Actuator, Lombok
- [ ] **README.md from the first commit** (this is a public showcase repo): what ccpay is, the architecture diagram, the latency/money-correctness constraints, how to run it, and a phase-by-phase learning log — update it as each phase lands
- [ ] **Start the branch + PR workflow now** (solo but deliberate): one short-lived branch per feature (`feat/rule-engine`, `feat/idempotency-key`), conventional commits (`feat:`, `fix:`, `test:`), open a PR against `main` even alone, squash-merge, keep `main` always green — this habit is exactly what Phase 8's CI/CD will automate
- [ ] Domain model: `AuthorizationRequest` (PAN, amount, currency, merchant, idempotency key), `AuthorizationDecision` (APPROVED/DECLINED + reason codes)
- [ ] Hexagonal layout: `domain/` (pure logic, no Spring), `application/` (ports), `infrastructure/` (adapters)
- [ ] **Start the ubiquitous language**: create `docs/ubiquitous-language.md` (Authorization, Capture, Decline, Reason Code, BIN…) and use those exact words in code, tests, and Gherkin
- [ ] **Tactical DDD**: value objects (`Money` in minor units, self-masking `CardNumber`, `Bin`, `IdempotencyKey`, `ReasonCode`), the `Authorization` **aggregate root** enforcing its invariants ("a decision, once made, is immutable"; "one decision per idempotency key"), and domain events (`AuthorizationRequested`, `AuthorizationApproved`, `AuthorizationDeclined`) raised by the aggregate
- [ ] Rule engine v1 (pure Java, unit tested): amount > limit? currency supported? card blocklisted?
- [ ] Luhn check + BIN validation on the PAN; **never log or store full PAN** (PCI mindset from day 1)
- [ ] `POST /v1/authorizations` with `Idempotency-Key` header — replay returns the original decision
- [ ] **DTOs at the API boundary**: `AuthorizationRequestDto` / `AuthorizationResponseDto` as Java records — the controller maps DTO → domain command → aggregate, and aggregate → response DTO; never expose the domain model over HTTP; response carries only the masked PAN (DDD translation layer at the edge)
- [ ] **Spring Security basics**: secure everything by default (`/actuator/health` public, all else denied), API-key auth on the authorization endpoint, 401/403 as problem+json
- [ ] Global exception handler, problem+json errors, request validation
- [ ] **BDD acceptance tests with Cucumber**: Gherkin features for the decision rules (e.g. `authorization-decision.feature`: "Given a card from a blocked BIN / When an authorization is requested / Then the decision is DECLINED with reason CARD_BLOCKED"); step definitions drive the domain through its ports — no HTTP, no Spring context for the pure rule scenarios
- [ ] Fast unit tests stay plain JUnit 5 (edge cases, boundary values); Cucumber only for business-readable behavior — don't write every test in Gherkin
- [ ] Integration tests with `@SpringBootTest` + `MockMvc`

**Exit:** `./mvnw test` green; curl an auth → APPROVED/DECLINED; idempotent replay
returns the same decision; the decision path has zero I/O; the Gherkin feature file
reads like the business rule document it replaces.

### Phase 2 — Persistence + Docker Compose (Week 3–4)
> Learn: Postgres, Flyway, Testcontainers, Docker, DB design for money.

- [ ] Spring Data JPA + Flyway; Postgres via **Testcontainers** in tests (Spring Data JPA uses **Hibernate** as its JPA provider under the hood — you get it automatically; the "persistence model ≠ domain model" task below is how you keep it in its lane)
- [ ] Schema: `authorizations`, `idempotency_keys` (unique constraint = the idempotency guarantee); amounts as `BIGINT` minor units + `CHAR(3)` currency
- [ ] Money-correct DB design: optimistic locking, append-only writes, audit columns
- [ ] **Persistence model ≠ domain model**: map the `Authorization` aggregate to tables inside the infrastructure adapter; keep JPA/Spring annotations out of `domain/` (the aggregate remains persistence-ignorant)
- [ ] `docker-compose.yml`: postgres + app; learn volumes, networks, healthchecks, `depends_on: condition: service_healthy`
- [ ] Multi-stage `Dockerfile` → small non-root runtime image

**Exit:** `docker compose up` runs everything; data survives container recreation;
concurrent duplicate `Idempotency-Key` requests cannot double-insert (prove with a
concurrency test).

### Phase 3 — Kafka + the async pipeline (Week 5–7)
> Learn: Kafka concepts, Spring Kafka, outbox pattern, consumer idempotency.

- [ ] Kafka (KRaft mode) in docker-compose; topics `auths.v1`, `captures.v1`
- [ ] **Transactional outbox**: the `Authorization` aggregate raises domain events; the auth transaction persists them to an outbox table; a poller publishes them to Kafka (cross-aggregate consistency through events, never shared tables)
- [ ] **ledger-service**: consumes `auths.v1`, writes double-entry rows (debit issuer-pending, credit merchant receivable); idempotent via `processed_events` table keyed by event id; the `LedgerTransaction` aggregate enforces "debits = credits"
- [ ] **notification-service**: same topic, logs/mock webhook + email; poison pills → DLQ topic
- [ ] Learn consumer groups, offsets, non-blocking retries (`DefaultErrorHandler` + retry topics), DLQs
- [ ] Define `AuthorizationAuthorized` as a versioned JSON event schema, documented in the repo (published language between contexts)

**Exit:** auth request → ledger row within ~2s; killing a consumer and restarting
resumes without loss or duplication; a malformed event lands on the DLQ; auth-api
restart loses no events (outbox works).

### Phase 4 — Split microservices + the sync/async boundary (Week 8–10)
> Learn: service decomposition, REST clients, resilience, Redis, strategic DDD.

- [ ] Extract **decision-service** (rules, BIN table, limits) — auth-api calls it synchronously over REST
- [ ] **Caching strategy (L1/L2)**: BIN table in **Caffeine** (in-process, short TTL, zero network hops — it's tiny and immutable); velocity counters in **Redis** (shared across pods, atomic `INCR` + `EXPIRE`, sliding windows). Learn why embedded caches (Ehcache) and data grids (Hazelcast) lose here: per-JVM caches break under HPA scaling, grids hand-roll what Redis gives you for free; measure the latency delta
- [ ] Resilience4j: timeouts, circuit breaker, bulkhead — a slow decision-service must not sink auth-api
- [ ] **Service-to-service security**: OAuth2 client-credentials flow (JWT) between auth-api and decision-service using Spring Security's resource-server support; run **Keycloak** in docker-compose as the IdP — this is how real payment platforms authenticate internal hops (mTLS comes in Phase 9)
- [ ] **fraud-service**: consumes `auths.v1`, computes velocity/risk scores asynchronously, publishes `fraud.flags.v1`; wraps consumption in an anti-corruption layer (own model, not auth-api's types)
- [ ] **settlement-service**: consumes `captures.v1`, end-of-day batch (Spring Batch or `@Scheduled`), generates settlement file, marks captured→settled
- [ ] Wire all 6 services in docker-compose; each service owns its DB schema
- [ ] **Strategic DDD**: treat each service as a **bounded context**; run an event-storming pass over the auth lifecycle to validate the split; map contexts (Kafka schemas = published language) and build **anti-corruption layers** in ledger-service and fraud-service so they translate inbound events into their own models instead of importing auth-api's types

**Exit:** `docker compose up` brings up 6 services + infra; auth still meets its
latency budget with decision-service behind a circuit breaker; the settlement file
reconciles 1:1 with authorizations.

### Phase 5 — Observability + performance (Week 11–13)
> Learn: metrics, tracing, SLOs, load testing, JVM performance.

- [ ] Micrometer + Prometheus `/actuator/prometheus`; Grafana dashboards: TPS, p50/p95/p99 auth latency, error rate, Kafka consumer lag, DB pool saturation
- [ ] OpenTelemetry tracing: auth-api → decision-service, and trace context propagated through Kafka headers; Grafana Tempo or Jaeger
- [ ] Structured JSON logging with correlation ids; ship to Loki (or Grafana Cloud free tier)
- [ ] Define SLOs: 99.9% of auths < 150ms; alert rules in Grafana
- [ ] Load test with **k6** or **Gatling**: ramp to realistic TPS; find the bottleneck (DB pool? rule engine? serialization?)
- [ ] JVM tuning: enable **virtual threads** (`spring.threads.virtual.enabled=true`), capture/read a JFR recording, GC choice, heap sizing; benchmark with compact object headers on/off (Java 25)
- [ ] Chaos-lite: `docker compose pause` a dependency; watch circuit breakers + metrics react

**Exit:** one Grafana dashboard that shows system health at a glance; one trace that
follows a single auth from API → decision → Kafka → ledger; a written note of measured
p99 before/after one optimization.

### Phase 6 — Kubernetes (Week 14–16)
> Learn: k8s primitives, Helm, config/secrets, autoscaling.

- [ ] Install **kind** (Kubernetes in Docker); push images to a local registry
- [ ] Hand-write manifests first (Deployment, Service, Ingress, ConfigMap, Secret, HPA) — understand what Helm later abstracts
- [ ] Liveness/readiness/startup probes wired to Actuator health groups
- [ ] Resource requests/limits; HPA on CPU (Kafka lag custom metric later)
- [ ] Package with **Helm**: one chart per service, values per environment
- [ ] Run Kafka/Postgres on k8s too (Bitnami charts / Strimzi) — fine for learning; know why real prod often uses managed services

**Exit:** kind cluster runs the whole platform; kill a pod → traffic recovers with
zero lost auths; HPA scales auth-api under k6 load.

### Phase 7 — AWS + Terraform (Week 17–20)
> Learn: IaC, cloud networking, managed services, cost awareness.

- [ ] Terraform basics: providers, remote state (S3 backend + DynamoDB lock), modules; `plan`/`apply` discipline
- [ ] Build: VPC (public/private subnets, NAT), ECR repos, EKS cluster (or ECS Fargate if EKS feels heavy), RDS Postgres, MSK or self-hosted Kafka
- [ ] Decide **managed vs self-hosted** per component and write down why (e.g., MSK vs Strimzi)
- [ ] Secrets: AWS Secrets Manager + External Secrets Operator; IRSA for pod IAM roles
- [ ] Point Helm releases at EKS; images pulled from ECR (Redis → ElastiCache here, keeping the "cache is disposable" model)
- [ ] Use free tier / billing alarms ruthlessly; tear down at night with `terraform destroy`

**Exit:** `terraform apply` builds the cloud home from zero; auth-api on EKS serves
traffic through the ALB; a single command tears it all down.

### Phase 8 — CI/CD (Week 21–22)
> Learn: pipelines, artifact promotion, GitOps.

- [ ] GitHub Actions: PR → build + unit/Testcontainers tests + lint; main → build image, push to ECR with git-SHA tags
- [ ] **Secret scanning in CI (gitleaks)** — the repo is public: a leaked key must fail the build, never sit in history; AWS/IdP credentials live only in GitHub Actions secrets + AWS Secrets Manager
- [ ] Environments: dev (auto) → staging → prod (manual approval); never deploy `:latest`
- [ ] Deploy via Helm upgrade in the pipeline, or go full **GitOps with ArgoCD** (recommended endgame)
- [ ] Post-deploy smoke test: synthetic auth transaction
- [ ] Rollback drill: deploy a deliberately broken version, roll back in < 5 minutes

**Exit:** PR → merged → running on the cluster with no manual steps; rollback proven
by execution, not theory.

### Phase 9 — Hardening & capstone (ongoing)
- [ ] Secret rotation; mTLS between services (Linkerd/Istio, optional)
- [ ] Contract tests between services (Spring Cloud Contract or Pact)
- [ ] Daily reconciliation job: ledger vs settlements vs authorizations must balance to zero
- [ ] Chaos engineering (Chaos Mesh on kind); k6 soak tests
- [ ] Optional CQRS read model: Elasticsearch transaction search fed from Kafka events
- [ ] Write an ADR for every big choice — this is what interviews actually probe

---

## 4. Rules of the road

1. **One phase at a time.** The sequencing is deliberate: correctness before distribution, observability before cloud.
2. **Tests are not optional.** Money code without tests is a liability exercise, not learning.
3. **Break things on purpose** once each phase works — kill containers, corrupt events, saturate the DB. Recovery is the skill.
4. **Commit small, commit often** with meaningful messages; your git history is a learning artifact.
5. **Write ADRs** (short markdown in `docs/adr/`) whenever you choose X over Y.
6. **Mind the PAN — the repo is public.** Every fixture, test, log line, and commit message uses only synthetic test card numbers (e.g., Stripe's `4242...`); treat a real PAN in any commit as a critical incident. Mask in logs (`****1111`).
7. **No secrets in git, ever.** Credentials go in environment variables, GitHub Actions secrets, and AWS Secrets Manager; gitleaks in CI (Phase 8) is the safety net — and a public, clean history is itself portfolio evidence.
8. **The human owns git history.** The agent may run read-only git commands (log, status, diff) to stay oriented, but never `add`/`commit`/`push`/`merge`/`reset` on its own — it proposes the exact commands and the human runs them.

---

## 5. Start here, right now (Phase 0 → 1 quickstart)

1. In IntelliJ: `File → Project Structure → SDK → Add SDK → Download JDK → Eclipse Temurin 25`.
2. Go to [start.spring.io](https://start.spring.io): Maven, Java 25, Boot 4.x, artifact `auth-api`;
   dependencies: Spring Web, Validation, Actuator, Lombok. Add Cucumber via
   `io.cucumber:cucumber-bom` + `cucumber-java`, `cucumber-spring`, `cucumber-junit-platform-engine`.
3. First commit, then build `POST /v1/authorizations` per Phase 1.

Ask me to scaffold Phase 1 (project structure, rule engine with tests, controller,
idempotency) whenever you're ready.






