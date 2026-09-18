@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package io.github.mimimishkin.jni.binding.consumer.jvm

import io.github.mimimishkin.jni.binding.consumer.ConsumerState
import io.github.mimimishkin.jni.binding.consumer.JniBindingConsumerErrors
import io.github.mimimishkin.jni.binding.consumer.Symbols
import io.github.mimimishkin.jni.binding.consumer.model.JavaType
import io.github.mimimishkin.jni.binding.consumer.model.JniExpectInfo
import org.jetbrains.kotlin.backend.jvm.JvmLoweredDeclarationOrigin
import org.jetbrains.kotlin.backend.jvm.extensions.ClassGenerator
import org.jetbrains.kotlin.backend.jvm.extensions.ClassGeneratorExtension
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Type as AsmType

private val jniExpectAnnotation = Symbols.JniExpect.asSingleFqName()
private val jniExpectsAnnotation = Symbols.JniExpects.asSingleFqName()

private fun IrAnnotationContainer.hasJniExpect() = hasAnnotation(jniExpectAnnotation)

/**
 * The single place where `@JniExpect` functions are matched against the JVM names the backend actually produces.
 *
 * Unlike a source-level scan, this runs as a `ClassGeneratorExtension` while the real bytecode is generated. The
 * `defineClass(...)`/`newMethod(...)` names it observes are the authoritative, `@JvmName`-remapped names produced by
 * `MethodSignatureMapper` during `JvmLowering`, so the consumer never has to re-derive them from annotations.
 *
 * For each `@JniExpect` function it builds the [JniExpectInfo] with those backend-resolved names, matches it against
 * the per-target `actuals.json`, reports missing actuals (and nullability mismatches), and reports leftover actuals
 * (`EXTRA_JNI_ACTUALS`) once every expect has been processed.
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

        return VerifyingDelegate(generator, state)
    }

    private class VerifyingDelegate(
        private val delegate: ClassGenerator,
        private val state: ConsumerState,
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
                checkFunction(declaration, currentClassName!!, name, desc)
            }
            return delegate.newMethod(declaration, access, name, desc, signature, exceptions)
        }

        private fun checkFunction(function: IrSimpleFunction, className: String, asmMethodName: String, desc: String) {
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
            actualize(expectInfo, function)
        }

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
                val match = actuals.firstOrNull { JniExpects.matches(expect, it, state.reporter) }
                if (match == null) {
                    state.reporter.at(source).report(
                        JniBindingConsumerErrors.MISSING_JNI_ACTUAL,
                        target to expect.methodName,
                        expect.parameterTypes,
                    )
                } else {
                    actuals.remove(match)
                }

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
}