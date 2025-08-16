package io.github.mimimishkin.jni.binding.plugin.consumer

import io.github.mimimishkin.jni.binding.plugin.producer.JniLibProducerConfig
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.the

public class JniLibConsumerPlugin : Plugin<Project> {
    public companion object {
        public const val GROUP: String = "jni binding"
        public const val EXTENSION_NAME: String = "jniLibrary"
    }

    override fun apply(consumer: Project) {
        val config = consumer.extensions.create<JniLibConsumerConfig>(EXTENSION_NAME)

        consumer.afterEvaluate {
            val producer = config.projectToBind.get()
            producer.apply(plugin = "io.github.mimimishkin.jni-binding-producer")

            val producerConfig = producer.the<JniLibProducerConfig>().apply { setConventions(config.producerConfig) }
            val afterEvaluate =  {
                val usedJVMVersion = consumer.the<JavaPluginExtension>().toolchain.languageVersion.get().asInt()
                val usedJNIVersion = producerConfig.jniVersion.get()
                if (usedJVMVersion < usedJNIVersion) {
                    consumer.logger.warn("Toolchain version ($usedJVMVersion) used by this project is less than the " +
                            "required JNI version ($usedJNIVersion). This will lead to runtime error if your " +
                            "application will be run under Java version less than $usedJNIVersion.")
                }

                producerConfig.doAfterEvaluate.forEach { it.invoke(producer) }
            }

            if (producer.state.executed) {
                afterEvaluate()
            } else {
                producer.afterEvaluate { afterEvaluate() }
            }
        }

        TODO("Not yet implemented")
    }
}