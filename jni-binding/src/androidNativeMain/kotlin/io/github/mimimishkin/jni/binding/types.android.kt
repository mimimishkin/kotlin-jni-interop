@file:Suppress("NOTHING_TO_INLINE")

package io.github.mimimishkin.jni.binding

import kotlinx.cinterop.*

public actual typealias JavaVMOption = platform.android.JavaVMOption

@Suppress("ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT")
public actual typealias JavaVMInitArgs = platform.android.JavaVMInitArgs

public actual typealias JavaVMAttachArgs = platform.android.JavaVMAttachArgs

@Suppress("ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT")
public actual typealias JValue = platform.android.jvalue

public actual typealias JniNativeMethod = platform.android.JNINativeMethod

@Suppress("ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT")
public actual typealias Raw_JniInvokeInterface = platform.android.JNIInvokeInterface

@Suppress("ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT")
public actual typealias Raw_JniNativeInterface = platform.android.JNINativeInterface

internal actual inline var JavaVMAttachArgs.group: COpaquePointer?
    get() = this.group
    set(value) { this.group = value }

public actual inline var JValue.ref: JObject?
    get() = this.l.wrap()
    set(value) { this.l = value.c }