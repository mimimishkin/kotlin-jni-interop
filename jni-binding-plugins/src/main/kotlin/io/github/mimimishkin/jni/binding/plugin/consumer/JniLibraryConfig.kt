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
import java.util.concurrent.Callable

/**
 * Configuration of a single JNI library used by a JVM/Android compilation.
 *
 * Configures per-platform binding sources (see [targets]), extra actuals policy, and where the binaries of the
 * individual targets are packaged (see [JniBindingTarget.copyToResources] and [JniBindingTarget.copyToJniLibs]).
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
     */
    private val importedLibrariesDir = project.layout.buildDirectory.dir(
        "generated/jniLibs/${consumerCompilation.disambiguateName(libraryName)}"
    )

    /**
     * The Android extension of this project, or `null` if this is a plain JVM one.
     */
    private val androidExtension: CommonExtension? = project.extensions.findByName("android") as? CommonExtension

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

            override fun copyToResources(resourceDir: String) {
                importTarget(this, BinaryLayout.RESOURCES, resourceDir)
            }

            override fun copyToJniLibs() {
                importTarget(this, BinaryLayout.JNI_LIBS, this.abi)
            }
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
     * Task that mirrors the binaries of this library's packaged targets into [importedLibrariesDir].
     *
     * A target contributes a source when it called [JniBindingTarget.copyToResources] or
     * [JniBindingTarget.copyToJniLibs]; the targets that did not are not packaged at all. A library none of whose
     * targets was packaged has no source either, and this task is skipped.
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
        // A `Sync` without any source is reported NO-SOURCE and its actions never run, which is exactly what a
        // library none of whose targets asked to be packaged should do. The per-target destinations are relative
        // to this one.
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

        // Every configured target's binaries are an input, including those that are not packaged: resolving them
        // schedules the producers' exportJniBinding tasks as well, so the files that are packaged are guaranteed to
        // exist on disk when this task executes, and a target whose bindings are missing altogether fails the build
        // rather than being silently skipped.
        inputs.files(allBinaries)
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
     * Every target that asked to be packaged, with the destination it is packaged into.
     *
     * The destination is mutable because a target may ask to be packaged more than once, in which case the last
     * request wins. A [Sync] task cannot drop a copy spec it has been given, so the spec of a target is registered
     * once and reads its destination from here when the task runs; packaging the same target again therefore moves
     * its binary rather than adding a second copy of it.
     */
    private val packagedTargets = mutableMapOf<JniBindingTarget, PackagedDestination>()

    /**
     * The layout and path a packaged target's binary goes to, both of which a later request may replace.
     */
    private class PackagedDestination(var layout: BinaryLayout, var path: String)

    /**
     * Layouts whose staging directory has been registered with the packaging machinery already.
     */
    private val registeredLayouts = mutableSetOf<BinaryLayout>()

    init {
        packImportedLibraries()
    }

    /**
     * Makes the packaging of this library depend on [importLibTaskProvider] having run.
     *
     * Registering a directory does not make Gradle run the task producing it: the staging directories are plain
     * build-directory providers with no producer attached, so the dependency has to be declared. A desktop project
     * reads them in `processResources`; an Android one reads them in the `merge*` tasks of the variant, which are the
     * last ones to do so before the variant is packaged.
     */
    private fun packImportedLibraries() {
        val sourceSetName = consumerCompilation.compilationName

        if (androidExtension == null) {
            val processResources = if (project.extensions.findByType<KotlinMultiplatformExtension>() != null) {
                consumerCompilation.disambiguateName("processResources")
            } else {
                camelCase(
                    sourceSetName.takeIf { it != KotlinCompilation.MAIN_COMPILATION_NAME },
                    "processResources"
                )
            }
            project.tasks.named(processResources).configure { it.dependsOn(importLibTaskProvider) }
            return
        }

        val variant = sourceSetName.replaceFirstChar { it.uppercase() }
        project.tasks.named { name ->
            name in setOf(
                "merge${variant}Assets",
                "merge${variant}NativeLibs",
                "merge${variant}JniLibFolders",
                "package${variant}",
            )
        }.configureEach { it.dependsOn(importLibTaskProvider) }
    }

    /**
     * Mirrors [target]'s binary into [importedLibrariesDir], laid out as [layout] requires.
     *
     * [pathInStagingDir] is where the binary goes within the layout's own directory, e.g. `natives/linux-x86_64` for
     * the resources. It is taken as given, so that a target may be packaged into a path of its own choosing. Asking
     * for the same target again replaces the destination asked for before, be it another path or another layout.
     *
     * The binaries are resolved lazily, because a target's `copyToResources()`/`copyToJniLibs()` may well be called
     * before its [JniBindingTarget.source] is configured.
     */
    private fun importTarget(target: JniBindingTarget, layout: BinaryLayout, pathInStagingDir: String) {
        // The path is accepted the way it is written in the JVM code that loads the binary, that is with a leading
        // slash and without a trailing one, and the staging directory must not be escaped either.
        val path = pathInStagingDir.trim('/')
        require(path.isNotEmpty()) {
            "Target '${target.name}' of $libraryName is to be packaged at an empty path"
        }

        val destination = packagedTargets[target]
        if (destination != null) {
            destination.layout = layout
            destination.path = path
            registerStagingDir(layout)
            return
        }

        val registered = PackagedDestination(layout, path)
        packagedTargets[target] = registered
        registerStagingDir(layout)

        importLibTaskProvider.configure { task ->
            // `from` is given a file collection rather than a resolved list of files, so that the dependencies the
            // producer attached to its artifacts are kept.
            //
            // The child spec is taken as a lambda parameter on purpose: a Kotlin lambda passed to a Java SAM
            // parameter has no receiver, so a bare `into(...)` here would silently configure the destination of the
            // whole task instead of this spec. Its path is relative to the destination set for the task as a whole,
            // and is resolved when the task runs rather than now, which is what lets a target be packaged again.
            task.from(project.provider { binariesOf(target) }) { spec ->
                spec.into(Callable { "${registered.layout.stagingDir}/${registered.path}" })
            }
        }
    }

    /**
     * Registers the directory [importLibTaskProvider] mirrors the binaries of [layout] into with the packaging
     * machinery of this project.
     *
     * A desktop project takes the `resources` subdirectory, which holds a directory of the target's own for every
     * packaged target and is therefore registered as a whole, while an Android one takes it among the assets.
     * Registering happens along with the first target of a layout, so that a library is not declared in a place it
     * is not packaged into.
     *
     * @throws IllegalStateException if [layout] is [BinaryLayout.JNI_LIBS] and the project does not apply an Android
     * plugin.
     */
    private fun registerStagingDir(layout: BinaryLayout) {
        if (!registeredLayouts.add(layout)) return

        val android = androidExtension
        val stagingDir = stagingDirOf(layout)

        if (android == null) {
            if (layout != BinaryLayout.RESOURCES) {
                error("copyToJniLibs() requires an Android application or library plugin to be applied")
            }
            val resources = consumerCompilation.defaultSourceSet.resources
            if (resources.srcDirs.none { it == stagingDir }) {
                resources.srcDir(stagingDir)
            }
            return
        }

        val sourceSetName = consumerCompilation.compilationName
        val sourceSet = android.sourceSets.findByName(sourceSetName)
            ?: error("There is no Android source set '$sourceSetName' to copy $libraryName into")

        // Both of these are plain string sets, hence the paths.
        val directories = when (layout) {
            BinaryLayout.RESOURCES -> sourceSet.assets.directories
            BinaryLayout.JNI_LIBS -> sourceSet.jniLibs.directories
        }
        val dir = stagingDir.absolutePath
        if (directories.none { it == dir }) {
            directories.add(dir)
        }
    }

    /**
     * Absolute path of the directory [importLibTaskProvider] mirrors the binaries of [layout] into.
     */
    private fun stagingDirOf(layout: BinaryLayout): File =
        importedLibrariesDir.get().asFile.resolve(layout.stagingDir)
}

/**
 * Where a target's binary is packaged: among the resources of the compilation (or, on Android, among its assets), or
 * into the `jniLibs` of the APK/AAR.
 */
private enum class BinaryLayout(val stagingDir: String) {
    RESOURCES("resources"),
    JNI_LIBS("jniLibs")
}

/**
 * The directory, under a producer's build directory, that `JniLibProducerPlugin` writes its output into.
 */
private const val PRODUCER_OUTPUT_DIR = "jniBindings"

/**
 * Checks each producer `info.properties` against the project's target JVM: the requested JNI version must be
 * no newer than the target JDK.
 */
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