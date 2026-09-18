package io.github.mimimishkin.jni.binding.producer.model

import kotlin.Int.Companion.MIN_VALUE

/**
 * Possible versions of JNI.
 */
public enum class JniVersion(public val major: Int, public val native: Int) {
    V1_1(1, 0x00010001),
    V1_2(2, 0x00010002),
    V1_4(4, 0x00010004),
    V1_6(6, 0x00010006),
    V1_8(8, 0x00010008),
    V9(9, 0x00090000),
    V10(10, 0x000a0000),
    V19(19, 0x00130000),
    V20(20, 0x00140000),
    V21(21, 0x00150000),
    V24(24, 0x00180000);

    public companion object {
        /**
         * Guesses the version of JNI from a given Java major version.
         */
        public fun fromMajor(version: Int): JniVersion {
            return when (version) {
                in MIN_VALUE..0 -> throw IllegalArgumentException("Invalid JNI version: $version")
                1 -> V1_1
                2, 3 -> V1_2
                4, 5 -> V1_4
                6, 7 -> V1_6
                8 -> V1_8
                9 -> V9
                10, 11, 12, 13, 14, 15, 16, 17, 18 -> V10
                19 -> V19
                20 -> V20
                21, 22, 23 -> V21
                else -> V24
            }
        }
    }
}
