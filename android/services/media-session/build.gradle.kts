plugins {
    id("multiplayer.android.library")
}

android {
    namespace = "com.mplayeraudio.services.mediasession"
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.player)
    implementation(projects.services.kithara)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.session)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}
