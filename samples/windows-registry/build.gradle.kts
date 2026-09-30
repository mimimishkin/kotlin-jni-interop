import io.github.mimimishkin.jni.binding.plugin.consumer.jniLibraries
import io.github.mimimishkin.jni.binding.plugin.consumer.mingwX64
import io.github.mimimishkin.jni.binding.plugin.producer.JniExportMethod
import io.github.mimimishkin.jni.binding.plugin.producer.linkJvm
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.konan.target.HostManager

// One module for both halves of the binding: the native targets produce it, and the jvm target consumes what the
// same module publishes. `fromProducer(project)` with no argument is that self-reference - a project cannot depend
// on itself, so the bindings are read from the producer's own artifacts instead of a project dependency.

plugins {
    kotlin("multiplatform") version "2.4.20"
    kotlin("plugin.power-assert") version "2.4.20"
    id("io.github.mimimishkin.jni-binding-producer") version "1.0.2"
    id("io.github.mimimishkin.jni-binding-consumer") version "1.0.2"
}

kotlin {
    jvmToolchain(17)

    jvm {
        compilations.named("main") {
            jniLibraries.create("native") {
                mingwX64 {
                    fromProducer(project)
                    resourceDir = "natives/"
                }

                copyToResources()
                allowExtraActuals = false
            }
        }
    }

    mingwX64 {
        binaries {
            if (konanTarget == HostManager.host) sharedLib("native") {
                linkJvm()
            }
        }
    }

    sourceSets.all {
        languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
        languageSettings.optIn("kotlin.experimental.ExperimentalNativeApi")
    }

    sourceSets.named("jvmTest") {
        dependencies {
            implementation(kotlin("test"))
        }
    }
}

jniLibraries {
    jniVersion = 17
    exportMethod = JniExportMethod.RegisterNatives
    allowSeveralHooks = true
}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
powerAssert {
    functions = listOf(
        "kotlin.assert",
        "kotlin.test.assertTrue",
        "kotlin.test.assertFalse",
        "kotlin.test.assertEquals",
        "kotlin.test.assertNotEquals",
    )
}
