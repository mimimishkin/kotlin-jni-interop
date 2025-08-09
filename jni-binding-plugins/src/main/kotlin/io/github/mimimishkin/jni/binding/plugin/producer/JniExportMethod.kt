package io.github.mimimishkin.jni.binding.plugin.producer

/**
 * Method of linkage `external` Kotlin methods (or `native` Java methods) with their native implementation.
 */
public enum class JniExportMethod {
    /**
     * Functions with appropriate names (like Java_package_name_ClassName_methodName) will be exposed.
     *
     * In this case, if your library contains native methods for several classes, it must be loaded in each of these
     * classes. This is no-op if both classes have the same class loader.
     */
    ExposeFunctions,

    /**
     * `RegisterNative` will be used inside generated `JNI_OnLoad`.
     *
     * In this case, if your library contains native methods for several classes, it can be loaded only one time.
     *
     * On the other hand, you have to provide additional info about JVM methods' signatures to `generateBindings` task.
     *
     * Cannot be used if [JniLibProducerConfig.generateHooks] is `false`.
     */
    BindOnLoad,
}