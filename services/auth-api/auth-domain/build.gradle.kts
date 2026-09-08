// Auth Context domain - pure Java, ZERO framework dependencies.
// The compiler now enforces what the package layout only suggested: nothing in
// here can import Spring, JPA, Kafka, or any other adapter technology, because
// those jars are simply not on this module's compile classpath.
plugins {
    java
}

description = "Auth Context domain model, events, and rule engine"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    // Test-only dependencies: test frameworks never leak into main code.
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.1"))
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
