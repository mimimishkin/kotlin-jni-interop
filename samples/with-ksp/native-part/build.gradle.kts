@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.apache.tools.ant.taskdefs.condition.Os
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.konan.target.HostManager.Companion.hostIsMac
import org.jetbrains.kotlin.konan.target.HostManager.Companion.hostIsMingw
import org.jetbrains.kotlin.konan.target.HostManager.Companion.hostIsLinux

plugins {
    kotlin("multiplatform")
    id("com.google.devtools.ksp") version "2.2.0-2.0.2"
}

val isX64 = Os.isArch("amd64") || Os.isArch("x86_64")

kotlin {
    val javaHome = System.getProperty("java.home")
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
            implementation("io.github.mimimishkin:jni-binding-annotations:1.0.2")
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

dependencies {
    if (hostIsMingw) "kspMingwX64"("io.github.mimimishkin:jni-binding-provider:1.0.2")
    if (isX64 && hostIsLinux) "kspLinuxX64"("io.github.mimimishkin:jni-binding-provider:1.0.2")
    if (!isX64 && hostIsLinux) "kspLinuxArm64"("io.github.mimimishkin:jni-binding-provider:1.0.2")
    if (isX64 && hostIsMac) "kspMacosX64"("io.github.mimimishkin:jni-binding-provider:1.0.2")
    if (!isX64 && hostIsMac) "kspMacosArm64"("io.github.mimimishkin:jni-binding-provider:1.0.2")
}

ksp {
    arg("jniBinding.interopConfig", "FromJniBinding")
    arg("jniBinding.useOnLoad", "false")
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