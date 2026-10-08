import org.gradle.plugin.compatibility.compatibility

plugins {
    alias(conventions.plugins.jvmLibrary)
    alias(libs.plugins.serialization)
    alias(libs.plugins.dokka)
    alias(conventions.plugins.pluginPublish)
}

gradlePlugin {
    plugins {
        listOf(
            create("jni-binding-producer") {
                id = "io.github.mimimishkin.jni-binding-producer"
                displayName = "JNI Binding (Producer part)"
                description = "Gradle plugin that simplifies writing JNI code and linking native binary"
                tags = setOf("jni", "kotlin", "binding")
                implementationClass = "io.github.mimimishkin.jni.binding.plugin.producer.JniLibProducerPlugin"
            },

            create("jni-binding-consumer") {
                id = "io.github.mimimishkin.jni-binding-consumer"
                displayName = "JNI Binding (Consumer part)"
                description = "Gradle plugin that simplifies wiring JVM module with JNI library"
                tags = setOf("jni", "kotlin", "binding")
                implementationClass = "io.github.mimimishkin.jni.binding.plugin.consumer.JniLibConsumerPlugin"
            }
        ).forEach {
            it.compatibility {
                features {
                    configurationCache = true
                    isolatedProjects = true
                }
            }
        }
    }
}

dependencies {
    implementation(libs.serialization.json)
    implementation(libs.foojay.discoclient)
    implementation(libs.commons.compress)
    implementation(libs.kotlinpoet)
    compileOnly(gradleKotlinDsl())
    compileOnly(kotlin("gradle-plugin"))
    compileOnly(libs.androidTools.build.gradle)
}

dokka {
    dokkaSourceSets {
        named("main") {
            includes.from("Module.md")
//            skipEmptyPackages = false
//            sourceRoots.setFrom(/* nothing */) // disable documenting sources
        }
    }
}