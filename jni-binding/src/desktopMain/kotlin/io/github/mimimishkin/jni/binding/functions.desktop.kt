package io.github.mimimishkin.jni.binding

import kotlinx.cinterop.*

/**
 * The length in bytes of the modified UTF-8 representation of a string.
 *
 * @since JDK 24
 */
context(env: JniEnv)
public val JString.utfLengthLong: Long get() {
    return env.GetStringUTFLengthAsLong!!(env.ptr, c)
}

/**
 * Returns the `java.lang.Module` object for the module that the class is a member of. If the class is not in a named
 * module, then the unnamed module of the class loader for the class is returned.
 * If the class represents an array type, then this function returns the Module object for the element type. If the
 * class represents a primitive type or void, then the Module object for the `java.base` module is returned.
 *
 * @return the module that the class or interface is a member of.
 *
 * @since JDK/JRE 9
 */
context(env: JniEnv)
public val JClass.module: JObject get() {
    return env.GetModule!!(env.ptr, c).wrap()!!
}

/**
 * Tests whether an object is a virtual Thread.
 *
 * @since JDK/JRE 21
 */
public fun JniEnv.isVirtualThread(thread: JObject?): Boolean {
    return this.IsVirtualThread!!(ptr, thread.c).toKBoolean()
}
