package io.github.mimimishkin.jni.binding.consumer

import io.github.mimimishkin.jni.binding.consumer.model.JavaType
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
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
            Renderer<List<JavaType>> { params -> params.joinToString(", ") { "''$it''" } }

        private val nullabilityRenderer =
            Renderer<JavaType> { type -> if (type.nullable) "nullable" else "not null" }

        /** Renders the Java type the way the native side declared it, quoting it to delimit it from the prose. */
        private val typeRenderer =
            Renderer<JavaType> { type -> "'$type'" }

        private val intRenderer =
            Renderer<Int> { value -> value.toString() }

        override val MAP by KtDiagnosticFactoryToRendererMap("KT") { map ->
            map.put(
                MISSING_JNI_ACTUAL,
                "@JniExpect doesn''t have a corresponding @JniActual implementation.\n" +
                        "Need to declare @JniActual {0} with parameters: {1}.",
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
                intRenderer,
                intRenderer
            )
            map.put(
                PARAMETER_TYPE_MISMATCH,
                "Parameter type mismatch between @JniExpect and @JniActual at index {0}: " +
                        "expected {1}, actual {2}.",
                intRenderer,
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
}