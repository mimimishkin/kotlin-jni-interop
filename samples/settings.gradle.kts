import org.gradle.internal.os.OperatingSystem

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenLocal()
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenLocal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "samples"

// All parent directories are included as projects when specifying a path to a subproject
include(":basic:native")
include(":basic:nativeHello")

val host = OperatingSystem.current()!!
if (host.isWindows) {
    include(":windows-registry")
}
