package io.github.mimimishkin.jni.binding.producer.fir

import io.github.mimimishkin.jni.binding.producer.Symbols
import io.github.mimimishkin.jni.binding.producer.ident
import io.github.mimimishkin.jni.binding.producer.model.ActualParameterType
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.FirSession.Companion.sessionComponentAccessor
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.FirCollectionLiteral
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.expressions.FirNamedArgumentExpression
import org.jetbrains.kotlin.fir.expressions.FirSpreadArgumentExpression
import org.jetbrains.kotlin.fir.expressions.FirVarargArgumentsExpression
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.isMarkedNullable
import org.jetbrains.kotlin.fir.types.typeAnnotations
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.StandardClassIds

private val primitiveJvmTypes = setOf("boolean", "byte", "char", "short", "int", "long", "float", "double", "void")

/**
 * Everything derived from the `@WithJvmSignature`/`@WithJvmType` annotations of a single [FirFunction]:
 * the per-parameter and return JVM binary types plus the exact annotated strings for the `actuals.json` contract.
 */
internal data class JvmSignatureInfo(
    val hasSignatureAnnotation: Boolean,
    val signatureParameterCount: Int,
    val parameterTypes: List<ActualParameterType?>,
    val returnType: ActualParameterType?,
    val jsonParameterTypes: List<String?>,
    val jsonReturnType: String?,
)

/**
 * Parses `@WithJvmType`/`@WithJvmSignature` exactly once per function and caches the result per symbol, so the
 * checker and the generator share one answer even though they may observe different [FirFunction] instances of the
 * same symbol.
 */
internal class JvmSignatureProvider(
    session: FirSession,
) : FirExtensionSessionComponent(session) {

    private val signatures = mutableMapOf<FirFunctionSymbol<*>, JvmSignatureInfo>()
    private val criticalLayouts = mutableMapOf<FirFunctionSymbol<*>, CriticalLayout>()

    fun signatureInfo(declaration: FirFunction): JvmSignatureInfo =
        signatures.getOrPut(declaration.symbol) {
            // A critical actual has more native parameters than the JVM method it implements, so its signature is the
            // collapsed one the layout produces rather than the per-parameter one [compute] derives.
            val layout = criticalLayout(declaration)
            if (layout != null) {
                if (layout.isValid) {
                    JvmSignatureInfo(
                        hasSignatureAnnotation = layout.isSignatureAnnotated,
                        signatureParameterCount = layout.slots.size,
                        parameterTypes = layout.jvmParameterTypes,
                        returnType = layout.returnType,
                        jsonParameterTypes = layout.jsonParameterTypes,
                        jsonReturnType = layout.jsonReturnType,
                    )
                } else {
                    // An invalid layout has already been reported by the checker; mark it unmappable so the
                    // generator skips the actual rather than generating a facade from a broken signature.
                    JvmSignatureInfo(
                        hasSignatureAnnotation = false,
                        signatureParameterCount = layout.slots.size,
                        parameterTypes = List(layout.slots.size) { null },
                        returnType = null,
                        jsonParameterTypes = List(layout.slots.size) { null },
                        jsonReturnType = null,
                    )
                }
            } else {
                compute(declaration)
            }
        }

    /**
     * The `@CriticalNative` parameter layout of [declaration], or `null` when it is not annotated as a critical
     * native. Memoized separately from [signatureInfo] because a critical actual resolves its JVM signature from the
     * collapsed slots rather than from its native parameters one by one.
     */
    fun criticalLayout(declaration: FirFunction): CriticalLayout? {
        val fn = declaration as? FirNamedFunction ?: return null
        if (!fn.isCriticalNative(session)) return null
        return criticalLayouts.getOrPut(declaration.symbol) { computeCriticalLayout(fn) }
    }

    /**
     * Walks the native parameters of a critical actual left to right, pairing every `CArrayPointer<T>` with the `Int`
     * length that must precede it. Every structural violation is collected as an issue so the checker can report all of
     * them at once, while [slots] still ends up holding everything that *is* usable.
     */
    private fun computeCriticalLayout(fn: FirNamedFunction): CriticalLayout {
        val resolvedAnnotation = withJvmSignatureAnnotation(fn)
        val declaredParameterTypes = resolvedAnnotation?.let { withJvmSignatureArguments(it) }
            ?.map { (it as? FirLiteralExpression)?.value as? String }.orEmpty()
        val declaredReturnType = resolvedAnnotation?.let { withJvmSignatureLiteral(it) }

        val issues = mutableListOf<CriticalIssue>()
        val slots = mutableListOf<CriticalSlot>()

        if (fn.contextParameters.isNotEmpty()) issues += CriticalIssue(CriticalProblem.ContextParameters)
        if (fn.receiverParameter != null) issues += CriticalIssue(CriticalProblem.Receiver)

        val parameters = fn.valueParameters
        var index = 0
        while (index < parameters.size) {
            val type = parameters[index].returnTypeRef.coneType
            if (type.isCArrayPointer()) {
                val element = type.criticalArrayElementOrNull()
                val lengthIndex = index - 1
                val hasLength = lengthIndex >= 0 &&
                        parameters[lengthIndex].returnTypeRef.coneType.classId == StandardClassIds.Int
                when {
                    element == null -> issues += CriticalIssue(CriticalProblem.UnsupportedArrayElement, index)
                    !hasLength -> issues += CriticalIssue(CriticalProblem.ArrayWithoutLength, index)
                    else -> {
                        // The length parameter was already recorded as a standalone scalar when it was visited;
                        // this pair consumes it, so the slot has to go or the collapsed signature would list both.
                        slots.removeIf { it is CriticalSlot.Scalar && it.parameterIndex == lengthIndex }
                        slots += CriticalSlot.Array(lengthIndex, index, element)
                        // The length parameter is consumed by this pair; skip it.
                        index++
                    }
                }
            } else {
                val scalar = criticalScalarTypeOrNull(type)
                if (scalar == null) issues += CriticalIssue(CriticalProblem.UnsupportedParameterType, index)
                else slots += CriticalSlot.Scalar(index, scalar)
            }
            index++
        }

        if (fn.returnTypeRef.coneType.classId !in CRITICAL_RETURN_CLASS_IDS) {
            issues += CriticalIssue(CriticalProblem.UnsupportedReturnType)
        }
        // An explicit JVM signature has to name exactly one type per collapsed JVM parameter - one per `(length, array)`
        // pair - so a mismatch is reported rather than silently taking the annotation over the derived layout.
        if (resolvedAnnotation != null && declaredParameterTypes.size != slots.size) {
            issues += CriticalIssue(CriticalProblem.UnresolvedType)
        }

        return CriticalLayout(
            slots = slots,
            issues = issues,
            declaredParameterTypes = declaredParameterTypes,
            declaredReturnType = declaredReturnType,
            hasSignatureAnnotation = resolvedAnnotation != null,
            derivedReturnType = criticalReturnTypeOrNull(fn.returnTypeRef.coneType),
        )
    }

    private fun compute(fn: FirFunction): JvmSignatureInfo {
        val resolvedAnnotation = withJvmSignatureAnnotation(fn)
        val hasSignatureAnnotation = resolvedAnnotation != null
        val signatureParameterCount =
            if (resolvedAnnotation != null) withJvmSignatureArguments(resolvedAnnotation).size else 0
        val parameterTypes = fn.valueParameters.indices.map { parameterType(fn, resolvedAnnotation, it) }
        val jsonParameterTypes = fn.valueParameters.indices.map { jsonTypeForActual(fn, resolvedAnnotation, it, false) }
        return JvmSignatureInfo(
            hasSignatureAnnotation = hasSignatureAnnotation,
            signatureParameterCount = signatureParameterCount,
            parameterTypes = parameterTypes,
            returnType = returnType(fn, resolvedAnnotation),
            jsonParameterTypes = jsonParameterTypes,
            jsonReturnType = jsonTypeForActual(fn, resolvedAnnotation, -1, isReturn = true),
        )
    }

    /**
     * The JVM-side binary type of the [index]-th parameter of [fn], or `null` when it cannot be determined
     * (`@WithJvmType`/`@WithJvmSignature` absent on a non-primitive type, or `void` used as a parameter type).
     */
    private fun parameterType(fn: FirFunction, resolvedAnnotation: FirAnnotationCall?, index: Int): ActualParameterType? {
        if (resolvedAnnotation != null) {
            val jvmType = withJvmSignatureArguments(resolvedAnnotation).getOrNull(index)
                ?.let { (it as? FirLiteralExpression)?.value as? String } ?: return null
            return ActualParameterType.fromJavaType(jvmType, isReturn = false)
                .takeUnless { it == ActualParameterType.Unit }
        }
        val param = fn.valueParameters.getOrNull(index) ?: return null
        val withJvmType = withJvmTypeValue(param.returnTypeRef)
        if (withJvmType != null) {
            return ActualParameterType.fromJavaType(withJvmType, isReturn = false)
                .takeUnless { it == ActualParameterType.Unit }
        }
        return coneTypeToActualParameterType(param.returnTypeRef.coneType.classId)
    }

    /**
     * The JVM-side binary type of the return type of [fn], or `null` when it cannot be determined
     * (`@WithJvmType`/`@WithJvmSignature` absent on a non-primitive, non-`Unit` type).
     */
    private fun returnType(fn: FirFunction, resolvedAnnotation: FirAnnotationCall?): ActualParameterType? {
        if (resolvedAnnotation != null) {
            val jvmReturn = withJvmSignatureLiteral(resolvedAnnotation) ?: return null
            return ActualParameterType.fromJavaType(jvmReturn, isReturn = true)
        }
        val returnTypeRef = fn.returnTypeRef
        val withJvmType = withJvmTypeValue(returnTypeRef)
        if (withJvmType != null) {
            return ActualParameterType.fromJavaType(withJvmType, isReturn = true)
        }
        return when (returnTypeRef.coneType.classId) {
            StandardClassIds.Boolean -> ActualParameterType.BooleanOut
            StandardClassIds.UByte -> ActualParameterType.UByte
            StandardClassIds.Byte -> ActualParameterType.Byte
            StandardClassIds.Char -> ActualParameterType.Char
            StandardClassIds.UShort -> ActualParameterType.UShort
            StandardClassIds.Short -> ActualParameterType.Short
            StandardClassIds.Int -> ActualParameterType.Int
            StandardClassIds.Long -> ActualParameterType.Long
            StandardClassIds.Float -> ActualParameterType.Float
            StandardClassIds.Double -> ActualParameterType.Double
            StandardClassIds.Unit -> ActualParameterType.Unit
            else -> null
        }
    }

    /**
     * The JVM type name recorded in the `actuals.json` contract for a parameter/return: `@WithJvmSignature` values are
     * used verbatim, reference types carry a `@NonNull ` prefix when non-null, and primitive types are always plain
     * names (`int`, `void`, ...) since a primitive is never nullable. Returns `null` when unresolved.
     */
    private fun jsonTypeForActual(
        fn: FirFunction,
        resolvedAnnotation: FirAnnotationCall?,
        index: Int,
        isReturn: Boolean,
    ): String? {
        if (resolvedAnnotation != null) {
            return if (isReturn) {
                withJvmSignatureLiteral(resolvedAnnotation)
            } else {
                withJvmSignatureArguments(resolvedAnnotation).getOrNull(index)
                    ?.let { (it as? FirLiteralExpression)?.value as? String }
            }
        }
        val typeRef = if (isReturn) fn.returnTypeRef else fn.valueParameters.getOrNull(index)?.returnTypeRef
        if (typeRef == null) return null
        return jvmType(typeRef)
    }

    private fun withJvmSignatureAnnotation(fn: FirFunction): FirAnnotationCall? =
        fn.annotations.filterIsInstance<FirAnnotationCall>().firstOrNull {
            it.annotationTypeRef.coneType.classId == Symbols.WithJvmSignature
        }

    /**
     * The vararg elements of the parameters argument of a `@WithJvmSignature` call.
     */
    private fun withJvmSignatureArguments(call: FirAnnotationCall): List<FirExpression> {
        val argument = call.argumentList.arguments.getOrNull(0) ?: return emptyList()
        val expression = (argument as? FirNamedArgumentExpression)?.expression ?: argument
        val elements = when (expression) {
            is FirVarargArgumentsExpression -> expression.arguments
            is FirCollectionLiteral -> expression.argumentList.arguments
            else -> return emptyList()
        }
        return elements.flatMap { element ->
            val unwrapped = if (element is FirSpreadArgumentExpression) element.expression else element
            if (unwrapped is FirCollectionLiteral) unwrapped.argumentList.arguments else listOf(unwrapped)
        }
    }

    /** The unwrapped `String` literal of the `returnType` argument of a `@WithJvmSignature` call. */
    private fun withJvmSignatureLiteral(call: FirAnnotationCall): String? {
        val argument = call.argumentList.arguments.getOrNull(1) ?: return null
        val expression = (argument as? FirNamedArgumentExpression)?.expression ?: argument
        return (expression as? FirLiteralExpression)?.value as? String
    }

    /**
     * The `String` value of the implicit `@WithJvmType` type-use annotation carried by the (possibly typealiased)
     * [typeRef].
     */
    private fun withJvmTypeValue(typeRef: FirTypeRef): String? {
        val annotations = buildList {
            (typeRef as? FirResolvedTypeRef)?.annotations?.let(this::addAll)
            addAll(typeRef.coneType.typeAnnotations)
            addAll(typeRef.coneType.fullyExpandedType(session).typeAnnotations)
        }
        val withJvmType = annotations.lastOrNull { it.isWithJvmType() } ?: return null
        val mapping = withJvmType.argumentMapping.mapping
        val valueExpression = mapping["type".ident()]
            ?: mapping.values.firstOrNull()
            ?: (withJvmType as? FirAnnotationCall)?.argumentList?.arguments?.firstOrNull()
        return (valueExpression as? FirLiteralExpression)?.value as? String
    }

    private fun FirAnnotation.isWithJvmType(): Boolean =
        annotationTypeRef.coneType.classId == Symbols.WithJvmType

    /** The JVM type name of [typeRef]/[coneType], or `null` when it is unknown and `@WithJvmType` is required. */
    private fun jvmType(typeRef: FirTypeRef): String? {
        val coneType = typeRef.coneType
        val withJvmType = withJvmTypeValue(typeRef)
        if (withJvmType != null) {
            return when {
                withJvmType in primitiveJvmTypes -> withJvmType
                coneType.isMarkedNullable -> withJvmType
                else -> "@NonNull $withJvmType"
            }
        }
        return when (coneType.classId) {
            StandardClassIds.Boolean, StandardClassIds.UByte -> "boolean"
            StandardClassIds.Byte -> "byte"
            StandardClassIds.Char, StandardClassIds.UShort -> "char"
            StandardClassIds.Short -> "short"
            StandardClassIds.Int -> "int"
            StandardClassIds.Long -> "long"
            StandardClassIds.Float -> "float"
            StandardClassIds.Double -> "double"
            StandardClassIds.Unit -> "void"
            else -> null
        }
    }

    /**
     * The native-side binary representation of a Kotlin primitive `ClassId`. `Unit` is deliberately absent - it is a
     * return-only type, so a parameter can never resolve to it here.
     */
    private fun coneTypeToActualParameterType(classId: ClassId?): ActualParameterType? = when (classId) {
        StandardClassIds.Boolean -> ActualParameterType.BooleanIn
        StandardClassIds.UByte -> ActualParameterType.UByte
        StandardClassIds.Byte -> ActualParameterType.Byte
        StandardClassIds.Char -> ActualParameterType.Char
        StandardClassIds.UShort -> ActualParameterType.UShort
        StandardClassIds.Short -> ActualParameterType.Short
        StandardClassIds.Int -> ActualParameterType.Int
        StandardClassIds.Long -> ActualParameterType.Long
        StandardClassIds.Float -> ActualParameterType.Float
        StandardClassIds.Double -> ActualParameterType.Double
        else -> null
    }
}

/** The session component exposing the once-per-function [JvmSignatureProvider]. */
internal val FirSession.jvmSignatureProvider: JvmSignatureProvider by sessionComponentAccessor()