package io.github.mimimishkin.jni.binding.plugin.consumer

import io.github.mimimishkin.jni.binding.BuildConfig
import io.github.mimimishkin.jni.binding.BuildConfig.ANNOTATIONS_ID
import io.github.mimimishkin.jni.binding.plugin.disambiguateName
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.androidJvm
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.jvm
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

/**
 * Plugin for consuming JNI libraries.
 */
public class JniLibConsumerPlugin : KotlinCompilerPluginSupportPlugin {
    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
        return kotlinCompilation.platformType in listOf(jvm, androidJvm) && kotlinCompilation.jniLibrariesProvider.isPresent
    }

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        kotlinCompilation.defaultSourceSet.dependencies {
            compileOnly(ANNOTATIONS_ID)
        }

        val project = kotlinCompilation.project

        // Aggregate import task for this compilation. It depends on every library's `importJniLib*`, whose
        // resolution of the producer's artifacts triggers the producer's `exportJniBinding` (the producer declares
        // it as `builtBy` of its artifacts). No direct consumer -> producer task dependency is declared, so this
        // also works for isolated/included builds.
        val importTaskName = kotlinCompilation.disambiguateName("importJniBinding")
        val importTaskProvider = project.tasks.register(importTaskName) { task ->
            task.group = "jni interop"
            task.description = "Imports JNI bindings of this compilation, running the producers' exportJniBinding"
            task.dependsOn(kotlinCompilation.jniLibrariesProvider.map { container ->
                container.map { it.importLibTaskProvider }
            })
        }

        // Make the JVM compilation depend on importing the bindings, and track the resolved producers'
        // actuals files as inputs. When a native module changes, `exportJniBinding` regenerates the actuals,
        // their hash changes, and the consumer recompiles.
        kotlinCompilation.compileTaskProvider.configure { compileTask ->
            compileTask.dependsOn(importTaskProvider)
            compileTask.inputs.files(
                project.provider {
                    kotlinCompilation.jniLibrariesProvider.orNull.orEmpty().flatMap { it.actualsFiles.files }
                }
            )
        }

        return project.provider {
            val container = kotlinCompilation.jniLibrariesProvider.orNull.orEmpty()
            if (container.isEmpty()) {
                listOf(SubpluginOption("enabled", "false"))
            } else {
                buildList {
                    add(SubpluginOption("enabled", "true"))

                    // Aggregate allowExtraActuals – if any library permits extra actuals, allow it globally.
                    val allowExtra = container.any { it.allowExtraActuals.get() }
                    add(SubpluginOption("allowExtraActuals", allowExtra.toString()))

                    // Emit one "actualsFile" option per target, valued "<target>:<path>". The compiler plugin
                    // accumulates the options for a target, since several libraries can bind the same platform.
                    for (library in container) {
                        for (target in library.targets) {
                            val source = if (target.source.isPresent) target.source.get() else null
                            val actualsFile = try {
                                source?.actualsFile(kotlinCompilation.compilationName)
                            } catch (e: Exception) {
                                if (library.allowAbsentBindings.get()) null else throw e
                            }

                            if (actualsFile != null && actualsFile.exists()) {
                                add(SubpluginOption("actualsFile", "${target.name}:${actualsFile.path}"))
                            } else if (actualsFile != null && !library.allowAbsentBindings.get()) {
                                error(
                                    "No JniActuals info for target ${target.name} at ${actualsFile.path}. " +
                                        "Run the producer's exportJniBinding, or copy prebuilt bindings there."
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun getCompilerPluginId(): String = BuildConfig.CONSUMER_ARTIFACT

    override fun getPluginArtifact(): SubpluginArtifact {
        return SubpluginArtifact(
            groupId = BuildConfig.GROUP,
            artifactId = BuildConfig.CONSUMER_ARTIFACT,
            version = BuildConfig.VERSION
        )
    }
}
