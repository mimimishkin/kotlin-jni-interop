@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.gradle.internal.extensions.stdlib.capitalized
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.konan.target.HostManager.Companion.hostIsLinux
import org.jetbrains.kotlin.konan.target.HostManager.Companion.hostIsMac
import org.jetbrains.kotlin.konan.target.HostManager.Companion.hostIsMingw

plugins {
    kotlin("multiplatform") version "2.2.0"
    id("com.vanniktech.maven.publish") version "0.33.0"
}

group = "io.github.mimimishkin"
version = "1.0.1"
description = "JNI bingdings for Kotlin Native"

kotlin {
    if (hostIsLinux) {
        // Cinterop generates enormous paths, so the build fails. Therefore, set the build directory to a short location
        layout.buildDirectory = file("/tmp/12345")
    }
    listOf(
        mingwX64(),
        macosX64(),
        macosArm64(),
        linuxX64(),
        linuxArm64(),
        androidNativeX64(),
        androidNativeArm64(),
    ).forEach { target ->
        target.compilations.all {
            cinterops.create("jni") {
                packageName = "io.github.mimimishkin.jni.internal.raw"
            }
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

afterEvaluate {
    for (repository in publishing.repositories) {
        val repositoryName = repository.name.capitalized()
        val supportedTasks = tasks.withType<PublishToMavenRepository>().filter { task ->
            repositoryName in task.name && when {
                "mingw" in task.publication.name -> hostIsMingw
                "macos" in task.publication.name -> hostIsMac
                else /* all others */ -> hostIsLinux
            }
        }

        tasks.register("publishAllSupportedPublicationsTo${repositoryName}Repository") {
            group = "publishing"
            description = "Publishes all supported by this host publications to the '${repository.name}' repository"
            dependsOn(supportedTasks)
        }
    }
}

mavenPublishing {
    publishToMavenCentral(false)

    signAllPublications()

    coordinates(groupId = group.toString(), artifactId = name, version = version.toString())

    pom {
        name = project.name
        description = project.description
        inceptionYear = "2025"
        url = "https://github.com/mimimishkin/${project.name}"
        licenses {
            license {
                name = "MIT"
            }
        }
        developers {
            developer {
                id = "mimimishkin"
                name = "mimimishkin"
                email = "printf.mika@gmail.com"
            }
        }
        scm {
            url = "https://github.com/mimimishkin/${project.name}"
            connection = "scm:git:git://github.com/mimimishkin/${project.name}"
        }
    }
}