package io.github.mimimishkin.jni.binding.annotation

/**
 * An alias to [dalvik.annotation.optimization.FastNative] on android, ignored on JVM.
 */
@OptIn(ExperimentalMultiplatform::class)
@OptionalExpectation
public expect annotation class FastNative