import io.github.mimimishkin.jni.binding.plugin.producer.JniExportMethod

plugins {
    kotlin("multiplatform") version "2.4.20"
    id("io.github.mimimishkin.jni-binding-producer") version "1.0.2"
}

kotlin {
    listOf(
        androidNativeArm32(),
        androidNativeArm64(),
        androidNativeX86(),
        androidNativeX64(),
    ).forEach {
        it.binaries.sharedLib("example")
    }

    sourceSets {
        commonMain.dependencies {
            implementation("io.github.mimimishkin:jni-binding:1.0.2")
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