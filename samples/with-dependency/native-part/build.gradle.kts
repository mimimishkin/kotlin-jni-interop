@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.apache.tools.ant.taskdefs.condition.Os
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.konan.target.HostManager.Companion.hostIsMac
import org.jetbrains.kotlin.konan.target.HostManager.Companion.hostIsMingw
import org.jetbrains.kotlin.konan.target.HostManager.Companion.hostIsLinux

plugins {
    kotlin("multiplatform")
}

kotlin {
    val javaHome = System.getProperty("java.home")
    val isX64 = Os.isArch("amd64") || Os.isArch("x86_64")
    listOfNotNull(
        if (hostIsMingw) mingwX64() else null,
        if (isX64 && hostIsMac) macosX64() else null,
        if (!isX64 && hostIsMac) macosArm64() else null,
        if (isX64 && hostIsLinux) linuxX64() else null,
        if (!isX64 && hostIsLinux) linuxArm64() else null
    ).forEach { target ->
        target.binaries {
            sharedLib {
                baseName = "computation"

                if (hostIsMingw) {
                    linkerOpts("-L$javaHome/lib", "-ljvm")
                } else {
                    linkerOpts("-L$javaHome/lib/server", "-ljvm")
                }
            }
        }
    }

    sourceSets {
        nativeMain.dependencies {
            implementation("io.github.mimimishkin:jni-binding:1.0.2")
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
    val files = fileTree(layout.buildDirectory.dir("bin")) {
        include("**/release*/*.so", "**/release*/*.dll", "**/release*/*.dylib")
    }
    from(files.files) // inline file hierarchy
    into("../src/main/resources")
}

tasks.named("build") {
    finalizedBy("copyJniLib")
}

tasks.named("clean") {
    doFirst {
        delete("../src/main/resources")
    }
}