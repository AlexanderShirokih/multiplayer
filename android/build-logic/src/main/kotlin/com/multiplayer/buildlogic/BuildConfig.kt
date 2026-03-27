package com.multiplayer.buildlogic

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

internal object AndroidSdk {
    const val compile = 36
    const val min = 26
    const val target = 36
}

internal fun Project.applyMultiplayerDetektWhenKotlinPresent() {
    val applyOnce = {
        if (!pluginManager.hasPlugin("multiplayer.detekt")) {
            pluginManager.apply("multiplayer.detekt")
        }
    }
    pluginManager.withPlugin("org.jetbrains.kotlin.android") { applyOnce() }
    pluginManager.withPlugin("org.jetbrains.kotlin.plugin.compose") { applyOnce() }
}

internal fun Project.multiplayerNamespace(): String {
    val suffix = path
        .split(":")
        .filter(String::isNotBlank)
        .map { segment ->
            segment.replace(Regex("[^A-Za-z0-9_]"), "")
        }
        .joinToString(separator = ".")

    return "com.multiplayer.$suffix"
}

internal fun Project.configureKotlinJvm() {
    extensions.configure<KotlinJvmProjectExtension> {
        jvmToolchain(17)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}

internal fun Project.configureKotlinAndroid() {
    extensions.configure<KotlinAndroidProjectExtension> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}

internal val java17: JavaVersion = JavaVersion.VERSION_17
