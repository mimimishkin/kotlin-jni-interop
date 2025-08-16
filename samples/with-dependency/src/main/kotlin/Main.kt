package io.github.mimimishkin.samples.longcomputation

import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlin.io.path.toPath
import kotlin.time.measureTime

object Main {
    init {
        val name = System.mapLibraryName("computation")
        val lib = Main::class.java.getResource("/$name")!!.toURI().toPath()
        val file = Files.createTempFile(null, name)
        Files.copy(lib, file, REPLACE_EXISTING)
        System.load(file.toString())
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