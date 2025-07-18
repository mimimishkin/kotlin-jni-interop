plugins {
    alias(libs.plugins.dokka)
}

subprojects {
    apply(plugin = "org.jetbrains.dokka")

    dokka {
        dokkaSourceSets.configureEach {
            includes.from("Module.md")

            sourceLink {
                localDirectory = rootDir
                remoteUrl = uri("https://github.com/mimimishkin/${rootProject.name}/tree/master")
            }
        }
    }
}

dependencies {
    subprojects.forEach { dokka(it) }
}