import org.gradle.internal.os.OperatingSystem

pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        google()
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenLocal()
        mavenCentral()
        google()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "samples"

// All parent directories are included as projects when specifying a path to a subproject
include(":basic:native")
include(":basic:nativeHello")
include(":basic:nativeCritical")
include(":android-basic:native")
include(":android-basic:app")

val host = OperatingSystem.current()!!
if (host.isWindows) {
    include(":windows-registry")
}
