package io.github.mimimishkin.jni.binding.plugin.producer

import eu.hansolo.jdktools.ArchiveType
import eu.hansolo.jdktools.Architecture as JdkArchitecture
import eu.hansolo.jdktools.Bitness
import eu.hansolo.jdktools.Latest
import eu.hansolo.jdktools.LibCType
import eu.hansolo.jdktools.Match
import eu.hansolo.jdktools.OperatingSystem as JdkOperatingSystem
import eu.hansolo.jdktools.PackageType
import eu.hansolo.jdktools.ReleaseStatus
import eu.hansolo.jdktools.TermOfSupport
import eu.hansolo.jdktools.versioning.VersionNumber
import io.foojay.api.discoclient.DiscoClient
import io.foojay.api.discoclient.pkg.Pkg
import io.foojay.api.discoclient.pkg.Scope
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.Logging
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
import java.net.URI
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermission
import java.util.zip.GZIPInputStream

/**
 * Distribution [downloadCompatibleJdk] uses when it is not told otherwise.
 *
 * Amazon Corretto publishes the widest set of platform combinations, which is what matters for a
 * target that is not the host.
 */
public const val DEFAULT_JDK_DISTRIBUTION: String = "corretto"

/**
 * File every JDK has at the root of its home directory, and what identifies one.
 */
private const val RELEASE_FILE: String = "release"

/**
 * Gradle property naming the directory JDKs are unpacked into, `$GRADLE_USER_HOME/jdks` by default.
 */
private const val JDK_STORAGE_LOCATION_PROPERTY: String = "jni.jdkStorageLocation"

/**
 * Provides the latest JDK of the Java version of [JniLibProducerExtension.expectedJdkVersion] built for the
 * platform of this binary's target, to link against.
 *
 * This is how a target that is not the host is linked, because Gradle serves Java toolchains for the
 * host only. The platform is worked out from the target itself, so only the version is left to say:
 *
 * ```kotlin
 * binaries {
 *     sharedLib("native") {
 *         linkJvm()                                         // the host, where a toolchain is enough
 *         linkJvm(downloadCompatibleJdk())                  // any other target, expectedJdkVersion
 *         linkJvm(downloadCompatibleJdk(21))                // or a named major version
 *         linkJvm(downloadCompatibleJdk("17.0.13.11.1"))    // or an exact build
 *     }
 * }
 * ```
 *
 * The JDK is looked up through the foojay Disco API, which means a build that downloads one needs
 * network access. A major version resolves to whichever build the distribution published last at the
 * time of the first download, and to that same one afterwards, since the JDK is then reused from
 * disk. Pass a version as a [String] to pin it.
 *
 * @param os operating system to build the JDK for, which is this target's one by default.
 * @param arch architecture to build the JDK for, which is this target's one by default.
 * @param distribution distribution to download from, for example `"corretto"` or `"zulu"`.
 */
public fun NativeBinary.downloadCompatibleJdk(
    os: JdkOperatingSystem = jdkOperatingSystem,
    arch: JdkArchitecture = target.konanTarget.architecture.jdkArchitecture,
    distribution: String = DEFAULT_JDK_DISTRIBUTION,
): Provider<Directory> {
    val expectedJdkVersion = project.the<JniLibProducerExtension>().expectedJdkVersion
    return jdkProvider(distribution, expectedJdkVersion, project.provider { "" }, os, arch)
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
    os: JdkOperatingSystem = jdkOperatingSystem,
    arch: JdkArchitecture = target.konanTarget.architecture.jdkArchitecture,
): Provider<Directory> =
    jdkProvider(distribution, project.provider { version }, project.provider { "" }, os, arch)

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
    os: JdkOperatingSystem = jdkOperatingSystem,
    arch: JdkArchitecture = target.konanTarget.architecture.jdkArchitecture,
): Provider<Directory> =
    jdkProvider(distribution, project.provider { 0 }, project.provider { version }, os, arch)

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
    os: JdkOperatingSystem,
    arch: JdkArchitecture,
): Provider<Directory> {
    val konanTarget = target.konanTarget
    val home = project.providers.of(JdkValueSource::class.java) { spec ->
        val parameters = spec.parameters
        parameters.distribution.set(distributionName)
        parameters.majorVersion.set(majorVersion)
        parameters.exactVersion.set(exactVersion)
        parameters.operatingSystem.set(os.name)
        parameters.architecture.set(arch.name)
        parameters.storageLocation.fileValue(project.jdkStorageLocation())
        parameters.targetName.set(konanTarget.name)
        parameters.targetArchitecture.set(konanTarget.architecture.name)
    }
    return project.layout.dir(project.provider { home.get() })
}

/**
 * Directory JDKs are unpacked into, `$GRADLE_USER_HOME/jdks` unless overridden by the
 * [JDK_STORAGE_LOCATION_PROPERTY] Gradle property.
 */
private fun Project.jdkStorageLocation(): File {
    val configured = providers.gradleProperty(JDK_STORAGE_LOCATION_PROPERTY).orNull
    return if (configured.isNullOrEmpty()) File(gradle.gradleUserHomeDir, "jdks") else File(configured)
}

/**
 * Operating system of this binary's target, which is the one a JDK linked into it has to be built for.
 *
 * Kotlin/Native has no `Family` for a Linux libc, so a musl target is told apart from a glibc one by
 * the target name.
 */
public val NativeBinary.jdkOperatingSystem: JdkOperatingSystem
    get() {
        val konanTarget = target.konanTarget
        return when (konanTarget.family) {
            Family.LINUX -> JdkOperatingSystem.LINUX
            Family.MINGW -> JdkOperatingSystem.WINDOWS
            Family.OSX -> JdkOperatingSystem.MACOS
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
public val Architecture.jdkArchitecture: JdkArchitecture
    get() = when (this) {
        Architecture.X64 -> JdkArchitecture.X86_64
        Architecture.X86 -> JdkArchitecture.X86
        Architecture.ARM64 -> JdkArchitecture.AARCH64
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
        val storageLocation: DirectoryProperty
        val targetName: Property<String>
        val targetArchitecture: Property<String>
    }

    override fun obtain(): File {
        val distribution = parameters.distribution.get()
        val os = JdkOperatingSystem.valueOf(parameters.operatingSystem.get())
        val arch = JdkArchitecture.valueOf(parameters.architecture.get())
        val exact = parameters.exactVersion.orNull?.ifEmpty { null }
        val major = parameters.majorVersion.get()

        val jdk = resolve(distribution, exact, major, os, arch)
        val home = File(parameters.storageLocation.get().asFile, jdk.directoryName)
        if (!File(home, RELEASE_FILE).isFile) {
            unpack(Archive(jdk.url, jdk.format), home)
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
     * Asks the foojay Disco API for the JDK matching [exact], or the latest build of [major] when
     * [exact] is null, and for the URL to download it from.
     */
    private fun resolve(
        distributionName: String,
        exact: String?,
        major: Int,
        os: JdkOperatingSystem,
        arch: JdkArchitecture,
    ): ResolvedJdk {
        val disco = DiscoClient()
        val distribution = DiscoClient.getDistributionFromText(distributionName)
            ?: error(
                "Unknown JDK distribution '$distributionName': the foojay Disco API does not know a " +
                    "distribution by that name"
            )
        val pkgs = disco.getPkgs(
            listOf(distribution),
            requestedVersion(exact, major),
            if (exact == null) Latest.AVAILABLE else Latest.ALL_OF_VERSION,
            os,
            libcTypeOf(os),
            arch,
            Bitness.NONE,
            ArchiveType.NONE,
            PackageType.JDK,
            false,
            true,
            listOf(ReleaseStatus.GA, ReleaseStatus.EA),
            TermOfSupport.NONE,
            listOf(Scope.PUBLIC),
            Match.ANY,
        )
        // The API also offers `.deb`, `.rpm` and other installers, which are not what this source
        // downloads, and it matches an exact version only down to the release line of the build.
        val matching = pkgs
            .filter { it.isPackableArchive() }
            .filter { exact == null || it.fileName.contains(exact) }
        val wanted = if (exact == null) "a JDK $major" else "the JDK $exact"
        val pkg = matching.maxByOrNull { it.javaVersion }
            ?: error("The foojay Disco API has no $wanted for $distributionName on $os $arch")
        val url = disco.getPkgDirectDownloadUri(pkg.id)
        check(url.isNotEmpty()) { "The foojay Disco API returned no download URL for ${pkg.fileName}" }
        return ResolvedJdk(pkg.homeDirectoryName(), url, formatOf(pkg))
    }

    /**
     * Version to ask the Disco API for, which for [exact] is only its feature version.
     *
     * The API matches a version down to its release line, never down to a build, so an exact build is
     * asked for as the line it belongs to and picked out of the builds of that line afterwards.
     */
    private fun requestedVersion(exact: String?, major: Int): VersionNumber =
        if (exact == null) {
            VersionNumber(major)
        } else {
            VersionNumber(VersionNumber.fromText(exact).feature.getAsInt())
        }

    /**
     * Whether this package is a `.zip` or tar archive of the kind this source can unpack, rather than
     * an installer such as a `.deb` or `.rpm`.
     */
    private fun Pkg.isPackableArchive(): Boolean =
        archiveType == ArchiveType.ZIP || archiveType == ArchiveType.TAR_GZ || archiveType == ArchiveType.TGZ

    /**
     * How to unpack the archive of [pkg], which is the one the Disco API offers it as.
     */
    private fun formatOf(pkg: Pkg): String = when (pkg.archiveType) {
        ArchiveType.ZIP -> "zip"
        ArchiveType.TAR_GZ, ArchiveType.TGZ -> "tar.gz"
        else -> error(
            "Cannot unpack ${pkg.fileName}: only `.zip` and `.tar.gz` JDK archives are supported, " +
                "while this one is a ${pkg.archiveType}"
        )
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
 * The libc the Disco API should filter [os] by, which only matters where several are published.
 */
private fun libcTypeOf(os: JdkOperatingSystem): LibCType = when (os) {
    JdkOperatingSystem.LINUX -> LibCType.GLIBC
    JdkOperatingSystem.LINUX_MUSL, JdkOperatingSystem.ALPINE_LINUX -> LibCType.MUSL
    else -> LibCType.NONE
}

/**
 * A JDK the Disco API matched, and where to download it from.
 *
 * @property directoryName directory to unpack this JDK into, named after its archive.
 */
private class ResolvedJdk(val directoryName: String, val url: String, val format: String)

/**
 * Name of the directory this package's JDK is unpacked into, which is its archive name without the
 * extension, so that a JDK is only ever downloaded once.
 */
private fun Pkg.homeDirectoryName(): String =
    fileName
        .removeSuffix(".tar.gz")
        .removeSuffix(".tar.xz")
        .removeSuffix(".tgz")
        .removeSuffix(".zip")
        .ifEmpty { id }
