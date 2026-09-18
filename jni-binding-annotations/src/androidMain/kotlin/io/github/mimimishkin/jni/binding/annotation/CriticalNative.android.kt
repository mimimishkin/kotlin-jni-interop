package io.github.mimimishkin.jni.binding.annotation

/**
 * An alias for android [dalvik.annotation.optimization.CriticalNative].
 *
 * Using `RegisterNatives` instead of exposing JNI functions is highly recommended for such functions.
 *
 * Note that using such functions on Android 7- will cause crashes.
 */
public actual typealias CriticalNative = dalvik.annotation.optimization.CriticalNative