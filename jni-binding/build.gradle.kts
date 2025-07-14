@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.HostManager.Companion.hostIsLinux

plugins {
    id("convention.multiplatform-library")
    id("convention.publish")
    id("convention.dokka")
}

group = "io.github.mimimishkin"
version = "1.0.1"
description = "JNI bingdings for Kotlin Native"

kotlin {
    targets.withType<KotlinNativeTarget> {
        compilations.all {
            cinterops.create("jni") {
                packageName = "io.github.mimimishkin.jni.internal.raw"
            }
        }
    }

    if (hostIsLinux) {
        // Cinterop generates enormous paths, so the build fails. Therefore, set the build directory to a short location
        layout.buildDirectory = file("/tmp/12345")
    }
}