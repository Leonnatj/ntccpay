// Auth Context infrastructure - all framework adapters.
// Web controllers, JPA repositories, Kafka outbox relay, security filters and
// the Spring configuration that wires the application layer's ports. This is
// the ONLY layer that knows Spring, Hibernate, Flyway, or Kafka exist.
plugins {
    `java-library`
}

description = "Auth Context infrastructure adapters (web, persistence, outbox, security)"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    // api: the web layer calls use-case ports and controllers expose domain
    // types, so consumers (the boot module) need these at compile time too.
    api(project(":services:auth-api:auth-application"))

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

    // Phase 3: Kafka (KafkaTemplate, admin/NewTopic) + Jackson for the auths.v1 payload.
    // Boot 4: Kafka auto-configuration is its own module — spring-kafka alone does not activate it
    implementation("org.springframework.boot:spring-boot-kafka")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
}
