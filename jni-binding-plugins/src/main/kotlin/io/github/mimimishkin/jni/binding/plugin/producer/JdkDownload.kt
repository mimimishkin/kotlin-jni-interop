package io.github.mimimishkin.jni.binding.plugin.producer

import com.palantir.gradle.jdks.CaCerts
import com.palantir.gradle.jdks.JdkDistribution
import com.palantir.gradle.jdks.JdkDistributionName
import com.palantir.gradle.jdks.JdkManager
import com.palantir.gradle.jdks.JdkRelease
import com.palantir.gradle.jdks.JdkSpec
import com.palantir.gradle.jdks.JdksExtension
import com.palantir.gradle.jdks.setup.common.Arch
import com.palantir.platform.OperatingSystem
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.kotlin.dsl.the
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBinary
import org.jetbrains.kotlin.konan.target.Architecture
import org.jetbrains.kotlin.konan.target.Family
import java.io.File
import java.io.InputStream
import java.nio.file.FileAlreadyExistsException
import java.nio.file.attribute.PosixFilePermission
import java.util.zip.GZIPInputStream
import java.nio.file.FileVisitResult
import java.nio.file.SimpleFileVisitor
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

/**
 * Distribution [downloadCompatibleJdk] uses when it is not told otherwise.
 *
 * Amazon Corretto publishes the widest set of platform combinations, which is what matters for a
 * target that is not the host.
 */
public const val DEFAULT_JDK_DISTRIBUTION: String = "amazon-corretto"

/**
 * File every JDK has at the root of its home directory, and what identifies one.
 */
private const val RELEASE_FILE: String = "release"

/**
 * The `jdks` extension of this project, or one holding the defaults when it has none.
 *
 * A project that configures the extension gets its own storage location and mirrors; one that does
 * not gets the defaults, which are what a build applying `com.palantir.jdks` starts from as well.
 */
public fun Project.jdksExtension(): JdksExtension {
    val existing = extensions.findByName("jdks")
    if (existing is JdksExtension) return existing
    return callStatic("com.palantir.gradle.jdks.JdksPlugin", "extension", this, newJdkDistributions())
        as JdksExtension
}

/**
 * Provides the latest JDK of the Java version of [JniLibProducerExtension.jniVersion] built for the
 * platform of this binary's target, to link against.
 *
 * This is how a target that is not the host is linked, because Gradle serves Java toolchains for the
 * host only. The platform is worked out from the target itself, so only the version is left to say:
 *
 * ```kotlin
 * binaries {
 *     sharedLib("native") {
 *         linkJvm()                                         // the host, where a toolchain is enough
 *         linkJvm(downloadCompatibleJdk())                  // any other target, jniVersion
 *         linkJvm(downloadCompatibleJdk(21))                // or a named major version
 *         linkJvm(downloadCompatibleJdk("17.0.13.11.1"))    // or an exact build
 *     }
 * }
 * ```
 *
 * A major version resolves to whichever build the distribution published last at the time of the first
 * download, and to that same one afterwards, since the JDK is then reused from disk. Pass a version as
 * a [String] to pin it.
 *
 * @param distribution distribution to download from, as a [JdkDistributionName] name.
 * @param os operating system to build the JDK for, which is this target's one by default.
 * @param arch architecture to build the JDK for, which is this target's one by default.
 * @param extension the `jdks` extension to read the storage location and the mirror URLs from. Pass
 * `rootProject.jdksExtension()` to use the one of the root project, which is where a build applying
 * `com.palantir.jdks` has it.
 */
public fun NativeBinary.downloadCompatibleJdk(
    distribution: String = DEFAULT_JDK_DISTRIBUTION,
    os: OperatingSystem = jdkOperatingSystem,
    arch: Arch = target.konanTarget.architecture.jdkArchitecture,
    extension: JdksExtension = project.jdksExtension(),
): Provider<Directory> {
    val jniVersion = project.the<JniLibProducerExtension>().jniVersion
    return jdkProvider(distribution, jniVersion, project.provider { "" }, os, arch, extension)
}

/**
 * Provides the latest JDK of a major [version] built for the platform of this binary's target, to
 * link against.
 *
 * See [downloadCompatibleJdk] for the details.
 */
public fun NativeBinary.downloadCompatibleJdk(
    version: Int,
    distribution: String = DEFAULT_JDK_DISTRIBUTION,
    os: OperatingSystem = jdkOperatingSystem,
    arch: Arch = target.konanTarget.architecture.jdkArchitecture,
    extension: JdksExtension = project.jdksExtension(),
): Provider<Directory> =
    jdkProvider(distribution, project.provider { version }, project.provider { "" }, os, arch, extension)

/**
 * Provides the JDK of exactly [version] built for the platform of this binary's target, to link
 * against.
 *
 * See [downloadCompatibleJdk] for what this is for; this overload pins the JDK to one build.
 *
 * @param version exact version as the distribution publishes it, for example `17.0.13.11.1`.
 */
public fun NativeBinary.downloadCompatibleJdk(
    version: String,
    distribution: String = DEFAULT_JDK_DISTRIBUTION,
    os: OperatingSystem = jdkOperatingSystem,
    arch: Arch = target.konanTarget.architecture.jdkArchitecture,
    extension: JdksExtension = project.jdksExtension(),
): Provider<Directory> =
    jdkProvider(distribution, project.provider { 0 }, project.provider { version }, os, arch, extension)

/**
 * Provider of the home directory of a JDK, which downloads and unpacks it when first read.
 *
 * @param majorVersion the major version to ask for when [exactVersion] is empty.
 * @param exactVersion the exact version to ask for, or empty to ask for the latest build of a major
 * version instead.
 */
private fun NativeBinary.jdkProvider(
    distributionName: String,
    majorVersion: Provider<Int>,
    exactVersion: Provider<String>,
    os: OperatingSystem,
    arch: Arch,
    extension: JdksExtension,
): Provider<Directory> {
    val distribution = distributionName.toJdkDistribution()
    val konanTarget = target.konanTarget
    val home = project.providers.of(JdkValueSource::class.java) { spec ->
        val parameters = spec.parameters
        parameters.distribution.set(distribution.uiName())
        parameters.majorVersion.set(majorVersion)
        parameters.exactVersion.set(exactVersion)
        parameters.operatingSystem.set(project.provider { os.uiName() })
        parameters.architecture.set(project.provider { arch.uiName() })
        parameters.baseUrl.set(extension.jdkDistributionFor(distribution).baseUrl)
        parameters.storageLocation.set(extension.jdkStorageLocation)
        parameters.caCerts.set(extension.caCerts)
        parameters.targetName.set(project.provider { konanTarget.name })
        parameters.targetArchitecture.set(project.provider { konanTarget.architecture.name })
    }
    return project.layout.dir(project.provider { home.get() })
}

/**
 * Operating system of this binary's target, which is the one a JDK linked into it has to be built for.
 *
 * Kotlin/Native has no `Family` for a Linux libc, so a musl target is told apart from a glibc one by
 * the target name.
 */
public val NativeBinary.jdkOperatingSystem: OperatingSystem
    get() {
        val konanTarget = target.konanTarget
        return when (konanTarget.family) {
            Family.LINUX ->
                if (konanTarget.name.contains("musl", ignoreCase = true)) {
                    OperatingSystem.LINUX_MUSL
                } else {
                    OperatingSystem.LINUX_GLIBC
                }
            Family.MINGW -> OperatingSystem.WINDOWS
            Family.OSX -> OperatingSystem.MACOS
            else -> error(
                "Cannot link a JDK into the ${konanTarget.name} target: JDKs are published for linux, " +
                    "macos and windows only, while ${konanTarget.name} is a ${konanTarget.family} target. " +
                    "Do not call linkJvm() for it."
            )
        }
    }

/**
 * Architecture of this binary's target, which is the one a JDK linked into it has to be built for.
 */
public val Architecture.jdkArchitecture: Arch
    get() = when (this) {
        Architecture.X64 -> Arch.X86_64
        Architecture.X86 -> Arch.X86
        Architecture.ARM64 -> Arch.AARCH64
        else -> error(
            "Cannot link a JDK into a $this target: no JDK distribution publishes a 32-bit ARM build, " +
                "so there is nothing to download. Do not call linkJvm() for it."
        )
    }

/**
 * Provides the home directory of a JDK, downloading and unpacking it if it is not unpacked yet.
 *
 * Its parameters are all read when it is evaluated rather than while configuring, which is what lets
 * `jniLibraries { }` sit anywhere in a build script.
 */
internal abstract class JdkValueSource : ValueSource<File, JdkValueSource.Parameters> {

    /**
     * Which JDK to provide.
     *
     * @property exactVersion the exact version to ask for, or empty to ask for the latest build of a
     * major [majorVersion] instead.
     */
    internal interface Parameters : ValueSourceParameters {
        val distribution: Property<String>
        val majorVersion: Property<Int>
        val exactVersion: Property<String>
        val operatingSystem: Property<String>
        val architecture: Property<String>
        val baseUrl: Property<String>
        val storageLocation: DirectoryProperty
        val caCerts: MapProperty<String, String>
        val targetName: Property<String>
        val targetArchitecture: Property<String>
    }

    override fun obtain(): File {
        val distribution = JdkDistributionName.fromStringThrowing(parameters.distribution.get())
        val os = OperatingSystem.fromStringThrowing(parameters.operatingSystem.get())
        val arch = Arch.fromStringThrowing(parameters.architecture.get())
        val exact = parameters.exactVersion.orNull?.ifEmpty { null }
        val major = parameters.majorVersion.get()

        val home = parameters.storageLocation.get().asFile.resolve(
            homeDirectoryName(distribution, exact, major, os, arch, parameters.caCerts.get())
        )
        if (!File(home, RELEASE_FILE).isFile) {
            val archive = if (exact == null) latestArchive(distribution, major, os, arch)
            else exactArchive(distribution, exact, os, arch)
            unpack(archive, home)
        }
        checkJdk(
            home,
            parameters.targetName.get(),
            Architecture.valueOf(parameters.targetArchitecture.get()),
            major,
        )
        return home
    }

    /**
     * Archive of the latest build of a major [version].
     */
    private fun latestArchive(
        distribution: JdkDistributionName,
        version: Int,
        os: OperatingSystem,
        arch: Arch,
    ): Archive {
        if (distribution != JdkDistributionName.AMAZON_CORRETTO) {
            error(
                "Cannot ask $distribution for the latest JDK $version: it publishes no build under a " +
                    "URL by major version alone, so the version has to be given exactly, as in " +
                    "downloadCompatibleJdk(\"17.0.13.11.1\")"
            )
        }
        val format = if (os == OperatingSystem.WINDOWS) "zip" else "tar.gz"
        val osName = when (os) {
            OperatingSystem.LINUX_GLIBC -> "linux"
            OperatingSystem.LINUX_MUSL -> "alpine-linux"
            OperatingSystem.MACOS -> "macosx"
            OperatingSystem.WINDOWS -> "windows"
        }
        val archName = when (arch) {
            Arch.X86_64 -> "x64"
            Arch.AARCH64 -> "aarch64"
            Arch.X86 -> error("Cannot ask $distribution for a JDK of $osName $arch: it publishes none")
        }
        return Archive(
            "${parameters.baseUrl.get().trimEnd('/')}/downloads/latest/" +
                "${distribution.uiName()}-$version-$archName-$osName-jdk.$format",
            format,
        )
    }

    /**
     * Archive of an exact [version].
     */
    private fun exactArchive(
        distribution: JdkDistributionName,
        version: String,
        os: OperatingSystem,
        arch: Arch,
    ): Archive {
        val path = jdkDistribution(distribution).path(
            JdkRelease.builder().version(version).os(os).arch(arch).build()
        )
        val format = path.extension().toString()
        return Archive("${parameters.baseUrl.get().trimEnd('/')}/${path.filename()}.$format", format)
    }

    /**
     * Directory the JDK is unpacked into, named the way `gradle-jdks` names it for an exact version, so
     * that the two share one copy on disk.
     */
    private fun homeDirectoryName(
        distribution: JdkDistributionName,
        exact: String?,
        version: Int,
        os: OperatingSystem,
        arch: Arch,
        caCerts: Map<String, String>,
    ): String =
        if (exact == null) {
            "$distribution-$version-${os.uiName()}-${arch.uiName()}-latest"
        } else {
            val spec = JdkSpec.builder()
                .distributionName(distribution)
                .release(JdkRelease.builder().version(exact).os(os).arch(arch).build())
                // The certificates take no part in unpacking, but they are part of the name gradle-jdks
                // gives this JDK, and taking them from the same place is what makes the two agree.
                .caCerts(CaCerts.from(caCerts))
                .build()
            "$distribution-$exact-${spec.consistentShortHash()}"
        }

    /**
     * Downloads the archive and unpacks the JDK in it into [home].
     */
    private fun unpack(archive: Archive, home: File) {
        val logger = Logging.getLogger(JdkValueSource::class.java)
        val file = Files.createTempFile("jni-jdk-", ".${archive.format}")
        // Created only now, so that a build that needs no JDK leaves no directory behind either.
        Files.createDirectories(home.toPath().parent)
        // Staged next to where the JDK goes, so that moving it into place stays on one file system.
        val staging = Files.createTempDirectory(home.parentFile.toPath(), "${home.name}-staging-")
        try {
            logger.lifecycle("Downloading JDK from {}", archive.url)
            URI(archive.url).toURL().openStream().use { input ->
                Files.newOutputStream(file).use { output -> input.copyTo(output) }
            }
            if (archive.format == "zip") {
                unzip(file, staging)
            } else {
                untar(file, staging)
            }
            val unpackedHome = findJdkHome(staging.toFile(), archive.url)
            logger.lifecycle("Unpacking JDK into {}", home)
            moveInto(unpackedHome.toPath(), home.toPath())
        } finally {
            staging.toFile().deleteRecursively()
            Files.deleteIfExists(file)
        }
    }

    /**
     * Writes every entry of a `.zip` archive under [into].
     */
    private fun unzip(archive: Path, into: Path) {
        ZipFile.builder().setPath(archive).get().use { zip ->
            val entries = zip.entries
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val target = into.resolve(entry.name)
                when {
                    entry.isDirectory -> Files.createDirectories(target)
                    entry.isUnixSymlink -> link(target, zip.getInputStream(entry).readBytes().decodeToString())
                    else -> write(target, zip.getInputStream(entry), executable = entry.unixMode.isExecutable())
                }
            }
        }
    }

    /**
     * Writes every entry of a tar archive under [into].
     */
    private fun untar(archive: Path, into: Path) {
        Files.newInputStream(archive).use { input ->
            TarArchiveInputStream(GZIPInputStream(input).buffered()).use { tar ->
                var entry: TarArchiveEntry? = tar.nextEntry
                while (entry != null) {
                    val target = into.resolve(entry.name)
                    when {
                        entry.isDirectory -> Files.createDirectories(target)
                        entry.isSymbolicLink -> link(target, tar.readBytes().decodeToString().trim())
                        else -> write(target, tar, executable = entry.mode.isExecutable())
                    }
                    entry = tar.nextEntry
                }
            }
        }
    }

    /**
     * Writes one entry of an archive, marking it executable when the archive says it is.
     */
    private fun write(target: Path, archive: InputStream, executable: Boolean = false) {
        Files.createDirectories(target.parent)
        Files.newOutputStream(target).use { output -> archive.copyTo(output) }
        if (executable) makeExecutable(target)
    }

    /**
     * Whether a file mode read from an archive marks its file executable.
     */
    private fun Int.isExecutable(): Boolean = this and 0b111 != 0

    /**
     * Marks [target] executable, where the file system can.
     */
    private fun makeExecutable(target: Path) {
        try {
            Files.setPosixFilePermissions(
                target,
                Files.getPosixFilePermissions(target) + PosixFilePermission.OWNER_EXECUTE,
            )
        } catch (e: UnsupportedOperationException) {
            // A file system without POSIX permissions, such as one on Windows.
        }
    }

    /**
     * A symlink to [linkTo], or a plain file naming it where the file system has no symlinks.
     */
    private fun link(target: Path, linkTo: String) {
        try {
            Files.createSymbolicLink(target, Path.of(linkTo))
        } catch (e: UnsupportedOperationException) {
            Files.writeString(target, linkTo)
        } catch (e: FileAlreadyExistsException) {
            // A second entry for a link already made.
        }
    }

    /**
     * Moves an unpacked directory into place, a file at a time where a move is not possible.
     */
    private fun moveInto(from: Path, to: Path) {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE)
            return
        } catch (e: AtomicMoveNotSupportedException) {
            // A file system without atomic moves, which also means a plain one is the best it offers.
        }
        copyInto(from, to)
        from.toFile().deleteRecursively()
    }

    /**
     * Copies a directory, as are the symlinks in it.
     */
    private fun copyInto(from: Path, into: Path) {
        Files.walkFileTree(from, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.createDirectories(into.resolve(from.relativize(dir).toString()))
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                val target = into.resolve(from.relativize(file).toString())
                // A symlink is copied as what it points at: the JDK is only linked against, so having
                // the file rather than the link is all that is needed of it.
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING)
                return FileVisitResult.CONTINUE
            }
        })
    }

    /**
     * Home directory inside the unpacked archive, which is the directory holding the [RELEASE_FILE].
     */
    private fun findJdkHome(unpacked: File, url: String): File =
        unpacked.walkTopDown()
            .filter { File(it, RELEASE_FILE).isFile }
            // The outermost directory holding a `release` file is the home, should there be several.
            .minByOrNull { it.toPath().nameCount }
            ?: error(
                "The archive downloaded from $url does not contain a JDK: there is no $RELEASE_FILE " +
                    "file anywhere under $unpacked"
            )

    /**
     * An archive to download, and how to unpack it.
     */
    private class Archive(val url: String, val format: String)
}

/**
 * URL layout of the named distribution, for a JDK of an exact version.
 */
private fun jdkDistribution(name: JdkDistributionName): JdkDistribution =
    callOn(newJdkDistributions(), "get", name) as JdkDistribution

/**
 * The URL layouts of the distributions, for the `jdks` extension to be created with.
 */
private fun newJdkDistributions(): Any = construct("com.palantir.gradle.jdks.JdkDistributions")

/**
 * This name as a [JdkDistributionName], listing the known ones when it is not one of them.
 */
private fun String.toJdkDistribution(): JdkDistributionName {
    val parsed = JdkDistributionName.fromString(this)
    return if (parsed.isPresent) {
        parsed.get()
    } else {
        error(
            "Unknown JDK distribution '$this'; expected one of " +
                JdkDistributionName.values().joinToString { "'${it.uiName()}'" }
        )
    }
}

/**
 * Calls the constructor of the named class, failing with an explanation when it has moved.
 */
private fun construct(className: String): Any = try {
    val constructor = loadType(className).declaredConstructors.single { it.parameterCount == 0 }
    constructor.isAccessible = true
    constructor.newInstance()
} catch (e: ReflectiveOperationException) {
    throw gradleJdksUnexpectedShape(className, e)
}

/**
 * Calls the static [methodName] of the named class, failing with an explanation when it has moved.
 */
private fun callStatic(className: String, methodName: String, vararg arguments: Any): Any = try {
    val method = loadType(className).declaredMethods.single { it.name == methodName && it.accepts(arguments) }
    method.isAccessible = true
    method.invoke(null, *arguments)
} catch (e: ReflectiveOperationException) {
    throw gradleJdksUnexpectedShape("$className.$methodName", e)
}

/**
 * Calls [methodName] on [receiver], failing with an explanation when either has moved.
 */
private fun callOn(receiver: Any, methodName: String, vararg arguments: Any): Any = try {
    val method = receiver.javaClass.declaredMethods.single { it.name == methodName && it.accepts(arguments) }
    method.isAccessible = true
    method.invoke(receiver, *arguments)
} catch (e: ReflectiveOperationException) {
    throw gradleJdksUnexpectedShape("${receiver.javaClass.name}.$methodName", e)
}

private fun loadType(className: String): Class<*> =
    Class.forName(className, true, JdkManager::class.java.classLoader)

/**
 * Whether this member takes exactly [arguments], matched against the declared parameter types rather
 * than looked up by them.
 */
private fun java.lang.reflect.Executable.accepts(arguments: Array<out Any>): Boolean =
    parameterTypes.size == arguments.size &&
        parameterTypes.indices.all { parameterTypes[it].isInstance(arguments[it]) }

/**
 * Reports that `gradle-jdks` is not shaped the way this plugin expects, which happens when it changes
 * in a version newer than the one this plugin was built against.
 */
private fun gradleJdksUnexpectedShape(what: String, cause: ReflectiveOperationException) =
    IllegalStateException(
        "Cannot use gradle-jdks to download a JDK: $what is not accessible the way this plugin " +
            "expects. Pass a javaHome to linkJvm() instead, or report this so that the plugin can be " +
            "updated for this version of gradle-jdks.",
        cause
    )
