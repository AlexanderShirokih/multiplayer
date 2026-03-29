import java.util.Properties

plugins {
    id("multiplayer.android.library")
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}

val yandexMusicStreamingSecret = localProperties.getProperty("YANDEX_MUSIC_STREAMING_SECRET", "")
val yandexMusicStreamingClient = localProperties.getProperty(
    "YANDEX_MUSIC_STREAMING_CLIENT",
    "YandexMusicAndroid/24022571",
)
val yandexMusicFileInfoSecret = localProperties.getProperty("YANDEX_MUSIC_FILE_INFO_SECRET", "")
val yandexMusicFileInfoClient = localProperties.getProperty(
    "YANDEX_MUSIC_FILE_INFO_CLIENT",
    "YandexMusicDesktopAppWindows/5.13.2",
)

android {
    namespace = "com.mplayeraudio.services.yandex"

    buildFeatures {
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    defaultConfig {
        buildConfigField(
            "String",
            "YANDEX_MUSIC_STREAMING_SECRET",
            "\"${yandexMusicStreamingSecret.escapeForBuildConfig()}\"",
        )
        buildConfigField(
            "String",
            "YANDEX_MUSIC_STREAMING_CLIENT",
            "\"${yandexMusicStreamingClient.escapeForBuildConfig()}\"",
        )
        buildConfigField(
            "String",
            "YANDEX_MUSIC_FILE_INFO_SECRET",
            "\"${yandexMusicFileInfoSecret.escapeForBuildConfig()}\"",
        )
        buildConfigField(
            "String",
            "YANDEX_MUSIC_FILE_INFO_CLIENT",
            "\"${yandexMusicFileInfoClient.escapeForBuildConfig()}\"",
        )
    }
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.domain)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

private fun String.escapeForBuildConfig(): String {
    return replace("\\", "\\\\")
        .replace("\"", "\\\"")
}
