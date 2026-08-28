# Security Posture

Security in ntccpay is layered across phases (see ROADMAP.md). This document
consolidates the posture, names the owner phase for each control, and lists the
gaps that are NOT yet in the roadmap.

## Non-negotiables (every phase, from day 1)

1. **PAN is toxic data.** Never log or store the full PAN. Mask as `****1111`.
   `CardNumber` is a self-masking value object — `toString()` is safe by
   construction, so accidental logging cannot leak (enforced by unit test).
2. **Test card data only.** Fixtures use published test PANs (Stripe's
   `4242424242424242`, `4000000000000002`) or synthetic Luhn constructions.
   Never a real card number. Verified for all files in
   `src/test/resources/features/`.
3. **Validate at the boundary, decide in the core.** Request shape/currency/
   PAN format are validated at the adapter; the rule engine never trusts input.

## Controls by phase

| Control | Phase | How it's proven |
|---|---|---|
| Luhn + PAN format check | 1 | Gherkin: `INVALID_PAN` scenario |
| Card/BIN blocklist | 1 | Gherkin: `CARD_BLOCKED` scenarios |
| AuthN on `POST /v1/authorizations` | 1 | Integration test: unauthenticated request → 401 |
| No full PAN in logs | 1 | Unit test on `CardNumber.toString()`; logback masking pattern; integration test asserting captured log output |
| Idempotency = replay safety | 1–2 | Gherkin idempotency feature + concurrent-insert test |
| Non-root container image | 2 | Dockerfile `USER` + `docker inspect` in CI |
| Append-only, optimistic locking | 2 | Schema review + concurrency test |
| Secret separation (env/k8s Secret) | 6 | No secret in git; manifests reviewed |
| Managed secrets (Secrets Manager + ESO, IRSA) | 7 | `terraform plan` review; no static creds |
| Secret rotation, mTLS, contract tests | 9 | Rotation drill executed; contract suite green |

## Gaps — not yet in the roadmap (to be added as tasks)

- **Dependency & image scanning in CI** (Phase 8): Dependabot + Trivy/OWASP
  dependency-check on every PR; fail the build on critical CVEs.
- **Rate limiting on the auth endpoint** (Phase 1 or 4): a bucket4j/Redis
  limiter so a misbehaving merchant cannot exhaust the auth path.
- **Automated PAN-leak prevention** (Phase 8): a CI job that greps the repo and
  test output for full-PAN patterns (`\d{13,19}` matching Luhn) — "never commit
  real card numbers" becomes a gate, not prose.
- **API authorization model** (Phase 1): decide *what* AuthN means — per-merchant
  API keys vs OAuth2 client credentials — and write the ADR.
- **Transport security** (Phase 4+): TLS termination point, mTLS between
  internal services (deferred to Phase 9 today).

## What security does NOT go in Gherkin

Gherkin carries business-readable *rules* (blocklists, limits, idempotency
semantics). Technical controls — masking, rate limits, TLS, secrets — are
proven by unit, integration, and CI tests. If a security control can be
described as a business rule a payments person owns, it belongs in a feature
file; if it is plumbing, it belongs in code and CI.
