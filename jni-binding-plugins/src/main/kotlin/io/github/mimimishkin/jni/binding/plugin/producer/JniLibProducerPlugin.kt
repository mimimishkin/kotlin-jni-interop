package io.github.mimimishkin.jni.binding.plugin.producer

import io.github.mimimishkin.jni.binding.BuildConfig
import io.github.mimimishkin.jni.binding.plugin.JniBindingAttributes
import io.github.mimimishkin.jni.binding.plugin.camelCase
import io.github.mimimishkin.jni.binding.plugin.commonSourceSet
import io.github.mimimishkin.jni.binding.plugin.finalName
import io.github.mimimishkin.jni.binding.plugin.mostCommonSourceSet
import io.github.mimimishkin.jni.binding.plugin.producer.JniExportMethod.RegisterNatives
import org.gradle.api.Project
import org.gradle.api.attributes.Usage
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Sync
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.FilesSubpluginOption
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

/**
 * Plugin for producing JNI libraries.
 *
 * The goal of this plugin is to greatly simplify the process of writing JNI libraries in Kotlin.
 */
public class JniLibProducerPlugin : KotlinCompilerPluginSupportPlugin {
    public companion object {
        /**
         * Name of the Gradle extension created by this plugin.
         */
        public const val EXTENSION_NAME: String = "jniLibraries"
    }

    override fun apply(target: Project) {
        @Suppress("UnstableApiUsage")
        target.configurations.consumable("jniLibrariesElements") {
            it.attributes.attribute(Usage.USAGE_ATTRIBUTE, target.objects.named("jni-binding"))
        }

        target.extensions.create<JniLibProducerExtension>(EXTENSION_NAME)

        // JNI binding dependencies must end up in a source set shared by the native compilations this plugin is
        // applied to. The KMP target/source-set model (dependsOn graph) is fully linked only after the project is
        // configured, so resolve the target source set lazily.
        target.afterEvaluate { it.addJniLibrariesAsDependencies() }
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean =
        kotlinCompilation is KotlinNativeCompilation &&
            // Only `main` is exported. The `test` compilation would otherwise publish a second, unusable set of
            // artifacts under the same target, and a consumer - which has one binding per target, not per producer
            // compilation - has no way to pick between them.
            kotlinCompilation.compilationName == KotlinCompilation.MAIN_COMPILATION_NAME

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val compilation = kotlinCompilation as KotlinNativeCompilation
        val project = compilation.project
        val config = project.the<JniLibProducerExtension>()

        val buildDir = project.layout.buildDirectory
        val target = compilation.target.name
        val flavor = compilation.compilationName
        val binariesDir = buildDir.dir("jniBindings/binaries/$target")
        val actualsInfoDir = buildDir.dir("jniBindings/actualsInfo/$target")

        // Work around KGP's native plugin classpath being non-transitive (KT-53477).
        val pluginClasspathConfiguration = camelCase(
            "kotlinCompilerPluginClasspath",
            compilation.target.disambiguationClassifier ?: target,
            flavor,
        )
        project.configurations.named(pluginClasspathConfiguration) {
            it.isTransitive = true
        }

        val actualsFile = actualsInfoDir.map { it.file("actuals.json") }

        // The compiler fills `actuals.json` while it runs, but a compilation with no sources is skipped as
        // `NO-SOURCE` and never runs it. Create an empty contract in that case, so a consumer that binds this
        // producer sees "no @JniActual declared" instead of a missing file it cannot act on.
        config.exportJniBindingTaskProvider.configure { task ->
            task.dependsOn(compilation.compileTaskProvider)
            task.doLast {
                val file = actualsFile.get().asFile
                if (!file.isFile) {
                    file.parentFile?.mkdirs()
                    file.writeText("[]")
                }
            }
        }

        project.artifacts.add("jniLibrariesElements", actualsFile) {
            // Resolving this artifact in a consumer must run the whole `exportJniBinding` build so bindings
            // are always up to date. This is `builtBy`-based (working for included/isolated builds), not a
            // directly declared task dependency from the consumer.
            it.builtBy(compilation.compileTaskProvider, config.exportJniBindingTaskProvider)
            it.type = JniBindingAttributes.ARTIFACT_TYPE_ACTUALS_INFO
            it.classifier = target
        }

        (kotlinCompilation.target.binaries).configureEach { binary ->
            if (binary.compilation != compilation) return@configureEach
            if (binary.buildType != NativeBuildType.RELEASE) return@configureEach

            // Use `Sync` instead of `Copy` to ensure that the binary is the only one file in dir.
            val exportBinary = project.tasks.register<Sync>(camelCase(binary.baseName, target, "JniLibrary")) {
                group = null
                description = "Copies ${binary.finalName} to the $target binding directory"

                from(binary.outputDirectory) {
                    include(binary.finalName)
                }

                into(binariesDir)

                dependsOn(binary.linkTaskProvider)
            }

            config.exportJniBindingTaskProvider.configure {
                it.dependsOn(exportBinary)
            }

            val output = binariesDir.map { it.file(binary.finalName) }
            project.artifacts.add("jniLibrariesElements", output) {
                it.builtBy(exportBinary)
                it.type = JniBindingAttributes.ARTIFACT_TYPE_JNI_LIBRARY
                it.classifier = binary.baseName
            }
        }

        return project.provider {
            val config = project.the<JniLibProducerExtension>()
            listOf(
                SubpluginOption("enabled", "true"),
                SubpluginOption("expectedJdkVersion", config.expectedJdkVersion.get().toString()),
                SubpluginOption("allowSeveralHooks", config.allowSeveralHooks.get().toString()),
                SubpluginOption("useRegisterNatives", (config.exportMethod.get() == RegisterNatives).toString()),
                FilesSubpluginOption("actualsFile", listOf(actualsFile.get().asFile)),
            )
        }
    }

    override fun getCompilerPluginId(): String = BuildConfig.PRODUCER_ARTIFACT

    override fun getPluginArtifact(): SubpluginArtifact {
        return SubpluginArtifact(
            groupId = BuildConfig.GROUP,
            artifactId = BuildConfig.PRODUCER_ARTIFACT,
            version = BuildConfig.VERSION
        )
    }

    private fun Project.addJniLibrariesAsDependencies() {
        val kotlin = extensions.findByType<KotlinMultiplatformExtension>() ?: return
        val nativeCompilations = kotlin.targets
            .flatMap { it.compilations }
            .filterIsInstance<KotlinNativeCompilation>()
            .ifEmpty { return }

        // `commonMain`/`commonTest` is only shared by these dependencies if nothing else compiles against it: with a
        // JVM target next to the native ones, those dependencies must not reach that target's compilation. The lowest
        // common ancestor of the native source sets (e.g. `nativeMain`) is the fallback, and the one used in a
        // project of native targets only.
        val allTargetsAreNative = kotlin.targets.none {
            it.platformType != KotlinPlatformType.native && it.platformType != KotlinPlatformType.common
        }

        for (compilationName in setOf(KotlinCompilation.MAIN_COMPILATION_NAME, KotlinCompilation.TEST_COMPILATION_NAME)) {
            val sourceSet = mostCommonSourceSet(
                compilations = nativeCompilations.filter { it.compilationName == compilationName },
                common = if (allTargetsAreNative) commonSourceSet(kotlin, compilationName) else null,
            ) ?: continue
            sourceSet.dependencies {
                implementation(BuildConfig.WRAPPER_ID)
                implementation(BuildConfig.ANNOTATIONS_ID)
            }
        }
    }
}