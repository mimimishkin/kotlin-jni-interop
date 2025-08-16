plugins {
    id("convention.native64bit-library")
    alias(libs.plugins.dokka)
    id("convention.publish")
}

description = "Annotations for kotlin-jni-interop project"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_1_1
}

kotlin {
    jvm()
}

dokka {
    dokkaSourceSets.configureEach {
        includes.from("Module.md")

        sourceLink {
            localDirectory = rootDir
            remoteUrl = uri("https://github.com/mimimishkin/${rootProject.name}/tree/master")
        }
    }
}

rootProject.dependencies {
    dokka(project)
}