plugins {
    kotlin("jvm")
    id("app.cash.sqldelight")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":sync-core"))
    implementation("app.cash.sqldelight:sqlite-driver:2.0.0")
    implementation("app.cash.sqldelight:jdbc-driver:2.0.0")
}

sqldelight {
    databases {
        create("SyncDatabase") {
            packageName.set("com.gelov.sync.db")
            srcDirs.setFrom("src/main/sqldelight")
        }
    }
}

tasks.matching { it.name.startsWith("verify") && it.name.endsWith("Migration") }.configureEach {
    enabled = false
}