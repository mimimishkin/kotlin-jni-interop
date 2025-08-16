@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier.*
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.HostManager.Companion.hostIsLinux

plugins {
    id("convention.native64bit-library")
    alias(libs.plugins.dokka)
    id("convention.publish")
}

description = "JNI bingdings for Kotlin Native"

kotlin {
    targets.withType<KotlinNativeTarget> {
        compilations.all {
            cinterops.create("jni") {
                packageName = "io.github.mimimishkin.jni.internal.raw"
            }
        }
    }

    jvmToolchain(17)

    if (hostIsLinux) {
        // Cinterop generates enormous paths, so the build fails. Therefore, set the build directory to a short location
        layout.buildDirectory = file("/tmp/12345")
    }

    sourceSets {
        nativeMain.dependencies {
            implementation(project(":jni-binding-annotations"))
        }
    }
}

dokka {
    dokkaSourceSets.configureEach {
        includes.from("Module.md")

        sourceLink {
            localDirectory = rootDir
            remoteUrl = uri("https://github.com/mimimishkin/${rootProject.name}/tree/master")
        }

        // Dokka does not include the Cinterop package.
        // So we create a file with a stub and include it in docs to at least document this package.
        perPackageOption {
            matchingRegex = ".*\\.internal\\.raw"
            documentedVisibilities = setOf(Public, Private)
        }
    }
}

rootProject.dependencies {
    dokka(project)
}