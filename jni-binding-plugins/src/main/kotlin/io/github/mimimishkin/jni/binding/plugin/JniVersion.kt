package io.github.mimimishkin.jni.binding.plugin

public enum class JniVersion(public val major: Int) {
    V1_1(1),
    V1_2(2),
    V1_4(4),
    V1_6(6),
    V1_8(8),
    V9(9),
    V_10(10);

    public companion object {
        public fun fromMajor(version: Int): JniVersion {
            return when (version) {
                in Int.MIN_VALUE..0 -> throw IllegalArgumentException("Invalid JNI version: $version")
                1 -> V1_1
                2, 3 -> V1_2
                4, 5 -> V1_4
                6, 7 -> V1_6
                8 -> V1_8
                9 -> V9
                else -> V_10
            }
        }
    }
}