pluginManagement {
    includeBuild("build-logic")

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
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
include(":services:yandex")
