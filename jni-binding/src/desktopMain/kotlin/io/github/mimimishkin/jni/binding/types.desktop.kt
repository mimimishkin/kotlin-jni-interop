@file:Suppress("NOTHING_TO_INLINE")

package io.github.mimimishkin.jni.binding

import kotlinx.cinterop.*

public actual typealias JavaVMOption = jni.JavaVMOption

@Suppress("ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT")
public actual typealias JavaVMInitArgs = jni.JavaVMInitArgs

public actual typealias JavaVMAttachArgs = jni.JavaVMAttachArgs

@Suppress("ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT")
public actual typealias JValue = jni.jvalue

public actual typealias JniNativeMethod = jni.JNINativeMethod

@Suppress("ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT")
public actual typealias Raw_JniInvokeInterface = jni.JNIInvokeInterface_

@Suppress("ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT")
public actual typealias Raw_JniNativeInterface = jni.JNINativeInterface_

internal actual inline var JavaVMAttachArgs.group: COpaquePointer?
    get() = this.group?.reinterpret()
    set(value) { this.group = value?.reinterpret() }

public actual inline var JValue.ref: JObject?
    get() = this.l.wrap()
    set(value) { this.l = value.c?.reinterpret() }