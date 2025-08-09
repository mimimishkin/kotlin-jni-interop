package io.github.mimimishkin.jni.binding.plugin

import java.io.Serializable

public sealed interface JniBindingFilter : Serializable {
    public fun accept(qualifier: String): Boolean

    /**
     * Filter by regular expression.
     *
     * @param patterns regular expressions to match function names annotated with `@JniExpect` and `@JniActual`.
     * For example, `com.example.jni.ObjectName.funName` can be matched with `.*ObjectName\.funName`.
     */
    public class Regex(public vararg val patterns: String) : JniBindingFilter {
        private val regexes = patterns.map { it.toRegex() }

        override fun accept(qualifier: String): Boolean = regexes.any { it.matches(qualifier) }
    }

    /**
     * Allowlist of functions to be processed.
     *
     * @param names fully qualified or simple names of functions annotated with `@JniExpect` and `@JniActual`.
     * For example, `com.example.jni.ObjectName.funName` can be also associated with `jni.ObjectName.funName`,
     * `ObjectName.funName` or simply `funName`.
     * @param strict if true, only fully qualified names will be matched, otherwise simple names will also be matched.
     */
    public class WhiteList(public val names: Collection<String>, public val strict: Boolean = false) : JniBindingFilter {
        override fun accept(qualifier: String): Boolean =
            names.any { strict && qualifier == it || qualifier.endsWith(it) }
    }

    /**
     * Denylist of functions to be processed.
     *
     * @param names fully qualified or simple names of functions annotated with `@JniExpect` and `@JniActual`.
     * For example, `com.example.jni.ObjectName.funName` can be also associated with `jni.ObjectName.funName`,
     * `ObjectName.funName` or simply `funName`.
     * @param strict if true, only fully qualified names will be matched, otherwise simple names will also be matched.
     */
    public class BlackList(public val names: Collection<String>, public val strict: Boolean = false) : JniBindingFilter {
        override fun accept(qualifier: String): Boolean =
            names.none { strict && qualifier == it || qualifier.endsWith(it) }
    }

    /**
     * All functions annotated with `@JniActual` will be exported to JVM.
     * And all functions annotated with `@JniExpect` will be linked with their native implementation.
     */
    public object All : JniBindingFilter {
        override fun accept(qualifier: String): Boolean = true
        private fun readResolve(): Any = All
    }

    public companion object {
        public fun toString(filter: JniBindingFilter): String = when (filter) {
            is Regex -> "Regex:${filter.patterns.joinToString(":")}"
            is WhiteList -> "WhiteList:${if (filter.strict) "strict" else "contains"}:${filter.names.joinToString(";")}"
            is BlackList -> "BlackList:${if (filter.strict) "strict" else "contains"}:${filter.names.joinToString(";")}"
            All -> "All"
        }

        public fun fromString(value: String): JniBindingFilter = when {
            value.startsWith("Regex:") -> Regex(*value.removePrefix("Regex:").split(':').toTypedArray())
            value.startsWith("WhiteList:") -> value.split(':').let { (_, strictness, names) ->
                val strict = when (strictness) {
                    "strict" -> true
                    "contains" -> false
                    else -> throw IllegalArgumentException("Unknown strictness: $strictness")
                }
                WhiteList(names.split(";"), strict)
            }
            value.startsWith("BlackList:") -> value.split(':').let { (_, strictness, names) ->
                val strict = when (strictness) {
                    "strict" -> true
                    "contains" -> false
                    else -> throw IllegalArgumentException("Unknown strictness: $strictness")
                }
                BlackList(names.split(";"), strict)
            }
            value == "All" -> All
            else -> throw IllegalArgumentException("Unknown JniBindingFilter: $value")
        }
    }
}