plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    androidTarget {
        publishLibraryVariants("release")
    }
}

android {
    compileSdk = 33
    defaultConfig {
        minSdk = 1
    }
    namespace = project.group.toString()
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_1_1
    }
}