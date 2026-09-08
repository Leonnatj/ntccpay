// Boot / composition root: the thinnest possible module. It owns the main
// class, application.yml, Flyway migrations and the Spring integration tests;
// every layer lives in its own Gradle module (auth-domain, auth-application,
// auth-infrastructure) so the compiler enforces the dependency direction.
plugins {
    java
    id("org.springframework.boot")
}

description = "Card payment authorization API - the synchronous critical path"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

val cucumberVersion = "7.34.7"

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.1"))

    // Layers (infrastructure exposes application+domain via its api dependency)
    implementation(project(":services:auth-api:auth-infrastructure"))

    // The main class needs Spring Boot annotations directly (implementation
    // dependencies of the infrastructure module are not on this compile classpath)
    implementation("org.springframework.boot:spring-boot")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework:spring-context")

    // Testcontainers 2.x (Phase 2): repository tests run against a real Postgres
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-kafka")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    // Boot 4 slice-test modules (the @DataJpaTest / @AutoConfigureTestDatabase annotations)
    testImplementation("org.springframework.boot:spring-boot-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-jdbc-test")

    // Cucumber BOM aligns all io.cucumber.* versions
    testImplementation(platform("io.cucumber:cucumber-bom:$cucumberVersion"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")
    testImplementation("jakarta.servlet:jakarta.servlet-api")
    testImplementation("org.springframework.security:spring-security-test")
    // The outbox integration test asserts the Kafka payload directly
    testImplementation("org.springframework.kafka:spring-kafka")
    testImplementation("com.fasterxml.jackson.core:jackson-databind")
    testImplementation("io.cucumber:cucumber-java")
    testImplementation("io.cucumber:cucumber-junit-platform-engine")
    testImplementation("org.junit.platform:junit-platform-suite")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// only the executable boot jar goes into build/libs (the Dockerfile copies build/libs/*.jar)
tasks.jar { enabled = false }

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}