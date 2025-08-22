package io.github.mimimishkin.jni.binding

import kotlinx.cinterop.*

/**
 * Unloads a Java VM and reclaims its resources.
 *
 * Any thread, whether attached or not, can invoke this function.
 * If the current thread is attached, the VM waits until the current thread is the only non-daemon user-level Java
 * thread.
 * If the current thread is not attached, the VM attaches the current thread and then waits until the current thread is
 * the only non-daemon user-level thread.
 */
public fun JavaVM.destroy() {
    JNI.safeCall {
        pointed!!.DestroyJavaVM!!(ptr)
    }
}

/**
 * Attaches the current thread to a Java VM. Returns a [JniEnv].
 *
 * Trying to attach a thread that is already attached is a no-op.
 *
 * A native thread cannot be attached simultaneously to two Java VMs.
 *
 * When a thread is attached to the VM, the context class loader is the bootstrap loader.
 *
 * @param version the requested JNI version.
 * @param name the name of the thread in the null-terminated modified UTF-8. Use [String.modifiedUtf8] to get it, or
 * **if you are sure that your string doesn't have illegal characters** you may use optimized [kotlinx.cinterop.utf8].
 * @param group global ref of a `ThreadGroup` object.
 */
context(autofreeScope: AutofreeScope)
public fun JavaVM.attachCurrentThread(
    version: JniVersion,
    name: CValuesRef<ByteVar>? = null,
    group: JObject? = null
): JniEnv {
    val env = autofreeScope.allocPointerTo<JniEnv>()

    val args = autofreeScope.alloc<jni.JavaVMAttachArgs> {
        this.version = version
        this.name = name?.getPointer(autofreeScope)
        this.group = group.c
    }

    JNI.safeCall {
        pointed!!.AttachCurrentThread!!(ptr, env.ptr, args.ptr)
    }

    return env.pointed!!
}

/**
 * Same semantics as [attachCurrentThread], but the newly created java.lang.Thread instance is a daemon.
 *
 * If the thread has already been attached via either AttachCurrentThread or AttachCurrentThreadAsDaemon, this routine
 * simply returns JNIEnv of the current thread. In this case neither AttachCurrentThread nor this routine have any
 * effect on the daemon status of the thread.
 *
 * @param version the requested JNI version.
 * @param name the name of the thread in the null-terminated modified UTF-8. Use [String.modifiedUtf8] to get it, or
 * **if you are sure that your string doesn't have illegal characters** you may use optimized [kotlinx.cinterop.utf8].
 * @param group global ref of a `ThreadGroup` object.
 */
context(autofreeScope: AutofreeScope)
public fun JavaVM.attachCurrentThreadAsDaemon(
    version: JniVersion,
    name: CValuesRef<ByteVar>? = null,
    group: JObject? = null
): JniEnv {
    val env = autofreeScope.allocPointerTo<JniEnv>()
    val args = autofreeScope.alloc<jni.JavaVMAttachArgs> {
        this.version = version
        this.name = name?.getPointer(autofreeScope)
        this.group = group.c
    }

    JNI.safeCall {
        pointed!!.AttachCurrentThreadAsDaemon!!(ptr, env.ptr, args.ptr)
    }

    return env.pointed!!
}

/**
 * Detaches the current thread from a Java VM.
 * All Java monitors held by this thread are released. All Java threads waiting for this thread to die are notified.
 *
 * The main thread can be detached from the VM.
 */
public fun JavaVM.detachCurrentThread() {
    JNI.safeCall {
        pointed!!.DetachCurrentThread!!(ptr)
    }
}

/**
 * If the current thread is not attached to the VM or the specified version is not supported, throw an exception.
 * Otherwise, returns [JniEnv].
 *
 * @param version the requested JNI version.
 */
context(placement: NativePlacement)
public fun JavaVM.getEnv(version: JniVersion): JniEnv {
    val env = placement.allocPointerTo<JniEnv>()
    JNI.safeCall {
        pointed!!.GetEnv!!(ptr, env.ptr, version)
    }
    return env.pointed!!
}

/**
 * Loads and initializes a Java VM.
 * The current thread becomes the main thread.
 *
 * Creation of multiple VMs in a single process is not supported.
 *
 * @param args The initialization arguments for the Java VM.
 *
 * @return A pair of [JavaVM] and [JniEnv] of the main thread.
 *
 * @see buildJavaVMInitArgs
 */
context(placement: NativePlacement)
public fun JNI.createJavaVM(args: JavaVMInitArgs): Pair<JavaVM, JniEnv> {
    val vm = placement.allocPointerTo<jni.JavaVMVar>()
    val env = placement.allocPointerTo<jni.JNIEnvVar>()

    safeCall {
        jni.JNI_CreateJavaVM(
            pvm = vm.ptr,
            penv = env.ptr,
            args = args.ptr
        )
    }

    @Suppress("UNCHECKED_CAST")
    return (vm.pointed!! as JavaVM) to (env.pointed!! as JniEnv)
}

/**
 * All Java VMs that have been created in the order they are created.
 *
 * Creation of multiple VMs in a single process is not supported.
 */
context(placement: NativePlacement)
public val JNI.javaVMs: List<JavaVM> get() {
    // test invocation to get VMs count
    val count = placement.alloc<IntVar>()
    safeCall {
        jni.JNI_GetCreatedJavaVMs(null, 0, count.ptr)
    }

    val vms = placement.allocArray<CPointerVar<jni.JavaVMVar>>(count.value)
    safeCall {
        jni.JNI_GetCreatedJavaVMs(vms, count.value, count.ptr)
    }

    @Suppress("UNCHECKED_CAST")
    return List(count.value) { i -> vms[i]!!.pointed as JavaVM }
}