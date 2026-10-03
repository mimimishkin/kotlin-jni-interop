@file:OptIn(
    org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class,
)

package io.github.mimimishkin.jni.binding.consumer.ir

import io.github.mimimishkin.jni.binding.consumer.JniBindingConsumerErrors
import io.github.mimimishkin.jni.binding.consumer.Symbols
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.createTmpVariable
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irIfThenElse
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTrue
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueDeclaration
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrAnonymousInitializerSymbolImpl
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isObject
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name

/**
 * Injects the call to each `@LoadMethod` function of an `@JniExpects` annotatee into the place where the native
 * library should be loaded.
 *
 * For a regular class, the `@LoadMethod` functions must live in its companion object; the calls are added to a
 * static [org.jetbrains.kotlin.ir.declarations.IrAnonymousInitializer] that the JVM backend moves into `<clinit>`
 * (see `StaticInitializersLowering`), ordered after the companion object's `INSTANCE` field assignment, so the
 * companion instance is always initialized before these calls run.
 *
 * For a Kotlin object, the `@LoadMethod` functions live in the object itself; the calls are injected into the
 * object's `<init>` (primary constructor) body, dispatching on `this`.
 *
 * Every `@LoadMethod` function is called with the owning container as the dispatch receiver, passing only the
 * `os`/`arch`/`vendor` String parameters it declares. The values are computed by [PlatformLoadArguments] and only
 * for the parameters some `@LoadMethod` function actually requires.
 */
internal class LoadMethodInjector(private val pluginContext: IrPluginContext) {

    private val irFactory = pluginContext.irFactory
    private val platformArguments = PlatformLoadArguments(pluginContext)

    /**
     * Generates and appends the loading call for every `@LoadMethod` function of [declaration] to the appropriate
     * place: `<clinit>` for a regular class (via its companion), `<init>` for a Kotlin object.
     *
     * @return `true` if a loading call was injected.
     */
    fun inject(declaration: IrClass): Boolean =
        if (declaration.isObject) injectIntoObject(declaration) else injectIntoClass(declaration)

    /**
     * Regular class: finds `@LoadMethod` functions in the companion object and adds a static `<clinit>` initializer
     * that calls each of them with the companion as the dispatch receiver.
     */
    private fun injectIntoClass(declaration: IrClass): Boolean {
        val companion = declaration.companionObjectOrNull() ?: return false
        val loadMethods = companion.loadMethods()
        if (loadMethods.isEmpty()) return false

        val builder = DeclarationIrBuilder(
            pluginContext,
            declaration.symbol,
            declaration.startOffset,
            declaration.endOffset,
        )

        val initializer = irFactory.createAnonymousInitializer(
            startOffset = declaration.startOffset,
            endOffset = declaration.endOffset,
            origin = IrDeclarationOrigin.DEFINED,
            symbol = IrAnonymousInitializerSymbolImpl(),
            isStatic = true,
        ).also { it.parent = declaration }

        initializer.body = builder.irBlockBody {
            buildLoadCalls(loadMethods, irGetObject(companion.symbol))
        }

        declaration.declarations += initializer
        return true
    }

    /**
     * Kotlin object: finds `@LoadMethod` functions declared in the object itself and appends calls to each of them
     * to the object's `<init>` (primary constructor) body, dispatching on `this`.
     */
    private fun injectIntoObject(declaration: IrClass): Boolean {
        val loadMethods = declaration.loadMethods()
        if (loadMethods.isEmpty()) return false

        val constructor = declaration.findConstructor() ?: return false
        val body = constructor.body as? IrBlockBody ?: return false
        val builder = DeclarationIrBuilder(
            pluginContext,
            constructor.symbol,
            constructor.startOffset,
            constructor.endOffset,
        )
        // At the IR-generation phase the object's constructor has no dispatch receiver parameter yet, but the
        // class-level `this` (IrClass.thisReceiver) is available and denotes the same object being constructed.
        val receiver = declaration.thisReceiver ?: return false
        val added = builder.irBlockBody {
            buildLoadCalls(loadMethods, irGet(receiver))
        }
        body.statements += added.statements
        return true
    }

    /** `@LoadMethod` functions declared directly in this container (its own declarations, not a companion). */
    private fun IrClass.loadMethods(): List<IrSimpleFunction> =
        declarations.filterIsInstance<IrSimpleFunction>().mapNotNull { fn ->
            if (fn.correspondingPropertySymbol != null ||
                fn.isSuspend ||
                !fn.hasAnnotation(Symbols.LoadMethod.asSingleFqName())
            ) {
                null
            } else {
                val illegal = fn.parameters.filter { param ->
                    // The member `this` (IrParameterKind.DispatchReceiver) is fine: the injector fills it via the
                    // object/class receiver.
                    // Only out-of-scope value parameters and extension/context receivers are not.
                    (param.kind == IrParameterKind.Regular && param.name !in Symbols.loadMethodParameters) ||
                        param.kind == IrParameterKind.ExtensionReceiver ||
                        param.kind == IrParameterKind.Context
                }
                if (illegal.isNotEmpty()) {
                    reportIllegalParameters(fn, illegal)
                    null
                } else {
                    fn
                }
            }
        }

    /**
     * Reports [JniBindingConsumerErrors.ILLEGAL_LOAD_METHOD_PARAMETERS] for an `@LoadMethod` function whose declared
     * parameters cannot be filled by the loader: an extension/context receiver or a parameter that is not one of
     * [Symbols.loadMethodParameters]. Such a function is otherwise silently never called.
     */
    private fun reportIllegalParameters(fn: IrSimpleFunction, illegal: List<IrValueParameter>) {
        val description = illegal.joinToString(", ") { param ->
            when (param.kind) {
                IrParameterKind.Context -> "context `${param.name}`"
                IrParameterKind.ExtensionReceiver -> "extension receiver `${param.name}`"
                else -> "`${param.name}`"
            }
        }
        pluginContext.diagnosticReporter.at(fn).report(
            JniBindingConsumerErrors.ILLEGAL_LOAD_METHOD_PARAMETERS,
            description,
        )
    }

    private fun IrClass.findConstructor(): IrConstructor? =
        constructors.firstOrNull { it.isPrimary } ?: constructors.firstOrNull()

    /** 
     * Emits the calls to every [loadMethods], passing the `os`/`arch`/`vendor` values it declares.
     */
    private fun IrBlockBodyBuilder.buildLoadCalls(loadMethods: List<IrSimpleFunction>, dispatchReceiver: IrExpression) {
        val needOs = loadMethods.any { it.hasName(Symbols.osParameter) }
        val needArch = loadMethods.any { it.hasName(Symbols.archParameter) }
        val needVendor = loadMethods.any { it.hasName(Symbols.vendorParameter) }

        val os = if (needOs) createTmpVariable(platformArguments.os(this)) else null
        val arch = if (needArch) createTmpVariable(platformArguments.arch(this)) else null
        val vendor = if (needVendor) createTmpVariable(platformArguments.vendor(this)) else null

        for (fn in loadMethods) {
            val call = irCall(fn.symbol)
            call.dispatchReceiver = dispatchReceiver
            call.type = fn.returnType
            for (param in fn.regularParameters()) {
                val value = when (param.name) {
                    Symbols.osParameter -> os
                    Symbols.archParameter -> arch
                    Symbols.vendorParameter -> vendor
                    else -> null
                }
                if (value != null) call.arguments[param] = irGet(value)
            }
            +call
        }
    }

    private fun IrSimpleFunction.hasName(name: Name) = regularParameters().any { it.name == name }

    private fun IrSimpleFunction.regularParameters(): List<IrValueParameter> =
        parameters.filter { it.kind == IrParameterKind.Regular }

    private fun IrClass.companionObjectOrNull(): IrClass? =
        declarations.filterIsInstance<IrClass>().firstOrNull { it.isCompanion }
}

/**
 * Builds the `os`/`arch`/`vendor` argument expressions a `@LoadMethod` function can declare, normalized from the
 * corresponding system properties.
 *
 * The values are only built for the parameters a load method actually declares; the raw system properties are read
 * once per kind (`os.name`, `os.arch`, `java.vendor`) and lowercased as needed by the respective normalizer.
 */
internal class PlatformLoadArguments(private val pluginContext: IrPluginContext) {

    private val irBuiltIns = pluginContext.irBuiltIns
    private val stringType: IrType = irBuiltIns.stringType
    private val booleanType: IrType = irBuiltIns.booleanType

    /** The `java.vendor` system property. */
    fun vendor(builder: IrBlockBodyBuilder): IrExpression =
        with(builder) { systemProperty("java.vendor") }

    /**
     * The normalized OS family: `windows`, `macos`, `linux`, `android`, or the raw lowercased name.
     *
     * Android is reported from `java.vendor` because its `os.name` is just `Linux`, which would otherwise make an
     * Android runtime indistinguishable from a desktop one.
     */
    fun os(builder: IrBlockBodyBuilder): IrExpression = with(builder) {
        val raw: IrValueDeclaration = createTmpVariable(stringToLowerCase(systemProperty("os.name", "")))
        val lower = irGet(raw)
        val vendor: IrValueDeclaration = createTmpVariable(stringToLowerCase(systemProperty("java.vendor", "")))
        fun contains(receiver: IrExpression, needle: String) = stringContains(receiver, needle)
        irIfThenElse(
            stringType,
            contains(irGet(vendor), "android"), irString("android"),
            irIfThenElse(
                stringType,
                contains(lower, "windows"), irString("windows"),
                irIfThenElse(
                    stringType,
                    contains(lower, "mac"), irString("macos"),
                    irIfThenElse(stringType, orElse(contains(lower, "nux"), contains(lower, "nix")), irString("linux"), lower),
                ),
            ),
        )
    }

    /** The normalized arch family from `os.arch` (the raw value is returned if it matches no known family). */
    fun arch(builder: IrBlockBodyBuilder): IrExpression = with(builder) {
        val raw: IrValueDeclaration = createTmpVariable(systemProperty("os.arch"))
        val value = irGet(raw)
        fun eq(literal: String) = stringEquals(value, literal)
        ARCH_GROUPS.foldRight<Pair<String, List<String>>, IrExpression>(value) { (family, literals), acc ->
            literals.foldRight(acc) { literal, nestedAcc ->
                irIfThenElse(stringType, eq(literal), irString(family), nestedAcc)
            }
        }
    }

    /** `System.getProperty(name)`. */
    private fun IrBlockBodyBuilder.systemProperty(name: String): IrExpression =
        irCall(function(Symbols.systemGetProperty, irBuiltIns.stringClass)).apply {
            fillArguments(irString(name))
            type = stringType
        }

    /** `System.getProperty(name, default)`, which never returns `null`. */
    private fun IrBlockBodyBuilder.systemProperty(name: String, default: String): IrExpression =
        irCall(function(Symbols.systemGetProperty, irBuiltIns.stringClass, irBuiltIns.stringClass)).apply {
            fillArguments(irString(name), irString(default))
            type = stringType
        }

    private fun IrBlockBodyBuilder.stringToLowerCase(receiver: IrExpression): IrExpression =
        irCall(function(Symbols.stringToLowerCase)).apply {
            dispatchReceiver = receiver
            type = stringType
        }

    private fun IrBlockBodyBuilder.stringContains(receiver: IrExpression, needle: String): IrExpression =
        irCall(function(Symbols.stringContains, irBuiltIns.charSequenceClass)).apply {
            dispatchReceiver = receiver
            fillArguments(irString(needle))
            type = booleanType
        }

    private fun IrBlockBodyBuilder.stringEquals(a: IrExpression, b: String): IrExpression =
        irCall(function(Symbols.objectsEquals, irBuiltIns.anyClass, irBuiltIns.anyClass)).apply {
            fillArguments(a, irString(b))
            type = booleanType
        }

    private fun IrBlockBodyBuilder.orElse(lhs: IrExpression, rhs: IrExpression): IrExpression =
        irIfThenElse(booleanType, lhs, irTrue(), rhs)

    /** Sizes the argument list to match the target function's parameters and fills the regular parameter slots. */
    private fun IrFunctionAccessExpression.fillArguments(vararg arguments: IrExpression) {
        val parameterCount = symbol.owner.parameters.size
        val valueArguments = this.arguments
        repeat(parameterCount - valueArguments.size) { valueArguments.add(null) }
        var regular = 0
        for ((index, parameter) in symbol.owner.parameters.withIndex()) {
            if (parameter.kind == IrParameterKind.Regular) {
                valueArguments[index] = arguments[regular++]
            }
        }
    }

    private fun function(callableId: CallableId, vararg paramTypes: IrClassSymbol): IrSimpleFunctionSymbol {
        val viaFinder = pluginContext.finderForBuiltins().findFunctions(callableId)
        val classSymbol = callableId.classId?.let { pluginContext.finderForBuiltins().findClass(it) }
        val fromOwner = classSymbol?.owner?.declarations?.filterIsInstance<IrSimpleFunction>()
            ?.filter { it.name.asString() == callableId.callableName.asString() }
            .orEmpty()
        val expected = paramTypes.asList()
        return (viaFinder + fromOwner.map { it.symbol })
            .firstOrNull { it.owner.regularParameterTypes() == expected }
            ?: error("Cannot resolve $callableId")
    }

    private fun IrSimpleFunction.regularParameterTypes(): List<IrClassSymbol> =
        parameters.filter { it.kind == IrParameterKind.Regular }.map { param ->
            (param.type as? IrSimpleType)?.classifier as? IrClassSymbol
                ?: error("Unexpected parameter type ${param.type} in $name")
        }
}

/** The `os.arch` spellings of each normalized arch family. */
private val ARCH_GROUPS = listOf(
    "x86" to listOf("x86", "i386", "ia-32", "i686"),
    "x86_64" to listOf("x86-64", "x86_64", "amd64", "x64"),
    "aarch32" to listOf("arm-v7", "armv7", "arm", "arm32"),
    "aarch64" to listOf("aarch64", "arm-v8", "arm64"),
    "riscv32" to listOf("riscv32", "rv32"),
    "riscv64" to listOf("riscv64", "rv64"),
)
