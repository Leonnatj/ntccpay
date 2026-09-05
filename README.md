# ntccpay — Card Payment Authorization Platform

A production-style card payment authorization platform, built from scratch as a
learning vehicle for modern cloud development: high transaction volume, strict
latency on the synchronous auth path, and absolute money correctness.

**Status: Phase 2 complete** — the authorization decision service is now
persistent (PostgreSQL + Flyway) and fully Dockerized. Next up: Phase 3
(Kafka + the transactional outbox). See [ROADMAP.md](ROADMAP.md) for the full
phase plan.

**Repository layout:** a Gradle multi-module monorepo (ADR 0004). Every
service lives under `services/` with its own build file, package root, DB
schema and Dockerfile; there is deliberately no shared code between services.
The root build pins versions and aggregates the service subprojects.

## Architecture

Hexagonal + DDD:

- `domain/` — pure business logic, zero Spring (and zero JPA): the
  `Authorization` aggregate (a decision, once made, is immutable), value
  objects (`Money` in minor units, self-masking `CardNumber`, `Bin`,
  `IdempotencyKey`), domain events, and the `AuthorizationRuleEngine`
  (rule order: `INVALID_PAN` → `CARD_BLOCKED` → `CURRENCY_NOT_SUPPORTED` →
  `AMOUNT_EXCEEDS_LIMIT`; first failure wins).
- `application/` — ports (in: `RequestAuthorization`; out:
  `AuthorizationRepository`) and the idempotent use case.
- `infrastructure/` — REST adapter (DTOs, problem+json errors) and per-merchant
  `X-API-Key` security, plus the **persistence adapter**:

  - Flyway owns the schema (`db/migration/V1__create_authorization_tables.sql`);
    Hibernate runs with `ddl-auto: validate` and may never create or alter it.
  - **Persistence model ≠ domain model**: `AuthorizationEntity` maps to and from
    the aggregate inside the adapter (`fromDomain`/`toDomain`); the domain
    package stays persistence-ignorant.
  - **PCI by construction**: only the masked PAN (`****1234`) is ever stored.
    Rehydrated cards are `CardNumber` masked references whose `raw()` /
    `luhnValid()` throw — the full PAN is unreachable after persistence.
  - **Idempotency is the database's job**: `idempotency_keys` has the key as
    `PRIMARY KEY` and a `request_fingerprint` (SHA-256 over
    PAN|amount|currency|merchant) for conflict detection, so concurrent
    duplicate requests physically cannot double-insert.

## Running and testing

```bash
./gradlew test             # 52 tests: unit + Cucumber BDD + integration (root build aggregates all services)
./gradlew bootRun          # needs the compose Postgres running on localhost:5432
```

Tests that touch persistence use **Testcontainers** to spin up a real
PostgreSQL 17 — **Docker Desktop must be running** for the full suite. Pure
unit and Cucumber tests stay container-free and fast.

```bash
# full stack: postgres + app (Flyway migrates on boot)
echo "SPRING_DATASOURCE_PASSWORD=<postgres password>" > .env   # git-ignored
docker compose up --build
```

(First build only: if `gradle-wrapper.jar` is missing, generate the wrapper once
with a locally installed Gradle — `gradle wrapper --gradle-version 9.7.1` — or let
IntelliJ IDEA generate it when importing the project.)

## Try it

```bash
curl -i -X POST http://localhost:8080/v1/authorizations \
  -H "X-API-Key: change-me-acme-key" \
  -H "Idempotency-Key: my-first-auth" \
  -H "Content-Type: application/json" \
  -d '{"pan":"4242424242424242","amountMinor":1000,"currency":"USD","merchant":"acme-corp"}'
```

- `201` = decided, `200` = idempotent replay of the same request,
  `409` = same key with a different request, `401` = missing/invalid API key.

Decisions are persisted — replay the same `Idempotency-Key` after a restart and
you still get the original decision.

**Test card data only.** All numbers in this repo are published test PANs
(Stripe's `4242…` style). See [docs/security.md](docs/security.md).
