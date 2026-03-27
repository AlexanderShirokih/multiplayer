import java.net.URI
import java.util.Properties

plugins {
    id("multiplayer.android.application")
    id("multiplayer.android.compose")
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}

fun escapedBuildConfigValue(value: String): String = value
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

fun localProperty(name: String, defaultValue: String): String {
    return localProperties.getProperty(name, defaultValue)
}

val yandexClientId = localProperty("YANDEX_CLIENT_ID", "")
val yandexClientSecret = localProperty("YANDEX_CLIENT_SECRET", "")
val yandexRedirectUri = localProperty("YANDEX_REDIRECT_URI", "multiplayer://oauth/yandex")
val yandexRedirectComponents = URI(yandexRedirectUri)
val yandexRedirectScheme = yandexRedirectComponents.scheme ?: "multiplayer"
val yandexRedirectHost = yandexRedirectComponents.host ?: "oauth"
val yandexRedirectPath = yandexRedirectComponents.path.ifBlank { "/yandex" }

android {
    defaultConfig {
        buildConfigField("String", "YANDEX_CLIENT_ID", "\"${escapedBuildConfigValue(yandexClientId)}\"")
        buildConfigField("String", "YANDEX_CLIENT_SECRET", "\"${escapedBuildConfigValue(yandexClientSecret)}\"")
        buildConfigField("String", "YANDEX_REDIRECT_URI", "\"${escapedBuildConfigValue(yandexRedirectUri)}\"")

        manifestPlaceholders["yandexAuthScheme"] = yandexRedirectScheme
        manifestPlaceholders["yandexAuthHost"] = yandexRedirectHost
        manifestPlaceholders["yandexAuthPath"] = yandexRedirectPath
    }
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.domain)
    implementation(projects.core.player)
    implementation(projects.core.ui)
    implementation(projects.feature.auth)
    implementation(projects.feature.library)
    implementation(projects.feature.player)
    implementation(projects.feature.search)
    implementation(projects.services.yandex)
    implementation(projects.services.yandexAuth)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)

    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
