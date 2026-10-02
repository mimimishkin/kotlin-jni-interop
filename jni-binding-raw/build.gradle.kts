import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.HostManager.Companion.hostIsLinux

plugins {
    alias(conventions.plugins.nativeDesktopLibrary)
    alias(libs.plugins.dokka)
    alias(conventions.plugins.publish)
}

description = "Cinterop output for jni.h and related headers"

kotlin {
    targets.withType<KotlinNativeTarget> {
        compilations.all {
            cinterops.create("jni") {
                // `language` is not a substitutable .def property, so it cannot be set per-target
                // in jni.def. Apple targets get a separate definition file with Objective-C enabled,
                // otherwise `darwin/jawt_md.h` is preprocessed in C mode and the `#ifdef __OBJC__`
                // part (JAWT_SurfaceLayers protocol) is missing from the bindings.
                if (konanTarget.family == Family.OSX) {
                    defFile(project.file("src/nativeInterop/cinterop/jni-osx.def"))
                }
            }
        }
    }

    if (hostIsLinux) {
        // Cinterop generates enormous paths, so the build fails. Therefore, set the build directory to a short location
        layout.buildDirectory = file("/tmp/12345")
    }
}

dokka {
    dokkaSourceSets.configureEach {
        includes.from("Module.md")
    }
}