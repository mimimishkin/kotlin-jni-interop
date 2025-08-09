plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    id("convention.dokka")
}

group = "io.github.mimimishkin"
version = "1.0.1"

subprojects {
    group = rootProject.group
    version = rootProject.version
}

dokka {
    dokkaPublications.html {
        includes.from("README.md")
    }
}