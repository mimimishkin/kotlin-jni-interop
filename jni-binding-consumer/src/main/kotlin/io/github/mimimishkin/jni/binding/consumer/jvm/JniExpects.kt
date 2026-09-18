package io.github.mimimishkin.jni.binding.consumer.jvm

import io.github.mimimishkin.jni.binding.consumer.JniBindingConsumerErrors.NULLABILITY_MISMATCH
import io.github.mimimishkin.jni.binding.consumer.model.JavaType
import io.github.mimimishkin.jni.binding.consumer.model.JniActualInfo
import io.github.mimimishkin.jni.binding.consumer.model.JniExpectInfo
import org.jetbrains.kotlin.ir.IrDiagnosticReporter

internal object JniExpects {
    /**
     * Whether a value declared as [this] nullability can be freely handed to something declared as [actual]
     * nullability without ever risking an unexpected `null`. Returns `false` exactly when [this] is nullable but
     * [actual] is not: the producer side may emit `null` where the accepting side promises it never sees one.
     */
    internal fun JavaType.matchesNullable(actual: JavaType): Boolean = !nullable || actual.nullable

    internal fun matches(
        expectInfo: JniExpectInfo,
        actualInfo: JniActualInfo,
        diagnosticReporter: IrDiagnosticReporter
    ): Boolean {
        if (actualInfo.className != expectInfo.className) return false
        if (actualInfo.methodName != expectInfo.methodName) return false
        if (actualInfo.isStasisValuable() && actualInfo.isStatic != expectInfo.isStatic) return false

        val actualReturns = actualInfo.returnType
        val expectReturns = expectInfo.returnType
        if (actualReturns.type != expectReturns.type) return false

        if (actualInfo.parameterTypes.size != expectInfo.parameterTypes.size) return false
        actualInfo.parameterTypes.zip(expectInfo.parameterTypes) { actual, expect ->
            if (actual.type != expect.type) return false
        }

        // Parameters flow JVM -> native: the expect (the JVM caller's contract) may hand `null` to a native whose
        // declared parameter is non-null, so the mismatch to flag is "expect nullable, actual not".
        actualInfo.parameterTypes.zip(expectInfo.parametersWithTypes) { actual, (irParam, expect) ->
            if (!expect.matchesNullable(actual)) {
                diagnosticReporter.at(irParam).report(NULLABILITY_MISMATCH, expect, actual)
            }
        }
        // The return value flows native -> JVM: the native may hand `null` where the expect (the JVM caller's
        // contract) promises it never sees one, so the mismatch to flag is "actual nullable, expect not".
        if (!actualReturns.matchesNullable(expectReturns)) {
            diagnosticReporter.at(expectInfo.source).report(NULLABILITY_MISMATCH, expectReturns, actualReturns)
        }

        return true
    }
}