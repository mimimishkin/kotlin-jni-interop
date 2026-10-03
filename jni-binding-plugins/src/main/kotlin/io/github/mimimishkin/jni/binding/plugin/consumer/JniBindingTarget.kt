package io.github.mimimishkin.jni.binding.plugin.consumer

import org.gradle.api.Named
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.HasProject
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.io.File

/**
 * A single platform target of a JNI library binding.
 *
 * Describes where the compiled native binary for this platform comes from
 * (see [fromProducer] and [fromPrebuiltBinding]) and where it must be placed inside resources.
 */
public interface JniBindingTarget : Named, HasProject {
    /**
     * Name of the operating system, e.g. `"windows"`, `"linux"`, `"macos"`.
     */
    public val os: String

    /**
     * Name of the processor architecture, e.g. `"x86_64"`, `"aarch64"`.
     */
    public val arch: String

    /**
     * Name of the platform ABI, e.g. `"gnu"`, `"darwin"`.
     *
     * For an Android target this is the Android ABI directory name - `"arm64-v8a"`, `"armeabi-v7a"`, `"x86"` or
     * `"x86_64".
     */
    public val abi: String

    /**
     * Corresponding Kotlin/Native target, or `null` if this target has no direct Kotlin/Native counterpart.
     */
    public val konanTarget: KonanTarget?

    /**
     * Source of the native binary and JniActuals' info for this target.
     *
     * Must be configured with [fromProducer] or [fromPrebuiltBinding].
     */
    public val source: Property<JniLibrarySource>

    /**
     * The producer project this target takes its binding from, or unset when the binding is taken from prebuilt
     * bindings.
     */
    public val producerProject: Property<Project>

    /**
     * Takes the binding from a producer project that applies [io.github.mimimishkin.jni.binding.plugin.producer.JniLibProducerPlugin].
     *
     * Gradle will rebuild the native binary and update the JniActuals' info on every change in the producer project.
     *
     * @param project producer project.
     */
    public fun fromProducer(project: Project)

    /**
     * Takes the binding from a producer project resolved by its path.
     *
     * @param projectPath path of the producer project, absolute or relative to the current project.
     */
    public fun fromProducer(projectPath: String)

    /**
     * Takes the binding from pre-built bindings in the given directory.
     *
     * The directory layout must be `<bindingsDir>/<targetName>/<binary>` plus `<bindingsDir>/info.properties`
     * describing binary names per platform.
     *
     * @param bindingsDir provider of the directory with aggregated prebuilt bindings.
     */
    public fun fromPrebuiltBinding(bindingsDir: Provider<Directory>)

    /**
     * Convenience overload of [fromPrebuiltBinding] accepting a [Directory].
     *
     * @param bindingsDir directory with aggregated prebuilt bindings.
     */
    public fun fromPrebuiltBinding(bindingsDir: Directory)

    /**
     * Convenience overload of [fromPrebuiltBinding] accepting a [File].
     *
     * @param bindingsDir directory with aggregated prebuilt bindings.
     */
    public fun fromPrebuiltBinding(bindingsDir: File)

    /**
     * Path of the resource directory (relative to the resources root, or to the Android assets root) the native
     * binary will be copied to by [JniLibraryConfig.copyToResources].
     *
     * By default, it is `"natives/$os-$arch"`.
     */
    public val resourceDir: Property<String>
}

/**
 * Provides paths to binding artifacts of a single [platform target][JniBindingTarget].
 */
public interface JniLibrarySource {
    /**
     * Whether this build produces the bindings, as opposed to reading them from files that already exist.
     */
    public val producedByThisBuild: Boolean

    /**
     * Returns the file(s) of the compiled native library.
     */
    public fun binaries(): FileCollection

    /**
     * Returns the JSON file describing `@JniActual` functions exported by the native library.
     */
    public fun actuals(): FileCollection
}
