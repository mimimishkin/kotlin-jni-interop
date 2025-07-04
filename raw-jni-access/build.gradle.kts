@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.apache.tools.ant.taskdefs.condition.Os
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    kotlin("multiplatform") version "2.2.0"
//    id("com.vanniktech.maven.publish") version "0.31.0"
}

group = "io.github.mimimishkin"
version = "1.0-SNAPSHOT"
description = "No-op/lightweight JNI bingdings for Kotlin Native"

kotlin {
    val isMac = Os.isFamily(Os.FAMILY_MAC)
    listOfNotNull(
        mingwX64(),
        linuxX64(),
        linuxArm64(),
        if (isMac) macosX64() else null,
        if (isMac) macosArm64() else null,
        androidNativeX64(),
        androidNativeArm64(),
    ).forEach {
        it.compilations.all {
            cinterops.create("raw_jni")
        }
    }

    explicitApi()

    sourceSets {
        all {
            languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
            languageSettings.optIn("kotlin.experimental.ExperimentalNativeApi")
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

//mavenPublishing {
//    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
//
//    signAllPublications()
//
//    coordinates(group.toString(), name, version.toString())
//
//    pom {
//        name = project.name
//        description = project.description
//        inceptionYear = "2025"
//        url = "https://github.com/mimimishkin/${project.name}"
//        licenses {
//            license {
//                name = "MIT"
//            }
//        }
//        developers {
//            developer {
//                id = "mimimishkin"
//                name = "mimimishkin"
//                email = "printf.mika@gmail.com"
//            }
//        }
//        scm {
//            url = "https://github.com/mimimishkin/${project.name}"
//            connection = "scm:git:git://github.com/mimimishkin/${project.name}"
//        }
//    }
//}