plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    id("convention.dokka")
    id("convention.publish") apply false
}

group = "io.github.mimimishkin"
version = "1.0.2"

subprojects {
    group = rootProject.group
    version = rootProject.version
}

dokka {
    dokkaPublications.html {
        includes.from("README.md")
    }
}