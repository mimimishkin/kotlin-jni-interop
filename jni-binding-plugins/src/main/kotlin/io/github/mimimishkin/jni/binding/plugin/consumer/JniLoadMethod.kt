package io.github.mimimishkin.jni.binding.plugin.consumer

import io.github.mimimishkin.jni.binding.plugin.MachineDependent

public sealed class JniLoadMethod {
    public object LoadLibrary : JniLoadMethod()

    public class ExtractFromResourceAndLoad(
        resource: MachineDependent<String>,
        extractTo: MachineDependent<String>? = null // null means new directory in system default temp directory
    ) : JniLoadMethod()

    public object DoNothing : JniLoadMethod() // No loading, just type checking.
}