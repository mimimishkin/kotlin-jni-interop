plugins {
    `kotlin-dsl`
    id("convention.plugin-publish")
}

kotlin {
    explicitApi()
}

gradlePlugin {
    website = "https://github.com/mimimishkin/${rootProject.name}"
    vcsUrl = "https://github.com/mimimishkin/${rootProject.name}.git"

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
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.ksp.api)
    implementation(libs.bundles.kotlinPoet)
    testImplementation(kotlin("test"))
}

// // Example of usage:
//
// kotlin {
//     jvm {
//         jniLibrary("native", projectToBind = ":native-part") {
//             loadMethod = JniLoadMethod.ExtractFromRecourceAndLoad(
//                 resource = machineDependend(failOnOther = true) { "jni/$os-$arch/$libName" },
//                 extractTo = null // null means system default temp directory
//             )
//             expectsFilter = machineIndependent { ExpectsFilter.All }
//             producerConfig {
//                 jniVersion = 8 // Use JNI 1.8
//                 actualsFilter = machineIndependent { ActualsFilter.All } // Export all actuals
//                 javaHome = project.layout.projectDirectory.dir("java-home") // Use custom Java home
//             }
//             bindMethod = JniBindingMethod.JniFunctions
//             linkNativeProject = true
//         }
//     }
// }
//
//