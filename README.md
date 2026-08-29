# ntccpay — Card Payment Authorization Platform

A production-style card payment authorization platform, built from scratch as a
learning vehicle for modern cloud development: high transaction volume, strict
latency on the synchronous auth path, and absolute money correctness.

**Status: Phase 1 in progress** — the in-memory authorization decision service.
See [ROADMAP.md](ROADMAP.md) for the full phase plan (Docker, Kafka, Kubernetes,
AWS/Terraform, CI/CD are coming in later phases).

## Architecture (Phase 1)

Hexagonal + DDD:

- `domain/` — pure business logic, zero Spring: the `Authorization` aggregate
  (a decision, once made, is immutable), value objects (`Money` in minor units,
  self-masking `CardNumber`, `Bin`, `IdempotencyKey`), domain events, and the
  `AuthorizationRuleEngine` (rule order: `INVALID_PAN` → `CARD_BLOCKED` →
  `CURRENCY_NOT_SUPPORTED` → `AMOUNT_EXCEEDS_LIMIT`; first failure wins).
- `application/` — ports (in: `RequestAuthorization`; out: `AuthorizationRepository`)
  and the idempotent use case.
- `infrastructure/` — REST adapter (DTOs, problem+json errors), per-merchant
  `X-API-Key` security, and the Phase 1 in-memory repository (zero I/O on the
  decision path; Postgres arrives in Phase 2).

## Running and testing

```bash
./gradlew test             # unit + Cucumber BDD + integration tests
./gradlew bootRun
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

**Test card data only.** All numbers in this repo are published test PANs
(Stripe's `4242…` style). See [docs/security.md](docs/security.md).
