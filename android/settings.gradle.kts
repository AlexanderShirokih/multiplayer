pluginManagement {
    includeBuild("build-logic")

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "multiplayer-android"

include(":app")
include(":core:domain")
include(":core:data")
include(":core:player")
include(":core:ui")
include(":feature:auth")
include(":feature:library")
include(":feature:player")
include(":feature:search")
include(":services:kithara")
include(":services:media-session")
include(":services:device-music")
include(":services:yandex")
include(":services:yandex-auth")
