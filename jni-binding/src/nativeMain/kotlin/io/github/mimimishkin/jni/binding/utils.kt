@file:Suppress("NOTHING_TO_INLINE")

package io.github.mimimishkin.jni.binding

import kotlinx.cinterop.*
import kotlin.LazyThreadSafetyMode.SYNCHRONIZED

/**
 * Executes a block of code within the context of a JNI environment of version [version] of the current thread.
 * If the current thread is not attached to the JavaVM or the specified version is not supported, an exception will be
 * thrown.
 */
context(placement: NativePlacement)
public inline fun <T> JavaVM.withEnv(version: JniVersion, block: context(JniEnv) () -> T): T {
    return context(getEnv(version), block)
}

/**
 * Attaches the current thread to the Java VM, executes the provided block of code within the attached environment
 * and then detaches the thread upon completion.
 *
 * @param version the requested JNI version to be used for attaching the current thread.
 * @param name the name of the thread in the null-terminated modified UTF-8. Use [String.modifiedUtf8] to get it, or
 * **if you are sure that your string doesn't have illegal characters** you may use optimized [String.utf8].
 * @param group a global ref of a `ThreadGroup` object.
 */
context(autofreeScope: AutofreeScope)
public inline fun <T> JavaVM.withEnvAttaching(
    version: JniVersion,
    name: CValuesRef<ByteVar>? = null,
    group: JObject? = null,
    block: context(JniEnv) () -> T
): T {
    val env = attachCurrentThread(version, name, group)
    try {
        return context(env, block)
    } finally {
        detachCurrentThread()
    }
}

/**
 * Alias for `memScoped { context(env) { /* your code */ } }` allowing to write less boilerplate code.
 */
public inline fun <R> JniEnv.use(block: context(MemScope, JniEnv) () -> R): R {
    return memScoped {
        block(this, this@use)
    }
}

/**
 * Alias for `memScoped { vm.withEnv(version) { /* your code */ } }` allowing to write less boilerplate code.
 */
public inline fun <R> JavaVM.useEnv(version: JniVersion, block: context(MemScope, JniEnv) () -> R): R {
    return memScoped {
        withEnv(version) {
            block()
        }
    }
}

/**
 * Alias for `memScoped { vm.withEnvAttaching(version) { /* your code */ } }` allowing to write less boilerplate code.
 */
public inline fun <R> JavaVM.useEnvAttaching(version: JniVersion, block: context(MemScope, JniEnv) () -> R): R {
    return memScoped {
        withEnvAttaching(version) {
            block()
        }
    }
}

/**
 * Allows declaring lazy values that requires [JniEnv] to be initialized.
 *
 * To create an instance of [JniLazy] use the [jniLazy] function.
 */
public class JniLazy<out T>(
    initializer: context(JniEnv, MemScope) () -> T
) {
    // hack! we need to store env to use it in lazy initializer
    // this field will never be accessed from different threads, so it's safe
    @PublishedApi internal lateinit var env: JniEnv

    @PublishedApi internal val lazy: Lazy<T> = lazy(SYNCHRONIZED) { env.use { initializer() } }

    /**
     * Gets the lazily initialized value of the current [JniLazy] instance.
     * Once the value was initialized it must not change during the rest of lifetime of this [JniLazy] instance.
     */
    context(env: JniEnv)
    public inline val value: T
        get() {
            this.env = env
            return lazy.value
        }

    /**
     * Whether the value has been already initialized.
     * Once this property has returned `true` it stays `true` for the rest of lifetime of this [JniLazy] instance.
     */
    public inline val isInitialized: Boolean
        get() = lazy.isInitialized()

    /**
     * Returns the lazily initialized value if it has been initialized, or `null` otherwise.
     * Doesn't require a [JniEnv] context.
     */
    public inline val maybeValue: T?
        get() = if (isInitialized) lazy.value else null
}

/**
 * Creates a new instance of the [JniLazy] that uses the specified initialization function [initializer].
 *
 * Uses a lock to ensure that only a single thread can initialize the value, and ensures that initialized value is
 * visible by all threads.
 *
 * If the initialization of a value throws an exception, it will attempt to reinitialize the value at next access.
 */
public inline fun <T> jniLazy(noinline initializer: context(JniEnv, MemScope) () -> T): JniLazy<T> {
    return JniLazy(initializer)
}

/**
 * Converts ordinal local ref into a global weak one.
 *
 * May return `null` if:
 * - the system has run out of memory
 * - the receiver was a weak global reference and has already been garbage collected.
 *
 * @throws OutOfMemoryError if the system runs out of memory.
 *
 * @since JDK/JRE 1.2
 */
context(env: JniEnv)
public fun <T : JRef<O>, O : _jobject> T.localIntoWeakRef(): T? {
    return newWeakGlobalRef()?.also { deleteLocalRef() }
}

/**
 * Converts ordinal local ref into a global one.
 *
 * May return `null` if:
 * - the system has run out of memory
 * - the receiver was a weak global reference and has already been garbage collected.
 *
 * @throws OutOfMemoryError if the system runs out of memory.
 */
context(env: JniEnv)
public fun <T : JRef<O>, O : _jobject> T.localIntoGlobalRef(): T? {
    return newGlobalRef()?.also { deleteLocalRef() }
}