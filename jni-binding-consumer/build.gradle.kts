plugins {
    alias(conventions.plugins.jvmLibrary)
    alias(libs.plugins.google.ksp)
    alias(libs.plugins.serialization)
    alias(conventions.plugins.publish)
}

description = "Simplifies wiring JVM module with JNI library"

kotlin {
    explicitApi()
}

dependencies {
    compileOnly(libs.kotlin.compiler)
    implementation(libs.autoService.annotations)
    ksp(libs.zacsweers.autoServiceKsp)

    implementation(libs.serialization.json)

    // The compile-testing framework (KCT fork) pulls in kotlin-compiler-embeddable transitively; pin it to the same
    // Kotlin version the plugin is compiled against so the in-JVM compiler matches the plugin's FIR API surface.
    testImplementation(libs.kotlin.compiler.embeddable)
    testImplementation(libs.zacsweers.kctFork)

    testImplementation(project(":jni-binding-annotations"))
}