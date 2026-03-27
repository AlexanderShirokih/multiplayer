package com.mplayeraudio.buildlogic

import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

class DetektConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("dev.detekt")
        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
        extensions.configure<DetektExtension> {
            config.from(rootProject.file("config/detekt/detekt.yml"))
            buildUponDefaultConfig.set(true)
            parallel.set(true)
        }
        dependencies {
            add("detektPlugins", libs.findLibrary("detekt-compose-rules").get())
        }
    }
}
