package io.github.mimimishkin.jni.binding.plugin.producer

import kotlinx.serialization.Serializable

/**
 * Information about configured interop with JNI.
 * Every function annotated with `@JniActual`, `@JniOnLoad`, and `@JniOnUnload` should have signature with classes
 * defined below.
 *
 * @property envType Type of `env` parameter. For example, `io.github.mimimishkin.jni.binding.JniEnv`.
 * @property vmType Type of `vm` parameter. For example, `io.github.mimishkin.jni.binding.JavaVM`.
 * @property objType Type of `obj` parameter. For example, `io.github.mimimishkin.jni.binding.JObject`.
 * @property clsType Type of `cls` parameter. For example, `io.github.mimimishkin.jni.binding.JClass`.
 * @property forOnLoad Information required if binding on load mode was selected.
 *
 * @see FromJniBinding
 */
@Serializable
public data class JniInteropConfig(
    val envType: String,
    val vmType: String,
    val objType: String,
    val clsType: String,

    val forOnLoad: ForOnLoadInfo? = null,
) {
    /**
     * @property nativeMethodType Type of `JNINativeMethod` structure. For example,
     *           `io.github.mimimishkin.jni.binding.JniNativeMethod`.
     * @property getEnv Member name of `GetEnv` function. For example, `io.github.mimimishkin.jni.binding.GetEnv`.
     *           This name will be imported to be used in [expressionGetEnv] as `%getEnv:M`.
     * @property expressionGetEnv Kotlin Poet expression to get `env` pointer from `vm` pointer. `vm` of type [vmType]
     *           and `jniVersion` of type [Int] parameters are available. For example, `%vm:L.%getEnv:M(%jniVersion:L)"`.
     * @property findClass Member name of `FindClass` function. For example,
     *           `io.github.mimimishkin.jni.binding.FindClass`. This name will be imported to be used in
     *           [expressionFindClass] as `%findClass:M`.
     * @property expressionFindClass Expression to find class by name. `env` of type [envType] and `className` of type
     *           `CPointer<ByteVar>` parameters are available. Also, code will be executed with the context parameter of
     *           type [envType]. For example, `"%findClass:M(%className:L)"`.
     * @property registerNatives Member name of `RegisterNatives` function. For example,
     *           `io.github.mimimishkin.jni.binding.RegisterNatives`. This name will be imported to be used in
     *           [expressionRegisterNatives] as `%registerNatives:M`..
     * @property expressionRegisterNatives Expression to register native methods. `env` of type [envType], `clazz` of
     *           type [clsType], `natives` of type `CArrayPointer<JNINativeMethod>` and `nativesCount` of type [Int]
     *           parameters are available. Also, code will be executed with the context parameter of type [envType].
     *           For example, `%registerNatives:M(%class:L, %natives:L, %nativesCount:L)`.
     */
    @Serializable
    public data class ForOnLoadInfo(
        public val nativeMethodType: String,
        public val getEnv: String,
        public val expressionGetEnv: String,
        public val findClass: String,
        public val expressionFindClass: String,
        public val registerNatives: String,
        public val expressionRegisterNatives: String,
    )

    public companion object {
        /**
         * Preconfigured [JniInteropConfig] for jni binding library.
         */
        public val FromJniBinding: JniInteropConfig = JniInteropConfig(
            envType = "io.github.mimimishkin.jni.binding.JniEnv",
            vmType = "io.github.mimimishkin.jni.binding.JavaVM",
            objType = "io.github.mimimishkin.jni.binding.JObject",
            clsType = "io.github.mimimishkin.jni.binding.JClass",
            forOnLoad = ForOnLoadInfo(
                nativeMethodType = "io.github.mimimishkin.jni.binding.JniNativeMethod",
                getEnv = "io.github.mimimishkin.jni.binding.GetEnv",
                expressionGetEnv = "%vm:L.%getEnv:M(%jniVersion:L)",
                findClass = "io.github.mimimishkin.jni.binding.FindClass",
                expressionFindClass = "%findClass:M(%className:L)",
                registerNatives = "io.github.mimimishkin.jni.binding.RegisterNatives",
                expressionRegisterNatives = "%registerNatives:M(%class:L, %natives:L, %nativesCount:L)"
            ),
        )
    }
}
