plugins {
    id("multiplayer.android.library")
    id("multiplayer.android.compose")
    alias(libs.plugins.kotlin.compose)
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.ui)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
