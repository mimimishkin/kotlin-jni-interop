@file:Suppress("NOTHING_TO_INLINE")

package io.github.mimimishkin.jni

import kotlinx.cinterop.toKString

context(env: JNIEnv)
public inline fun JString.toKString(): String? {
    val (chars, _) = env.GetStringChars(this) ?: return null
    val res = chars.toKString()
    env.ReleaseStringChars(this, chars)
    return res
}