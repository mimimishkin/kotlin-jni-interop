plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    mingwX64()
    macosX64()
    macosArm64()
    linuxX64()
    linuxArm64()

    explicitApi()

    sourceSets {
        all {
            languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
            languageSettings.optIn("kotlin.experimental.ExperimentalNativeApi")
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}