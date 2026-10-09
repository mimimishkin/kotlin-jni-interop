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
import io.foojay.api.discoclient.pkg.Scope
import io.github.mimimishkin.jni.binding.plugin.camelCase
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.the
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBinary
import org.jetbrains.kotlin.konan.target.Architecture
import org.jetbrains.kotlin.konan.target.Family
import java.io.File
import java.net.URI
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import javax.inject.Inject

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
 * The JDK is looked up through the foojay Disco API, so a build that needs one needs network access. A
 * major version resolves to whichever build the distribution published last at the time of the first
 * download, and to that same one afterwards, since the JDK is then reused from disk. Pass a version as a
 * [String] to pin it.
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
    return jdkProvider(distribution, "", expectedJdkVersion, project.provider { "" }, os, arch)
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
    jdkProvider(distribution, version.toString(), project.provider { version }, project.provider { "" }, os, arch)

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
    jdkProvider(distribution, version, project.provider { 0 }, project.provider { version }, os, arch)

/**
 * Provider of the home directory of a JDK, which an [UnpackJdkTask] downloads when the linker needs it.
 *
 * @param majorVersion the major version to ask for when [exactVersion] is empty.
 * @param exactVersion the exact version to ask for, or empty to ask for the latest build of a major
 * version instead.
 * @param namedVersion the version as this call names it, empty when it is
 * [JniLibProducerExtension.expectedJdkVersion] and so not known while configuring. It is what tells two
 * requests for the same platform apart.
 */
private fun NativeBinary.jdkProvider(
    distribution: String,
    namedVersion: String,
    majorVersion: Provider<Int>,
    exactVersion: Provider<String>,
    os: JdkOperatingSystem,
    arch: JdkArchitecture,
): Provider<Directory> {
    val konanTarget = target.konanTarget
    val name = camelCase("unpackJdk", distribution, os.apiString, arch.apiString, namedVersion)

    // Every binary asking for the same JDK gets the same task, so that it is downloaded once.
    val unpack = project.tasks.let { tasks ->
        if (name in tasks.names) {
            tasks.named(name, UnpackJdkTask::class)
        } else {
            tasks.register(name, UnpackJdkTask::class) { task ->
                task.description = "Downloads and unpacks the JDK $konanTarget is linked against"

                task.distribution.set(distribution)
                task.majorVersion.set(majorVersion)
                task.exactVersion.set(exactVersion)
                task.operatingSystem.set(os.name)
                task.architecture.set(arch.name)
                task.home.fileValue(
                    project.jdkStorageLocation().resolve(
                        camelCase("jdk", distribution, os.apiString, arch.apiString, namedVersion)
                    )
                )
            }
        }
    }

    // Named rather than inferred: the linker reads the JDK's home as a string path, which carries no
    // dependency of its own.
    linkTaskProvider.configure { it.dependsOn(unpack) }

    return project.layout.dir(unpack.map { it.home.get().asFile })
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
 * Downloads the JDK a binary is linked against and unpacks it, as [downloadCompatibleJdk] arranges.
 *
 * The request is the task's input and the unpacked JDK its output, so what an earlier run of this task
 * put in that directory is reused as it is.
 */
@DisableCachingByDefault(because = "the archive is downloaded by the task, so its contents are not a cache key")
internal abstract class UnpackJdkTask : DefaultTask() {

    /**
     * Distribution to download from, for example `"corretto"`.
     */
    @get:Input
    abstract val distribution: Property<String>

    /**
     * Major Java version to ask for, unless [exactVersion] names one.
     */
    @get:Input
    abstract val majorVersion: Property<Int>

    /**
     * Exact version to ask for, as the distribution publishes it, or empty to ask for the latest build
     * of [majorVersion].
     */
    @get:Input
    abstract val exactVersion: Property<String>

    /**
     * Operating system to build the JDK for.
     */
    @get:Input
    abstract val operatingSystem: Property<String>

    /**
     * Architecture to build the JDK for.
     */
    @get:Input
    abstract val architecture: Property<String>

    /**
     * Directory the JDK is unpacked into, and the task's output.
     */
    @get:OutputDirectory
    abstract val home: DirectoryProperty

    @get:Inject
    abstract val archives: ArchiveOperations

    @get:Inject
    abstract val fileSystems: FileSystemOperations

    @TaskAction
    fun unpack() {
        val archive = resolve()
        val home = home.get().asFile
        val file = Files.createTempFile("jni-jdk-", archive.format.fileEndings.first())
        // Staged next to where the JDK goes, so that moving it into place stays on one file system.
        val staging = Files.createTempDirectory(home.toPath().parent, "${home.name}-staging-")
        try {
            logger.lifecycle("Downloading JDK from {}", archive.url)
            URI(archive.url).toURL().openStream().use { input ->
                Files.copy(input, file)
            }
            val tree = when (archive.format) {
                ArchiveType.ZIP -> archives.zipTree(file)
                ArchiveType.TAR_GZ, ArchiveType.TGZ -> archives.tarTree(file)
                else -> error("Unsupported archive format: ${archive.format}")
            }
            fileSystems.copy { spec ->
                spec.from(tree)
                spec.into(staging.toFile())
            }
            val unpackedHome = findJdkHome(staging.toFile(), archive.url)
            logger.lifecycle("Unpacking JDK into {}", home)
            home.deleteRecursively()
            moveInto(unpackedHome.toPath(), home.toPath())
        } finally {
            staging.toFile().deleteRecursively()
            Files.deleteIfExists(file)
        }
    }

    /**
     * Asks the foojay Disco API for the JDK this task was asked for, and where to download it from.
     */
    private fun resolve(): Archive {
        val distributionName = distribution.get()
        val distribution = DiscoClient.getDistributionFromText(distributionName)
            ?: error(
                "Unknown JDK distribution '$distributionName': the foojay Disco API does not know a " +
                        "distribution by that name"
            )
        val os = JdkOperatingSystem.valueOf(operatingSystem.get())
        val arch = JdkArchitecture.valueOf(architecture.get())
        val exact = exactVersion.get().ifEmpty { null }
        val major = majorVersion.get()
        val version = exact ?: major

        val disco = DiscoClient()
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

        val archive = pkgs.asSequence()
            .filter { pkg -> pkg.archiveType in listOf(ArchiveType.ZIP, ArchiveType.TAR_GZ, ArchiveType.TGZ) }
            .filter { pkg -> exact == null || pkg.fileName.contains(exact) }
            .maxByOrNull { pkg -> pkg.javaVersion }
            ?.let { pkg -> Archive(disco.getPkgDirectDownloadUri(pkg.id), pkg.archiveType) }
            ?: error(
                "The foojay Disco API has no JDK ${exact ?: major} for $distributionName " +
                        "on ${os.apiString} ${arch.apiString}"
            )

        check(archive.url.isNotEmpty()) { "The foojay Disco API returned no download URL for JDK $version" }
        return archive
    }

    /**
     * Version to ask the Disco API for, which for an exact one is only its feature version.
     */
    private fun requestedVersion(exact: String?, major: Int): VersionNumber =
        if (exact == null) {
            VersionNumber(major)
        } else {
            VersionNumber(VersionNumber.fromText(exact).feature.getAsInt())
        }

    /**
     * The libc the Disco API should filter an operating system by, which only matters where several are
     * published.
     */
    private fun libcTypeOf(os: JdkOperatingSystem): LibCType = when (os) {
        JdkOperatingSystem.LINUX -> LibCType.GLIBC
        JdkOperatingSystem.LINUX_MUSL, JdkOperatingSystem.ALPINE_LINUX -> LibCType.MUSL
        else -> LibCType.NONE
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
     * Moves an unpacked directory into place, a file at a time where a move is not possible.
     */
    private fun moveInto(from: Path, to: Path) {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE)
            return
        } catch (e: AtomicMoveNotSupportedException) {
            // A file system without atomic moves, which also means a plain one is the best it offers.
        }
        Files.walkFileTree(from, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.createDirectories(to.resolve(from.relativize(dir).toString()))
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.copy(file, to.resolve(from.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING)
                return FileVisitResult.CONTINUE
            }
        })
        from.toFile().deleteRecursively()
    }

    /**
     * A JDK archive to download, and the form it comes in.
     */
    private class Archive(val url: String, val format: ArchiveType)
}