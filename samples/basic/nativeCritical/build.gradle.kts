import io.github.mimimishkin.jni.binding.plugin.producer.JniExportMethod
import io.github.mimimishkin.jni.binding.plugin.producer.downloadCompatibleJdk
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
            sharedLib("critical") {
                if (it.konanTarget == HostManager.host) {
                    linkJvm()
                } else {
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
    expectedJdkVersion = 17

    // A `@CriticalNative` is bound by symbol name, so it needs `ExposeFunctions`. `RegisterNatives`
    // cannot express a critical entry point on desktop.
    exportMethod = JniExportMethod.ExposeFunctions
    allowSeveralHooks = true
}