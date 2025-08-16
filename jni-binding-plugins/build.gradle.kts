plugins {
    `kotlin-dsl`
    alias(libs.plugins.serialization)
    id("convention.plugin-publish")
}

kotlin {
    explicitApi()
}

gradlePlugin {
    plugins {
        create("jniLibProducer") {
            id = "io.github.mimimishkin.jni-binding-producer"
            displayName = "JNI Binding (Producer part)"
            description = "Gradle plugin that simplifies writing JNI code and linking native binary"
            tags = setOf("jni", "kotlin", "binding")
            implementationClass = "io.github.mimimishkin.jni.binding.plugin.producer.JniLibProducerPlugin"
        }

        create("jniLibConsumer") {
            id = "io.github.mimimishkin.jni-binding-consumer"
            displayName = "JNI Binding (Consumer part)"
            description = "Gradle plugin that simplifies wiring JVM module with JNI library"
            tags = setOf("jni", "kotlin", "binding")
            implementationClass = "io.github.mimimishkin.jni.binding.plugin.consumer.JniLibConsumerPlugin"
        }
    }
}

dependencies {
    implementation(libs.serialization.json)
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.ksp.gradlePlugin)
    testImplementation(kotlin("test"))
}