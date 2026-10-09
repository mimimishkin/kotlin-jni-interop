package io.github.mimimishkin.jni.binding.consumer.model

import org.jetbrains.kotlin.ir.declarations.IrParameterKind
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
    val superClasses: Map<String, String?> = emptyMap(),
) {
    val parameterTypes: List<JavaType> get() = parametersWithTypes.map { it.second }

    /**
     * The Kotlin name of each parameter, in JVM order. A regular parameter keeps the name it was declared with; an
     * extension receiver has no source name of its own, so it is named [RECEIVER_PARAMETER_NAME] - which is what a
     * generated stub, having to declare the receiver as an ordinary parameter, asks for.
     */
    val parameterNames: List<String> get() = parametersWithTypes.map { (parameter, _) -> parameter.bindingName }
}

/** The name a generated stub declares an extension receiver under, because a receiver has no name of its own. */
private const val RECEIVER_PARAMETER_NAME = "receiver"

private val IrValueParameter.bindingName: String
    get() = if (kind == IrParameterKind.ExtensionReceiver) RECEIVER_PARAMETER_NAME else name.asString()