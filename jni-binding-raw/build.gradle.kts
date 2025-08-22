@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.HostManager.Companion.hostIsLinux

plugins {
    id("convention.native64bit-library")
    alias(libs.plugins.dokka)
    id("convention.publish")
}

description = "Cinterop output for jni.h and related headers"

kotlin {
    targets.withType<KotlinNativeTarget> {
        compilations.all {
            cinterops.create("jni")
        }
    }

    if (hostIsLinux) {
        // Cinterop generates enormous paths, so the build fails. Therefore, set the build directory to a short location
        layout.buildDirectory = file("/tmp/12345")
    }
}

dokka {
    dokkaSourceSets.configureEach {
        includes.from("Module.md")
    }
}

rootProject.dependencies {
    dokka(project)
}