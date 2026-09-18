package io.github.mimimishkin.jni.binding.producer.fir

import io.github.mimimishkin.jni.binding.producer.JniBindingProducerErrors.DEFAULT_PARAMETERS_IN_JNI_ACTUAL
import io.github.mimimishkin.jni.binding.producer.JniBindingProducerErrors.HOOK_EXTENSION_FUNCTION
import io.github.mimimishkin.jni.binding.producer.JniBindingProducerErrors.HOOK_INVALID_PARAMETER
import io.github.mimimishkin.jni.binding.producer.JniBindingProducerErrors.HOOK_INVALID_VM_PARAMETER
import io.github.mimimishkin.jni.binding.producer.JniBindingProducerErrors.HOOK_NOT_TOP_LEVEL
import io.github.mimimishkin.jni.binding.producer.JniBindingProducerErrors.HOOK_RETURN_VALUE
import io.github.mimimishkin.jni.binding.producer.JniBindingProducerErrors.HOOK_TOO_MANY_PARAMETERS
import io.github.mimimishkin.jni.binding.producer.JniBindingProducerErrors.HOOK_TYPE_PARAMETERS
import io.github.mimimishkin.jni.binding.producer.JniBindingProducerErrors.HOOK_VISIBILITY
import io.github.mimimishkin.jni.binding.producer.JniBindingProducerErrors.INVISIBLE_JNI_ACTUAL
import io.github.mimimishkin.jni.binding.producer.JniBindingProducerErrors.JNI_ACTUAL_EMPTY_CLASS_NAME
import io.github.mimimishkin.jni.binding.producer.JniBindingProducerErrors.JNI_ACTUAL_SUSPEND
import io.github.mimimishkin.jni.binding.producer.JniBindingProducerErrors.JNI_ACTUALS_INNER_CLASS
import io.github.mimimishkin.jni.binding.producer.JniBindingProducerErrors.JNI_ACTUALS_INVALID_CLASS_NAME
import io.github.mimimishkin.jni.binding.producer.JniBindingProducerErrors.JNI_ACTUALS_TYPE_PARAMETERS
import io.github.mimimishkin.jni.binding.producer.JniBindingProducerErrors.JVM_SIGNATURE_PARAMETER_COUNT_MISMATCH
import io.github.mimimishkin.jni.binding.producer.JniBindingProducerErrors.LOCAl_JNI_ACTUALS
import io.github.mimimishkin.jni.binding.producer.JniBindingProducerErrors.NO_JAVA_VM_CONSTRUCTOR
import io.github.mimimishkin.jni.binding.producer.JniBindingProducerErrors.NOT_STATIC_OR_INSTANCE
import io.github.mimimishkin.jni.binding.producer.JniBindingProducerErrors.OPEN_JNI_ACTUALS
import io.github.mimimishkin.jni.binding.producer.JniBindingProducerErrors.PRIMITIVE_NULLABLE_TYPE
import io.github.mimimishkin.jni.binding.producer.JniBindingProducerErrors.TYPE_PARAMETERS_IN_JNI_ACTUAL
import io.github.mimimishkin.jni.binding.producer.JniBindingProducerErrors.UNKNOWN_JVM_PARAMETER_TYPE
import io.github.mimimishkin.jni.binding.producer.JniBindingProducerErrors.UNKNOWN_JVM_RETURN_TYPE
import io.github.mimimishkin.jni.binding.producer.JniBindingProducerErrors.UNSUPPORTED_TYPE
import io.github.mimimishkin.jni.binding.producer.JniBindingProducerErrors.VARARG_PARAMETERS_IN_JNI_ACTUAL
import io.github.mimimishkin.jni.binding.producer.JniBindingProducerErrors.MULTIPLE_ON_LOAD_HOOKS
import io.github.mimimishkin.jni.binding.producer.JniBindingProducerErrors.WITH_JVM_SIGNATURE_ON_NON_JNI_ACTUAL
import io.github.mimimishkin.jni.binding.producer.Symbols
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities.Internal
import org.jetbrains.kotlin.descriptors.Visibilities.Public
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirClassChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFunctionChecker
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.declarations.FirPropertyAccessor
import org.jetbrains.kotlin.fir.declarations.constructors
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.utils.isInner
import org.jetbrains.kotlin.fir.declarations.utils.isLocal
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.resolve.getContainingClass
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.isMarkedNullable
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.name.StandardClassIds

/**
 * Validates `@JniActual`/`@JniActuals`/`@JniOnLoad`/`@JniOnUnload` declarations during FIR analysis
 * and reports a diagnostic at the offending declaration (see
 * [io.github.mimimishkin.jni.binding.producer.JniBindingProducerErrors]).
 */
public class FirJniBindingUseChecker(session: FirSession, private val allowSeveralHooks: Boolean) : FirAdditionalCheckersExtension(session) {
    override val declarationCheckers: DeclarationCheckers = object : DeclarationCheckers() {
        override val functionCheckers: Set<FirFunctionChecker>
            get() = setOf(FunctionDeclarationChecker())
        override val classCheckers: Set<FirClassChecker>
            get() = setOf(ClassDeclarationChecker)
    }

    private val actualPredicate = LookupPredicate.create { annotated(Symbols.JniActual.asSingleFqName()) }
    private val containerPredicate = LookupPredicate.create { annotated(Symbols.JniActuals.asSingleFqName()) }
    private val hookPredicate = LookupPredicate.create {
        annotated(Symbols.JniOnLoad.asSingleFqName()) or annotated(Symbols.JniOnUnload.asSingleFqName())
    }
    private val onLoadPredicate = LookupPredicate.create { annotated(Symbols.JniOnLoad.asSingleFqName()) }

    private val jvmSignatureProvider: JvmSignatureProvider get() = session.jvmSignatureProvider

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(actualPredicate, containerPredicate, hookPredicate, onLoadPredicate)
    }

    /**
     * Validates function-shaped declarations against the actual rules, the hook rules, the
     * single-`@JniOnLoad` constraint and the `@WithJvmSignature` placement rule.
     */
    private inner class FunctionDeclarationChecker : FirFunctionChecker(MppCheckerKind.Common) {
        context(context: CheckerContext, reporter: DiagnosticReporter)
        override fun check(declaration: FirFunction) {
            if (declaration is FirPropertyAccessor) return

            val isImplicitActual = declaration !is FirConstructor &&
                    declaration.visibility in listOf(Public, Internal) &&
                    declaration.getContainingClass()?.hasAnnotation(Symbols.JniActuals, context.session) == true
            val isExplicitActual = declaration.hasAnnotation(Symbols.JniActual, context.session)
            val isActual = isImplicitActual || isExplicitActual
            val isOnLoad = declaration.hasAnnotation(Symbols.JniOnLoad, context.session)
            val isOnUnload = declaration.hasAnnotation(Symbols.JniOnUnload, context.session)
            val isHook = isOnLoad || isOnUnload

            if (isExplicitActual) {
                if (declaration.visibility != Public && declaration.visibility != Internal) {
                    reporter.reportOn(declaration.source, INVISIBLE_JNI_ACTUAL, context)
                }
            }
            // Set while checking an actual: false when the @WithJvmSignature parameter count does not match the
            // function signature, in which case per-parameter JVM types cannot be resolved meaningfully.
            var signatureParamCountMatches = true

            if (isActual) {
                val signatureInfo = jvmSignatureProvider.signatureInfo(declaration)
                signatureParamCountMatches = !signatureInfo.hasSignatureAnnotation ||
                    signatureInfo.signatureParameterCount == declaration.valueParameters.size

                if (!signatureParamCountMatches) {
                    reporter.reportOn(declaration.source, JVM_SIGNATURE_PARAMETER_COUNT_MISMATCH, context)
                }

                if (declaration.typeParameters.isNotEmpty()) {
                    reporter.reportOn(declaration.source, TYPE_PARAMETERS_IN_JNI_ACTUAL, context)
                }
                if (declaration.status.isSuspend) {
                    reporter.reportOn(declaration.source, JNI_ACTUAL_SUSPEND, context)
                }
                declaration.receiverParameter?.let { receiver ->
                    val receiverType = receiver.typeRef.coneType
                    val pointed = receiverType.typeArguments.singleOrNull()?.type?.classId
                        .takeIf { receiverType.classId == Symbols.CPointer }
                    if (
                        pointed != Symbols.JObjectRaw &&
                        pointed != Symbols.JClassRaw &&
                        pointed != Symbols.JObjectWrapped &&
                        pointed != Symbols.JClassWrapped
                    ) {
                        reporter.reportOn(declaration.receiverParameter?.source ?: declaration.source, NOT_STATIC_OR_INSTANCE, receiverType.toString(), context)
                    }
                }
                for ((index, param) in declaration.valueParameters.withIndex()) {
                    if (param.defaultValue != null) {
                        reporter.reportOn(param.source, DEFAULT_PARAMETERS_IN_JNI_ACTUAL, context)
                    }
                    if (param.isVararg) {
                        reporter.reportOn(param.source, VARARG_PARAMETERS_IN_JNI_ACTUAL, context)
                    }
                    val paramType = param.returnTypeRef.coneType
                    if (paramType.isMarkedNullable && paramType.classId in primitiveClassIds) {
                        reporter.reportOn(param.source, PRIMITIVE_NULLABLE_TYPE, paramType.classId?.asString().orEmpty(), context)
                    }
                    if (!isSupportedNativeType(paramType, isReturnType = false)) {
                        reporter.reportOn(param.source, UNSUPPORTED_TYPE, paramType.toString(), context)
                    } else if (signatureParamCountMatches) {
                        val actualFn = declaration as? FirNamedFunction
                        if (actualFn != null && jvmSignatureProvider.signatureInfo(actualFn).parameterTypes.getOrNull(index) == null) {
                            reporter.reportOn(param.source, UNKNOWN_JVM_PARAMETER_TYPE, param.name, context)
                        }
                    }
                }
            }

            val returnConeType = declaration.returnTypeRef.coneType
            if (isActual && !isSupportedNativeType(returnConeType, isReturnType = true)) {
                reporter.reportOn(declaration.source, UNSUPPORTED_TYPE, returnConeType.toString(), context)
            }
            if (isActual && isSupportedNativeType(returnConeType, isReturnType = true) && signatureParamCountMatches) {
                if (jvmSignatureProvider.signatureInfo(declaration).returnType == null) {
                    reporter.reportOn(declaration.source, UNKNOWN_JVM_RETURN_TYPE, context)
                }
            }

            if (isHook) {
                val hookName = (if (isOnLoad) Symbols.JniOnLoad else Symbols.JniOnUnload).shortClassName
                val containingClass = declaration.getContainingClass()
                if (containingClass != null && !containingClass.hasAnnotation(
                        Symbols.JniActuals,
                        context.session,
                    )
                ) {
                    reporter.reportOn(declaration.source, HOOK_NOT_TOP_LEVEL, hookName, context)
                }
                if (declaration.visibility != Public && declaration.visibility != Internal) {
                    reporter.reportOn(
                        declaration.source,
                        HOOK_VISIBILITY,
                        hookName,
                        DescriptorVisibilities.toDescriptorVisibility(declaration.visibility),
                        context,
                    )
                }
                if (declaration.typeParameters.isNotEmpty()) {
                    reporter.reportOn(declaration.source, HOOK_TYPE_PARAMETERS, hookName, context)
                }
                val receiver = declaration.receiverParameter
                if (receiver != null) {
                    reporter.reportOn(receiver.source, HOOK_EXTENSION_FUNCTION, hookName, context)
                }
                val returnTypeClassId = declaration.returnTypeRef.coneType.classId
                if (returnTypeClassId != StandardClassIds.Unit) {
                    reporter.reportOn(declaration.source, HOOK_RETURN_VALUE, hookName, context)
                }
                if (declaration.valueParameters.size > 1) {
                    reporter.reportOn(declaration.source, HOOK_TOO_MANY_PARAMETERS, hookName, context)
                }
                for (param in declaration.valueParameters) {
                    if (param.defaultValue != null || param.isVararg) {
                        reporter.reportOn(param.source, HOOK_INVALID_PARAMETER, hookName, context)
                    }
                }
                declaration.valueParameters.singleOrNull()?.let { param ->
                    if (!param.returnTypeRef.coneType.isJavaVmType()) {
                        reporter.reportOn(param.source, HOOK_INVALID_VM_PARAMETER, hookName, param.returnTypeRef.coneType.toString(), context)
                    }
                }
            }

            if (isOnLoad && !allowSeveralHooks) {
                val provider = context.session.predicateBasedProvider
                val allOnLoad = provider.getSymbolsByPredicate(onLoadPredicate)
                val allContainers = provider.getSymbolsByPredicate(containerPredicate)
                val constructableContainers = allContainers.filter {
                    it is FirRegularClassSymbol && it.classKind == ClassKind.CLASS
                }
                if (allOnLoad.size + constructableContainers.size > 1) {
                    reporter.reportOn(declaration.source, MULTIPLE_ON_LOAD_HOOKS, context)
                }
            }

            if (declaration.hasAnnotation(Symbols.WithJvmSignature, context.session) && !isActual) {
                reporter.reportOn(declaration.source, WITH_JVM_SIGNATURE_ON_NON_JNI_ACTUAL, context)
            }
        }
    }

    /**
     * Validates `@JniActuals` containers: shape (non-open, non-generic, top-level, non-inner), the
     * `className` argument format, and - for class containers - the presence of a no-arg or `JavaVM`
     * constructor the generated initializer can call.
     */
    private object ClassDeclarationChecker : FirClassChecker(MppCheckerKind.Common) {
        context(context: CheckerContext, reporter: DiagnosticReporter)
        override fun check(declaration: FirClass) {
            val isActuals = declaration.hasAnnotation(Symbols.JniActuals, context.session)
            if (!isActuals) return

            val regClass = declaration as? FirRegularClass ?: return
            val classSymbol = regClass.symbol

            if (regClass.classKind != ClassKind.OBJECT && regClass.status.modality != Modality.FINAL) {
                reporter.reportOn(regClass.source, OPEN_JNI_ACTUALS, context)
                return
            }
            if (regClass.typeParameters.isNotEmpty()) {
                reporter.reportOn(regClass.source, JNI_ACTUALS_TYPE_PARAMETERS, context)
                return
            }
            if (classSymbol.isLocal || classSymbol.isInner) {
                reporter.reportOn(
                    regClass.source,
                    if (regClass.isInner) JNI_ACTUALS_INNER_CLASS else LOCAl_JNI_ACTUALS,
                    context,
                )
                return
            }
            if (regClass.symbol.classId.outerClassId != null) {
                reporter.reportOn(regClass.source, LOCAl_JNI_ACTUALS, context)
                return
            }

            val containerAnnotation = declaration.annotations.filterIsInstance<FirAnnotationCall>().firstOrNull {
                it.annotationTypeRef.coneType.classId == Symbols.JniActuals
            } ?: return
            val className = (containerAnnotation.argumentList.arguments.getOrNull(0) as? FirLiteralExpression)
                ?.value as? String
            if (className != null) {
                if (className.isBlank()) {
                    reporter.reportOn(regClass.source, JNI_ACTUAL_EMPTY_CLASS_NAME, context)
                    return
                }
                if (!isValidJvmClassName(className)) {
                    reporter.reportOn(regClass.source, JNI_ACTUALS_INVALID_CLASS_NAME, className, context)
                    return
                }
            }

            if (regClass.classKind != ClassKind.OBJECT) {
                val constructors = classSymbol.constructors(context.session)
                val hasNoArgConstructor = constructors.any { constructor ->
                    constructor.valueParameterSymbols.isEmpty()
                }
                val hasJavaVmConstructor = constructors.any { constructor ->
                    val params = constructor.valueParameterSymbols
                    var type = params.singleOrNull()?.resolvedReturnType ?: return@any false
                    if (type.classId != Symbols.CPointerVarOf) return@any false
                    type = type.typeArguments.singleOrNull()?.type ?: return@any false
                    if (type.classId != Symbols.CPointer) return@any false
                    type = type.typeArguments.singleOrNull()?.type ?: return@any false
                    type.classId != Symbols.JNINativeInterface
                }
                if (!hasNoArgConstructor && !hasJavaVmConstructor) {
                    reporter.reportOn(regClass.source, NO_JAVA_VM_CONSTRUCTOR, context)
                }
            }
        }
    }
}

/** The Kotlin primitives with a JNI binary representation. */
private val primitiveClassIds = setOf(
    StandardClassIds.Boolean, StandardClassIds.UByte,
    StandardClassIds.Byte,
    StandardClassIds.Char, StandardClassIds.UShort,
    StandardClassIds.Short,
    StandardClassIds.Int,
    StandardClassIds.Long,
    StandardClassIds.Float,
    StandardClassIds.Double,
)

/** The JNI-representable native types of a return position: the primitives plus `Unit`. */
private val supportedNativeClassIds = primitiveClassIds + StandardClassIds.Unit

/**
 * Whether a `@JniActual` parameter/return type can be passed through JNI. Only primitives (mapped to JVM primitives),
 * `Unit` (mapped to `void`) and `CPointer<T>` types (reference types such as `JObject`/`JClass`) are representable;
 * any other type (e.g. `String`, arbitrary classes) has no binary representation and is rejected.
 */
private fun isSupportedNativeType(type: ConeKotlinType, isReturnType: Boolean): Boolean {
    return type.classId == Symbols.CPointer ||
            type.classId in if (isReturnType) supportedNativeClassIds else primitiveClassIds
}

/**
 * Whether [name] is a syntactically acceptable JVM fully-qualified class name: dot-separated
 * identifiers where each identifier is a valid JVM identifier (starts with a Java letter, `$`,
 * or `_`, followed by Java letters, digits, `$`, or `_`).
 *
 * @see [JLS §3.8](https://docs.oracle.com/javase/specs/jls/se17/html/jls-3.html#jls-3.8)
 * @see [JVM spec §4.2.1](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.2.1)
 */
private fun isValidJvmClassName(name: String): Boolean =
    name.isNotEmpty() &&
        !name.startsWith('.') && !name.endsWith('.') && ".." !in name &&
        name.split('.').all { segment ->
            segment[0].isJavaIdentifierStart() &&
                segment.all(Char::isJavaIdentifierPart)
        }