plugins {
    alias(conventions.plugins.jvmLibrary)
    alias(libs.plugins.google.ksp)
    alias(libs.plugins.serialization)
    alias(conventions.plugins.publish)
}

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
}

// Kotlin compiler plugins are loaded by the compiler from a single artifact without transitive dependencies
// (`kotlinCompilerPluginClasspath`), so the serialization runtime must be bundled into the plugin jar itself.
tasks.jar {
    from(configurations.runtimeClasspath.map { classpath ->
        classpath.map { file -> if (file.isDirectory) file else project.zipTree(file) }
    })
    exclude(
        "module-info.class",
        "META-INF/*.SF",
        "META-INF/*.DSA",
        "META-INF/*.RSA",
        "META-INF/versions/**/module-info.class",
        // The stdlib is provided by the compiler; bundling it risks classloader clashes.
        "kotlin/**",
    )
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
