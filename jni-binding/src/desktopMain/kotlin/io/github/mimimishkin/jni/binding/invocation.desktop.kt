@file:Suppress("NOTHING_TO_INLINE")

package io.github.mimimishkin.jni.binding

import kotlinx.cinterop.*
import jni.*

internal actual inline fun platformGetDefaultJavaVMInitArgs(args: CPointer<JavaVMInitArgs>): Int =
    JNI_GetDefaultJavaVMInitArgs(args)

internal actual inline fun platformCreateJavaVM(
    pvm: CPointer<CPointerVar<JavaVM>>?,
    penv: CPointer<CPointerVar<JniEnv>>?,
    args: CPointer<JavaVMInitArgs>?
): Int = JNI_CreateJavaVM(
    pvm = pvm?.reinterpret(),
    penv = penv?.reinterpret(),
    args = args
)

internal actual inline fun platformGetCreatedJavaVMs(
    vms: CPointer<CPointerVar<JavaVM>>?,
    nVMs: Int,
    nCreated: CPointer<IntVar>?
): Int = JNI_GetCreatedJavaVMs(vms?.reinterpret(), nVMs, nCreated)