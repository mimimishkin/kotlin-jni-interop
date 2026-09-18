plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.kmpLibrary) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.powerAssert) apply false
    alias(libs.plugins.dokka)
    alias(conventions.plugins.publish)
}

group = "io.github.mimimishkin"
version = "1.0.2"

subprojects {
    group = rootProject.group
    version = rootProject.version
}

dokka {
    dokkaPublications.html {
        includes.from("README.md")
    }
}

dependencies {
    dokka(projects.jniBindingRaw)
    dokka(projects.jniBinding)
    dokka(projects.jniBindingAnnotations)
    dokka(projects.jniBindingPlugins)
//    dokka(projects.jniBindingConsumer)
//    dokka(projects.jniBindingProducer)
}