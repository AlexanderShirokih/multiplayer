package com.mplayeraudio.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

class KotlinLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(JavaLibraryPlugin::class.java)
        pluginManager.apply("org.jetbrains.kotlin.jvm")

        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(17))
            }
        }

        configureKotlinJvm()

        dependencies {
            add("testImplementation", libs.findLibrary("junit").get())
        }

        pluginManager.apply("multiplayer.detekt")
    }
}
