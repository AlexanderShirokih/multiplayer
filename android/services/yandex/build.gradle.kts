plugins {
    id("multiplayer.android.library")
}

android {
    namespace = "com.mplayeraudio.services.yandex"
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
