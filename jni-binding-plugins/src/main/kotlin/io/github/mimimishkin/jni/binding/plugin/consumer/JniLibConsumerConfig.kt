package io.github.mimimishkin.jni.binding.plugin.consumer

import io.github.mimimishkin.jni.binding.plugin.JniBindingFilter
import io.github.mimimishkin.jni.binding.plugin.MachineDependent
import io.github.mimimishkin.jni.binding.plugin.machineDependent
import io.github.mimimishkin.jni.binding.plugin.machineIndependent
import io.github.mimimishkin.jni.binding.plugin.producer.JniLibProducerConfig
import io.github.mimimishkin.jni.binding.plugin.producer.JniLibProducerPlugin
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.assign
import org.jetbrains.kotlin.gradle.plugin.HasProject
import javax.inject.Inject

public class JniLibConsumerConfig @Inject constructor(override val project: Project) : HasProject {
    /**
     * The project that produces JNI bindings.
     *
     * This project should apply [JniLibProducerPlugin] plugin. If it does not, it will be applied automatically.
     *
     * By default, it tries to locate `jni` project relative to the current project.
     */
    public val projectToBind: Property<Project> = project.objects.property<Project>()
        .convention(project.findProject("jni"))

    /**
     * The name of the project that produces JNI bindings.
     *
     * This is convenient alias for [projectToBind] property.
     */
    public var projectToBindName: String
        get() = projectToBind.get().name
        set(value) { projectToBind = project.project(value) }

    /**
     * Configuration for the producer part.
     *
     * This will be used as convention for the [projectToBind] configuration.
     */
    public val producerConfig: JniLibProducerConfig = project.objects.newInstance<JniLibProducerConfig>()

    /**
     * Configures the producer part of the JNI bindings.
     * This is a convenient way to set up the producer configuration without needing to access the producer project
     * directly.
     *
     * This configuration will be used as convention for the [projectToBind] configuration.
     */
    public fun producerConfig(action: Action<JniLibProducerConfig>) {
        action.execute(producerConfig)
    }

    /**
     * All functions annotated with `@JniExpect` and matching these criteria are supposed to be linked with their native
     * implementation.
     * All other actuals will be ignored, but if they were exported by JNI lib, they also will be linked. This filter
     * is used only for checking.
     *
     * The default value is [JniBindingFilter.All] for all platforms.
     */
    public val expectsFilter: Property<MachineDependent<JniBindingFilter>> = project.objects
        .property<MachineDependent<JniBindingFilter>>()
        .convention(machineIndependent { JniBindingFilter.All })

    /**
     * The name of the native library to be loaded.
     *
     * The default value is `"native"`.
     */
    public val libraryName: Property<String> = project.objects.property<String>().convention("native")

    /**
     * The method of loading the native library.
     *
     * The default value is [JniLoadMethod.ExtractFromResourceAndLoad] configured with resource path
     * `"$projectToBindName/$os-$arch/${mapNative(libraryName)}"` and extracting to temp directory.
     *
     * @see JniLoadMethod
     */
    public val loadMethod: Property<JniLoadMethod> = project.objects.property<JniLoadMethod>()
        .convention(libraryName.map { libraryName ->
            JniLoadMethod.ExtractFromResourceAndLoad(
                resource = machineDependent { "$projectToBindName/$os-$arch/${mapNative(libraryName)}" },
                extractTo = null
            )
        })

    /**
     * Whether to check the resources specified by [loadMethod] are present before `assemble` task.
     *
     * This is ignored if [loadMethod] is other than [JniLoadMethod.ExtractFromResourceAndLoad].
     *
     * The default value is `true`.
     */
    public val checkResources: Property<Boolean> = project.objects.property<Boolean>().convention(true)
}