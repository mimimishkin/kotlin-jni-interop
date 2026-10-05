@file:Suppress("NOTHING_TO_INLINE")

package io.github.mimimishkin.jni.binding

import kotlinx.cinterop.AutofreeScope
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.CPointer

/**
 * Alias for [defineClass] which accepts the class [name] as a Kotlin [String].
 *
 * Throws instead of returning `null` if the class cannot be defined.
 */
context(env: JniEnv, autofreeScope: AutofreeScope)
public inline fun defineClass(
    name: String,
    loader: JObject?,
    classBuf: CPointer<ByteVar>,
    classBufLen: Int,
): JClass {
    return defineClass(name.modifiedUtf8, loader, classBuf, classBufLen) ?: error("Cannot define class '$name'")
}