group = "org.jetbrains.research.code-summarization-dataset"
version = "0.0"

plugins {
    application
    kotlin("jvm") version "1.4.10"
    id("io.gitlab.arturbosch.detekt") version "1.14.2"
}

subprojects {
    apply {
        plugin("application")
        plugin("org.jetbrains.kotlin.jvm")
        plugin("io.gitlab.arturbosch.detekt")
    }

    repositories {
        jcenter()
        mavenCentral()
    }

    dependencies {
        testImplementation(kotlin("test-junit"))
        // reflect
        implementation("org.jetbrains.kotlin", "kotlin-reflect", "1.4.10")
        // json
        implementation("com.fasterxml.jackson.module", "jackson-module-kotlin", "2.11.3")
    }
}

application {
    mainClassName = "MainKt"
}

repositories {
    jcenter()
    mavenCentral()

    // astminer repo
    maven(url = "https://dl.bintray.com/egor-bogomolov/astminer/")
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
