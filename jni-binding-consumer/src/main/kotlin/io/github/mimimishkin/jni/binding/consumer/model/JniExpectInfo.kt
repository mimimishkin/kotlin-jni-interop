package io.github.mimimishkin.jni.binding.consumer.model

import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter

/**
 * The backend-resolved description of an `@JniExpect` function, built by `JniExpectMatcher` while bytecode is
 * generated.
 */
internal class JniExpectInfo(
    val targets: List<String>,
    val className: String,
    val methodName: String,
    val isStatic: Boolean,
    val parametersWithTypes: List<Pair<IrValueParameter, JavaType>>,
    val returnType: JavaType,
    val source: IrSimpleFunction,
) {
    val parameterTypes: List<JavaType> get() = parametersWithTypes.map { it.second }
}