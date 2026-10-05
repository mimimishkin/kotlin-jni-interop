package io.github.mimimishkin.jni.binding.plugin.consumer

import io.github.mimimishkin.jni.binding.BuildConfig
import io.github.mimimishkin.jni.binding.BuildConfig.ANNOTATIONS_ID
import io.github.mimimishkin.jni.binding.plugin.commonSourceSet
import io.github.mimimishkin.jni.binding.plugin.disambiguateName
import io.github.mimimishkin.jni.binding.plugin.mostCommonSourceSet
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.KotlinSingleTargetExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.androidJvm
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.jvm
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

/**
 * The platform types a JNI library can be consumed from, and therefore the ones the annotations belong in.
 */
private val CONSUMABLE_PLATFORMS = listOf(jvm, androidJvm)

/**
 * Plugin for consuming JNI libraries.
 */
public class JniLibConsumerPlugin : KotlinCompilerPluginSupportPlugin {
    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
        return kotlinCompilation.platformType in CONSUMABLE_PLATFORMS && kotlinCompilation.jniLibrariesProvider.isPresent
    }

    /**
     * The annotations are added per project, once the build script has configured every compilation.
     */
    override fun apply(target: Project) {
        target.afterEvaluate { it.addAnnotationsAsDependency() }
    }

    /**
     * Puts `jni-binding-annotations` to the source set all the applicable compilations have in common.
     */
    private fun Project.addAnnotationsAsDependency() {
        val targets = jniLibrariesTargets()
        val compilations = jniLibrariesCompilations().ifEmpty { return }

        // A source set shared by every target would leak the annotations into the published variant too, which is
        // why this is only done when all of them are consumable. A single-target project -- JVM or Android -- is such
        // a case by construction.
        val allTargetsAreConsumable = targets.all {
            it.platformType == jvm || it.platformType == androidJvm || it.platformType == KotlinPlatformType.common
        }

        for (compilationName in compilations.map { it.compilationName }.toSet()) {
            val sourceSet = mostCommonSourceSet(
                compilations = compilations.filter { it.compilationName == compilationName },
                common = if (allTargetsAreConsumable) {
                    extensions.findByType<KotlinMultiplatformExtension>()?.let { commonSourceSet(it, compilationName) }
                } else {
                    null
                },
            ) ?: continue
            sourceSet.dependencies {
                implementation(ANNOTATIONS_ID)
            }
        }
    }

    /**
     * Every compilation of this project this plugin is wired to, or `null` if the project has none.
     */
    private fun Project.jniLibrariesCompilations(): List<KotlinCompilation<*>> {
        return jniLibrariesTargets()
            .flatMap { target -> target.compilations }
            .filter { it.platformType in CONSUMABLE_PLATFORMS && it.jniLibrariesProvider.isPresent }
    }

    /**
     * Every target of this project, however its Kotlin plugin is spelled.
     *
     * The Android extension is `KotlinAndroidProjectExtension`, which is *not* a `KotlinJvmProjectExtension` --
     * only both descend from `KotlinTopLevelExtension`. Looking up the JVM extension alone therefore finds nothing
     * on an Android project, and the plugin would silently skip it.
     */
    private fun Project.jniLibrariesTargets(): List<KotlinTarget> {
        extensions.findByType<KotlinMultiplatformExtension>()?.let { return it.targets.toList() }

        // A single-target project registers its extension under the name `kotlin`, whichever of the two it is:
        // `KotlinJvmProjectExtension` or `KotlinAndroidProjectExtension`. The lookup is by name because Gradle
        // matches extensions by their declared generic type, and `KotlinSingleTargetExtension` is generic.
        val single = extensions.findByName("kotlin") as? KotlinSingleTargetExtension<*>
        return listOfNotNull(single?.target)
    }

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.project

        val expectsContract = project.layout.buildDirectory
            .file("jniBindings/expectsInfo/${kotlinCompilation.compilationName}/expects.json")

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

        project.tasks.register<GenerateJniActualsTask>(kotlinCompilation.disambiguateName("generateJniActuals")) {
            description = "Generates @JniActual stubs for the @JniExpects of this compilation"
            expectsFile.set(expectsContract)
            producers.set(project.provider { producerProjectsOf(kotlinCompilation) })
        }

        // Make the JVM compilation depend on importing the bindings, and track the resolved producers'
        // actuals files as inputs. When a native module changes, `exportJniBinding` regenerates the actuals,
        // their hash changes, and the consumer recompiles.
        kotlinCompilation.compileTaskProvider.configure { compileTask ->
            compileTask.dependsOn(importTaskProvider)
            compileTask.inputs.files(
                project.files(
                    kotlinCompilation.jniLibrariesProvider.orNull.orEmpty().flatMap { it.actualsFiles.files }
                )
            )
        }

        return project.provider {
            val container = kotlinCompilation.jniLibrariesProvider.orNull.orEmpty()
            buildList {
                if (container.isEmpty()) {
                    add(SubpluginOption("enabled", "false"))
                } else {
                    add(SubpluginOption("expectsFile", expectsContract.get().asFile.path))
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
                                source?.actuals()?.singleOrNull()
                            } catch (e: Exception) {
                                if (library.allowAbsentBindings.get()) null else throw e
                            }

                            // A target is skipped only when it is out of scope by request: bindings read from
                            // files that no task in this build will ever produce, missing, and `allowAbsentBindings`
                            // set. A producer's bindings are never skipped for being missing, because the build
                            // produces them - the compiler reads the file once the tasks have run, and reports
                            // what it finds there.
                            val outOfScope = actualsFile != null &&
                                !actualsFile.exists() &&
                                source?.producedByThisBuild == false &&
                                library.allowAbsentBindings.get()
                            if (actualsFile != null && !outOfScope) {
                                add(SubpluginOption("actualsFile", "${target.name}:${actualsFile.path}"))
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

    /**
     * The producer projects of [compilation]'s libraries, each with the `actuals.json` artifacts its targets publish.
     *
     * The artifacts are handed over unresolved, so this adds no dependency on the producers' `exportJniBinding`. That
     * matters: the task has to work before they were ever built, which is the state a new binding starts from.
     */
    private fun producerProjectsOf(compilation: KotlinCompilation<*>): List<JniActualsProducer> {
        val consumerProject = compilation.project
        val byProducer = compilation.jniLibrariesProvider.orNull.orEmpty()
            .flatMap { it.targets }
            .mapNotNull { target ->
                val producer = target.producerProject.orNull ?: return@mapNotNull null
                val source = target.source.orNull
                producer to source
            }

        return byProducer
            .distinctBy { (producer, _) -> producer.path }
            .map { (producer, _) ->
                val sources = byProducer.filter { it.first.path == producer.path }.map { it.second }
                JniActualsProducer(
                    path = producer.path,
                    projectDir = producer.projectDir,
                    actuals = consumerProject.objects.fileCollection().apply {
                        sources.forEach { source ->
                            from(consumerProject.provider { source?.actuals() ?: consumerProject.files() })
                        }
                    },
                )
            }
    }
}
