package io.github.mimimishkin.jni.binding.plugin.consumer

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

/**
 * One entry of a JNI binding contract file, as read from either the consumer's `expects.json` or a producer's
 * `actuals.json`.
 *
 * Both files describe the same thing from the two ends - a JVM method the binding must cover - so a single shape reads
 * both, carrying the fields of each: [needEnv] and [source] come from `actuals.json`, [targets] from `expects.json`,
 * and the rest is common. Which side a file was written by is exactly what the absent ones tell.
 *
 * A JVM type is stored as a plain string, the way the consumer serializes its `JavaType`: the bare name, optionally
 * prefixed with `@Nullable ` or `@NonNull `. [parameterTypeNames] and [returnTypeName] drop that prefix, because
 * nullability is not part of a JVM signature and never takes part in matching.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
// The two files are written by different modules and carry each other's bookkeeping, plus a signature key only the
// consumer puts in. Every field either side actually relies on is declared above, so anything left is not this
// module's business and a stricter decoder would only turn a contract written by another module into a failure.
@JsonIgnoreUnknownKeys
internal data class JniFunctionContract(
    val className: String,
    val methodName: String,
    val isStatic: Boolean? = null,
    val parameterTypes: List<String> = emptyList(),
    val returnType: String = "void",
    val targets: List<String> = emptyList(),
    /** Whether the `@JniActual` takes a `JniEnv` context parameter. Written by the producer only. */
    val needEnv: Boolean? = null,
    /** Source file declaring the `@JniActual`. Written by the producer only. */
    val source: String? = null,
) {
    val parameterTypeNames: List<String> get() = parameterTypes.map { it.withoutNullability() }

    val returnTypeName: String get() = returnType.withoutNullability()

    /**
     * Whether [actual] is this declaration's counterpart, i.e. whether the expect is already implemented and no stub
     * should be generated for it.
     *
     * This is the accepting side's own notion of a match, repeated rather than shared: the consumer module is a
     * compiler plugin and is not on this module's classpath, and the two must agree on it - reporting a missing actual
     * for something that is implemented, or generating a duplicate for something that is, both come from a difference
     * here.
     */
    fun isImplementedBy(actual: JniFunctionContract): Boolean =
        className == actual.className &&
            methodName == actual.methodName &&
            // `null` means the actual carries no receiver at all, which the consumer treats as "static or instance
            // either will do".
            (actual.isStatic == null || actual.isStatic == isStatic) &&
            returnTypeName == actual.returnTypeName &&
            parameterTypeNames == actual.parameterTypeNames
}

private fun String.withoutNullability(): String = when {
    startsWith("@Nullable ") -> removePrefix("@Nullable ")
    startsWith("@NonNull ") -> removePrefix("@NonNull ")
    else -> this
}
