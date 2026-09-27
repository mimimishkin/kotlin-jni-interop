package io.github.mimimishkin.jni.binding.plugin.consumer

import io.github.mimimishkin.jni.binding.plugin.JniBindingAttributes
import io.github.mimimishkin.jni.binding.plugin.camelCase
import io.github.mimimishkin.jni.binding.plugin.disambiguateName
import io.github.mimimishkin.jni.binding.plugin.targetJVM
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE
import org.gradle.api.attributes.Usage
import org.gradle.api.file.Directory
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.domainObjectContainer
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.HasProject
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.konan.target.KonanTarget.*
import java.io.File
import java.util.Properties

/**
 * Configuration of a single JNI library used by a JVM/Android compilation.
 *
 * Configures per-platform binding sources (see [targets]), extra actuals policy, the load method, and copying of
 * binaries into resources.
 */
@Suppress("UnstableApiUsage")
public class JniLibraryConfig internal constructor(
    /**
     * Name of the native library, e.g. `"native"`.
     */
    public val libraryName: String,

    /**
     * The consumer compilation this library is wired to.
     */
    public val consumerCompilation: KotlinCompilation<*>,
) : HasProject, Named {
    override val project: Project = consumerCompilation.project

    override fun getName(): String = libraryName

    /**
     * Dependency-scope configuration collecting producer projects for this library.
     */
    private val dependencyScope = project.configurations.dependencyScope(
        consumerCompilation.disambiguateName(libraryName, "jniLibraries")
    )

    /**
     * Resolvable configuration resolving binding artifacts published by producers.
     */
    private val resolvableConfig = project.configurations.resolvable(
        consumerCompilation.disambiguateName(libraryName, "jniLibrariesClasspath")
    ) {
        it.extendsFrom(dependencyScope)

        it.attributes.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named("jni-binding"))
    }

    /**
     * By default, all functions annotated with `@JniActual` must have `@JniExpect` counterpart.
     *
     * Set this to `true` to allow `@JniActual` without `@JniExpect` counterpart.
     * This may lead to overriding native methods of some other libraries, even Java standard library.
     */
    public val allowExtraActuals: Property<Boolean> = project.objects.property<Boolean>()
        .convention(false)

    /**
     * All platform targets of this library. Register new ones with [target] or its shortcuts
     * (e.g. [mingwX64], [linuxX64]).
     */
    public val targets: NamedDomainObjectContainer<JniBindingTarget> = project.objects.domainObjectContainer(JniBindingTarget::class)

    /**
     * Registers and configures a new platform target.
     *
     * @param name name of the target, e.g. `"mingwX64"`.
     * @param os operating system name, see [JniBindingTarget.os].
     * @param arch processor architecture name, see [JniBindingTarget.arch].
     * @param vendor platform vendor, see [JniBindingTarget.vendor].
     * @param abi platform ABI, see [JniBindingTarget.abi].
     * @param konanTarget corresponding Kotlin/Native target if any, see [JniBindingTarget.konanTarget].
     * @param configure additional configuration of the created target.
     */
    public fun target(
        name: String,
        os: String,
        arch: String,
        abi: String,
        konanTarget: KonanTarget?,
        configure: Action<JniBindingTarget>
    ): JniBindingTarget {
        val target = object : JniBindingTarget {
            override fun getName() = name
            override val project: Project = this@JniLibraryConfig.project
            override val os: String = os
            override val arch: String = arch
            override val abi: String = abi
            override val konanTarget: KonanTarget? = konanTarget

            override val source: Property<JniLibrarySource> = project.objects.property()

            override fun fromProducer(project: Project) {
                this.project.dependencies.add(dependencyScope.name, project)
                source.set(producerSource(name))
            }

            override fun fromProducer(projectPath: String) {
                fromProducer(project.project(projectPath))
            }

            override fun fromPrebuiltBinding(bindingsDir: Provider<Directory>) {
                source.set(prebuiltSource(bindingsDir, name))
            }

            override fun fromPrebuiltBinding(bindingsDir: Directory) {
                fromPrebuiltBinding(project.provider { bindingsDir })
            }

            override fun fromPrebuiltBinding(bindingsDir: File) {
                fromPrebuiltBinding(project.layout.dir(project.provider { bindingsDir }))
            }

            override val resourceDir: Property<String> = project.objects.property<String>()
                .convention("natives/$os-$arch")
        }
        targets += target
        configure.execute(target)
        return target
    }

    /**
     * Creates a [JniLibrarySource] that resolves binding artifacts from the prebuilt bindings directory.
     */
    private fun prebuiltSource(
        bindingsDir: Provider<Directory>,
        targetName: String
    ): JniLibrarySource = object : JniLibrarySource {
        override fun binaryFile(compilationName: String): File {
            val bindings = bindingsDir.get().asFile
            val dir = bindings.resolve("binaries/$targetName/$compilationName")
            val binaries = dir.listFiles()!!.asList()
            return binaries.singleOrNull() ?: error("Cannot find a single binary for $targetName in $binaries")
        }

        override fun actualsFile(compilationName: String): File {
            val bindings = bindingsDir.get().asFile
            val dir = bindings.resolve("actualsInfo/$targetName/$compilationName")
            return dir.resolve("actuals.json")
        }
    }

    /**
     * Creates a [JniLibrarySource] that resolves binding artifacts published by the producer project
     * added to the dependency-scope configuration.
     */
    private fun producerSource(targetName: String): JniLibrarySource = object : JniLibrarySource {
        override fun binaryFile(compilationName: String): File =
            resolveProducerArtifact(
                artifactType = JniBindingAttributes.ARTIFACT_TYPE_JNI_LIBRARY,
                targetName = targetName,
                compilationName = compilationName,
                preferRelease = true,
            ).file

        override fun actualsFile(compilationName: String): File =
            resolveProducerArtifact(
                artifactType = JniBindingAttributes.ARTIFACT_TYPE_ACTUALS_INFO,
                targetName = targetName,
                compilationName = compilationName,
                preferRelease = false,
            ).file
    }

    private fun resolveProducerArtifact(
        artifactType: String,
        targetName: String,
        compilationName: String,
        preferRelease: Boolean,
    ): ResolvedArtifactResult {
        val artifacts = resolvableConfig.get().incoming.artifactView { view ->
            view.attributes.attribute(ARTIFACT_TYPE_ATTRIBUTE, artifactType)
        }.artifacts.filter { artifact ->
            // for a reason I cannot understand, artifactView don't filter artifacts by artifactType,
            // so we filter manually:
            val path = artifact.file.absolutePath.replace('\\', '/')
            when (artifactType) {
                JniBindingAttributes.ARTIFACT_TYPE_JNI_LIBRARY ->
                    "/jniBindings/binaries/$targetName/$compilationName/" in path

                JniBindingAttributes.ARTIFACT_TYPE_ACTUALS_INFO ->
                    "/jniBindings/actualsInfo/$targetName/$compilationName/" in path

                else -> false
            }
        }

        if (preferRelease) {
            artifacts.find { "release" in it.variant.displayName.lowercase() }?.let { return it }
        }

        return artifacts.singleOrNull()
            ?: error(
                "Cannot find a single $artifactType artifact for $targetName in ${artifacts.map { it.file.name }}"
            )
    }

    /**
     * The producers' `info.properties` files describing binding configuration, resolved as artifacts so a change
     * in them is picked up by [importLibTaskProvider] (like `actuals.json` are in [actualsFiles]).
     */
    private fun resolveBindingInfoFiles(): List<File> =
        resolvableConfig.get().incoming.artifactView { view ->
            view.attributes.attribute(ARTIFACT_TYPE_ATTRIBUTE, JniBindingAttributes.ARTIFACT_TYPE_BINDING_INFO)
        }.artifacts.map { it.file }.filter { file ->
            file.absolutePath.replace('\\', '/').endsWith("/jniBindings/info.properties")
        }

    /**
     * Checks each producer `info.properties` against the project's target JVM: the requested JNI version must be
     * no newer than the target JDK. The generated `JNI_OnLoad` reports that version, and a JVM rejects a native
     * library that requests a JNI version newer than it supports, so a too-new binding would fail at runtime.
     */
    private fun validateBindingInfoFiles(files: FileCollection) {
        val targetJvmMajor = project.targetJVM.orNull?.asInt() ?: return
        for (file in files) {
            val props = Properties().apply { file.inputStream().use { load(it) } }
            val jniVersion = props.getProperty("jniVersion")?.toIntOrNull() ?: continue
            if (targetJvmMajor < jniVersion) {
                error(
                    "The binding $file requests JNI version $jniVersion, which needs JDK $jniVersion or newer, " +
                        "but the project's target JVM is $targetJvmMajor. Lower `jniLibraries.jniVersion` in the " +
                        "producer or increase `jvmToolchain` in the consumer."
                )
            }
        }
    }

    /**
     * Suppresses the error when binding files (native binary) are missing.
     *
     * By default, if a binding specifies where to find the native binary but
     * the files are absent, an error is thrown. Call this method to suppress that error.
     * This is intended for local development where not all platforms have been built yet.
     */
    public val allowAbsentBindings: Property<Boolean> = project.objects.property<Boolean>()
        .convention(false)

    /**
     * Task that copies binaries of this library into the resources of the consumer compilation.
     *
     * Every [target] contributes its binary placed under its [JniBindingTarget.resourceDir]
     * inside `generated/resources` of the build directory.
     *
     * @see copyToResources
     */
    public val importLibTaskProvider: TaskProvider<Copy> = project.tasks.register<Copy>(camelCase("importJniLib", libraryName)) {
        group = "jni interop"
        description = "Copies binaries of $libraryName to the resource directory."

        dependsOn(resolvableConfig)

        val destDir = project.layout.buildDirectory.dir("generated/resources")
        // A `Copy` without any source is reported NO-SOURCE and its actions never run, so every target must
        // contribute a source; destinations are relative to this one, per target.
        into(destDir)
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        outputs.dir(destDir)

        val bindingInfoFiles = project.files(project.provider {
            resolveBindingInfoFiles()
        })
        inputs.files(bindingInfoFiles)

        doFirst { validateBindingInfoFiles(bindingInfoFiles) }

        // Resolving the actuals info through the producer configuration schedules the producer's
        // exportJniBinding (declared as `builtBy` of its artifacts), so a change in the native module
        // is picked up by this task rather than by the compiler alone.
        inputs.files(actualsFiles)

        // Resolving the binaries schedules the producers' exportJniBinding tasks as well, so the files
        // are guaranteed to exist on disk when this task executes.
        inputs.files(
            project.files(project.provider { resolveBinaries() })
        )

        targets.configureEach { target ->
            // `project.files` is used instead of a bare provider: an absent binding resolves to an empty
            // list, while a `File?` provider would make Gradle query a `null` value while computing the
            // task dependencies and fail with "no value available".
            //
            // The child spec is taken as a lambda parameter on purpose: a Kotlin lambda passed to a Java
            // SAM parameter has no receiver, so a bare `into(...)` here would silently configure the
            // destination of the whole task instead of this spec.
            from(project.files(project.provider { resolveBinaries(target) })) { spec ->
                // Deferred: `configureEach` runs before the target is configured, so `resourceDir` may not have
                // a value yet. The path is relative to the destination set above.
                spec.into(project.provider { target.resourceDir.get() })
            }
        }
    }

    private fun resolveBinaries(): List<File> = targets.mapNotNull(::resolveBinary)

    private fun resolveBinaries(target: JniBindingTarget): List<File> =
        resolveBinary(target)?.let { listOf(it) } ?: emptyList()

    private fun resolveBinary(target: JniBindingTarget): File? = try {
        if (target.source.isPresent) target.source.get().binaryFile() else null
    } catch (e: Exception) {
        if (allowAbsentBindings.get()) null else throw e
    }

    /**
     * Resolved producers' `actuals.json` files for every target of this library.
     *
     * Resolving them through the producer configuration runs the producers' `exportJniBinding`
     * (declared as `builtBy` of the artifacts), keeping bindings up to date. Absent bindings resolve
     * to `null` when [allowAbsentBindings] is set, so the collection may be empty.
     */
    public val actualsFiles: FileCollection = project.files(
        project.provider {
            targets.mapNotNull { target ->
                val source = if (target.source.isPresent) target.source.get() else null
                if (source == null) {
                    if (allowAbsentBindings.get()) null
                    else error("source not configured for ${target.name}")
                } else {
                    try {
                        source.actualsFile(consumerCompilation.compilationName)
                    } catch (e: Exception) {
                        if (allowAbsentBindings.get()) null else throw e
                    }
                }
            }
        }
    )

    /**
     * Makes `processResources` to copy the JNI library to the resources of the consumer compilation.
     *
     * Libraries are copied to [JniBindingTarget.resourceDir].
     */
    public fun copyToResources() {
        val resources = consumerCompilation.defaultSourceSet.resources
        val resourcesDir = project.layout.buildDirectory.dir("generated/resources")
        // Every library of a compilation shares the same directory, so it must be registered only once: adding
        // it twice makes `processResources` visit every file twice and fail with a duplicate entry error.
        if (resources.srcDirs.none { it == resourcesDir.get().asFile }) {
            resources.srcDir(resourcesDir)
        }

        val processResources = if (project.extensions.findByType<KotlinMultiplatformExtension>() != null) {
            consumerCompilation.disambiguateName("processResources")
        } else {
            camelCase(
                consumerCompilation.compilationName.takeIf { it != KotlinCompilation.MAIN_COMPILATION_NAME },
                "processResources"
            )
        }
        project.tasks.named(processResources).configure {
            it.dependsOn(importLibTaskProvider)
        }
    }
}

    /**
     * Registers the Windows mingwX64 target.
 *
 * @param name name of the target in the producer.
 * @param configure additional configuration of the created target.
 */
public fun JniLibraryConfig.mingwX64(name: String = "mingwX64", configure: Action<JniBindingTarget> = {}): JniBindingTarget =
    target(name, os = "windows", arch = "x86_64", abi = "gnu", MINGW_X64, configure)

/**
 * Registers the Linux x86_64 target.
 *
 * @param name name of the target in the producer.
 * @param configure additional configuration of the created target.
 */
public fun JniLibraryConfig.linuxX64(name: String = "linuxX64", configure: Action<JniBindingTarget> = {}): JniBindingTarget =
    target(name, os = "linux", arch = "x86_64", abi = "gnu", LINUX_X64, configure)

/**
 * Registers the Linux ARM64 target.
 *
 * @param name name of the target in the producer.
 * @param configure additional configuration of the created target.
 */
public fun JniLibraryConfig.linuxArm64(name: String = "linuxArm64", configure: Action<JniBindingTarget> = {}): JniBindingTarget =
    target(name, os = "linux", arch = "aarch64", abi = "gnu", LINUX_ARM64, configure)

/**
 * Registers the macOS ARM64 (Apple Silicon) target.
 *
 * @param name name of the target in the producer.
 * @param configure additional configuration of the created target.
 */
public fun JniLibraryConfig.macosArm64(name: String = "macosArm64", configure: Action<JniBindingTarget> = {}): JniBindingTarget =
    target(name, os = "macos", arch = "aarch64", abi = "darwin", MACOS_ARM64, configure)

/**
 * Registers the Android Native x86 target.
 *
 * @param name name of the target in the producer.
 * @param configure additional configuration of the created target.
 */
public fun JniLibraryConfig.androidX86(name: String = "androidNativeX86", configure: Action<JniBindingTarget> = {}): JniBindingTarget =
    target(name, os = "android", arch = "x86", abi = "android", ANDROID_X86, configure)

/**
 * Registers the Android Native x86_64 target.
 *
 * @param name name of the target in the producer.
 * @param configure additional configuration of the created target.
 */
public fun JniLibraryConfig.androidX64(name: String = "androidNativeX64", configure: Action<JniBindingTarget> = {}): JniBindingTarget =
    target(name, os = "android", arch = "x86_64", abi = "android", ANDROID_X64, configure)

/**
 * Registers the Android Native ARM32 target.
 *
 * @param name name of the target in the producer.
 * @param configure additional configuration of the created target.
 */
public fun JniLibraryConfig.androidArm32(name: String = "androidNativeArm32", configure: Action<JniBindingTarget> = {}): JniBindingTarget =
    target(name, os = "android", arch = "aarch32", abi = "androideabi", ANDROID_ARM32, configure)

/**
 * Registers the Android Native ARM64 target.
 *
 * @param name name of the target in the producer.
 * @param configure additional configuration of the created target.
 */
public fun JniLibraryConfig.androidArm64(name: String = "androidNativeArm64", configure: Action<JniBindingTarget> = {}): JniBindingTarget =
    target(name, os = "android", arch = "aarch64", abi = "android", ANDROID_ARM64, configure)

// TODO one day: rust targets