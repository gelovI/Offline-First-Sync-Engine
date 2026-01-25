pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "offline-first-sync"

include(
    ":sync-core",
    ":sync-sqldelight",
    ":demo-notes",
    ":server-http"
)

include("server-http")