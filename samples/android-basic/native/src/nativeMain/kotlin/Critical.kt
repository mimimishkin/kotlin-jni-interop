import io.github.mimimishkin.jni.binding.annotation.CriticalNative
import io.github.mimimishkin.jni.binding.annotation.JniActuals
import kotlin.math.sqrt

@JniActuals(className = "org.sample.CriticalKt")
object Critical {

    @CriticalNative
    fun criticalHypot(x: Double, y: Double): Double = sqrt(x * x + y * y)

    @CriticalNative
    fun criticalMix(seed: Int, rounds: Int): Long {
        var state = seed.toLong() or 1L
        repeat(rounds) {
            state = state * 6364136223846793005L + 1442695040888963407L
            state = state xor (state ushr 31)
        }
        return state
    }

    @CriticalNative
    fun criticalIsOdd(value: Int): Boolean = (value and 1) == 1

    @CriticalNative
    fun criticalScale(value: Long, factor: Int): Long = value * factor

    @CriticalNative
    fun criticalBurn(seed: Int, rounds: Int) {
        var state = seed
        repeat(rounds) { state = state * 31 + 17 }
        if (state == Int.MIN_VALUE) println("unreachable $state")
    }
}