import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

group = "org.jetbrains.research.code-summarization-dataset"
version = "0.0"

plugins {
    application
    kotlin("jvm") version "1.4.21"
    id("io.gitlab.arturbosch.detekt") version "1.14.2"
    id("com.github.johnrengelman.shadow") version "5.2.0"
}

application {
    mainClassName = "MainKt"
}

tasks.withType<ShadowJar>() {
    manifest {
        attributes["Main-Class"] = "MainKt"
    }
    archiveFileName.set("cs_dataset.jar")
}

repositories {
    jcenter()
    mavenCentral()

    // astminer repo
    maven(url = uri("https://packages.jetbrains.team/maven/p/astminer/astminer"))
}

dependencies {
    testImplementation(kotlin("test-junit"))
    // reflect
    implementation("org.jetbrains.kotlin", "kotlin-reflect", "1.4.20")
    // co
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-core", "1.4.2")
    // astminer
    implementation("io.github.vovak", "astminer", "0.6.3")
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

    implementation("org.slf4j", "slf4j-nop", "1.7.30")
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
