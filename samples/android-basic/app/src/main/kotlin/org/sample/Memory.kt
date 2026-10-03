package org.sample

import io.github.mimimishkin.jni.binding.annotation.JniExpects
import io.github.mimimishkin.jni.binding.annotation.LoadMethod
import java.nio.ByteBuffer

/** Direct buffers, reference kinds and local frames. */
@JniExpects
object Memory {

    @LoadMethod
    @Suppress("UnsafeDynamicallyLoadedCode")
    private fun load() {
        System.loadLibrary("example")
    }

    /**
     * Writes [size] bytes into the buffer's memory through its raw address and returns their checksum.
     *
     * Pass a direct buffer: [ByteBuffer.allocate] has no address and native reports -1.
     */
    external fun fillDirect(buffer: ByteBuffer, size: Int, seed: Int): Int

    /** Native wraps freshly allocated native memory in a direct [ByteBuffer]. */
    external fun allocateDirect(size: Int, seed: Int): ByteBuffer

    /** Releases memory from [allocateDirect]. The buffer must not be used afterwards. */
    external fun freeDirect(buffer: ByteBuffer)

    /** Keeps [value] alive as a global reference and returns a token for it. */
    external fun retain(value: Any): Long

    /** `true` while the token from [retain] is still held. */
    external fun isRetained(token: Long): Boolean

    /** Deletes the global reference behind [token]. Returns `false` if the token was unknown. */
    external fun release(token: Long): Boolean

    /** Creates [loops] x [perLoop] local references, each batch inside its own frame. */
    external fun localFrameStress(loops: Int, perLoop: Int): Long
}
