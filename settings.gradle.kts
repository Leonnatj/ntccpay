rootProject.name = "ntccpay"

// Phase 3: the platform becomes a Gradle multi-module monorepo (ADR 0004).
// Each bounded context is an independent, self-contained subproject under
// services/ — its own build file, dependencies, package root, DB schema, and
// Dockerfile. There is deliberately NO shared application code between them:
// the versioned JSON event schema documented in docs/events/ is the only
// contract between services.
include("services:auth-api")
// Phase 3 (hexagonal enforcement): each layer is a Gradle module, so the
// compiler itself forbids inward->outward dependencies. auth-domain has zero
// application dependencies; auth-application depends only on auth-domain;
// auth-infrastructure holds every framework adapter; the auth-api boot module
// is the thin composition root (main class, application.yml, Flyway, tests).
include("services:auth-api:auth-domain")
include("services:auth-api:auth-application")
include("services:auth-api:auth-infrastructure")
