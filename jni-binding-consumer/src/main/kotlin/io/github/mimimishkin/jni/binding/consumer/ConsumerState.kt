package io.github.mimimishkin.jni.binding.consumer

import io.github.mimimishkin.jni.binding.consumer.model.JniActualInfo
import io.github.mimimishkin.jni.binding.consumer.model.JniExpectDeclaration
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.kotlin.diagnostics.KtSourcelessDiagnosticFactory
import org.jetbrains.kotlin.ir.IrDiagnosticReporter
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.utils.filterToSetOrEmpty
import java.io.File

/**
 * Mutable state shared between the two phases of this plugin:
 *  - the `IrGenerationExtension` phase (load-method injection, and the place where the `pluginContext` is known)
 *  - the JVM-codegen phase ([io.github.mimimishkin.jni.binding.consumer.jvm.JniExpectMatcher]), where the authoritative
 *    JVM names are resolved.
 *
 * The IR phase records the diagnostic reporter and, per target, the `@JniExpect` functions that apply to it; the
 * codegen phase removes each expect as it matches it against [actualsByTarget], reporting leftover actuals for a
 * target as soon as its remaining-expect set becomes empty, and feeds every expect it sees to the `expects.json`
 * contract via [recordExpect], which is also the only writer of that file.
 */
internal class ConsumerState(
    actualFiles: Map<String, List<File>>,
    expectsFile: File?,
    val allowExtraActuals: Boolean,
    val reportMessage: (factory: KtSourcelessDiagnosticFactory, message: String) -> Unit,
) {
    val actualsByTarget: Map<String, MutableList<JniActualInfo>> by lazy {
        actualFiles.mapValues { (_, files) ->
            files.flatMap { file -> Json.decodeFromString<List<JniActualInfo>>(file.readText()) }.toMutableList()
        }
    }

    /**
     * The `expects.json` being built, or `null` when the contract file was not requested. Reset on construction, the
     * way the producer resets its `actuals.json`: a stale contract from a previous run would otherwise leak
     * declarations that no longer exist into the generated stubs.
     */
    private val expectsFile: File? = expectsFile?.also {
        it.parentFile?.mkdirs()
        it.writeText("[]")
    }

    private val expectsJson = Json { prettyPrint = true }
    private val expects = LinkedHashMap<String, JniExpectDeclaration>()

    /** Set by the IR phase */
    lateinit var reporter: IrDiagnosticReporter

    /**
     * Per target, the `@JniExpect` functions (explicit `@JniExpect` plus external functions inside a `@JniExpects`
     * class/object/file) that still remains to be matched. Because expects can be restricted with `targetMachine`,
     * the remaining set is tracked per platform rather than globally. The IR phase records each expect by identity;
     * the codegen phase removes the exact expect it processed. Leftover actuals (`EXTRA_JNI_ACTUALS`) are reported
     * once the set is empty.
     */
    val remainingExpectsByTarget = mutableMapOf<String, MutableSet<IrSimpleFunction>>()

    /**
     * The container classes and files that declare at least one expect, recorded by the IR phase. The matcher
     * ([io.github.mimimishkin.jni.binding.consumer.jvm.JniExpectMatcher]) uses these to decide whether a generated class
     * needs its methods checked, instead of re-scanning every class's declarations for `@JniExpect`/`@JniExpects`
     * annotations. A generated file-facade class is covered via `expectFiles` (its parent is the file).
     */
    val expectContainers = mutableSetOf<IrClass>()
    val expectFiles = mutableSetOf<IrFile>()

    /** Targets whose leftover-actuals (`EXTRA_JNI_ACTUALS`) report has already been emitted. */
    val reportedExtraByTarget = mutableSetOf<String>()

    /**
     * Records an expect in the `expects.json` contract. Called by the codegen phase for every expect it processes,
     * right after the backend-resolved description of it was built, because that description is the only place the
     * final JVM names are known.
     *
     * The file is rewritten after every newly seen declaration instead of once at the end of the compilation.
     * `expects.json` is most valuable exactly when the build *failed* - that is the state in which the user needs
     * stubs - and this phase has no "everything is done" hook it could hang a single write on. Rewriting per
     * declaration costs one extra write per expect, which is irrelevant next to a compilation, and guarantees the
     * file is complete and readable even if the compiler dies mid-way: the only declarations missing then are the ones
     * it had not reached yet.
     */
    fun recordExpect(expect: JniExpectDeclaration) {
        val file = expectsFile ?: return
        val key = expect.signatureKey
        val previous = expects[key]
        // Two expects of the same JVM method (one restricted to some targets, one to others) collapse into a single
        // declaration carrying the union of the targets.
        val merged = when {
            previous == null -> expect
            previous.targets == expect.targets -> previous
            else -> expect.copy(targets = (previous.targets + expect.targets).distinct())
        }
        if (merged == previous) return
        expects[key] = merged
        file.writeText(expectsJson.encodeToString(ListSerializer(JniExpectDeclaration.serializer()), expects.values.toList()))
    }

    /**
     * Whether the upfront leftover-actuals sweep (for targets no expect applies to) has already been performed.
     */
    var upfrontSweepDone = false

    /**
     * The known targets a list of `targetMachine` names applies to. An empty list matches every known target.
     */
    fun targetsFor(expectTargetMachines: List<String>): Set<String> =
        actualsByTarget.keys.filterToSetOrEmpty { target ->
            expectTargetMachines.isEmpty() || target in expectTargetMachines
        }
}