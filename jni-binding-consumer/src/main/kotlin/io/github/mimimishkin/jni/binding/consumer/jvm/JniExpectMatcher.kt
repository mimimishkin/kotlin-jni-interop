@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package io.github.mimimishkin.jni.binding.consumer.jvm

import io.github.mimimishkin.jni.binding.consumer.ConsumerState
import io.github.mimimishkin.jni.binding.consumer.JniBindingConsumerErrors
import io.github.mimimishkin.jni.binding.consumer.Symbols
import io.github.mimimishkin.jni.binding.consumer.model.JavaType
import io.github.mimimishkin.jni.binding.consumer.model.JniActualInfo
import io.github.mimimishkin.jni.binding.consumer.model.JniExpectDeclaration
import io.github.mimimishkin.jni.binding.consumer.model.JniExpectInfo
import org.jetbrains.kotlin.backend.jvm.JvmLoweredDeclarationOrigin
import org.jetbrains.kotlin.backend.jvm.extensions.ClassGenerator
import org.jetbrains.kotlin.backend.jvm.extensions.ClassGeneratorExtension
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes.ACC_SYNCHRONIZED
import org.jetbrains.org.objectweb.asm.Type as AsmType

private val jniExpectAnnotation = Symbols.JniExpect.asSingleFqName()
private val jniExpectsAnnotation = Symbols.JniExpects.asSingleFqName()

private fun IrAnnotationContainer.hasJniExpect() = hasAnnotation(jniExpectAnnotation)

private fun isAndroidTarget(target: String): Boolean = target.startsWith("android", ignoreCase = true)

/**
 * The single place where `@JniExpect` functions are matched against the JVM names the backend actually produces.
 *
 * Unlike a source-level scan, this runs as a `ClassGeneratorExtension` while the real bytecode is generated. The
 * `defineClass(...)`/`newMethod(...)` names it observes are the authoritative, `@JvmName`-remapped names produced by
 * `MethodSignatureMapper` during `JvmLowering`, so the consumer never has to re-derive them from annotations.
 *
 * For each `@JniExpect` function it builds the [JniExpectInfo] with those backend-resolved names, matches it against
 * the per-target `actuals.json`, reports whatever is wrong with the match (missing actual, stasis mismatch, nullability
 * mismatch), and reports leftover actuals (`EXTRA_JNI_ACTUALS`) once every expect has been processed. Every expect it
 * builds is also fed to the `expects.json` contract, which is what lets a missing actual be generated rather than only
 * complained about.
 */
internal class JniExpectMatcher(
    private val state: ConsumerState,
) : ClassGeneratorExtension {

    override fun generateClass(generator: ClassGenerator, declaration: IrClass?): ClassGenerator {
        val irClass = declaration ?: return generator
        // Only classes that the IR phase recorded as declaring an expect (via `@JniExpect`/`@JniExpects`) need their
        // methods checked. A generated file-facade class is covered through its parent file. This avoids re-scanning
        // every class's declarations for the annotations.
        if (irClass !in state.expectContainers && (irClass.parent as? IrFile) !in state.expectFiles) return generator

        return VerifyingDelegate(generator)
    }

    private inner class VerifyingDelegate(
        private val delegate: ClassGenerator,
    ) : ClassGenerator by delegate {

        private var currentClassName: String? = null

        override fun newMethod(
            declaration: IrFunction?,
            access: Int,
            name: String,
            desc: String,
            signature: String?,
            exceptions: Array<out String>?,
        ): MethodVisitor {
            if (declaration is IrSimpleFunction && currentClassName != null) {
                checkFunction(declaration, currentClassName!!, name, desc, access)
            }
            return delegate.newMethod(declaration, access, name, desc, signature, exceptions)
        }

        private fun checkFunction(
            function: IrSimpleFunction,
            className: String,
            asmMethodName: String,
            desc: String,
            access: Int,
        ) {
            // The JVM backend emits a synthetic `getX$annotations` method carrying property-level annotations; it is
            // not an accessor and must never be treated as an expect.
            if (function.origin == JvmLoweredDeclarationOrigin.SYNTHETIC_METHOD_FOR_PROPERTY_OR_TYPEALIAS_ANNOTATIONS) return
            // Only actual `@JniExpect` targets participate in matching: an explicitly annotated function, an
            // accessor of a `@JniExpect`-annotated property (applying `@JniExpect` to a property is equivalent to
            // applying it to both its getter and setter), or an external function declared directly inside a
            // `@JniExpects` class/object or a `@file:JniExpects` file. Non-external helpers (e.g. `@LoadMethod`
            // functions, `<clinit>`, the annotation-stub methods) must be ignored, mirroring how the IR phase counts.
            val property = function.correspondingPropertySymbol?.owner
            val implicitExpect = function.isExternal && when (val container = function.parent) {
                is IrClass -> container.hasAnnotation(jniExpectsAnnotation)
                is IrFile -> container.hasAnnotation(jniExpectsAnnotation)
                else -> false
            }
            val explicitExpect = function.hasAnnotation(jniExpectAnnotation) || property?.hasJniExpect() == true
            val expectPresent = explicitExpect || implicitExpect
            if (!expectPresent) return

            val expectInfo = function.describeJniExpect(className, asmMethodName, desc)
            if (expectInfo.source.hasCriticalNative()) {
                if (access and ACC_SYNCHRONIZED != 0) {
                    state.reporter.at(expectInfo.source)
                        .report(JniBindingConsumerErrors.CRITICAL_NATIVE_MUST_NOT_BE_SYNCHRONIZED)
                }
                checkNoReferencesOnAndroid(expectInfo, desc)
            }
            state.recordExpect(expectInfo.toDeclaration())
            actualize(expectInfo, function)
        }

        /**
         * Rejects a critical expect that takes or returns a reference when any of its targets is an Android one.
         */
        private fun checkNoReferencesOnAndroid(expect: JniExpectInfo, desc: String) {
            if (state.targetsFor(expect.targets).none(::isAndroidTarget)) return
            fun AsmType.isReference() = sort == AsmType.ARRAY || sort == AsmType.OBJECT
            val methodType = AsmType.getMethodType(desc)
            val references = methodType.argumentTypes.filter { it.isReference() } +
                    listOfNotNull(methodType.returnType.takeIf { it.isReference() })
            if (references.isEmpty()) return
            state.reporter.at(expect.source).report(
                JniBindingConsumerErrors.CRITICAL_NATIVE_REFERENCE_ON_ANDROID,
                references.joinToString(", ") { it.className },
                expect.parametersWithTypes.joinToString(", ") { it.first.name.asString() }.ifEmpty { "no parameters" },
            )
        }

        private fun JniExpectInfo.toDeclaration() = JniExpectDeclaration(
            className = className,
            methodName = methodName,
            isStatic = isStatic,
            parameterTypes = parameterTypes,
            returnType = returnType,
            targets = targets,
            isCritical = source.hasCriticalNative(),
        )

        private fun IrSimpleFunction.describeJniExpect(className: String, methodName: String, desc: String): JniExpectInfo {
            val irParameters = nonDispatchParameters
            val parentClass = parent as? IrClass
            // The parameter/return type NAMEs come directly from the emitted bytecode descriptor produced by the JVM
            // backend (the authoritative, already-mapped Java names), so the consumer never re-derives Kotlin->JVM
            // names.
            // Nullability is not part of a JVM descriptor, so it is taken from the IR type.
            val methodType = AsmType.getMethodType(desc)
            val argTypes = methodType.argumentTypes
            return JniExpectInfo(
                targets(),
                className,
                methodName,
                parentClass?.let { isStatic } ?: true,
                irParameters.mapIndexed { index, param ->
                    param to JavaType(argTypes[index].className, param.type.isNullable())
                },
                JavaType(methodType.returnType.className, returnType.isNullable()),
                this,
            )
        }

        /**
         * The list of target names a `@JniExpect` is restricted to (e.g. `["mingwX64"]`), taken from the
         * `@JniExpect` (of the function or, for an accessor, of its property) or the `@JniExpects` annotation.
         */
        private fun IrSimpleFunction.targets(): List<String> =
            (getAnnotation(jniExpectAnnotation)
                ?: correspondingPropertySymbol?.owner?.getAnnotation(jniExpectAnnotation)
                ?: (parent as? IrAnnotationContainer)?.getAnnotation(jniExpectsAnnotation))
                ?.arguments
                ?.firstOrNull()
                ?.let { it as? IrVararg }
                ?.elements
                ?.filterIsInstance<IrConst>()
                ?.mapNotNull { it.value as? String }
                ?: emptyList()

        private fun actualize(expect: JniExpectInfo, source: IrSimpleFunction) {
            // A target can carry leftover actuals even without any expect that applies to it (e.g. every expect is
            // restricted to a different platform). Such actuals are already final the moment the first expect is
            // processed, so report them once up front. This must run before any expect is removed below.
            if (!state.upfrontSweepDone) {
                state.upfrontSweepDone = true
                for (target in state.remainingExpectsByTarget.keys) {
                    reportExtraActualsIfFinished(target)
                }
            }

            for (target in state.targetsFor(expect.targets)) {
                val actuals = state.actualsByTarget[target]!!
                matchAgainst(expect, actuals, target)

                // Once every expect that applies to this target has been processed, the matching actuals are removed
                // and whatever remains is genuinely extra for this target. The expect is removed from the remaining
                // set even when no actual matched, so "processed this expect" and "matched an actual" stay
                // independent.
                state.remainingExpectsByTarget[target]!!.remove(source)
                reportExtraActualsIfFinished(target)
            }
        }

        private fun reportExtraActualsIfFinished(target: String) {
            // A target with no recorded expects is already final; one with recorded expects finishes as soon as the
            // set is empty. An unknown target is treated as finished.
            if (state.remainingExpectsByTarget[target]?.isNotEmpty() ?: false) return
            if (!state.reportedExtraByTarget.add(target)) return
            reportExtraActuals(target)
        }

        private fun reportExtraActuals(target: String) {
            if (!state.allowExtraActuals) {
                val extra = state.actualsByTarget[target].orEmpty()
                if (extra.isNotEmpty()) {
                    state.reportMessage(
                        JniBindingConsumerErrors.EXTRA_JNI_ACTUALS,
                        "Found extra @JniActuals for $target:\n" + extra.joinToString("\n")
                    )
                }
            }
        }

        override fun defineClass(
            version: Int,
            access: Int,
            name: String,
            signature: String?,
            superName: String,
            interfaces: Array<out String>,
        ) {
            currentClassName = name.replace('/', '.')
            delegate.defineClass(version, access, name, signature, superName, interfaces)
        }
    }

    //   Matching

    /**
     * The concrete reason a candidate actual fails to implement an expect, or [SignatureDiff.None] when it does.
     */
    private sealed interface SignatureDiff {
        data object None : SignatureDiff

        data class Stasis(val expectIsStatic: Boolean, val actualIsStatic: Boolean) : SignatureDiff

        data class ReturnType(val expect: JavaType, val actual: JavaType) : SignatureDiff

        data class ParameterCount(val expect: Int, val actual: Int) : SignatureDiff

        data class ParameterType(
            val index: Int,
            val expect: JavaType,
            val actual: JavaType,
        ) : SignatureDiff

        /**
         * How well this diff explains the failure, lowest first. Used only to choose which candidate to report when
         * several actuals share the expect's method name; with a single candidate — the usual case — it has no effect.
         * A parameter-type mismatch ranks first because it means the arity lined up and most parameters matched
         * positionally, which is the strongest evidence this is the actual the user meant.
         */
        val distance: Int
            get() = when (this) {
                is None -> 0
                is ParameterType -> 1
                is ReturnType -> 2
                is ParameterCount -> 3
                is Stasis -> 4
            }
    }

    /**
     * Finds the actual in [actuals] that implements [expect], reporting whatever is wrong with the match.
     *
     * Reporting lives here rather than at the call site because the failure modes are not equally informative: naming
     * the concrete cause (`STASIS_MISMATCH`, a wrong parameter type, ...) is worth far more than the generic
     * "no corresponding @JniActual" message. The generic message is therefore only a fallback, for when no actual of
     * this method exists at all and there is no specific cause to point at.
     *
     * Nothing is reported until it is known that no candidate matches: a target may carry several actuals for the same
     * method, and reporting the first one that fails would condemn a match the second one satisfies.
     */
    private fun matchAgainst(expect: JniExpectInfo, actuals: MutableList<JniActualInfo>, target: String) {
        fun JavaType.matchesNullable(actual: JavaType): Boolean = !nullable || actual.nullable

        val candidates = actuals.filter { it.className == expect.className && it.methodName == expect.methodName }

        val diffs = candidates.map { it to diff(expect, it) }
        val matched = diffs.firstOrNull { it.second == SignatureDiff.None }?.first
        if (matched != null) {
            actuals.remove(matched)

            checkCriticalExpect(expect, matched)

            // Parameters flow JVM -> native: the expect (the JVM caller's contract) may hand `null` to a native whose
            // declared parameter is non-null, so the mismatch to flag is "expect nullable, actual not".
            matched.parameterTypes.zip(expect.parametersWithTypes) { actualParam, (irParam, expectParam) ->
                if (!expectParam.matchesNullable(actualParam)) {
                    state.reporter.at(irParam)
                        .report(JniBindingConsumerErrors.NULLABILITY_MISMATCH, expectParam, actualParam)
                }
            }

            // The return value flows native -> JVM: the native may hand `null` where the expect (the JVM caller's
            // contract) promises it never sees one, so the mismatch to flag is "actual nullable, expect not".
            val expectReturns = expect.returnType
            if (!matched.returnType.matchesNullable(expectReturns)) {
                state.reporter.at(expect.source)
                    .report(JniBindingConsumerErrors.NULLABILITY_MISMATCH, expectReturns, matched.returnType)
            }

            return
        }

        // Nothing matched. Every actual that IS this expect's method is already accounted for by the diagnostics below,
        // so take it out of the pool instead of letting it resurface as a leftover "extra" actual.
        actuals.removeAll(candidates.toSet())

        val closest = diffs.minByOrNull { it.second.distance }?.second ?: SignatureDiff.None
        report(expect, target, closest)
    }

    /**
     * Reports [diff] against the expect, pinpointing the offending declaration where one can be identified. A [diff] of
     * [SignatureDiff.None] means no actual of this method was declared at all, so there is no specific cause to name
     * and the generic "missing" message is the fallback.
     */
    private fun report(expect: JniExpectInfo, target: String, diff: SignatureDiff) {
        fun staticStatus(isStatic: Boolean?): String = when (isStatic) {
            true -> "static"
            false -> "instance"
            null -> "doesn't matter"
        }

        when (diff) {
            is SignatureDiff.None -> state.reporter.at(expect.source).report(
                JniBindingConsumerErrors.MISSING_JNI_ACTUAL,
                target to expect.methodName,
                expect.parameterTypes,
            )
            is SignatureDiff.Stasis -> state.reporter.at(expect.source).report(
                JniBindingConsumerErrors.STASIS_MISMATCH,
                staticStatus(diff.expectIsStatic),
                staticStatus(diff.actualIsStatic),
            )
            is SignatureDiff.ReturnType -> state.reporter.at(expect.source).report(
                JniBindingConsumerErrors.RETURN_TYPE_MISMATCH,
                diff.expect,
                diff.actual,
            )
            is SignatureDiff.ParameterCount -> state.reporter.at(expect.source).report(
                JniBindingConsumerErrors.PARAMETER_COUNT_MISMATCH,
                diff.expect,
                diff.actual,
            )
            is SignatureDiff.ParameterType -> {
                val irParam = expect.parametersWithTypes.getOrNull(diff.index)?.first
                if (irParam != null) {
                    state.reporter.at(irParam).report(
                        JniBindingConsumerErrors.PARAMETER_TYPE_MISMATCH,
                        diff.index,
                        diff.expect,
                        diff.actual,
                    )
                } else {
                    state.reporter.at(expect.source).report(
                        JniBindingConsumerErrors.PARAMETER_TYPE_MISMATCH,
                        diff.index,
                        diff.expect,
                        diff.actual,
                    )
                }
            }
        }
    }

    /**
     * Checks that both halves agree a `@JniExpect` is critical, and that it is `static`.
     *
     * A critical native is invoked without a class/object reference, so an instance expect would silently never get
     * one. The annotation is not read by the runtime - it is the marker the two halves agree on - so a mismatch is
     * reported whichever way it goes.
     */
    private fun checkCriticalExpect(expect: JniExpectInfo, actual: JniActualInfo) {
        val expectIsCritical = expect.source.hasCriticalNative()
        if (expectIsCritical != actual.isCritical) {
            if (actual.isCritical) {
                state.reporter.at(expect.source)
                    .report(JniBindingConsumerErrors.CRITICAL_NATIVE_ANNOTATION_MISSING)
            } else {
                state.reporter.at(expect.source)
                    .report(JniBindingConsumerErrors.CRITICAL_NATIVE_UNEXPECTED)
            }
        }
        if (actual.isCritical && !expect.isStatic) {
            state.reporter.at(expect.source)
                .report(JniBindingConsumerErrors.CRITICAL_NATIVE_MUST_BE_STATIC)
        }
    }

    /** Whether this function carries `@CriticalNative`, under either the project's spelling or Android's alias. */
    private fun IrSimpleFunction.hasCriticalNative(): Boolean =
        hasAnnotation(Symbols.CriticalNative) || hasAnnotation(Symbols.androidCriticalNative)

    /**
     * Compares [expect] against a candidate [actual] that is already known to be the same method by name, and returns
     * the concrete reason it does not implement the expect. Pure: it reports nothing.
     *
     * Checks run cheapest-and-most-specific first, so the returned diff is the most actionable one.
     */
    private fun diff(expect: JniExpectInfo, actual: JniActualInfo): SignatureDiff {
        if (actual.isStasisValuable() && actual.isStatic != expect.isStatic) {
            return SignatureDiff.Stasis(expect.isStatic, actual.isStatic!!)
        }

        val expectReturns = expect.returnType
        val actualReturns = actual.returnType
        if (actualReturns.type != expectReturns.type) return SignatureDiff.ReturnType(expectReturns, actualReturns)

        if (actual.parameterTypes.size != expect.parameterTypes.size) {
            return SignatureDiff.ParameterCount(expect.parameterTypes.size, actual.parameterTypes.size)
        }
        // Walk the parameters to the first one that differs: naming one wrong parameter is more useful than reporting
        // every one of them, and the arity already lined up.
        for ((index, pair) in expect.parametersWithTypes.withIndex()) {
            val expectParam = pair.second
            val actualParam = actual.parameterTypes[index]
            if (actualParam.type != expectParam.type) {
                return SignatureDiff.ParameterType(index, expectParam, actualParam)
            }
        }

        return SignatureDiff.None
    }
}