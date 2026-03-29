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
    implementation(projects.services.kithara)
    implementation(projects.services.yandex)
    implementation(projects.services.yandexAuth)
    implementation(files("${rootProject.projectDir}/libs/kithara.aar"))
    implementation(files("${rootProject.projectDir}/libs/rust-tls.aar"))
    implementation("net.java.dev.jna:jna:${libs.versions.jna.get()}@aar")
    implementation(libs.kotlinx.coroutines.core)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
