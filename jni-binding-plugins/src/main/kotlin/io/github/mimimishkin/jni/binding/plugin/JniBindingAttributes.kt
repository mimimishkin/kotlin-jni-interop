package io.github.mimimishkin.jni.binding.plugin

import org.gradle.api.attributes.Attribute

/**
 * Gradle variant attributes and artifact types used to publish and resolve JNI binding artifacts.
 */
public object JniBindingAttributes {
    /**
     * Native library base name (e.g. `"native"`).
     */
    public val LIBRARY_NAME: Attribute<String> = Attribute.of(
        "io.github.mimimishkin.jni.binding.libraryName",
        String::class.java,
    )

    /**
     * Kotlin/Native target name (e.g. `"mingwX64"`).
     */
    public val KONAN_TARGET: Attribute<String> = Attribute.of(
        "io.github.mimimishkin.jni.binding.konanTarget",
        String::class.java,
    )

    public const val ARTIFACT_TYPE_JNI_LIBRARY: String = "jniLibrary"
    public const val ARTIFACT_TYPE_ACTUALS_INFO: String = "actualsInfo"
    public const val ARTIFACT_TYPE_BINDING_INFO: String = "bindingInfo"
}
