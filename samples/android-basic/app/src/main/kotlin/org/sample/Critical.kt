package org.sample

import io.github.mimimishkin.jni.binding.annotation.CriticalNative
import io.github.mimimishkin.jni.binding.annotation.JniExpect
import io.github.mimimishkin.jni.binding.annotation.LoadMethod


@CriticalNative
@JniExpect
external fun criticalHypot(x: Double, y: Double): Double

@CriticalNative
@JniExpect
external fun criticalMix(seed: Int, rounds: Int): Long

@CriticalNative
@JniExpect
external fun criticalIsOdd(value: Int): Boolean

@CriticalNative
@JniExpect
external fun criticalScale(value: Long, factor: Int): Long

@CriticalNative
@JniExpect
external fun criticalBurn(seed: Int, rounds: Int)

@Suppress("UnsafeDynamicallyLoadedCode")
@LoadMethod
private fun loadExampleForCritical(os: String, arch: String) {
    System.loadLibrary("example")
}