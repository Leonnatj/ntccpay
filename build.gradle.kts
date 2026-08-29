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

    // Cucumber BOM aligns all io.cucumber.* versions
    testImplementation(platform("io.cucumber:cucumber-bom:$cucumberVersion"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-webmvc-test-autoconfigure")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("io.cucumber:cucumber-java")
    testImplementation("io.cucumber:cucumber-junit-platform-engine")
    testImplementation("org.junit.platform:junit-platform-suite")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
