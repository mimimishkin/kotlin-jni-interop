import com.android.build.api.withAndroid
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(conventions.plugins.nativeDesktopLibrary)
    alias(conventions.plugins.nativeAndroidLibrary)
    alias(conventions.plugins.jvmLikeLibrary)
    alias(libs.plugins.dokka)
    alias(conventions.plugins.publish)
}

description = "Annotations for kotlin-jni-interop project"

kotlin {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyHierarchyTemplate {
        common {
            group("native") {
                withMingwX64()
                withLinuxX64()
                withLinuxArm64()
                withMacosArm64()

                withAndroidNativeArm32()
                withAndroidNativeArm64()
                withAndroidNativeX86()
                withAndroidNativeX64()
            }
            group("jvmLike") {
                withJvm()
                withAndroid()
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