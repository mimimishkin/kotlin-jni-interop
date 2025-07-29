import org.jetbrains.kotlin.konan.target.HostManager.Companion.hostIsLinux
import org.jetbrains.kotlin.konan.target.HostManager.Companion.hostIsMac
import org.jetbrains.kotlin.konan.target.HostManager.Companion.hostIsMingw

plugins {
    alias(libs.plugins.mavenPublish)
}

afterEvaluate {
    for (repository in publishing.repositories) {
        val repositoryName = repository.name.replaceFirstChar { it.uppercase() }
        val supportedTasks = tasks.withType<PublishToMavenRepository>().filter { task ->
            repositoryName in task.name && when {
                "mingw" in task.publication.name -> hostIsMingw
                "macos" in task.publication.name -> hostIsMac
                else /* linux, android, jvm and kotlinMultiplatform */ -> hostIsLinux
            }
        }

        tasks.register("publishAllSupportedPublicationsTo${repositoryName}Repository") {
            group = "publishing"
            description = "Publishes all supported by this host publications to the '${repository.name}' repository"
            dependsOn(supportedTasks)
        }
    }
}

mavenPublishing {
    publishToMavenCentral(false)

    signAllPublications()

    coordinates(groupId = group.toString(), artifactId = name, version = version.toString())

    pom {
        name = project.name
        description = project.description
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