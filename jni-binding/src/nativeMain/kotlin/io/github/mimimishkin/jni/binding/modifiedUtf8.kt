package io.github.mimimishkin.jni.binding

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValues
import kotlinx.cinterop.set
import kotlin.text.iterator

/**
 * Converts a String into a null-terminated, modified UTF-8 encoded byte sequence for interoperation
 * with JNI functions.
 */
public val String.modifiedUtf8: CValues<ByteVar>
    get() {
    // Estimate the max possible length: 3 bytes per char + 1 for null terminator
    val bytes = ByteArray(this.length * 3 + 1)
    var pos = 0
    for (ch in this) {
        when (ch) {
            '\u0000' -> {
                // Null char is encoded as 0xC0 0x80
                bytes[pos++] = 0xC0.toByte()
                bytes[pos++] = 0x80.toByte()
            }
            in '\u0001'..'\u007F' -> {
                // 1-byte encoding
                bytes[pos++] = ch.code.toByte()
            }
            in '\u0080'..'\u07FF' -> {
                // 2-byte encoding
                bytes[pos++] = (0xC0 or (ch.code shr 6)).toByte()
                bytes[pos++] = (0x80 or (ch.code and 0x3F)).toByte()
            }
            else -> {
                // 3-byte encoding (including surrogates)
                bytes[pos++] = (0xE0 or (ch.code shr 12)).toByte()
                bytes[pos++] = (0x80 or ((ch.code shr 6) and 0x3F)).toByte()
                bytes[pos++] = (0x80 or (ch.code and 0x3F)).toByte()
            }
        }
    }

    return object : CValues<ByteVar>() {
        override val size = pos + 1
        override val align = 1

        override fun place(placement: CPointer<ByteVar>): CPointer<ByteVar> {
            for (i in 0..<size) placement[i] = bytes[i]
            // Null-terminate
            placement[size] = 0
            return placement
        }
    }
}