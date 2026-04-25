plugins {
    id("multiplayer.android.library")
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.mplayeraudio.services.userplaylists"

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.domain)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
