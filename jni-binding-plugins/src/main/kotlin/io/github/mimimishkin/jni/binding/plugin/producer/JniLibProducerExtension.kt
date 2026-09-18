package io.github.mimimishkin.jni.binding.plugin.producer

import io.github.mimimishkin.jni.binding.plugin.JniBindingAttributes
import io.github.mimimishkin.jni.binding.plugin.producer.JniExportMethod.RegisterNatives
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.WriteProperties
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.support.serviceOf
import org.jetbrains.kotlin.gradle.plugin.HasProject
import javax.inject.Inject

/**
 * Configuration for [JniLibProducerPlugin].
 */
public abstract class JniLibProducerExtension @Inject constructor(override val project: Project) : HasProject {
    /**
     * `jniBindings/info.properties` file that describes settings to generate a binding.
     */
    public val infoFile: Provider<RegularFile> = project.layout.buildDirectory.file("jniBindings/info.properties")

    /**
     * Generates the [infoFile] file describing binding configuration.
     */
    public val exportJniBindingInfoTaskProvider: TaskProvider<WriteProperties>

    /**
     * Generates the whole `jniBindings` directory: binding info, actuals info and native binaries.
     */
    public val exportJniBindingTaskProvider: TaskProvider<Task>

    init {
        exportJniBindingInfoTaskProvider = project.tasks.register("exportJniBindingInfo", WriteProperties::class) { task ->
            task.group = null
            task.description = "Generates jniBindings/info.properties"

            task.destinationFile.set(infoFile)
            task.property("jniVersion", jniVersion)
            task.property("allowSeveralHooks", allowSeveralHooks)
            task.property("useRegisterNatives", exportMethod.map { it == RegisterNatives })
        }

        exportJniBindingTaskProvider = project.tasks.register("exportJniBinding") {
            it.group = null
            it.description = "Generates jniBindings directory with binding info, actuals info and native binaries"

            it.dependsOn(exportJniBindingInfoTaskProvider)
        }

        project.artifacts.add("jniLibrariesElements", infoFile) {
            it.type = JniBindingAttributes.ARTIFACT_TYPE_BINDING_INFO
            it.builtBy(exportJniBindingInfoTaskProvider)
        }
    }

    /**
     * JNI of this Java version will be available inside JNI functions.
     * This is required parameter.
     *
     * More concrete mapping:
     * - 1 -> `JNI_VERSION_1_1`
     * - 2, 3 -> `JNI_VERSION_1_2`
     * - 4, 5 -> `JNI_VERSION_1_4`
     * - 6, 7 -> `JNI_VERSION_1_6`
     * - 8 -> `JNI_VERSION_1_8`
     * - 9 -> `JNI_VERSION_9`
     * - 10, 11, 12, 13, 14, 15, 16, 17, 18 -> `JNI_VERSION_10`
     * - 19 -> `JNI_VERSION_19`
     * - 20 -> `JNI_VERSION_20`
     * - 21, 22, 23 -> `JNI_VERSION_21`
     * - 24+ -> `JNI_VERSION_24`
     */
    public val jniVersion: Property<Int> = project.objects.property<Int>()

    /**
     * The SDK/JRE location that will be used to link native binaries with when [linkJVM]
     * is called.
     *
     * By default, Java obtained from Gradle Toolchain API will be used.
     */
    public val javaHome: DirectoryProperty = project.objects.directoryProperty().convention(
        project.serviceOf<JavaToolchainService>()
            .launcherFor { config ->
                config.languageVersion.set(jniVersion.map { JavaLanguageVersion.of(it) })
            }
            .map { it.metadata.installationPath }
    )

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
     * See details about it on [JniExportMethod.ExposeFunctions] and [JniExportMethod.RegisterNatives].
     */
    public val exportMethod: Property<JniExportMethod> = project.objects.property<JniExportMethod>()
        .convention(JniExportMethod.ExposeFunctions)
}