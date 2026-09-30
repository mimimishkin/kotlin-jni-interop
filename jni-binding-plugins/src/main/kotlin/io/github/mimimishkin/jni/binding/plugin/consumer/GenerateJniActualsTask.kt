package io.github.mimimishkin.jni.binding.plugin.consumer

import kotlinx.serialization.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import java.io.File

/**
 * A producer project the `@JniActual` stubs can be generated into.
 *
 * @property path Gradle project path of the producer, as shown when several of them are available. Machine-independent.
 * @property projectDir the producer's directory, as recorded while configuring. Only ever used to locate sources.
 * @property actuals the producer's `actuals.json` artifacts, obtained from the binding configuration this compilation
 * already declares.
 */
public data class JniActualsProducer(
    val path: String,
    val projectDir: File,
    val actuals: FileCollection,
)

/**
 * Generates `TODO()`-bodied `@JniActual` stubs for the `@JniExpect` declarations of a consumer compilation.
 *
 * It is meant to be run by hand, between the first consumer build (which fails on the missing actuals) and the next
 * one, and is therefore deliberately not wired into the build graph: it declares no dependency on the consumer
 * compilation nor on the producers, and it reads whatever the previous run left on disk. That is what lets it produce
 * stubs out of a *failed* compilation, which is the only situation it exists for. Running it before the consumer has
 * ever been compiled fails with an explanation rather than silently doing nothing.
 */
// Not `@CacheableTask`: the stubs belong to the selected producer, and which producer that is only becomes known once
// the task runs, so they cannot be declared as outputs. Re-running on unchanged input is cheap and idempotent anyway -
// it re-reads what is already on disk and writes only what is missing.
@UntrackedTask(
    because = "Writes @JniActual stubs into the selected producer's sources, which cannot be declared as outputs " +
        "before the producer has been selected",
)
public abstract class GenerateJniActualsTask : DefaultTask() {

    /**
     * The `expects.json` written by the consumer compilation this task belongs to.
     */
    @get:Internal
    public abstract val expectsFile: RegularFileProperty

    /**
     * Producer projects the stubs may be generated into, in configuration order.
     */
    @get:Internal
    public abstract val producers: ListProperty<JniActualsProducer>

    init {
        group = "jni interop"

        // The result is written into another project's source tree, and which producer is chosen is not known until
        // this task runs, so there is nothing to snapshot: this task is interactive and re-runnable, not incremental.
        outputs.upToDateWhen { false }
    }

    @TaskAction
    internal fun generate() {
        val expects = readExpects()
        val producer = selectProducer()
        val actuals = producer.actuals
            .filter(File::isFile)
            .flatMap(::readContracts)

        val implemented = expects.count { expect -> actuals.any { it.isImplementedBy(expect) } }
        val missing = expects.filter { expect -> actuals.none { it.isImplementedBy(expect) } }
        if (missing.isEmpty()) {
            logger.lifecycle(
                "All $implemented @JniExpect(s) of this compilation already have an @JniActual in ${producer.path}; " +
                    "nothing to generate."
            )
            return
        }

        val sourceRoot = producerSourceRoot(producer)
        val result = JniActualsStubWriter(sourceRoot).write(missing)
        if (result.written > 0) {
            logger.lifecycle("Generated ${result.written} @JniActual stub(s) in ${producer.path}:")
            result.files.forEach { logger.lifecycle("  ${it.absolutePath}") }
        }
        if (result.alreadyDeclared > 0) {
            logger.lifecycle(
                "${result.alreadyDeclared} of the ${missing.size} unimplemented @JniExpect(s) are already declared " +
                    "under $NATIVE_SOURCE_DIR of ${producer.path} and were left as they are."
            )
        }
        if (implemented > 0) {
            logger.lifecycle("$implemented of the ${expects.size} @JniExpect(s) already have an @JniActual.")
        }
    }

    /**
     * The producer's native source root, refusing to invent one.
     */
    private fun producerSourceRoot(producer: JniActualsProducer): File {
        val dir = producer.projectDir
        if (!dir.isDirectory) throw GradleException(
            "The directory of producer ${producer.path}, ${dir.path}, does not exist. It was recorded while " +
                "configuring and is held by the configuration cache, so an entry recorded before the project was " +
                "moved or renamed no longer points at the producer. Re-run with --no-configuration-cache to record " +
                "it again."
        )
        return dir.resolve(NATIVE_SOURCE_DIR)
    }

    private fun readExpects(): List<JniFunctionContract> {
        val file = expectsFile.orNull?.asFile ?: throw GradleException(
            "No 'expectsFile' configured for ${path}; this is a bug in the jni-binding consumer plugin."
        )
        if (!file.isFile) throw GradleException(
            "No @JniExpect contract at ${file.path}. The jni-binding consumer records it while compiling, so compile " +
                "this module first, then run $path again."
        )
        return readContracts(file)
    }

    private fun readContracts(file: File): List<JniFunctionContract> = runCatching {
        Json.decodeFromString<List<JniFunctionContract>>(file.readText())
    }.getOrElse { throw GradleException("Cannot read the JNI binding contract at ${file.path}: ${it.message}", it) }

    /**
     * Picks the producer to generate into, asking when the choice is not already made.
     *
     * A compilation can bind libraries coming from several producers, and a single stub file cannot serve two of them
     * at once, so which one gets the stubs is the one thing that cannot be inferred. It is asked rather than
     * configured.
     */
    private fun selectProducer(): JniActualsProducer {
        val producers = producers.get()
        if (producers.isEmpty()) throw GradleException(
            "No producer to generate @JniActual into: none of this compilation's jniLibraries targets is configured " +
                "with `fromProducer(project)`."
        )
        if (producers.size == 1) return producers.single()

        logger.lifecycle("Several producers are configured; choose where to generate the @JniActual stubs:")
        producers.forEachIndexed { index, producer ->
            logger.lifecycle("  ${index + 1}) ${producer.path} (${producer.projectDir})")
        }
        logger.lifecycle("Enter a number or a project path:")

        val answer = readlnOrNull()?.trim().orEmpty()
        if (answer.isEmpty()) throw GradleException(
            "No producer selected. This task needs an answer, and none was given on the standard input. " +
                "The producers of this compilation are: ${producers.joinToString(", ") { it.path }}."
        )
        val byIndex = answer.toIntOrNull()?.takeIf { it in 1..producers.size }?.let { producers[it - 1] }
        return byIndex ?: producers.firstOrNull { it.path == answer } ?: throw GradleException(
            "'$answer' is not one of the producers of this compilation: " +
                producers.joinToString(", ") { it.path } + "."
        )
    }

    private companion object {
        /**
         * Where the stubs go inside the producer, relative to its directory: `nativeMain` unconditionally.
         */
        const val NATIVE_SOURCE_DIR = "src/nativeMain/kotlin"
    }
}
