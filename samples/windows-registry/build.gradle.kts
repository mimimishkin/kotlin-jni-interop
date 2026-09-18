import io.github.mimimishkin.jni.binding.plugin.consumer.jniLibraries
import io.github.mimimishkin.jni.binding.plugin.consumer.mingwX64
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.power-assert") version "2.4.20"
    id("io.github.mimimishkin.jni-binding-consumer") version "1.0.2"
}

kotlin {
    jvmToolchain(17)

    target {
        compilations.named("main") {
            jniLibraries.create("native") {
                mingwX64 {
                    fromProducer(project("native"))
                    resourceDir = "natives/"
                }

                copyToResources()
                allowExtraActuals = false
            }
        }
    }
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
powerAssert {
    functions = listOf(
        "kotlin.assert",
        "kotlin.test.assertTrue",
        "kotlin.test.assertFalse",
        "kotlin.test.assertEquals",
        "kotlin.test.assertNotEquals",
    )
}