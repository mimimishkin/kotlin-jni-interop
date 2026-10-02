package io.github.mimimishkin.jni.binding.plugin.producer

import org.gradle.api.Action
import org.gradle.api.JavaVersion
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Provider
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainSpec
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.kotlin.dsl.the
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBinary
import org.jetbrains.kotlin.konan.target.Architecture
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.tooling.core.Extras
import org.jetbrains.kotlin.tooling.core.extrasKeyOf
import org.jetbrains.kotlin.tooling.core.getOrPut
import java.io.File
import java.nio.file.Path

private val defaultX11Libraries = listOf("X11", "Xext", "Xrender", "Xtst", "Xi")

/**
 * JDK selected by [linkJvm] for a single native binary and reused by [linkJAwt].
 *
 * [home] is a Gradle property rather than a plain field on purpose: the linker
 * options hold a provider reading it, so a later [linkJvm] call replaces the JDK
 * of the options registered earlier, and the configuration cache can still
 * serialize everything captured by that provider.
 */
private class JvmLinkage(
    /** JDK home of the most recent [linkJvm] call. */
    val home: DirectoryProperty
) {

    /** Whether the `libjvm` options were already added to the linker options. */
    var jvmLinked: Boolean = false

    /** Whether the `libjawt` options were already added to the linker options. */
    var jawtLinked: Boolean = false
}

private val JVM_LINKAGES_KEY: Extras.Key<MutableMap<String, JvmLinkage>> = extrasKeyOf()

/**
 * Linkage of every binary of this target, keyed by link task name.
 */
private val KotlinNativeTarget.jvmLinkages: MutableMap<String, JvmLinkage>
    get() = extras.getOrPut(JVM_LINKAGES_KEY) { mutableMapOf() }

/**
 * Linkage of this binary; created on the first [linkJvm] call.
 */
private val NativeBinary.jvmLinkage: JvmLinkage
    get() = target.jvmLinkages.getOrPut(linkTaskName) {
        JvmLinkage(project.objects.directoryProperty())
    }

/**
 * Adds the JVM runtime library (`libjvm`) to the native linker options.
 *
 * The JDK is taken from the Gradle Java toolchain matching [JniLibProducerExtension.expectedJdkVersion],
 * which is the right choice as long as the target architecture matches the host one. For
 * cross-compilation point [linkJvm] at a JDK built for the target instead, which [downloadCompatibleJdk]
 * provides:
 *
 * ```kotlin
 * linkJvm()                                                               // toolchain of the host
 * linkJvm(downloadCompatibleJdk("17.0.13.11.1"))                                    // JDK of the target
 * linkJvm(javaHome = file("/opt/jdk-17-linux-x64"))                          // File
 * linkJvm(javaHome = providers.gradleProperty("jni.jdk.x64").map(::file))    // or a Provider
 * linkJvm(languageVersion = 17)                                              // toolchain by version
 * linkJvm(toolchainSpec = {
 *     languageVersion = JavaLanguageVersion.of(17)
 * })
 * ```
 *
 * A call for a binary replaces the JDK selected by a previous call for the same
 * binary, and the selected JDK is reused by [linkJAwt]. The selected JDK is
 * validated against the target architecture when the linker options are read.
 *
 * This function adds only `libjvm`. Call [linkJAwt] when the native library also
 * uses JAWT, and [linkX11IfLinux] on Linux when the AWT runtime needs X11.
 */
public fun NativeBinary.linkJvm() {
    val expectedJdkVersion = project.the<JniLibProducerExtension>().expectedJdkVersion
    linkJvmHome(expectedJdkVersion.flatMap { version ->
        toolchainJavaHome {
            it.languageVersion.set(JavaLanguageVersion.of(version))
        }
    })
}

/**
 * Adds the JVM runtime library (`libjvm`) linked against the Gradle Java toolchain
 * of the given [languageVersion].
 *
 * See the parameterless [linkJvm] for details.
 */
public fun NativeBinary.linkJvm(languageVersion: Int) {
    linkJvmHome(toolchainJavaHome {
        it.languageVersion.set(JavaLanguageVersion.of(languageVersion))
    })
}

/**
 * Adds the JVM runtime library (`libjvm`) linked against the JDK in [javaHome].
 *
 * This is the overload for a home directory a build works out itself.
 *
 * See the parameterless [linkJvm] for details.
 */
public fun NativeBinary.linkJvm(javaHome: Provider<Directory>) {
    linkJvmHome(javaHome)
}

/**
 * Adds the JVM runtime library (`libjvm`) linked against the JDK at [javaHome].
 *
 * This is the overload to use for a target that is not the host, where no toolchain can help because
 * Gradle only serves JDKs for the host: [downloadCompatibleJdk] fetches one built for the target, and its
 * result is what this takes.
 *
 * See the parameterless [linkJvm] for details.
 */
public fun NativeBinary.linkJvm(javaHome: Path) {
    linkJvm(javaHome.toFile())
}

/**
 * Adds the JVM runtime library (`libjvm`) linked against the JDK at [javaHome].
 *
 * The path is accepted as a [File] or as a path relative to the project directory.
 * See the parameterless [linkJvm] for details.
 */
public fun NativeBinary.linkJvm(javaHome: File) {
    linkJvm(project.provider { javaHome })
}

/**
 * Adds the JVM runtime library (`libjvm`) linked against the JDK at [javaHome].
 *
 * The path is accepted as a [File] or as a path relative to the project directory.
 * See the parameterless [linkJvm] for details.
 */
@JvmName("linkJvmFileProvider")
public fun NativeBinary.linkJvm(javaHome: Provider<File>) {
    linkJvmHome(project.layout.dir(javaHome))
}

/**
 * Adds the JVM runtime library (`libjvm`) linked against the JDK resolved from [toolchainSpec].
 *
 * Unlike an explicit `javaHome`, the JDK is provisioned by Gradle. Gradle only
 * serves JDKs for the host architecture, so this overload selects the version,
 * vendor and implementation of the JDK rather than its architecture.
 * See the parameterless [linkJvm] for details.
 */
public fun NativeBinary.linkJvm(toolchainSpec: Action<JavaToolchainSpec>) {
    linkJvmHome(toolchainJavaHome(toolchainSpec))
}

/**
 * Adds the JAWT library (`libjawt`) to the native linker options.
 *
 * Reuses the JDK selected by [linkJvm] for this binary, so [linkJvm] must be called for it first.
 * This function adds only `libjawt`; call [linkJvm] separately when the native library
 * also needs `libjvm`, and [linkX11IfLinux] on Linux for X11 support.
 */
public fun NativeBinary.linkJAwt() {
    val linkage = target.jvmLinkages[linkTaskName] ?: error(
        "linkJAwt() reuses the JDK selected by linkJvm(), " +
                "but linkJvm() was not called for '$baseName' binary of target ${target.name}"
    )
    val options = lazyLinkerOptions()
    if (!linkage.jawtLinked) {
        linkage.jawtLinked = true
        options.add("-L")
        options.add(javaLibraryPathProvider(linkage, "lib"))
        options.add("-l")
        options.add("jawt")
    }
}

/**
 * Adds Linux X11 linker options.
 *
 * @param libraryPath optional directory passed to the linker with `-L`.
 * It may be required in a headless build environment where X11 libraries are
 * not present in the default linker search path.
 */
public fun NativeBinary.linkX11IfLinux(
    libraryPath: String? = null,
) {
    if (target.konanTarget.family != Family.LINUX) return

    val options = lazyLinkerOptions()
    libraryPath?.takeIf { it.isNotBlank() }?.let { directory ->
        options.add("-L")
        options.add(directory)
    }
    defaultX11Libraries.forEach { library ->
        options.add("-l$library")
    }
}

/**
 * Selects [home] as the JDK of this binary and adds the `libjvm` options.
 *
 * Options are added to every linker option list only once, so a later call
 * replaces the JDK instead of appending a second set of options.
 */
private fun NativeBinary.linkJvmHome(home: Provider<Directory>) {
    val linkage = jvmLinkage
    linkage.home.set(home)

    val options = lazyLinkerOptions()
    if (!linkage.jvmLinked) {
        linkage.jvmLinked = true
        val libraryDirectory = if (target.konanTarget.family == Family.MINGW) "lib" else "lib/server"
        options.add("-L")
        options.add(javaLibraryPathProvider(linkage, libraryDirectory))
        options.add("-l")
        options.add("jvm")
    }
}

/**
 * Path of the JDK directory to pass to the linker with `-L`.
 *
 * Only values that the configuration cache can serialize are captured: a
 * [DirectoryProperty], a [Provider] and plain values.
 */
private fun NativeBinary.javaLibraryPathProvider(
    linkage: JvmLinkage,
    relativeLibraryPath: String,
): Provider<String> {
    val konanTarget = target.konanTarget
    val targetName = konanTarget.name
    val architecture = konanTarget.architecture
    val expectedJdkVersion = project.the<JniLibProducerExtension>().expectedJdkVersion
    return linkage.home.zip(expectedJdkVersion) { directory, minVersion ->
        val javaHome = directory.asFile
        checkJdk(javaHome, targetName, architecture, minVersion)
        javaHome.resolve(relativeLibraryPath).absolutePath
    }
}

/**
 * Home of the JDK that Gradle resolves for the toolchain configured by [configure].
 */
private fun NativeBinary.toolchainJavaHome(configure: Action<JavaToolchainSpec>): Provider<Directory> =
    project.serviceOf<JavaToolchainService>()
        .launcherFor(configure)
        .map { it.metadata.installationPath }

private fun NativeBinary.lazyLinkerOptions(): LazyLinkerOptions {
    val current = linkerOpts
    return current as? LazyLinkerOptions ?: LazyLinkerOptions(current).also { linkerOpts = it }
}

/**
 * Checks that the JDK at [javaHome] can be linked into the [targetName] target.
 *
 * A JDK is built for a single platform, so linking an incompatible one either
 * fails with an unreadable linker error or produces an unloadable library.
 *
 * @throws IllegalArgumentException if [javaHome] is not a JDK at all, if its
 * version is lower than [minVersion], or if its architecture differs from the
 * [architecture] of the target.
 */
internal fun checkJdk(javaHome: File, targetName: String, architecture: Architecture, minVersion: Int) {
    val release = javaHome.resolve("release")
    require(release.isFile) {
        "JDK release file not found at $release, ${javaHome.absolutePath} is not a JDK installation"
    }

    val properties = release.readLines()
    fun releaseProperty(name: String): String? =
        properties.firstNotNullOfOrNull { line ->
            val property = line.trim()
            when {
                property.startsWith("#") -> null
                property.startsWith("$name=") -> property.removePrefix("$name=").trim('"', ' ')
                else -> null
            }
        }

    val version = requireNotNull(releaseProperty("JAVA_VERSION")) { "JAVA_VERSION is not defined in $release" }
    val versionInt = JavaVersion.toVersion(version).majorVersion.toInt()
    require(versionInt >= minVersion) {
        "Version of the provided Java is too low: $versionInt, while minimum version is $minVersion"
    }

    val osArch = requireNotNull(releaseProperty("OS_ARCH")) { "OS_ARCH is not defined in $release" }
    require(osArch.lowercase() in architecture.jdkOsArches) {
        "Architecture of the JDK at ${javaHome.absolutePath} ($osArch) does not match " +
                "the architecture of $targetName ($architecture); " +
                "pass a JDK built for $architecture to linkJvm()"
    }
}

/**
 * Values of the `OS_ARCH` property in a JDK `release` file for this architecture.
 */
private val Architecture.jdkOsArches: Set<String>
    get() = when (this) {
        Architecture.X64 -> setOf("x86_64", "amd64", "x64")
        Architecture.X86 -> setOf("x86", "i386", "i486", "i586", "i686")
        Architecture.ARM64 -> setOf("aarch64", "arm64")
        Architecture.ARM32 -> setOf("arm", "arm32", "armv7l", "armhf")
    }

private class LazyLinkerOptions(
    initialValues: Iterable<String>,
) : AbstractMutableList<String>() {
    private val elements: MutableList<Any> = mutableListOf<Any>().apply {
        addAll(initialValues)
    }

    override val size: Int
        get() = elements.size

    override fun get(index: Int): String = elements[index].resolve()

    override fun set(index: Int, element: String): String {
        val previous = get(index)
        elements[index] = element
        return previous
    }

    override fun add(index: Int, element: String) {
        elements.add(index, element)
    }

    override fun removeAt(index: Int): String {
        val removed = get(index)
        elements.removeAt(index)
        return removed
    }

    fun add(element: Provider<String>): Boolean {
        elements.add(element)
        return true
    }

    fun add(index: Int, element: Provider<String>) {
        elements.add(index, element)
    }

    fun addAll(elements: List<Provider<String>>): Boolean {
        if (elements.isEmpty()) return false
        elements.forEach { element ->
            this.elements.add(element)
        }
        return true
    }

    fun addAll(index: Int, elements: List<Provider<String>>): Boolean {
        if (elements.isEmpty()) return false
        elements.forEachIndexed { offset, element ->
            this.elements.add(index + offset, element)
        }
        return true
    }

    @Suppress("UNCHECKED_CAST")
    private fun Any.resolve(): String = when (this) {
        is String -> this
        is Provider<*> -> get() as String
        else -> error("Unsupported linker option type: ${this::class.java.name}")
    }
}