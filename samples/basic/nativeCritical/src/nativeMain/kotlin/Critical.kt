import io.github.mimimishkin.jni.binding.annotation.CriticalNative
import io.github.mimimishkin.jni.binding.annotation.JniActuals
import kotlinx.cinterop.CArrayPointer
import kotlinx.cinterop.DoubleVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.get
import kotlinx.cinterop.set

// All actuals in this file do not need [JniEnv] or [JClass]/[JObject]. Let's pretend that they should be very fast, so
// to speed up them we are relying on Critical JNI Natives mechanism.
// Two facades will be generated for each of them: one ordial `Java_...` and one `JavaCritical_...`, so method will work
// in JDK that not support critical natives.
// Note that critical natives should be fast, since the thread is "frozen" in a state that can block the garbage
// collector.
@JniActuals(className = "org.sample.CriticalKt")
object CriticalNatives {

    // This method actually has this JVM signature: `(I[I)J`.
    // Note that int array is passed as a pair of length and a primitive array pointer.
    @CriticalNative
    fun sum(size: Int, values: CArrayPointer<IntVar>): Long {
        var total = 0L
        for (index in 0 until size) {
            total += values[index].toLong()
        }
        return total
    }

    // We can have any number of primitive arrays but only primiteve ones. And return only primitives.
    @CriticalNative
    fun dot(leftSize: Int, left: CArrayPointer<DoubleVar>, rightSize: Int, right: CArrayPointer<DoubleVar>): Double {
        var result = 0.0
        for (index in 0 until minOf(leftSize, rightSize)) {
            result += left[index] * right[index]
        }
        return result
    }

    @CriticalNative
    fun isAllPositive(size: Int, values: CArrayPointer<IntVar>): Boolean {
        for (index in 0 until size) {
            if (values[index] <= 0) {
                return false
            }
        }
        return true
    }

    // We can not only read, but also write to primitive arrays.
    @CriticalNative
    fun fillTwice(size: Int, values: CArrayPointer<LongVar>) {
        for (index in 0 until size) {
            values[index] = values[index] * 2L
        }
    }
}
