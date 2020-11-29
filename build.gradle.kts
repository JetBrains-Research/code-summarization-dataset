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
    implementation("org.jetbrains.kotlin", "kotlin-reflect", "1.4.10")

    // fuel
    implementation(group = "com.github.kittinunf.fuel", name = "fuel", version = "2.3.0")
    // astminer
    implementation("io.github.vovak.astminer", "astminer", "0.6")
    // dependency astminer
    implementation("com.github.ajalt.clikt", "clikt", "3.0.1")
    // fuel
    implementation("com.github.kittinunf.fuel", "fuel", "2.3.0")
    // json
    implementation("com.fasterxml.jackson.module", "jackson-module-kotlin", "2.11.3")
    // jgit
    implementation("org.eclipse.jgit", "org.eclipse.jgit", "5.9.0.202009080501-r")
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
