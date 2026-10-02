package io.github.mimimishkin.jni.binding

/**
 * `JNI_EDETACHED`: the current thread is not attached to the Java VM.
 *
 * @see JavaVM.attachCurrentThread
 * @see JavaVM.getEnv
 */
public class JniThreadDetachedException : IllegalStateException("Thread detached from the VM.")

/**
 * `JNI_EVERSION`: the requested JNI version is not supported by the Java VM.
 *
 * @see JniVersion
 */
public class JniVersionException : IllegalArgumentException("JNI version error.")

/**
 * `JNI_ENOMEM`: the Java VM could not allocate the memory the call needs.
 */
public class JniOutOfMemoryException : IllegalStateException("Not enough memory.")

/**
 * `JNI_EEXIST`: a Java VM has already been created in this process, and JNI does not support more than one.
 *
 * @see JNI.createJavaVM
 */
public class JniVmAlreadyExistsException : IllegalStateException("VM already created.")
