@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package io.github.mimimishkin.jni.binding.consumer.ir

import io.github.mimimishkin.jni.binding.consumer.ConsumerState
import io.github.mimimishkin.jni.binding.consumer.Symbols
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrAnnotationContainer
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.hasAnnotation

private val jniExpectAnnotation = Symbols.JniExpect.asSingleFqName()
private val jniExpectsAnnotation = Symbols.JniExpects.asSingleFqName()

/**
 * Injects the native library load methods into `@JniExpects` classes and objects.
 */
internal class ProcessJniExpectsTransformer(
    pluginContext: IrPluginContext,
    private val state: ConsumerState,
) : IrElementTransformerVoidWithContext() {

    init {
        // initialize [reporter] to use it in [JniExpectMatcher]
        state.reporter = pluginContext.diagnosticReporter
    }

    private val loadMethodInjector = LoadMethodInjector(pluginContext)

    /** Classes that declare `@JniExpect` functions, into which the native library loading is injected. */
    private val expectsClasses = mutableSetOf<IrClass>()

    override fun visitSimpleFunction(declaration: IrSimpleFunction): IrStatement {
        val parentClass = declaration.parent as? IrClass
        val parentFile = declaration.parent as? IrFile
        val implicitJniExpect = declaration.isExternal &&
            (parentClass?.hasJniExpects() == true || parentFile?.hasJniExpects() == true)
        val explicitJniExpect = declaration.hasJniExpect() ||
                declaration.correspondingPropertySymbol?.owner?.hasJniExpect() == true

        if (implicitJniExpect || explicitJniExpect) {
            if (declaration.isExternal && parentClass != null) expectsClasses += parentClass
            // Accessors live under their property, so walk up through it to find the real container.
            val container = (declaration.parent as? IrProperty)?.parent ?: declaration.parent
            when (container) {
                is IrClass -> state.expectContainers += container
                is IrFile -> state.expectFiles += container
            }
            for (target in state.targetsFor(declaration.effectiveTargetMachines())) {
                state.remainingExpectsByTarget.getOrPut(target) { mutableSetOf() }.add(declaration)
            }
        }

        return super.visitSimpleFunction(declaration)
    }

    override fun visitModuleFragment(declaration: IrModuleFragment): IrModuleFragment {
        state.actualsByTarget.keys.forEach {
            state.remainingExpectsByTarget.putIfAbsent(it, mutableSetOf())
        }
        val result = super.visitModuleFragment(declaration)
        expectsClasses.forEach { loadMethodInjector.inject(it) }
        return result
    }
}

private fun IrAnnotationContainer.hasJniExpect() = hasAnnotation(jniExpectAnnotation)

private fun IrAnnotationContainer.hasJniExpects() = hasAnnotation(jniExpectsAnnotation)

/** The `@JniExpect` annotation driving this expect: the function's own or, for an accessor, its property's. */
private fun IrSimpleFunction.expectAnnotation() =
    getAnnotation(jniExpectAnnotation) ?: correspondingPropertySymbol?.owner?.getAnnotation(jniExpectAnnotation)

/** The effective `targetMachine` list of an expect, from its own `@JniExpect` (or its property's) or its container's
 * `@JniExpects`. */
private fun IrSimpleFunction.effectiveTargetMachines(): List<String> =
    (expectAnnotation() ?: (parent as? IrAnnotationContainer)?.getAnnotation(jniExpectsAnnotation))
        ?.arguments
        ?.firstOrNull()
        ?.let { it as? IrVararg }
        ?.elements
        ?.filterIsInstance<IrConst>()
        ?.mapNotNull { it.value as? String }
        ?: emptyList()