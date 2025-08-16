package io.github.mimimishkin.jni.binding.plugin.producer

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.HasProject
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBinary
import org.jetbrains.kotlin.konan.target.HostManager.Companion.hostIsMingw
import java.io.File
import javax.inject.Inject

/**
 * Configuration for [JniLibProducerPlugin].
 */
public class JniLibProducerConfig @Inject constructor(override val project: Project) : HasProject {
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
     * The SDK/JRE location that will be used to link native binaries with.
     *
     * By default, Java obtained from Gradle Toolchain API will be used.
     *
     * @see linkJVM
     * @see addJvmToLinkerOpts
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
     * Information about configured interop with JNI.
     * Every function annotated with `@JniActual`, `@JniOnLoad`, and `@JniOnUnload` should have signature described in
     * this property.
     */
    public val interopConfig: Property<JniInteropConfig> = project.objects.property<JniInteropConfig>()

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
     * See details about it on [JniExportMethod.ExposeFunctions] and [JniExportMethod.BindOnLoad].
     */
    public val exportMethod: Property<JniExportMethod> = project.objects.property<JniExportMethod>()
        .convention(JniExportMethod.ExposeFunctions)

    /**
     * Information about functions that are expected to be presented in the JNI library.
     *
     * If empty, all `@JniActual` functions are considered expected.
     */
    public val expectations: SetProperty<JniExpectInfo> = project.objects.setProperty<JniExpectInfo>()

    /**
     * If `true`, then all functions annotated with `@JniActual` will be exported to JVM.
     * If `false`, then all functions annotated with `@JniActual` that are not expected will be reported as an error.
     *
     * Default value is `false`.
     *
     * @see expectations
     */
    public val allowExtraActuals: Property<Boolean> = project.objects.property<Boolean>()
        .convention(expectations.map { it.isEmpty() })

    /**
     * Sets conventions for all properties of this configuration by values from the provided [config].
     */
    public fun setConventions(config: JniLibProducerConfig) {
        jniVersion.convention(config.jniVersion)
        javaHome.convention(config.javaHome)
        interopConfig.convention(config.interopConfig)
        allowSeveralHooks.convention(config.allowSeveralHooks)
        exportMethod.convention(config.exportMethod)
        expectations.convention(config.expectations)
        allowExtraActuals.convention(config.allowExtraActuals)
        doAfterEvaluate += config.doAfterEvaluate
    }

    private val libDir: Provider<String> = javaHome.flatMap { home ->
        val home = home.asFile
        jniVersion.map { version ->
            checkJavaVersion(home, version)
            home.resolve("lib").absolutePath
        }
    }

    internal val doAfterEvaluate = mutableListOf<(Project) -> Unit>()

    internal fun afterEvaluate(action: Project.() -> Unit) {
        doAfterEvaluate += action
    }

    /**
     * Adds JVM native libraries to the [NativeBinary] linker options.
     */
    public fun NativeBinary.addJvmToLinkerOpts() {
        val lib = libDir.get()
        val javaOptions = if (hostIsMingw) {
            listOf("-L", lib, "-l", "jvm", "-l", "jawt")
        } else {
            listOf("-L", "$lib/server", "-l", "jvm", "-L", lib, "-l", "jawt")
        }

        linkerOpts(javaOptions)
    }
}

/**
 * Adds JVM native libraries to the linker options of all non-android native binaries after a project is evaluated.
 */
public fun JniLibProducerConfig.linkJVM() {
    afterEvaluate {
        the<KotlinMultiplatformExtension>().targets.withType<KotlinNativeTarget> {
            binaries.configureEach {
                if ("android" !in targetName) {
                    addJvmToLinkerOpts()
                }
            }
        }
    }
}

/**
 * Automatically adds `jni-binding` library to the `nativeMain` source set dependencies and sets
 * [JniLibProducerConfig.interopConfig].
 */
public fun JniLibProducerConfig.useJniBindingLib() {
    afterEvaluate {
        val kotlin = the<KotlinMultiplatformExtension>()
        kotlin.apply {
            sourceSets {
                nativeMain.dependencies {
                    implementation("io.github.mimimishkin:jni-binding:1.0.2")
                }
            }
        }
    }
    interopConfig = JniInteropConfig.FromJniBinding
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
