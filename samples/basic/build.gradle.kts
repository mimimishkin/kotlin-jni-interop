import io.github.mimimishkin.jni.binding.plugin.consumer.jniLibraries
import io.github.mimimishkin.jni.binding.plugin.consumer.mingwX64
import io.github.mimimishkin.jni.binding.plugin.consumer.linuxX64
import io.github.mimimishkin.jni.binding.plugin.consumer.linuxArm64
import io.github.mimimishkin.jni.binding.plugin.consumer.macosArm64
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.fus.internal.isCiBuild
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.power-assert") version "2.4.20"
    id("io.github.mimimishkin.jni-binding-consumer") version "1.0.2"
}

val isCI = isCiBuild()

kotlin {
    jvmToolchain(17)

    target {
        compilations.named("main") {
            jniLibraries.create("native") {
                listOf(
                    mingwX64(),
                    linuxX64(),
                    linuxArm64(),
                    macosArm64(),
                ).forEach {
                    // We must set up [source] property. There are [fromProducer] and [fromPrebuiltBinding] for this.
                    if (it.konanTarget?.family == HostManager.host.family) {
                        // We can use producer directly on the supported targets.
                        // Gradle will update native binary and JniActuals' info on every change in project 'native'.
                        it.fromProducer(project("native"))
                    } else {
                        // If the target platform is not supported (for example, building for macOS on Windows),
                        // we should fall back to a prebuilt JNI bindings.
                        // This assumes that the `exportJniBinding` task has already been run (on supported machine)
                        // and its output has been copied to the `jniBindings/<os>-<arch>` folder in the project root.
                        it.fromPrebuiltBinding(rootDir.resolve("jniBindings/${it.os}-${it.arch}"))
                    }

                    // We also must set up [resourceDir] where the native binary will be copied to.
                    // In this example there will be this layout:
                    // generated/ressources/natives
                    //  |- windows-x86_64/native.dll
                    //  |- linux-x86_64/libnative.so
                    //  |- linux-aarch64/libnative.so
                    //  |- macos-aarch64/libnative.dylib
                    it.resourceDir = "natives/${it.os}-${it.arch}"
                }

                // Makes `processResources` to copy the JNI library to the resources of the consumer compilation.
                copyToResources()
                // Do not allow JniActual without JniExpect counterpart.
                allowExtraActuals = false

                // For local development, allow [bindingsDir] to be absent
                if (!isCI) allowAbsentBindings = true
            }

            // A second library, built by the `nativeHello` producer module. Unlike `native`, each of its platforms
            // exports a different binding, so the JVM side has one `@JniExpect` per target (see `ImplByTargets.kt`).
            jniLibraries.create("hello") {
                listOf(
                    mingwX64(),
                    linuxX64(),
                    linuxArm64(),
                ).forEach {
                    if (it.konanTarget?.family == HostManager.host.family) {
                        it.fromProducer(project("nativeHello"))
                    } else {
                        it.fromPrebuiltBinding(rootDir.resolve("jniBindings/${it.os}-${it.arch}"))
                    }
                    it.resourceDir = "natives/${it.os}-${it.arch}"
                }

                copyToResources()
                allowExtraActuals = false
                if (!isCI) allowAbsentBindings = true
            }
        }
    }
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
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