// Auth Context application layer - use cases and ports.
// Depends only on the domain (api, so its port signatures expose domain types
// to adapters). Framework-free: bean wiring happens in the infrastructure
// composition root; the only external API allowed here is slf4j for logging.
plugins {
    `java-library`
}

description = "Auth Context use cases and ports (in/out)"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    api(project(":services:auth-api:auth-domain"))

    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.1"))
    implementation("org.slf4j:slf4j-api")

    // Test-only dependencies: plain unit tests over an in-memory repository.
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
