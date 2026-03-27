package com.mplayeraudio.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import java.net.URI
import java.net.URISyntaxException
import java.util.Properties

internal object AndroidSdk {
    const val compile = 36
    const val min = 26
    const val target = 36
}

internal object YandexAuthDefaults {
    const val redirectUri = "mplayeraudio://oauth/yandex"
}

internal fun Project.multiplayerNamespace(): String {
    val suffix = path
        .split(":")
        .filter(String::isNotBlank)
        .joinToString(separator = ".") { segment -> segment.replace(Regex("[^A-Za-z0-9_]"), "") }

    return "com.mplayeraudio.$suffix"
}

internal fun Project.configureKotlinJvm() {
    extensions.configure<KotlinJvmProjectExtension> {
        jvmToolchain(17)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}

internal fun Project.configureYandexAuthBuild(applicationExtension: ApplicationExtension) {
    val localProperties = Properties().apply {
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use(::load)
        }
    }

    val yandexClientId = localProperties.getProperty("YANDEX_CLIENT_ID", "")
    val yandexClientSecret = localProperties.getProperty("YANDEX_CLIENT_SECRET", "")
    val yandexRedirectUri = localProperties.getProperty(
        "YANDEX_REDIRECT_URI",
        YandexAuthDefaults.redirectUri,
    )

    val redirectComponents = try {
        URI(yandexRedirectUri)
    } catch (exception: URISyntaxException) {
        throw GradleException(
            "Yandex redirect URI in build logic must be a valid URI.",
            exception,
        )
    }

    val yandexRedirectScheme = redirectComponents.scheme ?: "multiplayer"
    val yandexRedirectHost = redirectComponents.host ?: "oauth"
    val yandexRedirectPath = redirectComponents.path.ifBlank { "/yandex" }

    applicationExtension.defaultConfig {
        buildConfigField(
            "String",
            "YANDEX_CLIENT_ID",
            "\"${yandexClientId.escapeForBuildConfig()}\""
        )
        buildConfigField(
            "String",
            "YANDEX_CLIENT_SECRET",
            "\"${yandexClientSecret.escapeForBuildConfig()}\""
        )
        buildConfigField(
            "String",
            "YANDEX_REDIRECT_URI",
            "\"${yandexRedirectUri.escapeForBuildConfig()}\""
        )

        manifestPlaceholders["yandexAuthScheme"] = yandexRedirectScheme
        manifestPlaceholders["yandexAuthHost"] = yandexRedirectHost
        manifestPlaceholders["yandexAuthPath"] = yandexRedirectPath
    }
}

private fun String.escapeForBuildConfig(): String {
    return replace("\\", "\\\\")
        .replace("\"", "\\\"")
}

internal val java17: JavaVersion = JavaVersion.VERSION_17
