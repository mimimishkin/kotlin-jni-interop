package io.github.mimimishkin.jni.binding.plugin.producer

import org.gradle.api.JavaVersion
import org.gradle.kotlin.dsl.the
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBinary
import org.jetbrains.kotlin.konan.target.HostManager.Companion.hostIsMingw
import java.io.File

/**
 * Adds JVM native libraries to the linker options after project evaluation.
 */
public fun NativeBinary.linkJVM() {
    val extension = project.the<JniLibProducerExtension>()
    val lib = extension.javaHome.zip(extension.jniVersion) { home, version ->
        checkJavaVersion(home.asFile, version)
        home.dir("lib")
    }

    project.afterEvaluate {
        val lib = lib.get().asFile.absolutePath
        val javaOptions = if (hostIsMingw) {
            listOf("-L", lib, "-l", "jvm", "-l", "jawt")
        } else {
            listOf("-L", "$lib/server", "-l", "jvm", "-L", lib, "-l", "jawt")
        }

        linkerOpts(javaOptions)
    }
}

/**
 * Checks that the Java installation at [javaHome] is not older than [minVersion].
 *
 * @throws IllegalArgumentException if the installed Java version is lower than [minVersion].
 */
private fun checkJavaVersion(javaHome: File, minVersion: Int) {
    val releaseFile = File(javaHome, "release")
    val regex = Regex("JAVA_VERSION=\"([^\"]+)\"")
    val version = regex.find(releaseFile.readText())!!.groupValues[1]
    val versionInt = JavaVersion.toVersion(version).majorVersion.toInt()
    if (versionInt < minVersion) {
        throw IllegalArgumentException("Version of the provided Java is too low: $versionInt, while minimum version is $minVersion")
    }
}