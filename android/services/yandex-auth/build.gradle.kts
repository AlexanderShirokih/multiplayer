plugins {
    id("multiplayer.android.library")
}

android {
    namespace = "com.multiplayer.services.yandexauth"
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.domain)
    implementation(platform(libs.koin.bom))
    implementation(libs.androidx.security.crypto.ktx)
    implementation(libs.koin.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.core)

    testImplementation(libs.kotlinx.coroutines.test)
}
