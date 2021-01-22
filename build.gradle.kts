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

    // astminer repo
    maven(url = "https://dl.bintray.com/egor-bogomolov/astminer/")
}

dependencies {
    testImplementation(kotlin("test-junit"))
    // reflect
    implementation("org.jetbrains.kotlin", "kotlin-reflect", "1.4.10")
    // astminer
    implementation("io.github.vovak.astminer", "astminer", "0.6")
    // fuel - requests
    implementation("com.github.kittinunf.fuel", "fuel", "2.3.0")
    // clikt - cli
    implementation("com.github.ajalt.clikt", "clikt", "3.0.1")
    // jgit - git
    implementation("org.eclipse.jgit", "org.eclipse.jgit", "5.9.0.202009080501-r")
    // fileutils
    implementation("commons-io", "commons-io", "2.8.0")
    // gzip
    implementation("org.apache.commons", "commons-compress", "1.20")
    // json
    implementation("com.fasterxml.jackson.module", "jackson-module-kotlin", "2.11.3")
}

detekt {
    failFast = true // fail build on any finding
    config = files("detekt.yml")
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
}
