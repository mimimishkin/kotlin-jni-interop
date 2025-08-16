package io.github.mimimishkin.jni.binding.plugin.producer

import com.google.devtools.ksp.gradle.KspExtension
import kotlinx.serialization.json.Json
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.invoke
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

/**
 * Plugin for producing JNI libraries.
 *
 * The goal of this plugin is to greatly simplify the process of writing JNI libraries in Kotlin.
 */
public class JniLibProducerPlugin : Plugin<Project> {
    public companion object {
        public const val EXTENSION_NAME: String = "jniLibrary"
    }

    override fun apply(project: Project) {
        val config = project.extensions.create<JniLibProducerConfig>(EXTENSION_NAME)
        val kotlin = project.extensions.getByType<KotlinMultiplatformExtension>()

        kotlin.apply {
            sourceSets {
                nativeMain.dependencies {
                    implementation("io.github.mimimishkin:jni-binding-annotations:1.0.2")
                }
            }

            compilerOptions {
                freeCompilerArgs.add("-Xcontext-parameters")
            }
        }

        project.the<KspExtension>().apply {
            arg("jniBinding.jniVersion", config.jniVersion.map { it.toString() })
            arg("jniBinding.allowSeveralHooks", config.allowSeveralHooks.map { it.toString() })
            arg("jniBinding.useOnLoad", config.exportMethod.map { (it == JniExportMethod.BindOnLoad).toString() })
            arg("jniBinding.interopConfig", config.interopConfig.map { Json.encodeToString(it) })
            arg("jniBinding.expectations", config.expectations.map { Json.encodeToString(it) })
            arg("jniBinding.allowExtraActuals", config.allowExtraActuals.map { it.toString() })
        }

        project.afterEvaluate {
            val targets = kotlin.targets.withType<KotlinNativeTarget>()
            targets.forEach { target ->
                val kspConfig = "ksp" + target.name.replaceFirstChar { it.uppercase() }
                project.dependencies {
                    kspConfig("io.github.mimimishkin:jni-binding-provider:1.0.2")
                }
            }
        }
    }
}