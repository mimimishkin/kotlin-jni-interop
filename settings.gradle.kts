enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")

    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
    }
}

rootProject.name = "kotlin-jni-interop"

include(":jni-binding-raw")
include(":jni-binding")
include(":jni-binding-annotations")
include(":jni-binding-provider")
include(":jni-binding-plugins")