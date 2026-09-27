package io.github.mimimishkin.jni.binding.consumer

import io.github.mimimishkin.jni.binding.consumer.model.JniActualInfo
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
 * target as soon as its remaining-expect set becomes empty.
 */
internal class ConsumerState(
    actualFiles: Map<String, List<File>>,
    val allowExtraActuals: Boolean,
    val reportMessage: (factory: KtSourcelessDiagnosticFactory, message: String) -> Unit,
) {
    val actualsByTarget: Map<String, MutableList<JniActualInfo>> by lazy {
        actualFiles.mapValues { (_, files) ->
            files.flatMap { file -> Json.decodeFromString<List<JniActualInfo>>(file.readText()) }.toMutableList()
        }
    }

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

    /** Whether the upfront leftover-actuals sweep (for targets no expect applies to) has already been performed. */
    var upfrontSweepDone = false

    /**
     * The known targets a list of `targetMachine` names applies to. An empty list matches every known target.
     */
    fun targetsFor(expectTargetMachines: List<String>): Set<String> =
        actualsByTarget.keys.filterToSetOrEmpty { target ->
            expectTargetMachines.isEmpty() || target in expectTargetMachines
        }
}