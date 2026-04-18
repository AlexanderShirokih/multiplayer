plugins {
    id("multiplayer.android.library")
}

android {
    namespace = "com.mplayeraudio.services.devicemusic"
}

dependencies {
    implementation(projects.core.domain)
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)

    testImplementation(libs.junit)
}
