rootProject.name = "ntccpay"

// Phase 3: the platform becomes a Gradle multi-module monorepo (ADR 0004).
// Each bounded context is an independent, self-contained subproject under
// services/ — its own build file, dependencies, package root, DB schema, and
// Dockerfile. There is deliberately NO shared application code between them:
// the versioned JSON event schema documented in docs/events/ is the only
// contract between services.
include("services:auth-api")
