package io.github.mimimishkin.jni.binding.annotation

/**
 * Marks a function as not requiring JNI environment and a class/object reference.
 * A function will be exposed to JVM like a regular JNI function AND almost the same one with `JavaCritical_` prefix
 * (instead of standard `Java_`) will be exposed.
 *
 * Restrictions HotSpot puts on the method (see the JDK-7013347 RFE that introduced them): it must be `static`,
 * `synchronized` and throwing are not allowed, and the parameters must be primitives or primitive arrays.
 *
 * Two JNI functions will be generated: ordinal and critical one. Which of the two symbols a given call goes through is
 * up to the JVM: critical is only taken once the call site has been compiled and inlined, and only on a JVM that
 * supports it. The mechanism is undocumented and unsupported, and Project Panama's FFM API is the intended
 * replacement.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
@Retention(AnnotationRetention.BINARY)
public actual annotation class CriticalNative