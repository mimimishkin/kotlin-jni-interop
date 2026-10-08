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
    id("io.github.mimimishkin.jni-binding-consumer") version "2.0.0"
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

                    // We also must set up directory where the native binary will be copied to.
                    // In this example there will be this layout:
                    // generated/ressources/natives
                    //  |- windows-x86_64/native.dll
                    //  |- linux-x86_64/libnative.so
                    //  |- linux-aarch64/libnative.so
                    //  |- macos-aarch64/libnative.dylib
                    it.copyToResources("natives/${it.os}-${it.arch}")
                }

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
                    macosArm64()
                ).forEach {
                    if (it.konanTarget?.family == HostManager.host.family) {
                        it.fromProducer(project("nativeHello"))
                    } else {
                        it.fromPrebuiltBinding(rootDir.resolve("jniBindings/${it.os}-${it.arch}"))
                    }
                    it.copyToResources()
                }

                if (!isCI) allowAbsentBindings = true
            }

            // A third library, built by the `nativeCritical` producer module. It is the only one exported with
            // `ExposeFunctions` instead of `RegisterNatives`, because a critical native is bound by symbol name and
            // `RegisterNatives` cannot express one.
            jniLibraries.create("critical") {
                listOf(
                    mingwX64(),
                    linuxX64(),
                    linuxArm64(),
                    macosArm64()
                ).forEach {
                    if (it.konanTarget?.family == HostManager.host.family) {
                        it.fromProducer(project("nativeCritical"))
                    } else {
                        it.fromPrebuiltBinding(rootDir.resolve("jniBindings/${it.os}-${it.arch}"))
                    }
                    it.copyToResources()
                }

                if (!isCI) allowAbsentBindings = true
            }
        }
    }
}

dependencies {
    testImplementation(kotlin("test"))
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

// By default, the tests in this module - `CriticalTest` included - reach the natives through the ordinary
// `Java_<class>_<method>` entry points, which is what a JVM does in practice. The critical entry point needs both
// the flag below and a call site that has been compiled and inlined, so `-XX:+CriticalJNINatives` is all this adds.
tasks.withType<Test>().configureEach {
    val testJavaVersion = javaLauncher.get().metadata.languageVersion.asInt()
    // Critical JNI Natives was dropped in JDK 22, so check
    if (testJavaVersion <= 22) {
        jvmArgs("-XX:+CriticalJNINatives")
    }
}