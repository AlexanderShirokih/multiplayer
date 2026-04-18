plugins {
    id("multiplayer.android.library")
}

android {
    namespace = "com.mplayeraudio.services.kithara"
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.domain)
    implementation(projects.core.player)
    compileOnly(files("${rootProject.projectDir}/libs/kithara.aar"))
    compileOnly(files("${rootProject.projectDir}/libs/rust-tls.aar"))
    compileOnly("net.java.dev.jna:jna:${libs.versions.jna.get()}@aar")
    implementation(libs.kotlinx.coroutines.core)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
