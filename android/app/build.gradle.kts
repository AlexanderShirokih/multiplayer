plugins {
    id("multiplayer.android.application")
    id("multiplayer.android.compose")
    alias(libs.plugins.kotlin.compose)
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
}
