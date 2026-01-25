plugins {
    kotlin("jvm")
    application
    id("org.jetbrains.kotlin.plugin.serialization")
    id("app.cash.sqldelight")
}

kotlin {
    jvmToolchain(17)
}

sqldelight {
    databases {
        create("ServerStoreDatabase") {
            packageName.set("com.gelov.server.store")
        }
    }
}

dependencies {
    val ktorVersion = "2.3.13"

    constraints {
        implementation("io.netty:netty-codec-http:4.1.114.Final")
        implementation("io.netty:netty-handler:4.1.114.Final")
        implementation("io.netty:netty-common:4.1.114.Final")
        implementation("ch.qos.logback:logback-core:1.5.13")
    }

    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")

    implementation("ch.qos.logback:logback-classic:1.5.13")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

    implementation(project(":sync-core"))

    implementation("app.cash.sqldelight:sqlite-driver:2.0.2")
    implementation("app.cash.sqldelight:jdbc-driver:2.0.2")
}

application {
    mainClass.set("com.gelov.server.ServerMainKt")
}