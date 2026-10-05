package io.github.mimimishkin.jni.binding.producer.fir

import io.github.mimimishkin.jni.binding.producer.Symbols
import io.github.mimimishkin.jni.binding.producer.ident
import io.github.mimimishkin.jni.binding.producer.model.ActualParameterType
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.StandardClassIds

/**
 * The primitive element types a `@CriticalNative` array pair may carry.
 *
 * [varClassId] is the class behind the `kotlinx.cinterop.*Var` a critical actual writes in its `CArrayPointer`,
 * [jvmTypeName] the corresponding JVM element type and [arrayClassId] the `jni-binding` array wrapper through which
 * the fallback facade receives a `jarray`. `boolean` is `UByte`/`_jbooleanArray`, matching the `jboolean` binary
 * representation.
 *
 * Note the `*VarOf` suffix: `IntVar` and friends are typealiases for `IntVarOf<Int>`, so it is the *aliased* class
 * that a parameter's type argument actually resolves to.
 */
internal enum class CriticalElement(
    val varClassId: ClassId,
    val jvmTypeName: String,
    val arrayClassId: ClassId,
) {
    Boolean(cinteropVarOf("UByte"), "boolean", Symbols.JBooleanArrayClass),
    Byte(cinteropVarOf("Byte"), "byte", Symbols.JByteArrayClass),
    Char(cinteropVarOf("UShort"), "char", Symbols.JCharArrayClass),
    Short(cinteropVarOf("Short"), "short", Symbols.JShortArrayClass),
    Int(cinteropVarOf("Int"), "int", Symbols.JIntArrayClass),
    Long(cinteropVarOf("Long"), "long", Symbols.JLongArrayClass),
    Float(cinteropVarOf("Float"), "float", Symbols.JFloatArrayClass),
    Double(cinteropVarOf("Double"), "double", Symbols.JDoubleArrayClass),
}

/** The `kotlinx.cinterop.<name>VarOf` class a `kotlinx.cinterop.<name>Var` typealias resolves to. */
private fun cinteropVarOf(name: String): ClassId = ClassId(Symbols.cinteropPackage, "${name}VarOf".ident())

/**
 * One JVM-facing parameter slot of a `@CriticalNative` actual.
 *
 * A critical actual has more *native* parameters than the JVM method it implements, because every array arrives as a
 * `(length: Int, array: CArrayPointer<T>)` pair. Each slot collapses to exactly one JVM parameter, and it is the slots -
 * not the native parameters - that the `actuals.json` contract and the C symbol mangling have to be derived from.
 */
internal sealed interface CriticalSlot {

    /** A standalone primitive parameter: one native parameter, one JVM parameter. */
    data class Scalar(val parameterIndex: Int, val type: ActualParameterType) : CriticalSlot

    /**
     * A `(length: Int, array: CArrayPointer<T>)` pair occupying [lengthIndex] and [arrayIndex] of the actual and
     * collapsing into the single JVM array parameter `<element>[]`.
     */
    data class Array(val lengthIndex: Int, val arrayIndex: Int, val element: CriticalElement) : CriticalSlot {
        val jvmType: ActualParameterType get() = ActualParameterType.Ref("${element.jvmTypeName}[]")
        val jsonType: String get() = "${element.jvmTypeName}[]"
    }

    /** The JVM binary type of this slot when the actual does not override it with `@WithJvmSignature`. */
    val defaultJvmType: ActualParameterType
        get() = when (this) {
            is Scalar -> type
            is Array -> jvmType
        }

    /** The `actuals.json` type name of this slot when the actual does not override it with `@WithJvmSignature`. */
    val defaultJsonType: String
        get() = when (this) {
            is Scalar -> type.withoutNullabilityPrefix
            is Array -> jsonType
        }
}

/** The single reason a `@CriticalNative` actual is not usable. */
internal enum class CriticalProblem {
    /** A `context` parameter of any kind: a critical native is invoked without a `JniEnv`. */
    ContextParameters,

    /** An extension receiver: a critical native is invoked without a class/object reference either. */
    Receiver,

    /** A `CArrayPointer` parameter not preceded by the `Int` length it must be paired with. */
    ArrayWithoutLength,

    /** A parameter that is neither a primitive nor a `CArrayPointer` of a primitive `Var`. */
    UnsupportedParameterType,

    /** A `CArrayPointer` of something that is not a primitive `Var` (e.g. `CArrayPointer<Byte>`). */
    UnsupportedArrayElement,

    /** A return type that is neither a primitive nor `Unit`. */
    UnsupportedReturnType,

    /** A parameter/return type whose JVM binary type cannot be resolved. */
    UnresolvedType,
}

/**
 * A [CriticalProblem] together with the parameter it was found at, or `null` when it concerns the declaration itself.
 */
internal data class CriticalIssue(val problem: CriticalProblem, val parameterIndex: Int? = null)

/**
 * The parameter layout of a `@CriticalNative` actual: how its native parameters collapse into JVM parameters.
 *
 * [slots] is filled best-effort - the checker reports every entry of [issues], and [isValid] is `false` whenever there
 * is at least one, in which case the type lists are not meaningful and the actual is excluded from code generation.
 */
internal class CriticalLayout(
    val slots: List<CriticalSlot>,
    val issues: List<CriticalIssue>,
    private val declaredParameterTypes: List<String?>,
    private val declaredReturnType: String?,
    private val hasSignatureAnnotation: Boolean,
    private val derivedReturnType: ActualParameterType?,
) {
    /** Whether the actual spelled its JVM signature out with `@WithJvmSignature`. */
    val isSignatureAnnotated: Boolean get() = hasSignatureAnnotation
    /**
     * Whether this layout can drive code generation: no structural problem, and - when the JVM signature is spelled out
     * explicitly - exactly one type per collapsed JVM parameter.
     */
    val isValid: Boolean
        get() = issues.isEmpty() &&
                (!hasSignatureAnnotation || declaredParameterTypes.size == slots.size)

    /** The JVM binary type per collapsed JVM parameter, `null` when unresolved. */
    val jvmParameterTypes: List<ActualParameterType?> by lazy {
        slots.mapIndexed { index, slot ->
            declaredParameterTypes.getOrNull(index)
                ?.let { ActualParameterType.fromJavaType(it, isReturn = false) }
                ?: slot.defaultJvmType
        }
    }

    /** The `actuals.json` type name per collapsed JVM parameter, `null` when unresolved. */
    val jsonParameterTypes: List<String?> by lazy {
        slots.mapIndexed { index, slot ->
            declaredParameterTypes.getOrNull(index) ?: slot.defaultJsonType
        }
    }

    /** The JVM binary type of the return value, or `null` when unresolved. */
    val returnType: ActualParameterType? by lazy {
        declaredReturnType?.let { ActualParameterType.fromJavaType(it, isReturn = true) }
            ?: derivedReturnType
    }

    /** The `actuals.json` type name of the return value, or `null` when unresolved. */
    val jsonReturnType: String? get() = declaredReturnType ?: returnType?.jvmType

    /** The slot owning the native parameter at [index], or `null` when no slot claims it. */
    fun slotAt(index: Int): CriticalSlot? =
        slots.firstOrNull {
            when (it) {
                is CriticalSlot.Scalar -> it.parameterIndex == index
                is CriticalSlot.Array -> it.lengthIndex == index || it.arrayIndex == index
            }
        }

    /** The array slot whose pointer half is the native parameter at [index], or `null` for a scalar. */
    fun arrayAt(index: Int): CriticalSlot.Array? = slotAt(index) as? CriticalSlot.Array

    /** The JNI type of the standalone parameter at [index], or `null` when it is an array half. */
    fun scalarTypeAt(index: Int): ActualParameterType? = (slotAt(index) as? CriticalSlot.Scalar)?.type

    /** Whether the return value crosses the boundary as the JNI `jboolean`, needing a conversion on the way out. */
    val isBooleanReturn: Boolean get() = returnType == ActualParameterType.BooleanOut

    /** The Kotlin type the critical facade exposes its return value as. */
    fun nativeReturnClassId(): ClassId =
        (returnType ?: ActualParameterType.Unit).nativeClassId

    /** The JVM binary types a critical facade's name is mangled with, empty when the name carries no signature. */
    fun signatureParameters(includeSignature: Boolean): List<ActualParameterType> =
        if (includeSignature) jvmParameterTypes.filterNotNull() else emptyList()
}

/** The native parameter index a slot's *length* half, or the whole slot, occupies. */
internal val CriticalSlot.nativeLengthIndex: Int
    get() = when (this) {
        is CriticalSlot.Scalar -> parameterIndex
        is CriticalSlot.Array -> lengthIndex
    }

/** The native parameter index a slot's *pointer* half occupies; a scalar occupies its own index. */
internal val CriticalSlot.nativePointerIndex: Int
    get() = when (this) {
        is CriticalSlot.Scalar -> parameterIndex
        is CriticalSlot.Array -> arrayIndex
    }

/**
 * Whether [this] function is annotated `@CriticalNative`.
 *
 * On Android the annotation is a typealias for the platform's own, so both spellings are recognized.
 */
internal fun FirNamedFunction.isCriticalNative(session: FirSession): Boolean =
    hasAnnotation(Symbols.CriticalNative, session) || hasAnnotation(Symbols.DalvikCriticalNative, session)

/**
 * Whether [this] is a `CArrayPointer<T>` of some `T`.
 *
 * `CArrayPointer<T>` is a typealias for `CPointer<T>`, so a resolved parameter's `classId` is [Symbols.CPointer] and
 * not the alias' own name; both are accepted so the check reads the same as the declaration it inspects.
 */
internal fun ConeKotlinType.isCArrayPointer(): Boolean =
    classId == Symbols.CArrayPointer || classId == Symbols.CPointer

/**
 * The [CriticalElement] of a `CArrayPointer<T>` of a primitive `Var`, or `null` when this is not such a pointer
 * (or its element type is not a primitive variable type).
 */
internal fun ConeKotlinType.criticalArrayElementOrNull(): CriticalElement? {
    if (!isCArrayPointer()) return null
    val argument = typeArguments.singleOrNull()?.type ?: return null
    return CriticalElement.entries.firstOrNull { argument.classId == it.varClassId }
}

/** The Kotlin primitives a critical actual may declare as a standalone parameter, with their JNI representation. */
private val CRITICAL_SCALAR_TYPES: Map<ClassId?, ActualParameterType> = mapOf(
    StandardClassIds.Boolean to ActualParameterType.BooleanIn,
    StandardClassIds.UByte to ActualParameterType.UByte,
    StandardClassIds.Byte to ActualParameterType.Byte,
    StandardClassIds.Char to ActualParameterType.Char,
    StandardClassIds.UShort to ActualParameterType.UShort,
    StandardClassIds.Short to ActualParameterType.Short,
    StandardClassIds.Int to ActualParameterType.Int,
    StandardClassIds.Long to ActualParameterType.Long,
    StandardClassIds.Float to ActualParameterType.Float,
    StandardClassIds.Double to ActualParameterType.Double,
)

/** The Kotlin types a critical actual may return: the primitives plus `Unit`. */
internal val CRITICAL_RETURN_CLASS_IDS: Set<ClassId?> = CRITICAL_SCALAR_TYPES.keys + StandardClassIds.Unit

/** The [ActualParameterType] of a standalone critical parameter, or `null` when the type is not a JNI primitive. */
internal fun criticalScalarTypeOrNull(type: ConeKotlinType): ActualParameterType? =
    CRITICAL_SCALAR_TYPES[type.classId]

/**
 * The [ActualParameterType] a critical actual's native return type maps to.
 *
 * `Boolean` maps to [ActualParameterType.BooleanOut] rather than the `BooleanIn` a parameter would use, because on the
 * way back out a `kotlin.Boolean` is the JNI `jboolean`; `Unit` maps to `void`.
 */
internal fun criticalReturnTypeOrNull(type: ConeKotlinType): ActualParameterType? = when (type.classId) {
    StandardClassIds.Boolean -> ActualParameterType.BooleanOut
    StandardClassIds.Unit -> ActualParameterType.Unit
    else -> criticalScalarTypeOrNull(type)
}