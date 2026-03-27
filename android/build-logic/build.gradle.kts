plugins {
    `kotlin-dsl`
}

group = "com.mplayeraudio.buildlogic"

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
            implementationClass = "com.mplayeraudio.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "multiplayer.android.library"
            implementationClass = "com.mplayeraudio.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "multiplayer.android.compose"
            implementationClass = "com.mplayeraudio.buildlogic.AndroidComposeConventionPlugin"
        }
        register("kotlinLibrary") {
            id = "multiplayer.kotlin.library"
            implementationClass = "com.mplayeraudio.buildlogic.KotlinLibraryConventionPlugin"
        }
        register("detekt") {
            id = "multiplayer.detekt"
            implementationClass = "com.mplayeraudio.buildlogic.DetektConventionPlugin"
        }
    }
}
