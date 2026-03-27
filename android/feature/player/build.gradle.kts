plugins {
    id("multiplayer.android.library")
    id("multiplayer.android.compose")
    alias(libs.plugins.kotlin.compose)
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.player)
    implementation(projects.core.ui)
}
