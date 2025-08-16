import io.github.mimimishkin.jni.binding.JInt
import io.github.mimimishkin.jni.binding.JObject
import io.github.mimimishkin.jni.binding.JniEnv
import io.github.mimimishkin.jni.binding.annotation.JniActual
import io.github.mimimishkin.jni.binding.annotation.JniOnLoad

// This can be exported both ways: 1. as a JNI function (that start with "Java_") or 2. using RegisterNatives
// Which way will be used depends on the `jniBinding.useOnLoad` ksp option. Default is `false`.

// Note that `className` will be mapped to the right form, no matter what characters are used in it.

@JniActual(className = "io.github.mimimishkin.samples.long_computation.Главный")
fun nativeComputation(count: JInt) {
    val array = ByteArray(count)
    for ((index, b) in array.withIndex()) {
        array[index] = (b + index).toByte()
    }
}

@JniOnLoad
fun onLoad() {
    println("Hello from Kotlin/Native!")
}