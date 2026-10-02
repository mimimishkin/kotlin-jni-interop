@file:Suppress("NOTHING_TO_INLINE")

package io.github.mimimishkin.jni.binding

import kotlinx.cinterop.*
import kotlinx.cinterop.reinterpret

internal actual inline fun platformGetDefaultJavaVMInitArgs(args: CPointer<JavaVMInitArgs>): Int =
    platform.android.JNI_GetDefaultJavaVMInitArgs(args.reinterpret<JavaVMInitArgs>())

internal actual inline fun platformCreateJavaVM(
    pvm: CPointer<CPointerVar<JavaVM>>?,
    penv: CPointer<CPointerVar<JniEnv>>?,
    args: CPointer<JavaVMInitArgs>?
): Int = platform.android.JNI_CreateJavaVM(
    pvm?.reinterpret(),
    penv?.reinterpret(),
    args?.reinterpret<JavaVMInitArgs>()
)

internal actual inline fun platformGetCreatedJavaVMs(
    vms: CPointer<CPointerVar<JavaVM>>?,
    nVMs: Int,
    nCreated: CPointer<IntVar>?
): Int = platform.android.JNI_GetCreatedJavaVMs(
    vms?.reinterpret(),
    nVMs,
    nCreated?.reinterpret()
)