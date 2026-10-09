import io.github.mimimishkin.jni.binding.plugin.consumer.androidArm32
import io.github.mimimishkin.jni.binding.plugin.consumer.androidArm64
import io.github.mimimishkin.jni.binding.plugin.consumer.androidX64
import io.github.mimimishkin.jni.binding.plugin.consumer.androidX86
import io.github.mimimishkin.jni.binding.plugin.consumer.jniLibraries

plugins {
    id("com.android.application")
    id("io.github.mimimishkin.jni-binding-consumer") version "2.1.0"
}

android {
    namespace = "org.sample"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.sample.jniexamples"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }
}

kotlin {
    jvmToolchain(17)

    target {
        compilations.configureEach {
            if (name != "debug" && name != "release") return@configureEach
            jniLibraries.create("example") {
                listOf(
                    androidArm32(),
                    androidArm64(),
                    androidX86(),
                    androidX64(),
                ).forEach {
                    it.fromProducer(project(":android-basic:native"))
                    it.copyToJniLibs()
                }
            }
        }
    }
}
