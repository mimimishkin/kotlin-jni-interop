@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.apache.tools.ant.taskdefs.condition.Os
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

plugins {
    kotlin("multiplatform")
}

kotlin {
    val javaHome = System.getProperty("java.home")
    val isMac = Os.isFamily(Os.FAMILY_MAC)
    val isWindows = Os.isFamily(Os.FAMILY_WINDOWS)
    val isLinux = !isMac && Os.isFamily(Os.FAMILY_UNIX)
    listOfNotNull(
        if (isWindows) mingwX64() else null,
        if (isMac) macosX64() else null,
        if (isMac) macosArm64() else null,
        if (isLinux) linuxX64() else null,
        if (isLinux) linuxArm64() else null
    ).forEach { target ->
        target.binaries {
            sharedLib {
                baseName = "computation"

                if (isWindows) {
                    linkerOpts("-L$javaHome/lib", "-ljvm")
                } else {
                    linkerOpts("-L$javaHome/lib/server", "-ljvm")
                }
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation("io.github.mimimishkin:jni-binding:1.0-SNAPSHOT")
        }

        all {
            languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
            languageSettings.optIn("kotlin.experimental.ExperimentalNativeApi")
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}