@file:Suppress("NOTHING_TO_INLINE")

package io.github.mimimishkin.jni

import kotlinx.cinterop.MemScope
import kotlinx.cinterop.placeTo
import kotlinx.cinterop.toKString
import kotlinx.cinterop.utf16

/**
 * Converts JVM string into Kotlin Native string.
 *
 * @return converted string or `null` if fails.
 */
context(env: JNIEnv)
public inline fun JString.toKString(): String? {
    val (chars, _) = env.GetStringChars(this) ?: return null
    val res = chars.toKString()
    env.ReleaseStringChars(this, chars)
    return res
}

/**
 * Converts Kotlin Native string into JVM string.
 *
 * @return converted string or `null` if fails.
 */
context(env: JNIEnv, memScope: MemScope)
public inline fun String.toJString(): JString? {
    return env.NewString(utf16.placeTo(memScope), length)
}