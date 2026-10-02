import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(conventions.plugins.nativeDesktopLibrary)
    alias(conventions.plugins.nativeAndroidLibrary)
    alias(libs.plugins.dokka)
    alias(conventions.plugins.publish)
}

description = "JNI bingdings for Kotlin Native"

kotlin {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyHierarchyTemplate {
        common {
            group("native") {
                group("desktop") {
                    group("mingw") {
                        withMingwX64()
                    }
                    group("linux") {
                        withLinuxX64()
                        withLinuxArm64()
                    }
                    group("macos") {
                        withMacosArm64()
                    }
                }

                group("androidNative") {
                    withAndroidNativeArm32()
                    withAndroidNativeArm64()
                    withAndroidNativeX86()
                    withAndroidNativeX64()
                }
            }
        }
    }

    sourceSets {
        nativeMain.dependencies {
            implementation(projects.jniBindingAnnotations)
        }
        named("desktopMain") {
            dependencies {
                implementation(projects.jniBindingRaw)
            }
        }
    }
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