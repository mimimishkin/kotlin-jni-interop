import io.github.mimimishkin.jni.binding.plugin.producer.JniExportMethod

plugins {
    kotlin("multiplatform") version "2.4.20"
    id("io.github.mimimishkin.jni-binding-producer") version "2.1.0"
}

kotlin {
    listOf(
        androidNativeArm32(),
        androidNativeArm64(),
        androidNativeX86(),
        androidNativeX64(),
    ).forEach {
        it.binaries.sharedLib("example") {
            // A device with a 16 KB page size can only load a library whose ELF LOAD segments are aligned to 16 KB,
            // which is what Android 15+ requires of every app submitted to Google Play. `arm64` already gets it from
            // the Kotlin/Native linker; the other three default to 4 KB, so they need the page size raised. A library
            // aligned to 16 KB still loads on a 4 KB device, which is why this can be set unconditionally.
            if (target.name != "androidNativeArm64") {
                linkerOpts(
                    "-Wl,-z,max-page-size=16384",
                    "-Wl,-z,common-page-size=16384",
                )
            }
        }
    }

    sourceSets.all {
        languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
        languageSettings.optIn("kotlin.experimental.ExperimentalNativeApi")
        languageSettings.optIn("kotlin.concurrent.atomics.ExperimentalAtomicApi")
    }
}

jniLibraries {
    exportMethod = JniExportMethod.RegisterNatives
    allowSeveralHooks = true
}