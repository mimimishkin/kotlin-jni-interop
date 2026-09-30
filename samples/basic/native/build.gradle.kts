import io.github.mimimishkin.jni.binding.plugin.producer.JniExportMethod
import io.github.mimimishkin.jni.binding.plugin.producer.linkJvm
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    kotlin("multiplatform") version "2.4.20"
    id("io.github.mimimishkin.jni-binding-producer") version "1.0.2"
}

kotlin {
    jvmToolchain(17)

    listOf(
        mingwX64(),
        linuxX64(),
        linuxArm64(),
        macosArm64()
    ).forEach {
        it.binaries {
            sharedLib("native") {
                if (it.konanTarget == HostManager.host) {
                    // The host has a JDK of its own, so a toolchain of the right version is enough.
                    linkJvm()
                } else {
                    // Gradle serves toolchains for the host only, so a target that is not the host needs a
                    // JDK built for it, of the Java version `jniLibraries.jniVersion` declares.
                    linkJvm(downloadCompatibleJdk())
                }
            }
        }
    }

    sourceSets.all {
        languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
        languageSettings.optIn("kotlin.experimental.ExperimentalNativeApi")
    }
}

jniLibraries {
    jniVersion = 17
    exportMethod = JniExportMethod.RegisterNatives
    allowSeveralHooks = true
}