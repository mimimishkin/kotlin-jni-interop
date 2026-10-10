plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.kmpLibrary) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.powerAssert) apply false
    alias(libs.plugins.dokka)
    alias(conventions.plugins.publish)
}

group = "io.github.mimimishkin"
version = "2.1.0"

subprojects {
    group = rootProject.group
    version = rootProject.version
}

val docVersion: String = providers.gradleProperty("docVersion").getOrElse(version.toString())
val publishedDocsDir = providers.gradleProperty("publishedDocsDir").map { layout.projectDirectory.dir(it) }.orNull
val dokkaArchiveDir = layout.buildDirectory.dir("dokka-archive")

// Dokka's versioning plugin wants an archive that holds every previous version as its own Dokka output, one directory
// per version. The previously published site already is exactly that set - its root is the newest version and the
// versions before it sit under `older/` - so this task reshapes it into the archive the plugin expects.
val prepareDokkaArchive = tasks.register("prepareDokkaArchive") {
    description = "Reshapes the published documentation site into Dokka's archive of previous versions."
    group = "documentation"

    publishedDocsDir?.let { inputs.dir(it).withPropertyName("publishedDocs").optional() }
    outputs.dir(dokkaArchiveDir)

    doLast {
        val archive = dokkaArchiveDir.get().asFile
        archive.deleteRecursively()
        archive.mkdirs()

        val published = publishedDocsDir?.asFile
        if (published != null) {
            // The root of the site is its current version, and the bundle of even older versions it carries around
            // must not become part of its own archive entry.
            val versionFile = published.resolve("version.json")
            if (versionFile.isFile) {
                val rootVersion = Regex("\"version\"\\s*:\\s*\"([^\"]+)\"")
                    .find(versionFile.readText())
                    ?.groupValues[1]
                if (rootVersion != null) {
                    val entry = archive.resolve(rootVersion)
                    published.copyRecursively(entry)
                    entry.resolve("older").deleteRecursively()
                }
            }

            // Every other version is a directory under `older/`, already shaped as its own archive entry.
            val older = published.resolve("older")
            if (older.isDirectory) {
                older.listFiles { file -> file.isDirectory }?.forEach { version ->
                    version.copyRecursively(archive.resolve(version.name))
                }
            }
        }

        // Rebuilding the version that already is the current one must not list it as its own predecessor.
        archive.resolve(docVersion).deleteRecursively()
    }
}

dokka {
    dokkaPublications.html {
        includes.from("README.md")
    }

    pluginsConfiguration {
        versioning {
            version = docVersion
            if (publishedDocsDir != null) {
                olderVersionsDir = dokkaArchiveDir.get()
            }
        }
    }
}

tasks.matching { it.name == "dokkaGenerate" || it.name == "dokkaGeneratePublicationHtml" }.configureEach {
    if (publishedDocsDir != null) {
        dependsOn(prepareDokkaArchive)
    }
}

dependencies {
    dokka(projects.jniBindingRaw)
    dokka(projects.jniBinding)
    dokka(projects.jawtBinding)
    dokka(projects.jniBindingAnnotations)
    dokka(projects.jniBindingPlugins)
//    dokka(projects.jniBindingConsumer)
//    dokka(projects.jniBindingProducer)

    dokkaHtmlPlugin(libs.dokka.versioning)
}