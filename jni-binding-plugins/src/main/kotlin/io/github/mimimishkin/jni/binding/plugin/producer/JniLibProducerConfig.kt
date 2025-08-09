package io.github.mimimishkin.jni.binding.plugin.producer

import io.github.mimimishkin.jni.binding.plugin.JniBindingFilter
import io.github.mimimishkin.jni.binding.plugin.MachineDependent
import io.github.mimimishkin.jni.binding.plugin.machineIndependent
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Property
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.HasProject
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.HostManager.Companion.hostIsMingw
import java.io.File
import javax.inject.Inject

public class JniLibProducerConfig @Inject constructor(project: Project) : HasProject {
    override var project: Project = project
        internal set

    /**
     * JNI of this Java version will be available inside JNI functions.
     * If not specified, will be obtained from the `java.toolchain`.
     *
     * More concrete mapping:
     * - 1 -> `JNI_VERSION_1_1`
     * - 2, 3 -> `JNI_VERSION_1_2`
     * - 4, 5 -> `JNI_VERSION_1_4`
     * - 6, 7 -> `JNI_VERSION_1_6`
     * - 8 -> `JNI_VERSION_1_8`
     * - 9 -> `JNI_VERSION_9`
     * - 10+ -> `JNI_VERSION_10`
     */
    public val jniVersion: Property<Int> = project.objects.property<Int>().convention(
        project.the<JavaPluginExtension>().toolchain.languageVersion.map { it.asInt() }
    )

    /**
     * The SDK/JRE location that will be used to automatically link native binaries if [linkJVM] is true.
     *
     * By default, Java obtained from Gradle Toolchain API will be used.
     */
    public val javaHome: DirectoryProperty = project.objects.directoryProperty().convention(
        jniVersion.flatMap { jniVersion ->
            val launcher = project.the<JavaToolchainService>().launcherFor {
                languageVersion = JavaLanguageVersion.of(jniVersion)
            }

            launcher.map { it.metadata.installationPath }
        }
    )

    /**
     * Controls how functions annotated with `@JniActual` and `@JniOnLoad`/`@JniOnUnload` should look.
     *
     * The default value is [SignatureTypes.CorrectlyNamed].
     */
    public val signatureTypes: Property<SignatureTypes> = project.objects.property<SignatureTypes>()
        .convention(SignatureTypes.CorrectlyNamed)

    /**
     * Weather to generate `JNI_OnLoad` and `JNI_OnUnload`. You may disable it if you have some problems.
     *
     * The default value is `true`. Note that if you disable it, you need to expose `JNI_OnLoad` by yourself to access
     * JNI environment of latest versions.
     */
    public val generateHooks: Property<Boolean> = project.objects.property<Boolean>().convention(true)

    /**
     * By default, only one function annotated with `@JniOnLoad` is allowed. The same with `@JniOnUnload`.
     * Set this to `true` to allow multimple load listeners and finalizers.
     */
    public val allowSeveralHooks: Property<Boolean> = project.objects.property<Boolean>().convention(false)

    /**
     * There are two methods to link `external` (or `native` in Java) methods to their native implementation:
     * 1. Expose functions with names like Java_some_package_ClassName_methodName.
     * 2. Use `RegisterNative` inside a `JNI_OnLoad`.
     *
     * See details about it on [JniExportMethod.ExposeFunctions] and [JniExportMethod.BindOnLoad]
     */
    public val exportMethod: Property<JniExportMethod> = project.objects.property<JniExportMethod>().convention(
        JniExportMethod.ExposeFunctions
    )

    /**
     * Sets conventions for all properties of this configuration by values from the provided [config].
     */
    public fun setConventions(config: JniLibProducerConfig) {
        jniVersion.convention(config.jniVersion)
        javaHome.convention(config.javaHome)
        signatureTypes.convention(config.signatureTypes)
        generateHooks.convention(config.generateHooks)
        allowSeveralHooks.convention(config.allowSeveralHooks)
        exportMethod.convention(config.exportMethod)
    }
}

/**
 * Immediately adds JVM native libraries to the linker options of all non-android native binaries.
 */
public fun JniLibProducerConfig.linkJVMImmediately() {
    val jniVersion = jniVersion.get()
    val javaHome = javaHome.get().asFile
    checkJavaVersion(javaHome, jniVersion)

    val lib = javaHome.resolve("lib").absolutePath
    val javaOptions = if (hostIsMingw) {
        listOf("-L", lib, "-l", "jvm", "-l", "jawt")
    } else {
        listOf("-L", "$lib/server", "-l", "jvm", "-L", lib, "-l", "jawt")
    }

    project.the<KotlinMultiplatformExtension>().targets.withType<KotlinNativeTarget> {
        binaries.configureEach {
            if ("android" !in targetName) {
                linkerOpts(javaOptions)
            }
        }
    }
}

/**
 * Adds JVM native libraries to the linker options of all non-android native binaries after a project is evaluated.
 */
public fun JniLibProducerConfig.linkJVM() {
    project.afterEvaluate {
        linkJVMImmediately()
    }
}

/**
 * Automatically adds `jni-binding` library to the `nativeMain` source set dependencies and sets
 * [JniLibProducerConfig.signatureTypes] to [SignatureTypes.FromJniBindings].
 */
public fun JniLibProducerConfig.useJniBindingLib() {
    val sourceSets = project.the<KotlinMultiplatformExtension>().sourceSets
    sourceSets.named("nativeMain") {
        dependencies {
            implementation("io.github.mimimishkin:jni-binding:1.0.1")
        }
    }

    signatureTypes = SignatureTypes.FromJniBindings
}

internal fun checkJavaVersion(javaHome: File, minVersion: Int) {
    val releaseFile = File(javaHome, "release")
    val regex = Regex("JAVA_VERSION=\"([^\"]+)\"")
    val version = regex.find(releaseFile.readText())!!.groupValues[1]
    val versionInt = JavaVersion.toVersion(version).majorVersion.toInt()
    if (versionInt < minVersion) {
        throw IllegalArgumentException("Version of the provided Java is too low: $versionInt, while minimum version is $minVersion")
    }
}
