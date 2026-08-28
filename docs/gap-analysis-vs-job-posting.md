# Gap Analysis — ntccpay roadmap vs. payments backend job posting

Reference: Backend Engineering / Payment Systems posting (Aug 2026).
**Purpose: comparison only.** Nothing here is auto-imported into ROADMAP.md —
each gap is decided explicitly, either "already covered", "add later if needed",
or "out of scope". Backend-first.

## 1. Already covered by the roadmap

| Posting requirement | Where ntccpay covers it |
|---|---|
| Java + Spring Boot, high throughput, low latency | Java 25 + Boot 4.x; p99 < 150ms latency budget; fast path touches only Redis + in-memory rules (Phases 1–5) |
| Event-driven APIs | Kafka backbone, transactional outbox, consumer groups, retry topics, DLQs (Phase 3) |
| Reconciliation + settlement flows | settlement-service batch + settlement files (Phase 4); daily reconciliation balancing to zero (Phase 9) |
| Idempotency | `Idempotency-Key` on the API (Phase 1), unique-constraint guarantee (Phase 2), `processed_events` consumer idempotency (Phase 3) |
| Circuit breakers / fault tolerance | Resilience4j timeouts, circuit breaker, bulkhead (Phase 4); chaos-lite (Phase 5); Chaos Mesh + k6 soak (Phase 9) |
| Observability & telemetry first | Micrometer/Prometheus, OTel traces through Kafka, structured logs → Loki, SLOs + Grafana alerts (Phase 5) — the roadmap's core principle |
| Load testing / performance | k6/Gatling ramp tests, virtual threads, JFR, GC tuning, measured p99 before/after (Phase 5) |
| Data access layer (SQL) | PostgreSQL-for-everything ADR, money-correct schema, optimistic locking, L1 Caffeine / L2 Redis caching (§2.1, Phase 4) |
| PCI: sensitive data handling | PAN is toxic data: self-masking `CardNumber` VO, masked logs, test PANs only, no-PAN-in-logs tests (`docs/security.md`) |
| Built from scratch, no inherited code | The whole project is greenfield by design |

## 2. Gaps — real differences worth knowing about

Ordered by how much they matter to this project's goals.

1. **Availability target (99.99%)** — the roadmap has a *latency* SLO (99.9% of
   auths < 150ms) but no *availability* SLO, no error budget, and no zero-downtime
   deploy proof (there is a rollback drill, but not "deploy under load, zero failed
   auths"). **Decision: add an availability SLO + error budget in Phase 5 and a
   zero-downtime deploy drill in Phase 6/8.** Cheap to add, closes the biggest gap.
2. **Client-side retry logic** — Phase 4 lists timeout/CB/bulkhead but no Resilience4j
   **Retry with backoff + jitter** on the auth-api → decision-service call, and no
   explicit "retries are safe because calls are idempotency-keyed" statement.
   **Decision: add to Phase 4 — one task, one paragraph in the ADR.**
3. **Graceful degradation** — a circuit breaker opens, but then what? No explicit
   fallback (e.g., serve from stale Caffeine BIN cache with a `DEGRADED` flag) and
   no load shedding. **Decision: add a fallback + 503/Retry-After behavior to
   Phase 4.** Rate limiting (bucket4j + Redis) is already a known gap in
   `docs/security.md` — same phase.
4. **Inbound callback handling** — notification-service does *outbound* webhooks;
   there is no *inbound* callback receiver (HMAC signature verification, dedupe by
   event id, async processing). Common in real payment integrations.
   **Decision: optional Phase 4 task; not on the critical path.**
5. **PCI DSS depth** — masking ≠ compliance. Missing: tokenization/vault pattern,
   encryption-at-rest story, RBAC for internal endpoints, actor-level audit log
   ("who did what" vs DB audit columns). **Decision: note in `docs/security.md`
   as future work; not needed for a learning platform using test PANs.**
6. **NoSQL hands-on** — the posting says "SQL and NoSQL"; the ADR deliberately
   rejected NoSQL (correct for a ledger). The §2.1 hook (Elasticsearch CQRS read
   model, Phase 9+) already covers this if ever wanted. **Decision: keep deferred;
   the ADR reasoning is the interview answer.**
7. **Profiling depth** — JFR is in Phase 5; async-profiler flame graphs are not.
   **Decision: nice-to-have, add only if a Phase 5 bottleneck needs it.**

## 3. Not applicable / deliberately out of scope

- **gRPC / MCP** — REST covers every flow in this platform; see "gRPC FAQ" below
  for what it is and when it would matter.
- **Payout engine, FX, Bloomberg feeds, AI treasury orchestration** — different
  domain. The transferable core (double-entry ledger, batching, reconciliation,
  settlement files) is already in Phases 2–4. A future "payouts bounded context"
  reusing outbox + ledger would be the natural stretch.
- **React frontend** — posting is backend-first for this project; a tiny ops
  dashboard is a Phase 9+ stretch at most.
- **Team collaboration / code reviews** — solo project substitute: ADRs + PR-based
  workflow on this repo.

## 4. gRPC FAQ

**What is it?** A RPC framework: you define services and messages in a `.proto`
file (strongly typed, with required fields and types), generate client/server
stubs, and calls travel over **HTTP/2** with **Protobuf** binary serialization.

**Why it exists / when it wins over REST+JSON:**
- Smaller payloads, faster (de)serialization → lower p99 on hot internal paths
- A generated, compile-time-checked contract (no hand-written client code drift)
- HTTP/2 multiplexing and bidirectional streaming (server can stream updates)

**When REST is the better answer:** public/external APIs (universal tooling,
curl-able, cacheable, browser-friendly), simple CRUD, when payload size and
serialization cost are not the bottleneck.

**Does ntccpay need it?** No — the only sync internal call is
auth-api → decision-service, one small request/response whose latency is dominated
by the rule engine, not JSON parsing. The p99 budget is met with REST. If that
call ever became the bottleneck, gRPC would be the first lever — and that would
be an ADR decision, exactly the kind of reasoning the posting is testing.

---

**Bottom line:** the roadmap's true gaps are small and cheap (availability SLO,
client-side retry, fallback behavior — items 1–3); everything else is either
already covered or deliberately out of scope.
