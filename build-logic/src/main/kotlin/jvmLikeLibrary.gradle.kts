plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmpLibrary)
}

kotlin {
    jvmToolchain(17)

    jvm()
    android {
        namespace = group.toString()
        compileSdk = 37
    }

    explicitApi()
}