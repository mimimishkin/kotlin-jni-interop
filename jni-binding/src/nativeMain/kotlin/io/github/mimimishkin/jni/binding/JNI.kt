package io.github.mimimishkin.jni.binding

/**
 * Java version that corresponds to the new JNI API.
 *
 * Without configuration, JNI functions receive [JniEnv] of version [JNI.v1].
 */
public typealias JniVersion = Int

@PublishedApi internal const val JNI_VERSION_1_1: JniVersion = 0x00010001
@PublishedApi internal const val JNI_VERSION_1_2: JniVersion = 0x00010002
@PublishedApi internal const val JNI_VERSION_1_4: JniVersion = 0x00010004
@PublishedApi internal const val JNI_VERSION_1_6: JniVersion = 0x00010006
@PublishedApi internal const val JNI_VERSION_1_8: JniVersion = 0x00010008
@PublishedApi internal const val JNI_VERSION_9: JniVersion = 0x00090000
@PublishedApi internal const val JNI_VERSION_10: JniVersion = 0x000a0000
@PublishedApi internal const val JNI_VERSION_19: JniVersion = 0x00130000
@PublishedApi internal const val JNI_VERSION_20: JniVersion = 0x00140000
@PublishedApi internal const val JNI_VERSION_21: JniVersion = 0x00150000
@PublishedApi internal const val JNI_VERSION_24: JniVersion = 0x00180000

@PublishedApi internal const val JNI_OK: Int = 0
@PublishedApi internal const val JNI_ERR: Int = -1
@PublishedApi internal const val JNI_EDETACHED: Int = -2
@PublishedApi internal const val JNI_EVERSION: Int = -3
@PublishedApi internal const val JNI_ENOMEM: Int = -4
@PublishedApi internal const val JNI_EEXIST: Int = -5
@PublishedApi internal const val JNI_EINVAL: Int = -6

/**
 * Functions and utilities to work with JNI (Java Native Interface).
 *
 * Note: every `throws` in documentation is about java side.
 */
public object JNI {
    /**
     * JDK/JRE version *1.1*.
     */
    public inline val v1: JniVersion get() = JNI_VERSION_1_1

    /**
     * JDK/JRE versions *1.2*, *1.3*.
     */
    public inline val v2: JniVersion get() = JNI_VERSION_1_2

    /**
     * JDK/JRE version *1.4*, *1.5*.
     */
    public inline val v3: JniVersion get() = JNI_VERSION_1_4

    /**
     * JDK/JRE versions *1.6*, *1.7*.
     */
    public inline val v6: JniVersion get() = JNI_VERSION_1_6

    /**
     * JDK/JRE version *1.8*
     */
    public inline val v8: JniVersion get() = JNI_VERSION_1_8

    /**
     * JDK/JRE version *9*.
     */
    public inline val v9: JniVersion get() = JNI_VERSION_9

    /**
     * JDK/JRE versions *10*, *11*, *12*, *13*, *14*, *15*, *16*, *17*, *18*.
     */
    public inline val v10: JniVersion get() = JNI_VERSION_10

    /**
     * JDK/JRE version *19*.
     */
    public inline val v19: JniVersion get() = JNI_VERSION_19

    /**
     * JDK/JRE version *20*.
     */
    public inline val v20: JniVersion get() = JNI_VERSION_20

    /**
     * JDK/JRE versions *21*, *22*, *23*.
     */
    public inline val v21: JniVersion get() = JNI_VERSION_21

    /**
     * JDK/JRE version *24* and later.
     */
    public inline val v24: JniVersion get() = JNI_VERSION_24

    /**
     * Executes a [block] containing a JNI call that return one of `JNI_OK`, `JNI_ERR`, `JNI_EDETACHED`, `JNI_EVERSION`,
     * `JNI_ENOMEM`, `JNI_EEXIST`, `JNI_EINVAL`.
     *
     * If the returned value is not `JNI_OK` throws corresponding exception.
     */
    internal fun safeCall(block: () -> Int) {
        when (val code = block()) {
            JNI_OK -> {/* success */}
            JNI_ERR -> throw RuntimeException("Unknown error.")
            JNI_EDETACHED -> throw JniThreadDetachedException()
            JNI_EVERSION -> throw JniVersionException()
            JNI_ENOMEM -> throw JniOutOfMemoryException()
            JNI_EEXIST -> throw JniVmAlreadyExistsException()
            JNI_EINVAL -> throw IllegalArgumentException("Invalid arguments.")
            else -> throw Exception("Unknown error code: $code")
        }
    }
}