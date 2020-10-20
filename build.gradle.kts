group = "org.jetbrains.research.code-summarization-dataset"
version = "0.0"

plugins {
    kotlin("jvm") version "1.4.10"
    application
}

application {
    mainClassName = "MainKt"
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test-junit"))
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
}