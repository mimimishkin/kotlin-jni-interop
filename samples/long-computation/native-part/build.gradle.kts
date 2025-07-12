@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.apache.tools.ant.taskdefs.condition.Os
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

plugins {
    kotlin("multiplatform")
}

kotlin {
    val javaHome = System.getProperty("java.home")
    val isX64 = Os.isArch("amd64") || Os.isArch("x86_64")
    val isMac = Os.isFamily(Os.FAMILY_MAC)
    val isWindows = Os.isFamily(Os.FAMILY_WINDOWS)
    val isLinux = !isMac && Os.isFamily(Os.FAMILY_UNIX)
    listOfNotNull(
        if (isWindows) mingwX64() else null,
        if (isX64 && isMac) macosX64() else null,
        if (!isX64 && isMac) macosArm64() else null,
        if (isX64 && isLinux) linuxX64() else null,
        if (!isX64 && isLinux) linuxArm64() else null
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
            implementation("io.github.mimimishkin:jni-binding:1.0.1")
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

tasks.register<Copy>("copyJniLib") {
    val bins = layout.buildDirectory.dir("bin")
    from(
        fileTree(bins) {
            include("**/release*/*.so", "**/release*/*.dll", "**/release*/*.dylib")
        }.files
    )
    into("../src/main/resources")
}

tasks.named("build") {
    finalizedBy("copyJniLib")
}