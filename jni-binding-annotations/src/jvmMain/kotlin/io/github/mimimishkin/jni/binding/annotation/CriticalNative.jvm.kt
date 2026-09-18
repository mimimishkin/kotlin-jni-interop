package io.github.mimimishkin.jni.binding.annotation

/**
 * Marks a function as not requiring JNI environment and a class/object reference.
 * A function will be exposed to JVM like a regular JNI function AND almost the same one with `JavaCritical_` prefix
 * (instead of standard `Java_`) will be exposed.
 *
 * Note that this functionality requires `-XX:+CriticalJNINatives` arg in OpenJDK/HotSpot starting from JDK 8 and until
 * JDK 15, was deprecated at JDK 16 and was removed at JDK 22. The Foreign Function & Memory API (Project Panama) came
 * to replace JNI and Critical Natives.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
@Retention(AnnotationRetention.SOURCE)
public actual annotation class CriticalNative