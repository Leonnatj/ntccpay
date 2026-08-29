# ADR 0001: Maven over Gradle as the build tool

- Status: superseded by ADR 0002 (Gradle)
- Date: 2026-08-29
- Deciders: project owner

## Context

ntccpay needs a build tool for its Java 25 / Spring Boot 4 services. The two
credible candidates are Maven and Gradle. The project's stated purpose is
learning and interview showcase for modern cloud development, with correctness
of the money path as the priority - not build engineering.

## Decision

Use Maven for all services in this repository.

## Reasons

1. Universality: Maven is the default in enterprise Java, especially in
   payments/banking; interviewers expect pom.xml fluency.
2. Convention over configuration: the Spring Boot parent POM supplies dependency
   management, plugin defaults, and lifecycle with no build-script code - fewer
   moving parts while the domain is the hard part.
3. No second language: Gradle's Groovy/Kotlin DSL is an extra API to master;
   the learning budget goes to Java 25, Spring, DDD, Cucumber.
4. Documentation gravity: Spring docs and community answers are Maven-first.
5. Showcase readability: a pom.xml is legible to any Java reviewer in seconds.

Trade-offs accepted: Gradle's incremental build/cache speed advantages are
irrelevant at this codebase size, and its multi-module/composite-build powers
are unused. Revisit if the repo grows into a large multi-module monorepo.

## Consequences

- All services use Maven; do not mix build tools in this repository.
- If Gradle skills are wanted later, do it as a deliberate, whole-repo
  migration (cheapest while the repo is small) or in a separate repository.
- Standard lifecycle: `mvn test`, `mvn spring-boot:run`; add the Maven wrapper
  (`mvn wrapper:wrapper`) when CI lands in Phase 8 so builds pin the Maven version.
