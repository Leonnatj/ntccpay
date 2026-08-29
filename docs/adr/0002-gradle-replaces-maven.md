# ADR 0002: Gradle (Kotlin DSL) replaces Maven as the build tool

- Status: accepted
- Date: 2026-08-29
- Deciders: project owner
- Supersedes: ADR 0001

## Context

ADR 0001 chose Maven for its enterprise ubiquity and convention-over-configuration
simplicity. The owner has since weighed the alternative rationale: the project's
future size is unknown, and the roadmap plans a multi-service platform
(ledger, fraud, notification, settlement services) with a CI/CD pipeline.

## Decision

Migrate the whole repository to Gradle with the Kotlin DSL now, while the repo is
small and the migration is a one-file change.

- Gradle 9.7.x, Kotlin DSL (build.gradle.kts, settings.gradle.kts)
- Spring Boot Gradle plugin 4.1.1; dependency versions imported from the
  spring-boot-dependencies BOM via platform() (no separate
  dependency-management plugin - modern Boot Gradle practice)
- Java toolchain pinned to 25 so builds do not depend on the ambient JDK
- Cucumber aligned via the cucumber-bom platform, as before

## Reasons

1. Unknown future scale: the roadmap adds several services; Gradle's incremental
   builds, build cache, and multi-module support pay off as the platform grows.
2. Cheapest migration point: one module, no custom build logic, no CI yet.
3. Learning value: Gradle + Kotlin DSL is a common requirement in modern
   cloud-native job postings; using it on a real project beats a toy exercise.
4. Convention retention: the Kotlin DSL build mirrors the Maven POM 1:1
   (same dependencies, same versions), so no behavior changes.

Trade-offs accepted: Maven's universality is lost (documented in ADR 0001);
build scripts now require Gradle/Kotlin-DSL knowledge to read; the Gradle
daemon adds a background process to the dev environment.

## Consequences

- pom.xml is removed; never reintroduce a second build system.
- The Gradle wrapper is the only supported way to build (CI in Phase 8 will
  call ./gradlew). If gradle-wrapper.jar is absent, generate it once with a
  locally installed Gradle (`gradle wrapper --gradle-version 9.7.1`) or let
  IntelliJ generate it on first project sync.
- Future services become Gradle subprojects in settings.gradle.kts when the
  platform splits (Phase 4).
