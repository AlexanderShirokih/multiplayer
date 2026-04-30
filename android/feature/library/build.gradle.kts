plugins {
    id("multiplayer.android.library")
    id("multiplayer.android.compose")
    alias(libs.plugins.kotlin.compose)
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.ui)
    implementation(projects.core.player)
    implementation(projects.services.deviceMusic)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(platform(libs.koin.bom))
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
