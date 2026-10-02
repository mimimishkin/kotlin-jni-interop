plugins {
    alias(conventions.plugins.jvmLibrary)
    alias(libs.plugins.serialization)
    alias(libs.plugins.dokka)
    alias(conventions.plugins.pluginPublish)
}

gradlePlugin {
    plugins {
        create("jni-binding-producer") {
            id = "io.github.mimimishkin.jni-binding-producer"
            displayName = "JNI Binding (Producer part)"
            description = "Gradle plugin that simplifies writing JNI code and linking native binary"
            tags = setOf("jni", "kotlin", "binding")
            implementationClass = "io.github.mimimishkin.jni.binding.plugin.producer.JniLibProducerPlugin"
        }

        create("jni-binding-consumer") {
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

    // TODO: replace with io.foojay:discoclient:2.0.39
    implementation(libs.palantir.gradle.jdks) {
        // `gradle-jdks` pulls in `gradle-baseline-java` for the `javaVersions { }` configuration it
        // sets up when its own plugin is applied. This plugin never applies it and never configures
        // `javaVersions`, so the whole Baseline tree is dead weight here - and it is not resolvable
        // from Maven Central, so leaving it in makes this plugin unusable in a build that does not
        // have the Gradle Plugin Portal among its `pluginManagement` repositories.
        exclude(group = "com.palantir.baseline")
    }
    // `gradle-jdks` declares both of these as `implementation` dependencies of its own, so they are
    // not on the classpath of anything that depends on it, while the types used to describe a JDK
    // (`OperatingSystem`, `Arch`) come from them.
    implementation(libs.palantir.gradle.jdks.setup.common)
    implementation(libs.palantir.platform)
    // A JDK is published as a `.tar.gz` for linux and macos, and the JVM reads a `.zip` on its own but
    // has no reader for a tar archive. This one covers both formats outside of Gradle's services, which
    // are not available wherever a JDK may be fetched.
    implementation(libs.commons.compress)

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