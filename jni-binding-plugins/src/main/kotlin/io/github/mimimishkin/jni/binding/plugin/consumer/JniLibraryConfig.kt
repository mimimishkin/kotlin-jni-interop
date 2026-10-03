package io.github.mimimishkin.jni.binding.plugin.consumer

import com.android.build.api.dsl.CommonExtension
import io.github.mimimishkin.jni.binding.plugin.JniBindingAttributes
import io.github.mimimishkin.jni.binding.plugin.producer.JniLibProducerExtension
import io.github.mimimishkin.jni.binding.plugin.camelCase
import io.github.mimimishkin.jni.binding.plugin.disambiguateName
import io.github.mimimishkin.jni.binding.plugin.targetJVM
import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE
import org.gradle.api.attributes.Usage
import org.gradle.api.file.Directory
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Sync
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
     * Directory [importLibTaskProvider] mirrors the binaries into.
     *
     * A desktop consumer registers it among the resources of its compilation; an Android one registers it among
     * either `jniLibs` ([copyToJniLibs]) or its assets ([copyToResources]). Binaries end up under the target's
     * [JniBindingTarget.resourceDir] for the resource/asset layout and under its [JniBindingTarget.abi] for `jniLibs`.
     */
    private val importedLibrariesDir = project.layout.buildDirectory.dir(
        "generated/jniLibs/${consumerCompilation.disambiguateName(libraryName)}"
    )

    /**
     * Whether binaries are laid out for loading from files (a target's [JniBindingTarget.resourceDir]) instead of
     * for `jniLibs` (an Android target's [JniBindingTarget.abi]).
     */
    private val packToResources: Property<Boolean> = project.objects.property<Boolean>()
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

            override val producerProject: Property<Project> = project.objects.property()

            override fun fromProducer(project: Project) {
                val isSelf = project == this.project
                if (!isSelf) {
                    this.project.dependencies.add(dependencyScope.name, project)
                }
                producerProject.set(project)
                source.set(if (isSelf) selfProducerSource(name) else producerSource(name))
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
        override val producedByThisBuild: Boolean = false

        override fun binaries(): FileCollection {
            val dir = bindingsDir.get().asFile.resolve("binaries/$targetName")
            val binaries = dir.listFiles()?.asList().orEmpty()
            return project.files(
                binaries.singleOrNull() ?: error("Cannot find a single binary for $targetName in $binaries")
            )
        }

        override fun actuals(): FileCollection {
            val dir = bindingsDir.get().asFile.resolve("actualsInfo/$targetName")
            return project.files(dir.resolve("actuals.json"))
        }
    }

    /**
     * Creates a [JniLibrarySource] that resolves binding artifacts published by the producer project
     * added to the dependency-scope configuration.
     */
    private fun producerSource(targetName: String): JniLibrarySource = object : JniLibrarySource {
        override val producedByThisBuild: Boolean = true

        override fun binaries(): FileCollection =
            producerArtifacts(JniBindingAttributes.ARTIFACT_TYPE_JNI_LIBRARY, "binaries", targetName)

        override fun actuals(): FileCollection =
            producerArtifacts(JniBindingAttributes.ARTIFACT_TYPE_ACTUALS_INFO, "actualsInfo", targetName)
    }

    /**
     * All the producer artifacts of [artifactType], kept unresolved until a task actually needs them.
     */
    private fun producerArtifactFiles(artifactType: String): FileCollection =
        resolvableConfig.get().incoming.artifactView { view ->
            view.attributes.attribute(ARTIFACT_TYPE_ATTRIBUTE, artifactType)
        }.files

    private fun producerArtifacts(
        artifactType: String,
        directory: String,
        targetName: String,
    ): FileCollection {
        val path = "/jniBindings/$directory/$targetName/"
        return producerArtifactFiles(artifactType)
            .filter { path in it.absolutePath.replace('\\', '/') }
    }

    private fun selfProducerSource(targetName: String): JniLibrarySource = object : JniLibrarySource {
        override val producedByThisBuild: Boolean = true

        override fun binaries(): FileCollection =
            selfProduced("binaries", targetName)

        override fun actuals(): FileCollection =
            selfProduced("actualsInfo", targetName)
    }

    private fun selfProduced(directory: String, targetName: String): FileCollection {
        val dir = project.layout.buildDirectory.dir("$PRODUCER_OUTPUT_DIR/$directory/$targetName")
        val files = project.objects.fileCollection()
        // Listed rather than named, because the producer `Sync`s exactly one file into each of these directories and
        // its name is platform-specific (`native.dll`, `libnative.so`, ...).
        files.from(project.provider { dir.get().asFile.listFiles()?.toList().orEmpty() })
        project.extensions.findByType<JniLibProducerExtension>()
            ?.let { files.builtBy(it.exportJniBindingTaskProvider) }
        return files
    }

    /**
     * The producers' `info.properties` files describing binding configuration, resolved as artifacts so a change
     * in them is picked up by [importLibTaskProvider] (like `actuals.json` are in [actualsFiles]).
     */
    private fun bindingInfoFiles(): FileCollection =
        producerArtifactFiles(JniBindingAttributes.ARTIFACT_TYPE_BINDING_INFO)
            .filter { it.absolutePath.replace('\\', '/').endsWith("/jniBindings/info.properties") }

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
     * Task that mirrors binaries of this library into the directory the packaging methods register.
     *
     * Every [target] contributes its binary. [copyToResources] lays them out under each target's
     * [JniBindingTarget.resourceDir] (on Android among the assets), while [copyToJniLibs] lays an Android target out
     * under its [JniBindingTarget.abi] for `System.loadLibrary`.
     *
     * @see copyToResources
     * @see copyToJniLibs
     */
    public val importLibTaskProvider: TaskProvider<Sync> = project.tasks.register<Sync>(
        camelCase(
            "importJniLib",
            libraryName,
            consumerCompilation.compilationName.takeIf { it != KotlinCompilation.MAIN_COMPILATION_NAME },
        )
    ) {
        group = "jni interop"
        description = "Mirrors binaries of $libraryName to the resource directory."

        val targetJvmMajor = project.targetJVM.orNull?.asInt() ?: 0

        // The producer's tasks come from the artifacts themselves: every file collection below is backed by an
        // artifact view, and the `exportJniBinding` the producer declares as `builtBy` of them rides along with it.
        // That is why nothing here declares a dependency on the producer's configuration: a `Configuration` is not
        // a serializable task dependency, and passing one is what the configuration cache rejects.
        val destDir = importedLibrariesDir
        // A `Sync` without any source is reported NO-SOURCE and its actions never run, so every target must
        // contribute a source; destinations are relative to this one, per target.
        into(destDir)
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        outputs.dir(destDir)

        val infoFiles = bindingInfoFiles()
        inputs.files(infoFiles)

        doFirst { validateBindingInfoFiles(infoFiles, targetJvmMajor) }

        // Resolving the actuals info through the producer configuration schedules the producer's
        // exportJniBinding (declared as `builtBy` of its artifacts), so a change in the native module
        // is picked up by this task rather than by the compiler alone.
        inputs.files(actualsFiles)

        // Resolving the binaries schedules the producers' exportJniBinding tasks as well, so the files
        // are guaranteed to exist on disk when this task executes.
        inputs.files(allBinaries)

        targets.configureEach { target ->
            // `into` resolves a provider lazily, so the relative path can depend on the layout chosen by
            // `copyToResources()`/`copyToJniLibs()`, which may be called after this target was configured.
            val resourcesLayout = packToResources
            val resourceDir = target.resourceDir
            val abi = target.abi
            val isAndroid = target.os == "android"

            // `from` is given a file collection rather than a resolved list of files, so that the dependencies
            // the producer attached to its artifacts are kept. It has to stay deferred: `configureEach` runs
            // before the user's configuration lambda, so `source` is not set yet at this point.
            //
            // The child spec is taken as a lambda parameter on purpose: a Kotlin lambda passed to a Java
            // SAM parameter has no receiver, so a bare `into(...)` here would silently configure the
            // destination of the whole task instead of this spec.
            from(binariesOf(target)) { spec ->
                // The path is relative to the destination set above.
                spec.into(project.provider {
                    if (resourcesLayout.get() || !isAndroid) resourceDir.get() else abi
                })
            }
        }
    }

    /**
     * Every target's native binary, or an empty collection for a target whose binding is absent and
     * [allowAbsentBindings] permits it.
     */
    private val allBinaries: FileCollection = project.objects.fileCollection().also { all ->
        targets.configureEach { target ->
            all.from(project.provider { binariesOf(target) })
        }
    }

    private fun binariesOf(target: JniBindingTarget): FileCollection = try {
        if (target.source.isPresent) target.source.get().binaries() else project.files()
    } catch (e: Exception) {
        if (allowAbsentBindings.get()) project.files() else throw e
    }

    /**
     * Resolved producers' `actuals.json` files for every target of this library.
     *
     * Resolving them through the producer configuration runs the producers' `exportJniBinding`
     * (declared as `builtBy` of the artifacts), keeping bindings up to date. Absent bindings contribute
     * nothing when [allowAbsentBindings] is set, so the collection may be empty.
     */
    public val actualsFiles: FileCollection = project.objects.fileCollection().also { all ->
        targets.configureEach { target ->
            all.from(
                project.provider {
                    val source = if (target.source.isPresent) target.source.get() else null
                    if (source == null) {
                        if (allowAbsentBindings.get()) project.files()
                        else error("source not configured for ${target.name}")
                    } else {
                        try {
                            source.actuals()
                        } catch (e: Exception) {
                            if (allowAbsentBindings.get()) project.files() else throw e
                        }
                    }
                }
            )
        }
    }

    /**
     * Makes the consumer compilation copy the JNI library into its resources (or assets on Android).
     *
     * Libraries are copied to the relative path defined by [JniBindingTarget.resourceDir].
     */
    public fun copyToResources() {
        packToResources.set(true)

        val android = project.extensions.findByName("android") as? CommonExtension
        if (android != null) {
            val sourceSetName = consumerCompilation.compilationName
            val sourceSet = android.sourceSets.findByName(sourceSetName)
                ?: error("There is no Android source set '$sourceSetName' to copy $libraryName into")
            val assetsDir = importedLibrariesDir.get().asFile.absolutePath
            sourceSet.assets.directories.add(assetsDir)

            // Registering the directory does not make Gradle run the copy task: `importedLibrariesDir` is a plain
            // build-directory provider with no producer attached, so the dependency has to be declared.
            val variant = sourceSetName.replaceFirstChar { it.uppercase() }
            project.tasks.matching { task ->
                task.name == "merge${variant}Assets" ||
                    task.name == "package${variant}"
            }.configureEach { it.dependsOn(importLibTaskProvider) }
            return
        }

        val resources = consumerCompilation.defaultSourceSet.resources
        if (resources.srcDirs.none { it == importedLibrariesDir.get().asFile }) {
            resources.srcDir(importedLibrariesDir)
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

    /**
     * Makes `merge*NativeLibs` to copy the JNI library to the `jniLibs` of the android consumer compilation.
     *
     * Each binary is placed under its [JniBindingTarget.abi]. This is the default Android layout; call
     * [copyToResources] instead to load the binary from the assets.
     *
     * @throws IllegalStateException if the project does not apply an Android plugin.
     */
    public fun copyToJniLibs() {
        packToResources.set(false)

        val android = project.extensions.findByName("android") as? CommonExtension
            ?: error("copyToJniLibs() requires an Android application or library plugin to be applied")

        val sourceSetName = consumerCompilation.compilationName
        val sourceSet = android.sourceSets.findByName(sourceSetName)
            ?: error("There is no Android source set '$sourceSetName' to copy $libraryName into")

        val jniLibsDir = importedLibrariesDir.get().asFile.absolutePath
        if (sourceSet.jniLibs.directories.none { it == jniLibsDir }) {
            sourceSet.jniLibs.directories.add(jniLibsDir)
        }

        // Registering the directory does not make Gradle run the copy task: `importedLibrariesDir` is a plain
        // build-directory provider with no producer attached, so the dependency has to be declared. The merge task is
        // the last one that reads `jniLibs` before packaging, which makes it the safe place to hook.
        val variant = sourceSetName.replaceFirstChar { it.uppercase() }
        val mergeTasks = project.tasks.matching { task ->
            task.name == "merge${variant}NativeLibs" ||
                task.name == "merge${variant}JniLibFolders" ||
                task.name == "package${variant}"
        }
        mergeTasks.configureEach { it.dependsOn(importLibTaskProvider) }
    }
}

/**
 * Checks each producer `info.properties` against the project's target JVM: the requested JNI version must be
 * no newer than the target JDK.
 */
/** The directory, under a producer's build directory, that `JniLibProducerPlugin` writes its output into. */
private const val PRODUCER_OUTPUT_DIR = "jniBindings"

private fun validateBindingInfoFiles(files: FileCollection, targetJvmMajor: Int) {
    if (targetJvmMajor <= 0) return
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
    target(name, os = "android", arch = "x86", abi = "x86", ANDROID_X86, configure)

/**
 * Registers the Android Native x86_64 target.
 *
 * @param name name of the target in the producer.
 * @param configure additional configuration of the created target.
 */
public fun JniLibraryConfig.androidX64(name: String = "androidNativeX64", configure: Action<JniBindingTarget> = {}): JniBindingTarget =
    target(name, os = "android", arch = "x86_64", abi = "x86_64", ANDROID_X64, configure)

/**
 * Registers the Android Native ARM32 target.
 *
 * @param name name of the target in the producer.
 * @param configure additional configuration of the created target.
 */
public fun JniLibraryConfig.androidArm32(name: String = "androidNativeArm32", configure: Action<JniBindingTarget> = {}): JniBindingTarget =
    target(name, os = "android", arch = "aarch32", abi = "armeabi-v7a", ANDROID_ARM32, configure)

/**
 * Registers the Android Native ARM64 target.
 *
 * @param name name of the target in the producer.
 * @param configure additional configuration of the created target.
 */
public fun JniLibraryConfig.androidArm64(name: String = "androidNativeArm64", configure: Action<JniBindingTarget> = {}): JniBindingTarget =
    target(name, os = "android", arch = "aarch64", abi = "arm64-v8a", ANDROID_ARM64, configure)

// TODO one day: rust targets