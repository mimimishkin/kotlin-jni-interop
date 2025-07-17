@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("convention.native64bit-library")
    id("convention.android-library")
    id("convention.publish")
}

description = "Annotations for kotlin-jni-interop project"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_1_1
}

kotlin {
    jvm()

    applyDefaultHierarchyTemplate {
        common {
            group("jvmLike") {
                withAndroidTarget()
                withJvm()
            }
        }
    }
}