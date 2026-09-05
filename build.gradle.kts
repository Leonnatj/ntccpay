// ntccpay umbrella build (ADR 0004). Groups independent service subprojects
// under services/; the root only pins plugin versions and shared conventions.
// Application code and dependencies live in each subproject's own build file.

plugins {
    id("org.springframework.boot") version "4.1.1" apply false
}

allprojects {
    group = "com.ntccpay"
    version = "0.1.0-SNAPSHOT"
    repositories {
        mavenCentral()
    }
}