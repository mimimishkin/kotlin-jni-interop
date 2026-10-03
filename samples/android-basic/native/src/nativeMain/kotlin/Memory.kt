import io.github.mimimishkin.jni.binding.JByteBuffer
import io.github.mimimishkin.jni.binding.JObject
import io.github.mimimishkin.jni.binding.JniEnv
import io.github.mimimishkin.jni.binding.address
import io.github.mimimishkin.jni.binding.annotation.JniActuals
import io.github.mimimishkin.jni.binding.capacity
import io.github.mimimishkin.jni.binding.deleteGlobalRef
import io.github.mimimishkin.jni.binding.newDirectByteBuffer
import io.github.mimimishkin.jni.binding.newGlobalRef
import io.github.mimimishkin.jni.binding.refFrame
import io.github.mimimishkin.jni.binding.toJString
import kotlinx.cinterop.AutofreeScope
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference

@JniActuals(className = "org.sample.Memory")
object Memory {

    /**
     * Global references handed out by [retain], keyed by token.
     */
    private val retained = AtomicReference<Map<Long, JObject>>(emptyMap())
    private val nextToken = AtomicLong(0L)

    /**
     * Writes [size] bytes through the buffer's raw address and returns their checksum.
     */
    context(env: JniEnv)
    fun fillDirect(buffer: JByteBuffer?, size: Int, seed: Int): Int {
        val target = buffer ?: return -1
        val bytes = target.address?.reinterpret<ByteVar>() ?: return -1
        if (target.capacity < size.toLong()) return -1
        var state = seed
        var checksum = 0
        for (i in 0 until size) {
            state = state xor (state shl 13)
            state = state xor (state ushr 7)
            state = state xor (state shl 17)
            val value = state.toByte()
            bytes[i] = value
            checksum += value
        }
        return checksum
    }

    /**
     * Allocates native memory, wraps it in a direct `ByteBuffer` and fills it.
     */
    context(env: JniEnv)
    fun allocateDirect(size: Int, seed: Int): JByteBuffer? {
        val memory = nativeHeap.allocArray<ByteVar>(size)
        var state = seed
        for (i in 0 until size) {
            state = state xor (state shl 13)
            state = state xor (state ushr 7)
            state = state xor (state shl 17)
            memory[i] = state.toByte()
        }
        return newDirectByteBuffer(memory, size.toLong())
    }

    /**
     * Releases memory from [allocateDirect].
     */
    context(env: JniEnv)
    fun freeDirect(buffer: JByteBuffer?) {
        val address = buffer?.address ?: return
        nativeHeap.free(address.rawValue)
    }

    /**
     * Promotes [value] to a global reference and returns a token for it.
     */
    context(env: JniEnv)
    fun retain(value: JObject?): Long {
        val global = value?.newGlobalRef() ?: return 0L
        val token = nextToken.addAndFetch(1L)
        while (true) {
            val current = retained.load()
            if (retained.compareAndSet(current, current + (token to global))) break
        }
        return token
    }

    /**
     * Whether the token from [retain] is still held.
     */
    fun isRetained(token: Long): Boolean = retained.load().containsKey(token)

    /**
     * Deletes the global reference behind [token]; `false` when the token was never held.
     */
    context(env: JniEnv)
    fun release(token: Long): Boolean {
        while (true) {
            val current = retained.load()
            val global = current[token] ?: return false
            if (retained.compareAndSet(current, current - token)) {
                global.deleteGlobalRef()
                return true
            }
        }
    }

    /**
     * Creates [loops] x [perLoop] local references, each batch inside its own `refFrame`.
     */
    context(env: JniEnv, autofreeScope: AutofreeScope)
    fun localFrameStress(loops: Int, perLoop: Int): Long {
        var created = 0L
        repeat(loops) {
            refFrame(perLoop + 1) {
                repeat(perLoop) {
                    "local".toJString()
                    created++
                }
            }
        }
        return created
    }
}
