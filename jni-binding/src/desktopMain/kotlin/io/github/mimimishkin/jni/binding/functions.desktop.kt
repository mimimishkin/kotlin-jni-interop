package io.github.mimimishkin.jni.binding

import kotlinx.cinterop.*

/**
 * Loads a class from a [classBuf] of raw class data.
 *
 * The buffer containing the raw class data is not referenced by the VM after the [defineClass] call returns, and it may
 * be discarded if desired.
 *
 * @param name the name of the class or interface to be defined. May be `null`, or it must match the name encoded within
 * the class file data. Must be encoded in the null-terminated modified UTF-8. Use [String.modifiedUtf8] to get it or
 * **if you are sure that your string doesn't have illegal characters** you may use optimized [String.utf8].
 * @param loader a class loader assigned to the defined class. May be `null`, indicating the "null class loader" (or
 * "bootstrap class loader").
 * @param classBuf buffer containing the `.class` file data.
 * @param classBufLen buffer length.
 *
 * @return a Java class object or `null` if an error occurs.
 *
 * @throws ClassFormatError if the class data does not specify a valid class.
 * @throws ClassCircularityError if a class or interface is its own superclass or superinterface.
 * @throws OutOfMemoryError if the system runs out of memory.
 * @throws SecurityException if the caller attempts to define a class in the "java" package tree.
 */
context(env: JniEnv, autofreeScope: AutofreeScope)
public fun defineClass(name: CValuesRef<ByteVar>?, loader: JObject?, classBuf: CPointer<ByteVar>, classBufLen: Int): JClass? {
    return env.DefineClass!!(env.ptr, name?.getPointer(autofreeScope), loader.c, classBuf, classBufLen).wrap()
}

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
