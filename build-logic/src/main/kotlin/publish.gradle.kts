plugins {
    alias(libs.plugins.mavenPublish)
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = false)

    signAllPublications()

    coordinates(groupId = group.toString(), artifactId = name, version = version.toString())

    pom {
        name = project.name
        description = project.provider { project.description }
        inceptionYear = "2025"
        url = "https://github.com/mimimishkin/${rootProject.name}"
        licenses {
            license {
                name = "MIT"
            }
        }
        developers {
            developer {
                id = "mimimishkin"
                name = "mimimishkin"
                email = "printf.mika@gmail.com"
            }
        }
        scm {
            url = "https://github.com/mimimishkin/${rootProject.name}"
            connection = "scm:git:git://github.com/mimimishkin/${rootProject.name}.git"
        }
    }
}