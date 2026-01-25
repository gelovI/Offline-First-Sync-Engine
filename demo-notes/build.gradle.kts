plugins {
    kotlin("jvm")
    id("app.cash.sqldelight")
    application
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(project(":sync-core"))
    implementation(project(":sync-sqldelight"))

    implementation("app.cash.sqldelight:jdbc-driver:2.0.0")
    implementation("app.cash.sqldelight:sqlite-driver:2.0.0")

    val ktorVersion = "2.3.13"

    implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-cio-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("ch.qos.logback:logback-classic:1.5.12")
}

application {
    mainClass.set("com.gelov.demo.notes.MainKt")
}

sqldelight {
    databases {
        create("NotesDatabase") {
            packageName.set("com.gelov.demo.notes")
            srcDirs.setFrom("src/main/sqldelight")
        }
    }
}

tasks.matching { it.name.startsWith("verify") && it.name.endsWith("Migration") }.configureEach {
    enabled = false
}

tasks.named<JavaExec>("run") {
    // client
    systemProperty("client", System.getProperty("client") ?: "A")
    // flow
    systemProperty("flow", System.getProperty("flow") ?: "seedDelete")

    System.getProperty("id")?.let { systemProperty("id", it) }
    System.getProperty("title")?.let { systemProperty("title", it) }
    System.getProperty("text")?.let { systemProperty("text", it) }
    System.getProperty("doSync")?.let {
        systemProperty("doSync", it)
    }
    System.getProperty("fixedAt")?.let { systemProperty("fixedAt", it) }
}

