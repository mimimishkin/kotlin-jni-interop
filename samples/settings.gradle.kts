dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenLocal()
        mavenCentral()
    }
}

rootProject.name = "samples"

include(":with-dependency", ":with-dependency:native-part")
include(":with-ksp", ":with-ksp:native-part")