plugins {
    java
    id("org.springframework.boot") version "4.1.1"
}

group = "com.ntccpay"
version = "0.1.0-SNAPSHOT"
description = "Card payment authorization API - the synchronous critical path"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

val cucumberVersion = "7.34.7"

dependencies {
    // Spring Boot BOM manages all org.springframework.* versions
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.1"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Persistence (Phase 2): Flyway owns the schema; Hibernate only validates it
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // Boot 4: Flyway integration is its own module — flyway-core alone does not activate it
    implementation("org.springframework.boot:spring-boot-flyway")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // Testcontainers 2.x (Phase 2): repository tests run against a real Postgres
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    // Boot 4 slice-test modules (the @DataJpaTest / @AutoConfigureTestDatabase annotations)
    testImplementation("org.springframework.boot:spring-boot-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-jdbc-test")

    // Cucumber BOM aligns all io.cucumber.* versions
    testImplementation(platform("io.cucumber:cucumber-bom:$cucumberVersion"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
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
