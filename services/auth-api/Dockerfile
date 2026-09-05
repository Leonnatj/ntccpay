# syntax=docker/dockerfile:1
# Build context is the REPOSITORY ROOT (see docker-compose.yml: build context ".")
# so every service build shares the single Gradle wrapper. Only this service's
# sources are copied into the build stage.

# ---- build stage: compile with the Gradle wrapper on a JDK 25 image ----
FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /workspace

# wrapper + build files first for layer caching: dependencies only
# re-download when the build definition changes, not on every src edit
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY services/auth-api/build.gradle.kts services/auth-api/build.gradle.kts
# the repo is checked out with CRLF line endings on Windows; the wrapper
# shell script cannot run with \r in its shebang and command lines
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
RUN ./gradlew --no-daemon :services:auth-api:dependencies > /dev/null 2>&1 || true

COPY services/auth-api/src ./services/auth-api/src
RUN ./gradlew --no-daemon :services:auth-api:bootJar

# ---- runtime stage: JRE only, non-root, minimal attack surface ----
FROM eclipse-temurin:25-jre-alpine AS runtime
RUN addgroup -S ntcc && adduser -S ntcc -G ntcc
WORKDIR /app
COPY --from=build /workspace/services/auth-api/build/libs/*.jar app.jar
USER ntcc
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --retries=12 \
  CMD wget -qO- http://localhost:8080/actuator/health >/dev/null 2>&1 || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]