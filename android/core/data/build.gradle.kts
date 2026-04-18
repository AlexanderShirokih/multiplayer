plugins {
    id("multiplayer.android.library")
    id("multiplayer.android.compose")
    alias(libs.plugins.kotlin.compose)
}

dependencies {
    implementation(projects.core.domain)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
