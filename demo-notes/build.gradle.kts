plugins {
    kotlin("jvm")
    id("app.cash.sqldelight")
    application
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

