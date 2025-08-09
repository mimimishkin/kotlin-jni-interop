package io.github.mimimishkin.jni.binding.plugin.producer

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.invoke
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

public class JniLibProducerPlugin : Plugin<Project> {
    public companion object {
        public const val EXTENSION_NAME: String = "jniLibrary"
    }

    override fun apply(project: Project) {
        val config = project.extensions.create<JniLibProducerConfig>(EXTENSION_NAME)
        val kotlin = project.extensions.getByType<KotlinMultiplatformExtension>()

        kotlin.apply {
            sourceSets {
                nativeMain {
                    dependencies {
                        implementation("io.github.mimimishkin:jni-binding-annotations:1.0.1")
                    }
                }
            }

            compilerOptions {
                freeCompilerArgs.add("-Xcontext-parameters")
            }
        }

        project.afterEvaluate {
            if (!config.generateHooks.get() && config.exportMethod.get() == JniExportMethod.BindOnLoad) {
                throw IllegalStateException("Cannot generate binding inside JNI_OnLoad because it was disabled")
            }
        }
    }
}