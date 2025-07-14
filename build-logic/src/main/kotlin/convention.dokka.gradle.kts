plugins {
    alias(libs.plugins.dokka)
}

dokka {
    dokkaSourceSets.configureEach {
        sourceLink {
            localDirectory = project.file("src")
            val projectPath = rootProject.relativePath(projectDir)
            remoteUrl = uri("https://github.com/mimimishkin/${rootProject.name}/tree/master/$projectPath/src")
        }
    }
}