# ADR 0004: One repo, many services — the ntccpay monorepo

- Status: accepted
- Date: 2026-09-05
- Deciders: project owner
- Related: ADR 0002 (Gradle subprojects when the platform splits)

## Context

Phase 3 adds `ledger-service` and `notification-service` next to `auth-api`;
Phase 4 grows the platform to six services. ADR 0002 already anticipated
"future services become Gradle subprojects in `settings.gradle.kts`". This ADR
makes that concrete and states the boundary rules now that a second service is
imminent.

Options considered:

1. **Polyrepo** — one git repository per service.
2. **Monorepo with shared code** — one repo plus a shared domain/events library.
3. **Monorepo with independent subprojects** (chosen) — one repo; every service
   is a self-contained Gradle subproject under `services/` with **zero shared
   Java code**.

## Decision

Adopt option 3. `settings.gradle.kts` includes `services:auth-api` and will
include `services:ledger-service`, `services:notification-service` (and the
Phase 4 services) as they land. The repository root owns the docs (ADRs,
ubiquitous language, event schema), docker-compose, k8s manifests, secrets, and
CI.

- Each subproject declares its own plugins and dependencies; the root build
  pins versions (Spring Boot `apply false`) and shared conventions only.
- **No shared application code.** No shared domain JAR, no shared DTOs.
  The versioned JSON event schema documented under `docs/events/` is the only
  contract between services — the published language of the bounded contexts.
- Each service owns its DB schema, its Dockerfile, its `application.yml`, its
  actuator surface, and remains independently deployable.

## Rationale

- **One owner, one deployment target.** Polyrepo pays off for disjoint team
  ownership, independent release trains, and per-repo access control — none of
  which apply to a solo learning/portfolio platform.
- **The published language must move in lockstep.** When `auths.v1` changes,
  every consumer changes in the same commit; ubiquitous-language and ADRs stay
  in one tree. Splitting later is cheap because the subprojects are already
  self-contained.
- **Cross-service exit criteria are integration features of one stack.**
  Phase 3's "auth request -> ledger row < 2s", kill/restart consumer resumes,
  and malformed event -> DLQ are exercised with one `docker compose up` and one
  `./gradlew test`.

## Consequences

- The anti-corruption-layer discipline planned for Phase 4 is preserved *by
  construction*: a service cannot import another service's types because none
  exist to import.
- CI (Phase 8) should enforce the no-cross-module-dependency rule by failing
  any build that references another module's classes.
- Moving `auth-api` into `services/auth-api/` rewrote tracked paths; history
  remains available via `git log --follow`.
- Trade-off accepted vs polyrepo: schema-registry-style *enforcement* is
  replaced here by documented schemas plus consumer tests written against the
  same JSON contract.