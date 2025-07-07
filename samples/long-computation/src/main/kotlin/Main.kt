package io.github.mimimishkin.samples.longcomputation

import kotlin.time.measureTime

object Main {
    init {
        System.load("C:\\Users\\mika\\Desktop\\my_folder\\0\\projects\\java-kotlin-native-interop\\samples\\long-computation\\native-part\\build\\bin\\mingwX64\\releaseShared\\computation.dll")
    }

    fun crossPlatformComputation(count: Int) {
        val array = ByteArray(count)
        for ((index, b) in array.withIndex()) {
            array[index] = (b + index).toByte()
        }
    }

    external fun nativeComputation(count: Int)
}

fun main() {
    val count = 1_000_000_000
    println("Java computation time on count=$count: " + measureTime { Main.crossPlatformComputation(count) })
    println("Native computation time on count=$count: " + measureTime { Main.nativeComputation(count) })
}