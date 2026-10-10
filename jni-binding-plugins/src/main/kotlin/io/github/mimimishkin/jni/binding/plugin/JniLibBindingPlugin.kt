package io.github.mimimishkin.jni.binding.plugin

import io.github.mimimishkin.jni.binding.plugin.consumer.JniLibConsumerPlugin
import io.github.mimimishkin.jni.binding.plugin.producer.JniLibProducerPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Plugin for projects that produce and consume their JNI bindings in one module.
 *
 * Most projects keep the native **producer** and the JVM **consumer** apart, because they are separate
 * artifacts with separate lifecycles. A small project — a library, a sample, a prototype — has one module
 * with a native target and a JVM target instead, and applying
 * [JniLibProducerPlugin] and [JniLibConsumerPlugin] to it is all that setup takes:
 *
 * ```kotlin
 * plugins {
 *     kotlin("multiplatform")
 *     id("io.github.mimimishkin.jni-binding") version "2.0.0"
 * }
 *
 * kotlin {
 *     jvm {
 *         compilations.named("main") {
 *             jniLibraries.create("native") {
 *                 mingwX64 {
 *                     fromProducer(project)   // this very module is the producer
 *                     copyToResources()
 *                 }
 *             }
 *         }
 *     }
 *     mingwX64 {
 *         binaries.sharedLib("native") { linkJvm() }
 *     }
 * }
 *
 * jniLibraries {
 *     expectedJdkVersion = 17
 * }
 * ```
 *
 * This plugin is nothing but those two applied in one go. Both extensions — the `jniLibraries` block on the
 * project and `jniLibraries.create(...)` on a JVM/Android compilation — are configured exactly as they are
 * when the two plugins are applied separately, and the two plugins can still be applied explicitly instead of
 * this one.
 */
public class JniLibBindingPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply(JniLibProducerPlugin::class.java)
        target.pluginManager.apply(JniLibConsumerPlugin::class.java)
    }
}
