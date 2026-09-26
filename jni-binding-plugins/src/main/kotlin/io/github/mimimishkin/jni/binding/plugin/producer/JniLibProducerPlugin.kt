package io.github.mimimishkin.jni.binding.plugin.producer

import io.github.mimimishkin.jni.binding.BuildConfig
import io.github.mimimishkin.jni.binding.plugin.JniBindingAttributes
import io.github.mimimishkin.jni.binding.plugin.camelCase
import io.github.mimimishkin.jni.binding.plugin.finalName
import io.github.mimimishkin.jni.binding.plugin.producer.JniExportMethod.RegisterNatives
import org.gradle.api.Project
import org.gradle.api.attributes.Usage
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Sync
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.plugin.FilesSubpluginOption
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
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
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean =
        kotlinCompilation is KotlinNativeCompilation

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val compilation = kotlinCompilation as KotlinNativeCompilation
        val project = compilation.project
        val config = project.the<JniLibProducerExtension>()

        compilation.defaultSourceSet.dependencies {
            implementation(BuildConfig.WRAPPER_ID)
            compileOnly(BuildConfig.ANNOTATIONS_ID)
            if (compilation.compilationName == KotlinCompilation.MAIN_COMPILATION_NAME) {
                // Using compileOnly dependencies in these targets is not currently supported, because compileOnly
                // dependencies must be present during the compilation of projects that depend on this project.
                // To ensure consistent compilation behavior, compileOnly dependencies should be exposed as api
                // dependencies.
                api(BuildConfig.ANNOTATIONS_ID)
            } else {
                implementation(BuildConfig.ANNOTATIONS_ID)
            }
        }

        val buildDir = project.layout.buildDirectory
        val target = compilation.target.name
        val flavor = compilation.compilationName
        val binariesDir = buildDir.dir("jniBindings/binaries/$target/$flavor")
        val actualsInfoDir = buildDir.dir("jniBindings/actualsInfo/$target/$flavor")

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

            // use `Sync` instead of `Copy` to ensure that the binary is the only one file in dir
            val exportBinary = project.tasks.register<Sync>(binary.name + "JniLibrary") {
                group = null
                description = "Copies ${binary.finalName} to $target/$flavor binding directory"

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
                SubpluginOption("jniVersion", config.jniVersion.get().toString()),
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
}