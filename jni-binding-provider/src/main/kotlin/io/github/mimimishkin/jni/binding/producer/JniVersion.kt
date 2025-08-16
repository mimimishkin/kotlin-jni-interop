package io.github.mimimishkin.jni.binding.producer

import com.squareup.kotlinpoet.CodeBlock

/**
 * Possible versions of JNI.
 */
public enum class JniVersion(public val major: Int, internal val native: CodeBlock) {
    V1_1(1, CodeBlock.of("/* version 1.1 */ %L", "0x00010001")),
    V1_2(2, CodeBlock.of("/* version 1.2 */ %L", "0x00010002")),
    V1_4(4, CodeBlock.of("/* version 1.4 */ %L", "0x00010004")),
    V1_6(6, CodeBlock.of("/* version 1.6 */ %L", "0x00010006")),
    V1_8(8, CodeBlock.of("/* version 1.8 */ %L", "0x00010008")),
    V9(9,   CodeBlock.of("/* version 9 */ %L", "0x00090000")),
    V10(10, CodeBlock.of("/* version 10 */ %L", "0x000a0000"));

    public companion object {
        /**
         * Guesses the version of JNI from a given Java major version.
         */
        public fun fromMajor(version: Int): JniVersion {
            return when (version) {
                in Int.MIN_VALUE..0 -> throw IllegalArgumentException("Invalid JNI version: $version")
                1 -> V1_1
                2, 3 -> V1_2
                4, 5 -> V1_4
                6, 7 -> V1_6
                8 -> V1_8
                9 -> V9
                else -> V10
            }
        }
    }
}