plugins {
    kotlin("jvm") version "2.1.21" apply false
    id("app.cash.sqldelight") version "2.0.0" apply false
}

allprojects {
    repositories {
        mavenCentral()
        google()
    }
}