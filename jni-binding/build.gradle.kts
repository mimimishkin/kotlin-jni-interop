@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("convention.native64bit-library")
    alias(libs.plugins.dokka)
    id("convention.publish")
}

description = "JNI bingdings for Kotlin Native"

kotlin {
    applyHierarchyTemplate {
        common {
            group("native") {
                group("intermideate") {
                    group("mingw") {
                        withMingwX64()
                    }
                    group("linux") {
                        withLinuxX64()
                        withLinuxArm64()
                    }
                    group("macos") {
                        withMacosX64()
                        withMacosArm64()
                    }
                }
            }
        }
    }

    sourceSets {
        nativeMain.dependencies {
            implementation(projects.jniBindingRaw)
            implementation(projects.jniBindingAnnotations)
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
    }
}