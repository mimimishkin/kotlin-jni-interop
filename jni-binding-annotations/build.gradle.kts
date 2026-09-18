plugins {
    alias(conventions.plugins.native64bitLibrary)
    alias(conventions.plugins.jvmLikeLibrary)
    alias(libs.plugins.dokka)
    alias(conventions.plugins.publish)
}

description = "Annotations for kotlin-jni-interop project"

dokka {
    dokkaSourceSets.configureEach {
        includes.from("Module.md")

        sourceLink {
            localDirectory = rootDir
            remoteUrl = uri("https://github.com/mimimishkin/${rootProject.name}/tree/master")
        }
    }
}