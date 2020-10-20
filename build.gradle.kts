group = "org.jetbrains.research.code-summarization-dataset"
version = "0.0"

plugins {
    application
    kotlin("jvm") version "1.4.10"
    id("io.gitlab.arturbosch.detekt") version "1.14.2"
}

application {
    mainClassName = "MainKt"
}

repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test-junit"))
}

detekt {
    failFast = true // fail build on any finding
    buildUponDefaultConfig = true // preconfigure defaults
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
}