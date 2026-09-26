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
            if (it.konanTarget == HostManager.host) sharedLib("native") {
                linkJvm()
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