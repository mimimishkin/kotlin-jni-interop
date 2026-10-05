@file:OptIn(SymbolInternals::class)

package io.github.mimimishkin.jni.binding.producer.fir

import io.github.mimimishkin.jni.binding.producer.Symbols
import io.github.mimimishkin.jni.binding.producer.callableId
import io.github.mimimishkin.jni.binding.producer.ident
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.expressions.FirNamedArgumentExpression
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.expressions.FirStringConcatenationCall
import org.jetbrains.kotlin.fir.resolve.getContainingClass
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name

/**
 * A `@JniActual` function together with the generated container instance its dispatch receiver binds
 * to: `null` for top-level functions and `object` members, the generated `lateinit` property for
 * members of a constructable container.
 */
internal data class FunLocation(
    val fn: FirFunctionSymbol<*>,
    val container: FirRegularPropertySymbol? = null,
) {
    /**
     * The facade name encoding the full callable path (package + class + name) plus the mangled JVM parameter
     * signature, so overloaded actuals produce distinct generated functions (e.g. `add(Int, Int)` vs `add(Float, Float)`).
     */
    fun facadeCallableId(jniSignature: String = ""): CallableId {
        val encodedName = fn.callableId.asSingleFqName().asString().replace('.', '_') + "JniBinding"
        return Symbols.generatedPackage.callableId(encodedName + jniSignature)
    }

    /**
     * The generated name of the `JavaCritical_<class>_<method>` entry point of a `@CriticalNative` actual, next to the
     * [facadeCallableId] of its fallback facade.
     *
     * Only the generated Kotlin name is encoded here: the symbol a JVM resolves is fixed by the `@CName` annotation,
     * which [FirJniFacades.criticalFacade] derives from the `JavaCritical_` prefix.
     */
    fun criticalFacadeCallableId(jniSignature: String = ""): CallableId {
        val encodedName = fn.callableId.asSingleFqName().asString().replace('.', '_') + "CriticalJniBinding"
        return Symbols.generatedPackage.callableId(encodedName + jniSignature)
    }
}

/**
 * A hook step: a fragment-local top-level function holding a single statement the entry point must run. The
 * callable id encodes the step's *phase* (a global rank valid for the whole compilation) and its fragment-local
 * position, so `JniHookStepsMerger` (IR) restores the canonical order across all fragments.
 */
internal data class HookStep(
    val callableId: CallableId,
    val isLoad: Boolean,
    val buildBody: (vmParameterSymbol: FirValueParameterSymbol, vmType: ConeKotlinType) -> FirStatement,
)

/** The JNI receiver status of a facade: `true` for a static (`jclass`) receiver, `false` for an instance (`jobject`) one, `null` otherwise. */
internal fun facadeReceiverStasis(receiverTypeRef: FirResolvedTypeRef?): Boolean? {
    var type = receiverTypeRef?.coneType ?: return null
    if (type.classId != Symbols.CPointer) return null
    type = type.typeArguments.singleOrNull()?.type ?: return null

    return when (type.classId) {
        Symbols.JClassRaw, Symbols.JClassWrapped -> true
        Symbols.JObjectRaw, Symbols.JObjectWrapped -> false
        else -> null
    }
}

/**
 * Whether [this] is a `JniEnv`.
 *
 * `JniEnv` is a type alias for `CPointerVar<Raw_JniNativeInterface>`, and `Raw_JniNativeInterface` is actualized either
 * by a type alias to the cinterop `jni.JNINativeInterface_` or - on the targets that need a delegating declaration to
 * bridge the two JNI headers - by the library's own class. Both have to be recognized.
 */
internal fun ConeKotlinType.isJniEnvType(): Boolean {
    if (classId != Symbols.CPointerVarOf) return false
    val pointer = typeArguments.singleOrNull()?.type ?: return false
    if (pointer.classId != Symbols.CPointer) return false
    val pointed = pointer.typeArguments.singleOrNull()?.type ?: return false
    return pointed.classId in setOf(Symbols.JNINativeInterface, Symbols.JNINativeInterfaceWrapped, Symbols.JNINativeInterfaceAndroid)
}

/**
 * Whether [this] is a `JavaVM`.
 *
 * See [isJniEnvType] for why both the cinterop and the library's own `Raw_JniInvokeInterface` are accepted.
 */
internal fun ConeKotlinType.isJavaVmType(): Boolean {
    if (classId != Symbols.CPointerVarOf) return false
    val pointer = typeArguments.singleOrNull()?.type ?: return false
    if (pointer.classId != Symbols.CPointer) return false
    val pointed = pointer.typeArguments.singleOrNull()?.type ?: return false
    return pointed.classId in setOf(Symbols.JNIInvokeInterface, Symbols.JNIInvokeInterfaceWrapped, Symbols.JNIInvokeInterfaceAndroid)
}

/**
 * Whether [this] is a `JArray`, i.e. a pointer to any JNI array.
 */
internal fun ConeKotlinType.isJArrayType(): Boolean =
    classId == Symbols.CPointer && typeArguments.singleOrNull()?.type?.classId == Symbols.JArrayWrapped

/** The JVM class and method a `@JniActual` function binds to. */
internal data class JniTarget(
    val className: String,
    val methodName: String,
)

/**
 * The JVM target of a function: from an explicit `@JniActual(className, methodName)`, or implicitly the enclosing
 * `@JniActuals` container class with the function's own name as the method. `null` when no annotation context is present.
 */
internal fun jniActualArguments(fn: FirFunctionSymbol<*>): JniTarget? {
    val declaration = fn.fir
    val defaultMethodName = fn.name.asString()

    // Explicit @JniActual on the function
    val explicitAnnotation = declaration.annotations.filterIsInstance<FirAnnotationCall>().find {
        it.annotationTypeRef.coneType.classId == Symbols.JniActual
    }
    if (explicitAnnotation != null) {
        val className = explicitAnnotation.argumentValue("className")
        val methodName = explicitAnnotation.argumentValue("methodName")
        return JniTarget(
            className = className ?: fn.callableId.asSingleFqName().asString(),
            methodName = methodName?.takeIf { it.isNotEmpty() } ?: defaultMethodName,
        )
    }

    // Implicit actual: function inside @JniActuals container
    val containerClass = (declaration as? FirNamedFunction)?.getContainingClass() ?: return null
    val containerAnnotation = containerClass.annotations.filterIsInstance<FirAnnotationCall>().firstOrNull {
        it.annotationTypeRef.coneType.classId == Symbols.JniActuals
    } ?: return null
    val className = containerAnnotation.argumentValue("className")
        ?: (containerClass.symbol as? FirClassSymbol<*>)?.classId?.asSingleFqName()?.asString()
        ?: return null
    return JniTarget(className = className, methodName = defaultMethodName)
}

internal fun FirAnnotationCall.argumentValue(name: String): String? =
    argumentMapping.mapping[name.ident()]?.literalStringValue()
        ?: argumentList.arguments
            .filterIsInstance<FirNamedArgumentExpression>()
            .firstOrNull { it.name.asString() == name }
            ?.expression
            ?.literalStringValue()

/**
 * The literal `String` value of this expression when statically known: unwraps named-argument
 * wrappers and re-assembles plain string concatenations; `null` otherwise.
 */
internal fun FirExpression.literalStringValue(): String? {
    val expression = if (this is FirNamedArgumentExpression) expression else this
    if (expression is FirStringConcatenationCall) {
        return expression.argumentList.arguments.filterIsInstance<FirLiteralExpression>()
            .joinToString("") { it.value as? String ?: "" }.ifEmpty { null }
    }
    return (expression as? FirLiteralExpression)?.value as? String
}
