@file:Suppress("NOTHING_TO_INLINE")

package io.github.mimimishkin.jni.binding

import kotlinx.cinterop.AutofreeScope
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.CPointer

/**
 * Alias for [findClass] which accepts the class [name] as a Kotlin [String].
 *
 * Throws instead of returning `null` if the class cannot be found.
 */
context(env: JniEnv, autofreeScope: AutofreeScope)
public inline fun findClass(name: String): JClass {
    return findClass(name.modifiedUtf8) ?: error("Cannot find class '$name'")
}

/**
 * Alias for [JClass.methodId] which accepts the method [name] and [sig] as Kotlin [String]s.
 *
 * Throws instead of returning `null` if the method cannot be found.
 */
context(env: JniEnv, autofreeScope: AutofreeScope)
public inline fun JClass.methodId(name: String, sig: String): JMethodID {
    return methodId(name.modifiedUtf8, sig.modifiedUtf8) ?: error("Cannot find method '$name$sig'")
}

/**
 * Alias for [JClass.staticMethodId] which accepts the method [name] and [sig] as Kotlin [String]s.
 *
 * Throws instead of returning `null` if the method cannot be found.
 */
context(env: JniEnv, autofreeScope: AutofreeScope)
public inline fun JClass.staticMethodId(name: String, sig: String): JMethodID {
    return staticMethodId(name.modifiedUtf8, sig.modifiedUtf8) ?: error("Cannot find static method '$name$sig'")
}

/**
 * Alias for [JClass.fieldId] which accepts the field [name] and [sig] as Kotlin [String]s.
 *
 * Throws instead of returning `null` if the field cannot be found.
 */
context(env: JniEnv, autofreeScope: AutofreeScope)
public inline fun JClass.fieldId(name: String, sig: String): JFieldID {
    return fieldId(name.modifiedUtf8, sig.modifiedUtf8) ?: error("Cannot find field '$name$sig'")
}

/**
 * Alias for [JClass.staticFieldId] which accepts the field [name] and [sig] as Kotlin [String]s.
 *
 * Throws instead of returning `null` if the field cannot be found.
 */
context(env: JniEnv, autofreeScope: AutofreeScope)
public inline fun JClass.staticFieldId(name: String, sig: String): JFieldID {
    return staticFieldId(name.modifiedUtf8, sig.modifiedUtf8) ?: error("Cannot find static field '$name$sig'")
}

/**
 * Alias for [throwNew] which accepts the [message] as a Kotlin [String].
 */
context(env: JniEnv, autofreeScope: AutofreeScope)
public inline fun throwNew(clazz: JClass, message: String) {
    throwNew(clazz, message.modifiedUtf8)
}

/**
 * Alias for [fatalError] which accepts the [message] as a Kotlin [String].
 */
context(env: JniEnv, autofreeScope: AutofreeScope)
public inline fun fatalError(message: String): Nothing {
    return fatalError(message.modifiedUtf8)
}

/**
 * Alias for [JavaVM.attachCurrentThread] which accepts the thread [name] as a Kotlin [String].
 */
context(autofreeScope: AutofreeScope)
public inline fun JavaVM.attachCurrentThread(
    version: JniVersion,
    name: String,
    group: JObject? = null,
): JniEnv {
    return attachCurrentThread(version, name.modifiedUtf8, group)
}

/**
 * Alias for [JavaVM.attachCurrentThreadAsDaemon] which accepts the thread [name] as a Kotlin [String].
 */
context(autofreeScope: AutofreeScope)
public inline fun JavaVM.attachCurrentThreadAsDaemon(
    version: JniVersion,
    name: String,
    group: JObject? = null,
): JniEnv {
    return attachCurrentThreadAsDaemon(version, name.modifiedUtf8, group)
}

/**
 * Alias for [withEnvAttaching] which accepts the thread [name] as a Kotlin [String] encoded as modified UTF-8 with
 * [String.modifiedUtf8].
 */
context(autofreeScope: AutofreeScope)
public inline fun <T> JavaVM.withEnvAttaching(
    version: JniVersion,
    name: String,
    group: JObject? = null,
    block: context(JniEnv) () -> T
): T {
    return withEnvAttaching(version, name.modifiedUtf8, group, block)
}

/**
 * Alias for [JNINativeMethodRegistry.register] which accepts [name] and [signature] as Kotlin [String]s.
 */
context(autofreeScope: AutofreeScope)
public inline fun JNINativeMethodRegistry.register(
    name: String,
    signature: String,
    functionPointer: JRef<CFunction<*>>,
): Unit = register(name.modifiedUtf8, signature.modifiedUtf8, functionPointer)
