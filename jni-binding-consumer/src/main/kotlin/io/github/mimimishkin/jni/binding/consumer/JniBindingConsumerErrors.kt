package io.github.mimimishkin.jni.binding.consumer

import io.github.mimimishkin.jni.binding.consumer.model.JavaType
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticRenderers
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies.DECLARATION_NAME
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies.DECLARATION_RETURN_TYPE
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies.DECLARATION_SIGNATURE
import org.jetbrains.kotlin.diagnostics.error0
import org.jetbrains.kotlin.diagnostics.error1
import org.jetbrains.kotlin.diagnostics.error2
import org.jetbrains.kotlin.diagnostics.error3
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers
import org.jetbrains.kotlin.diagnostics.rendering.Renderer
import org.jetbrains.kotlin.diagnostics.warning2
import org.jetbrains.kotlin.diagnostics.warningWithoutSource
import org.jetbrains.kotlin.psi.KtElement

internal object JniBindingConsumerErrors : KtDiagnosticsContainer() {
    override fun getRendererFactory(): BaseDiagnosticRendererFactory = renderer
    private val renderer = object : BaseDiagnosticRendererFactory() {
        private val parametersRenderer =
            Renderer<List<JavaType>> { params -> if (params.isEmpty()) "no params" else params.joinToString(", ") { "''$it''" } }

        private val nullabilityRenderer =
            Renderer<JavaType> { type -> if (type.nullable) "nullable" else "not null" }

        /** Renders the Java type the way the native side declared it, quoting it to delimit it from the prose. */
        private val typeRenderer =
            Renderer<JavaType> { type -> "'$type'" }

        override val MAP by KtDiagnosticFactoryToRendererMap("KT") { map ->
            map.put(
                MISSING_JNI_ACTUAL,
                "@JniExpect doesn''t have a corresponding @JniActual implementation.\n" +
                        "Need to declare @JniActual {0} with parameters: {1}.\n" +
                        "The `generateJniActuals` task of this Gradle module can write that declaration into the " +
                        "producer for you.",
                Renderer { (target, name): Pair<String, String> -> "for name '$name' in target '$target'" },
                parametersRenderer
            )
            map.put(
                STASIS_MISMATCH,
                "Static status mismatch between @JniExpect and @JniActual: expected {0}, actual {1}.",
                CommonRenderers.STRING,
                CommonRenderers.STRING,
            )
            map.put(
                RETURN_TYPE_MISMATCH,
                "Return type mismatch between @JniExpect and @JniActual: expected {0}, actual {1}.",
                typeRenderer,
                typeRenderer
            )
            map.put(
                PARAMETER_COUNT_MISMATCH,
                "Parameter count mismatch between @JniExpect and @JniActual: expected {0} parameter(s), " +
                        "actual {1} parameter(s).",
                KtDiagnosticRenderers.TO_STRING,
                KtDiagnosticRenderers.TO_STRING
            )
            map.put(
                PARAMETER_TYPE_MISMATCH,
                "Parameter type mismatch between @JniExpect and @JniActual at index {0}: " +
                        "expected {1}, actual {2}.",
                KtDiagnosticRenderers.TO_STRING,
                typeRenderer,
                typeRenderer
            )
            map.put(
                NULLABILITY_MISMATCH,
                "Nullability mismatch between @JniExpect and @JniActual: expected {0}, actual {1}.",
                nullabilityRenderer,
                nullabilityRenderer
            )
            map.put(
                NON_EXTERNAL_JNI_EXPECT,
                "@JniExpect can only be applied to external (native) functions."
            )
            map.put(
                EXTRA_JNI_ACTUALS, "{0}"
            )
            map.put(
                ILLEGAL_LOAD_METHOD_PARAMETERS,
                "@LoadMethod function must declare only `os`, `arch`, `vendor` parameters (no extension/context " +
                        "receivers), but has: {0}.",
                Renderer { it }
            )
            map.put(
                CRITICAL_NATIVE_ANNOTATION_MISSING,
                "@JniExpect must be annotated @CriticalNative because its @JniActual is a critical native: the two " +
                        "sides must agree on the calling convention."
            )
            map.put(
                CRITICAL_NATIVE_MUST_BE_STATIC,
                "@JniExpect must be static because its @JniActual is a critical native: a critical native is " +
                        "invoked without a class/object reference."
            )
            map.put(
                CRITICAL_NATIVE_MUST_NOT_BE_SYNCHRONIZED,
                "@JniExpect must not be synchronized because its @JniActual is a critical native: it must not be " +
                        "able to block."
            )
            map.put(
                CRITICAL_NATIVE_REFERENCE_ON_ANDROID,
                "@JniExpect must not take or return a reference on Android because its @JniActual is a critical " +
                        "native, and the two sides must agree on the calling convention.\n" +
                        "Found {0} in {1}. Use primitives only, or declare it as an ordinary native.",
                CommonRenderers.STRING,
                CommonRenderers.STRING,
            )
            map.put(
                CRITICAL_NATIVE_UNEXPECTED,
                "@JniExpect must not be annotated @CriticalNative because its @JniActual is not a critical " +
                        "native: the two sides disagree about the calling convention."
            )
        }
    }

    val MISSING_JNI_ACTUAL by error2<KtElement, Pair<String, String>, List<JavaType>>(DECLARATION_SIGNATURE)
    val STASIS_MISMATCH by error2<KtElement, String, String>(DECLARATION_NAME)
    val RETURN_TYPE_MISMATCH by error2<KtElement, JavaType, JavaType>(DECLARATION_RETURN_TYPE)
    val PARAMETER_COUNT_MISMATCH by error2<KtElement, Int, Int>(DECLARATION_SIGNATURE)
    val PARAMETER_TYPE_MISMATCH by error3<KtElement, Int, JavaType, JavaType>(DECLARATION_NAME)
    val NULLABILITY_MISMATCH by warning2<KtElement, JavaType, JavaType>(DECLARATION_RETURN_TYPE)
    val NON_EXTERNAL_JNI_EXPECT by error0<KtElement>(DECLARATION_NAME)
    val EXTRA_JNI_ACTUALS by warningWithoutSource()
    val ILLEGAL_LOAD_METHOD_PARAMETERS by error1<KtElement, String>(DECLARATION_SIGNATURE)
    val CRITICAL_NATIVE_ANNOTATION_MISSING by error0<KtElement>(DECLARATION_NAME)
    val CRITICAL_NATIVE_MUST_BE_STATIC by error0<KtElement>(DECLARATION_NAME)
    val CRITICAL_NATIVE_MUST_NOT_BE_SYNCHRONIZED by error0<KtElement>(DECLARATION_NAME)
    val CRITICAL_NATIVE_REFERENCE_ON_ANDROID by error2<KtElement, String, String>(DECLARATION_SIGNATURE)
    val CRITICAL_NATIVE_UNEXPECTED by error0<KtElement>(DECLARATION_NAME)
}