package io.github.mimimishkin.jni.binding.producer

import org.jetbrains.kotlin.descriptors.DescriptorVisibility
import org.jetbrains.kotlin.diagnostics.*
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies.DECLARATION_NAME
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies.DECLARATION_RETURN_TYPE
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies.DECLARATION_SIGNATURE
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies.MODALITY_MODIFIER
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies.PARAMETERS_WITH_DEFAULT_VALUE
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies.PARAMETER_VARARG_MODIFIER
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies.TYPE_PARAMETERS_LIST
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies.VISIBILITY_MODIFIER
import org.jetbrains.kotlin.diagnostics.KtDiagnosticRenderers
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers
import org.jetbrains.kotlin.diagnostics.rendering.Renderer
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtElement


internal object JniBindingProducerErrors : KtDiagnosticsContainer() {
    override fun getRendererFactory() = object : BaseDiagnosticRendererFactory() {
        override val MAP by KtDiagnosticFactoryToRendererMap("JniBindingProducerErrors") { map ->
            map.put(OPEN_JNI_ACTUALS, "@JniActuals cannot be used on open classes.")
            map.put(LOCAl_JNI_ACTUALS, "@JniActuals can only be used on top-level declarations")
            map.put(NO_JAVA_VM_CONSTRUCTOR, "@JniActuals class must have a constructor with either no or single parameter of type JavaVM")
            map.put(NOT_STATIC_OR_INSTANCE, "Extension receiver of @JniActual can be either JObject or JClass, but was {0}", CommonRenderers.STRING)
            map.put(INVISIBLE_JNI_ACTUAL, "@JniActual function must be visible from generated code.")
            map.put(TYPE_PARAMETERS_IN_JNI_ACTUAL, "@JniActual functions cannot have type parameters")
            map.put(INVALID_CONTEXT_PARAMETER, "Context parameter of @JniActual must be of type JniEnv/JNIEnv, but was {0}", CommonRenderers.STRING)
            map.put(DEFAULT_PARAMETERS_IN_JNI_ACTUAL, "Default parameters are not allowed in @JniActual functions")
            map.put(VARARG_PARAMETERS_IN_JNI_ACTUAL, "Vararg parameters are not allowed in @JniActual functions")
            map.put(UNSUPPORTED_TYPE, "Unsupported parameter/return type: {0}", CommonRenderers.STRING)
            map.put(PRIMITIVE_NULLABLE_TYPE, "Primitive parameter/return type cannot be nullable: {0}", CommonRenderers.STRING)
            map.put(UNKNOWN_JVM_PARAMETER_TYPE, "Corresponding type on JVM side of parameter ''{0}'' is unknown. Specify it with @WithJvmType or @WithJvmSignature", CommonRenderers.NAME)
            map.put(UNKNOWN_JVM_RETURN_TYPE, "Corresponding type on JVM side of return type is unknown. Specify it with @WithJvmType or @WithJvmSignature")
            map.put(JVM_SIGNATURE_PARAMETER_COUNT_MISMATCH, "Number of parameters in @WithJvmSignature does not match the number of parameters in the function.")
            map.put(JVM_TYPE_MISMATCH_RETURN, "JVM type ''{0}'' does not match actual return type ''{1}''", CommonRenderers.STRING, CommonRenderers.STRING)
            map.put(JVM_TYPE_MISMATCH_PARAMETER, "JVM type ''{0}'' does not match actual type ''{1}''", CommonRenderers.STRING, CommonRenderers.STRING)
            map.put(HOOK_VISIBILITY, "@{0} function must be visible from generated code but is {1}", CommonRenderers.NAME, Renderer { it.externalDisplayName })
            map.put(HOOK_TYPE_PARAMETERS, "@{0} functions cannot have type parameters", CommonRenderers.NAME)
            map.put(HOOK_EXTENSION_FUNCTION, "@{0} cannot be an extension function", CommonRenderers.NAME)
            map.put(HOOK_RETURN_VALUE, "@{0} cannot return a value", CommonRenderers.NAME)
            map.put(HOOK_TOO_MANY_PARAMETERS, "@{0} cannot have more than one parameter", CommonRenderers.NAME)
            map.put(HOOK_INVALID_PARAMETER, "@{0} cannot have default or vararg parameters", CommonRenderers.NAME)
            map.put(HOOK_INVALID_VM_PARAMETER, "@{0} vm parameter must be of type JavaVM, but was {1}", CommonRenderers.NAME, KtDiagnosticRenderers.TO_STRING)
            map.put(WITH_JVM_SIGNATURE_ON_NON_JNI_ACTUAL, "@WithJvmSignature can only be used on @JniActual functions")
            map.put(JNI_ACTUAL_SUSPEND, "@JniActual functions cannot be suspend")
            map.put(HOOK_NOT_TOP_LEVEL, "@{0} must be a top-level function", CommonRenderers.NAME)
            map.put(JNI_ACTUAL_EMPTY_CLASS_NAME, "@JniActuals class name cannot be empty")
            map.put(JNI_ACTUALS_INVALID_CLASS_NAME, "Invalid JVM class name: ''{0}''", CommonRenderers.STRING)
            map.put(JNI_ACTUALS_WITHOUT_JVM_CLASS, "There is no JVM class named ''{0}''", CommonRenderers.STRING)
            map.put(JNI_ACTUALS_TYPE_PARAMETERS, "@JniActuals cannot have type parameters")
            map.put(JNI_ACTUALS_INNER_CLASS, "@JniActuals cannot be used on inner classes")
            map.put(MULTIPLE_ON_LOAD_HOOKS, "More than one @JniOnLoad and/or constructable @JniActuals detected. Either merge them into one or set 'allowSeveralHooks = true'")
            map.put(CRITICAL_CONTEXT_PARAMETERS, "@CriticalNative function cannot have context parameters: a critical native is invoked without a JniEnv.")
            map.put(CRITICAL_RECEIVER, "@CriticalNative function cannot have a receiver: a critical native is invoked without a class/object reference.")
            map.put(CRITICAL_ARRAY_WITHOUT_LENGTH, "@CriticalNative array parameter ''{0}'' must be preceded by its length: an `Int` parameter right before the CArrayPointer.", CommonRenderers.NAME)
            map.put(CRITICAL_UNSUPPORTED_PARAMETER_TYPE, "@CriticalNative function can only have primitive parameters or (length: Int, array: CArrayPointer<T>) pairs, but ''{0}'' is {1}.", CommonRenderers.NAME, CommonRenderers.STRING)
            map.put(CRITICAL_UNSUPPORTED_ARRAY_ELEMENT, "@CriticalNative array parameter ''{0}'' must be a CArrayPointer of a primitive Var (IntVar, LongVar, ...), but {1} was given.", CommonRenderers.NAME, CommonRenderers.STRING)
            map.put(CRITICAL_UNSUPPORTED_RETURN_TYPE, "@CriticalNative function can only return a primitive or Unit, but was {0}.", CommonRenderers.STRING)
            map.put(CRITICAL_UNRESOLVED_TYPE, "@CriticalNative function has {0} @WithJvmSignature parameter type(s) but {1} collapsed JVM parameter(s) (a length/array pair counts as one).", KtDiagnosticRenderers.TO_STRING, KtDiagnosticRenderers.TO_STRING)
            map.put(CRITICAL_UNSUPPORTED_BY_REGISTER_NATIVES, "@CriticalNative is not supported with exportMethod=RegisterNatives. Use the exposed-functions mode on this platform.")
            map.put(CRITICAL_UNSUPPORTED_BY_JDK_VERSION, "@CriticalNative is not supported for expectedJdkVersion {0}: critical natives were removed from HotSpot in JDK 22.", KtDiagnosticRenderers.TO_STRING)
            map.put(CRITICAL_REFERENCE_UNSUPPORTED_ON_ANDROID, "@CriticalNative array parameter ''{0}'' is not supported on Android: a critical native there takes no references, an array included. Use primitives only, or declare it as an ordinary native.", CommonRenderers.NAME)
        }
    }

    val OPEN_JNI_ACTUALS by error0<KtElement>(MODALITY_MODIFIER)
    val LOCAl_JNI_ACTUALS by error0<KtElement>()
    val NO_JAVA_VM_CONSTRUCTOR by error0<KtElement>()
    val NOT_STATIC_OR_INSTANCE by error1<KtElement, String>(DECLARATION_RETURN_TYPE)
    val INVISIBLE_JNI_ACTUAL by error0<KtElement>(VISIBILITY_MODIFIER)
    val TYPE_PARAMETERS_IN_JNI_ACTUAL by error0<KtElement>(TYPE_PARAMETERS_LIST)
    val INVALID_CONTEXT_PARAMETER by error1<KtElement, String>(DECLARATION_RETURN_TYPE)
    val DEFAULT_PARAMETERS_IN_JNI_ACTUAL by error0<KtElement>(PARAMETERS_WITH_DEFAULT_VALUE)
    val VARARG_PARAMETERS_IN_JNI_ACTUAL by error0<KtElement>(PARAMETER_VARARG_MODIFIER)
    val UNSUPPORTED_TYPE by error1<KtElement, String>(DECLARATION_RETURN_TYPE)
    val PRIMITIVE_NULLABLE_TYPE by error1<KtElement, String>(DECLARATION_RETURN_TYPE)
    val UNKNOWN_JVM_PARAMETER_TYPE by error1<KtElement, Name>(DECLARATION_RETURN_TYPE)
    val UNKNOWN_JVM_RETURN_TYPE by error0<KtElement>(DECLARATION_RETURN_TYPE)
    val JVM_SIGNATURE_PARAMETER_COUNT_MISMATCH by error0<KtElement>()
    val JVM_TYPE_MISMATCH_RETURN by error2<KtElement, String, String>(DECLARATION_RETURN_TYPE)
    val JVM_TYPE_MISMATCH_PARAMETER by error2<KtElement, String, String>(DECLARATION_NAME)
    val HOOK_VISIBILITY by error2<KtElement, Name, DescriptorVisibility>(VISIBILITY_MODIFIER)
    val HOOK_TYPE_PARAMETERS by error1<KtElement, Name>(TYPE_PARAMETERS_LIST)
    val HOOK_EXTENSION_FUNCTION by error1<KtElement, Name>()
    val HOOK_RETURN_VALUE by error1<KtElement, Name>(DECLARATION_RETURN_TYPE)
    val HOOK_TOO_MANY_PARAMETERS by error1<KtElement, Name>(DECLARATION_SIGNATURE)
    val HOOK_INVALID_PARAMETER by error1<KtElement, Name>(DECLARATION_RETURN_TYPE)
    val HOOK_INVALID_VM_PARAMETER by error2<KtElement, Name, String>(DECLARATION_RETURN_TYPE)
    val WITH_JVM_SIGNATURE_ON_NON_JNI_ACTUAL by error0<KtElement>(DECLARATION_NAME)
    val JNI_ACTUAL_SUSPEND by error0<KtElement>()
    val HOOK_NOT_TOP_LEVEL by error1<KtElement, Name>(DECLARATION_NAME)
    val JNI_ACTUAL_EMPTY_CLASS_NAME by error0<KtElement>(DECLARATION_NAME)
    val JNI_ACTUALS_INVALID_CLASS_NAME by error1<KtElement, String>(DECLARATION_NAME)
    val JNI_ACTUALS_WITHOUT_JVM_CLASS by error1<KtElement, String>(DECLARATION_NAME)
    val JNI_ACTUALS_TYPE_PARAMETERS by error0<KtElement>(TYPE_PARAMETERS_LIST)
    val JNI_ACTUALS_INNER_CLASS by error0<KtElement>(DECLARATION_NAME)
    val MULTIPLE_ON_LOAD_HOOKS by error0<KtElement>()
    val CRITICAL_CONTEXT_PARAMETERS by error0<KtElement>()
    val CRITICAL_RECEIVER by error0<KtElement>()
    val CRITICAL_ARRAY_WITHOUT_LENGTH by error1<KtElement, Name>(DECLARATION_NAME)
    val CRITICAL_LENGTH_WITHOUT_ARRAY by error1<KtElement, Name>(DECLARATION_NAME)
    val CRITICAL_UNSUPPORTED_PARAMETER_TYPE by error2<KtElement, Name, String>(DECLARATION_NAME)
    val CRITICAL_UNSUPPORTED_ARRAY_ELEMENT by error2<KtElement, Name, String>(DECLARATION_NAME)
    val CRITICAL_UNSUPPORTED_RETURN_TYPE by error1<KtElement, String>(DECLARATION_RETURN_TYPE)
    val CRITICAL_UNRESOLVED_TYPE by error2<KtElement, Int, Int>(DECLARATION_SIGNATURE)
    val CRITICAL_UNSUPPORTED_BY_REGISTER_NATIVES by error0<KtElement>()
    val CRITICAL_UNSUPPORTED_BY_JDK_VERSION by error1<KtElement, Int>(DECLARATION_NAME)
    val CRITICAL_REFERENCE_UNSUPPORTED_ON_ANDROID by error1<KtElement, Name>(DECLARATION_NAME)
}
