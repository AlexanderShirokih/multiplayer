plugins {
    `kotlin-dsl`
}

group = "com.multiplayer.buildlogic"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.detekt.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "multiplayer.android.application"
            implementationClass = "com.multiplayer.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "multiplayer.android.library"
            implementationClass = "com.multiplayer.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "multiplayer.android.compose"
            implementationClass = "com.multiplayer.buildlogic.AndroidComposeConventionPlugin"
        }
        register("kotlinLibrary") {
            id = "multiplayer.kotlin.library"
            implementationClass = "com.multiplayer.buildlogic.KotlinLibraryConventionPlugin"
        }
        register("detekt") {
            id = "multiplayer.detekt"
            implementationClass = "com.multiplayer.buildlogic.DetektConventionPlugin"
        }
    }
}
