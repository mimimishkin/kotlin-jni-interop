package io.github.mimimishkin.jni.binding

import kotlinx.cinterop.*
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.allocPointerTo
import kotlinx.cinterop.invoke
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlin.String

/**
 * The version of the native method interface.
 */
context(env: JniEnv)
public val jniVersion: JniVersion get() {
    return env.GetVersion!!(env.ptr)
}

/**
 * In JDK release 1.1, this function loads a locally defined class. It searches the directories and zip files specified
 * by the CLASSPATH environment variable for the class with the specified name.
 *
 * Since JDK 1.2, the Java security model allows non-system classes to load and call native methods. [findClass] locates
 * the class loader associated with the current native method; that is, the class loader of the class that declared the
 * native method. If the native method belongs to a system class, no class loader will be involved.
 * Otherwise, the proper class loader will be invoked to load, link and initialize the named class.
 *
 * Since JDK 1.2, when [findClass] is called through the Invocation Interface, there is no current native method or its
 * associated class loader. In that case, the result of `ClassLoader.getSystemClassLoader` is used. This is the class
 * loader the virtual machine creates for applications and is able to locate classes listed in the
 * `java.class.path` property.
 *
 * If [findClass] is called from a library lifecycle function hook, the class loader is determined as follows:
 * for JNI_OnLoad and JNI_OnLoad_L the class loader of the class that is loading the native library is used for
 * JNI_OnUnload and JNI_OnUnload_L the class loader returned by `ClassLoader.getSystemClassLoader` is used (as the class
 * loader used at on-load time may no longer exist).
 * The name argument is a fully qualified class name or an array type signature.
 *
 * For example, the fully qualified class name for the `java.lang.String` class is `"java/lang/String"`.
 * The array type signature of the array class `java.lang.Object[]` is `"[Ljava/lang/Object;"`.
 *
 * See also: [JNI_OnLoad](https://docs.oracle.com/javase/8/docs/technotes/guides/jni/spec/invocation.html#JNJI_OnLoad),
 * [JNI_OnUnload](https://docs.oracle.com/javase/8/docs/technotes/guides/jni/spec/invocation.html#JNI_OnUnload)
 *
 * @param name name of the class in the null-terminated modified UTF-8. Use [String.modifiedUtf8] to get it, or **if you
 * are sure that your string doesn't have illegal characters** you may use optimized [String.utf8].
 *
 * @return a class object from a fully qualified name, or `null` if the class cannot be found.
 *
 * @throws ClassFormatError if the class data does not specify a valid class.
 * @throws ClassCircularityError if a class or interface is its own superclass or superinterface.
 * @throws NoClassDefFoundError if no definition for a requested class or interface can be found.
 * @throws OutOfMemoryError if the system runs out of memory.
 */
context(env: JniEnv, autofreeScope: AutofreeScope)
public fun findClass(name: CValuesRef<ByteVar>): JClass? {
    return env.FindClass!!(env.ptr, name.getPointer(autofreeScope)).wrap()
}

/**
 * Converts a `java.lang.reflect.Method` or `java.lang.reflect.Constructor` object to a method ID.
 *
 * @return A JNI method ID that corresponds to the given Java reflection method, or `null` if the operation fails.
 *
 * @since JDK/JRE 1.2
 */
public fun JniEnv.fromReflectedMethod(method: JObject): JMethodID? {
    return this.FromReflectedMethod!!(ptr, method.c)?.wrap()
}

/**
 * Converts a `java.lang.reflect.Field` to a field ID.
 *
 * @return A JNI field ID that corresponds to the given Java reflection field, or `null` if the operation fails.
 *
 * @since JDK/JRE 1.2
 */
public fun JniEnv.fromReflectedField(field: JObject): JFieldID? {
    return this.FromReflectedField!!(ptr, field.c)?.wrap()
}

/**
 * Converts a method ID derived from [cls] to a `java.lang.reflect.Method` or `java.lang.reflect.Constructor` object.
 * [isStatic] must be set to `true` if the method ID refers to a static field.
 *
 * @return Returns an instance of the `java.lang.reflect.Method` or `java.lang.reflect.Constructor` which corresponds to
 * the given methodId, or `null` if the operation fails.
 *
 * @throws OutOfMemoryError if fails.
 *
 * @since JDK/JRE 1.2
 *
 * @see fromReflectedMethod
 */
context(env: JniEnv)
public fun JMethodID.toReflectedMethod(cls: JClass, isStatic: Boolean): JObject? {
    return env.ToReflectedMethod!!(env.ptr, cls.c, c, isStatic.toJBoolean()).wrap()
}

/**
 * Converts a field ID derived from [cls] to a `java.lang.reflect.Field` object.
 * `isStatic` must be set to `true` if fieldID refers to a static field.
 *
 * @return an instance of the java.lang.reflect.Field which corresponds to the given fieldID, or `null` if the operation fails.
 *
 * @throws OutOfMemoryError if fails.
 *
 * @since JDK/JRE 1.2
 *
 * @see fromReflectedField
 */
context(env: JniEnv)
public fun JFieldID.toReflectedField(cls: JClass, isStatic: Boolean): JObject? {
    return env.ToReflectedField!!(env.ptr, cls.c, c, isStatic.toJBoolean()).wrap()
}

/**
 * If this class represents any class other than the class Object, then it is the object that represents the superclass
 * of the class specified by this class.
 *
 * If this class specifies the class `Object`, or represents an interface, is `null`.
 *
 * @return the superclass of the class represented by this class, or `null`.
 */
context(env: JniEnv)
public val JClass.superclass: JClass? get() {
    return env.GetSuperclass!!(env.ptr, c).wrap()
}

/**
 * Determines whether this class can be safely cast to [other].
 *
 * Returns `true` if either of the following is true:
 * - The first and the second class arguments refer to the same Java class.
 * - The first class is a subclass of the second class.
 * - The first class has the second class as one of its interfaces.
 */
context(env: JniEnv)
public infix fun JClass.isAssignableFrom(other: JClass): Boolean {
    return env.IsAssignableFrom!!(env.ptr, c, other.c).toKBoolean()
}

/**
 * Causes a `java.lang.Throwable` object to be thrown.
 */
context(env: JniEnv)
public fun throwEx(throwable: JThrowable) {
    JNI.safeCall {
        env.Throw!!(env.ptr, throwable.c)
    }
}

/**
 * Constructs an exception object from the specified [clazz] with the [message] and causes that exception to be thrown.
 *
 * @param clazz a subclass of `java.lang.Throwable`
 * @param message the message used to construct the `java.lang.Throwable` object in the null-terminated modified UTF-8.
 * Use [String.modifiedUtf8] to get it, or **if you are sure that your string doesn't have illegal characters** you may
 * use optimized [String.utf8].
 */
context(env: JniEnv, autofreeScope: AutofreeScope)
public fun throwNew(clazz: JClass, message: CValuesRef<ByteVar>?) {
    JNI.safeCall {
        env.ThrowNew!!(env.ptr, clazz.c, message?.getPointer(autofreeScope))
    }
}

/**
 * Exception object currently in the process of being thrown, or `null` if there is no one.
 *
 * There are two ways to handle an exception in native code:
 *  - The native method can choose to return immediately, causing the exception to be thrown in the Java code that
 *    initiated the native method call.
 *  - The native code can clear the exception by calling [clearException], and then execute its own exception-handling
 *    code.
 *
 *
 * After an exception has been raised, the native code must first clear the exception before making other JNI calls.
 * When there is an exception being thrown, the JNI functions that are safe to call are:
 *  - [pendingException]
 *  - [printStackTrace]
 *  - [clearException]
 *  - [isExceptionThrown]
 *  - [JString.releaseChars]
 *  - [JString.releaseUTFChars]
 *  - [JString.releaseCharsCritical]
 *  - `J<Type>Array.releaseElements`
 *  - [JPrimitiveArray.releaseElementsCritical]
 *  - [JObject.deleteLocalRef]
 *  - [JObject.deleteGlobalRef]
 *  - [JWeak.deleteWeakGlobalRef]
 *  - [monitorExit]
 *  - [pushLocalFrame]
 *  - [popLocalFrame]
 *  - [JavaVM.detachCurrentThread]
 */
context(env: JniEnv)
public val pendingException: JThrowable? get() {
    return env.ExceptionOccurred!!(env.ptr).wrap()
}

/**
 * Convenient way to write
 * ```
 * pendingException?.let { throwable ->
 *     // ...
 * }
 * ```
 *
 * There are two ways to handle an exception in native code:
 *  - The native method can choose to return immediately, causing the exception to be thrown in the Java code that
 *    initiated the native method call.
 *  - The native code can clear the exception by calling [clearException], and then execute its own exception-handling
 *    code.
 *
 *
 * After an exception has been raised, the native code must first clear the exception before making other JNI calls.
 * When there is an exception being thrown, the JNI functions that are safe to call are:
 *  - [pendingException]
 *  - [printStackTrace]
 *  - [clearException]
 *  - [isExceptionThrown]
 *  - [JString.releaseChars]
 *  - [JString.releaseUTFChars]
 *  - [JString.releaseCharsCritical]
 *  - `J<Type>Array.releaseElements`
 *  - [JPrimitiveArray.releaseElementsCritical]
 *  - [JObject.deleteLocalRef]
 *  - [JObject.deleteGlobalRef]
 *  - [JWeak.deleteWeakGlobalRef]
 *  - [monitorExit]
 *  - [pushLocalFrame]
 *  - [popLocalFrame]
 *  - [JavaVM.detachCurrentThread]
 */
context(env: JniEnv)
public inline fun handleJvmException(block: (JThrowable) -> Unit) {
    pendingException?.let { throwable ->
        block(throwable)
    }
}

/**
 * Prints an exception and a backtrace of the stack to a system error-reporting channel, such as stderr.
 *
 * The pending exception is cleared as a side effect of calling this function.
 * This is a convenience routine provided for debugging.
 */
context(env: JniEnv)
public fun printStackTrace() {
    env.ExceptionDescribe!!(env.ptr)
}

/**
 * Clears any exception that is currently being thrown.
 * If no exception is currently being thrown, this routine has no effect.
 */
context(env: JniEnv)
public fun clearException() {
    env.ExceptionClear!!(env.ptr)
}

/**
 * Raises a fatal error and does not expect the VM to recover.
 *
 * This function does not return.
 *
 * @param message an error message in the null-terminated modified UTF-8. Use [String.modifiedUtf8] to get it, or **if
 * you are sure that your string doesn't have illegal characters** you may use optimized [String.utf8].
 */
context(env: JniEnv, autofreeScope: AutofreeScope)
public fun fatalError(message: CValuesRef<ByteVar>?): Nothing {
    env.FatalError!!(env.ptr, message?.getPointer(autofreeScope))
    throw Error() // this won't be invoked
}

/**
 * A convenient way to check for pending exceptions without creating a local reference to the exception object.
 *
 * `true` when there is a pending exception, `false` otherwise.
 */
context(env: JniEnv)
public val isExceptionThrown: Boolean get() {
    return env.ExceptionCheck!!(env.ptr).toKBoolean()
}

/**
 * Deletes the local reference pointed to by the receiver.
 *
 * Note: JDK/JRE 1.1 provides the DeleteLocalRef function above so that programmers can manually delete local
 * references. For example, if native code iterates through a potentially large array of objects and uses one element
 * in each iteration, it is a good practice to delete the local reference to the no-longer-used array element before a
 * new local reference is created in the next iteration.
 *
 * As of JDK/JRE 1.2 an additional set of functions are provided: [ensureLocalCapacity], [pushLocalFrame],
 * [popLocalFrame] and [newLocalRef].
 */
context(env: JniEnv)
public fun JObject.deleteLocalRef() {
    env.DeleteLocalRef!!(env.ptr, c)
}

/**
 * Ensures that at least a given number of local references can be created in the current thread.
 *
 * Before it enters a native method, the VM automatically ensures that at least 16 local references can be created.
 *
 * For backward compatibility, the VM allocates local references beyond the ensured capacity. (As a debugging support,
 * the VM may give the user warnings that too many local references are being created. In the JDK, the programmer can
 * supply the `-verbose:jni` command line option to turn on these messages.)
 * The VM calls [fatalError] if no more local references can be created beyond the ensured capacity.
 *
 * Some Java Virtual Machine implementations may choose to limit the maximum capacity. The HotSpot JVM implementation,
 * for example, uses the `-XX:+MaxJNILocalCapacity` flag (default: 65 536).
 *
 * @param capacity the minimum number of required local references. Must be >= 0.
 *
 * @throws OutOfMemoryError if fails.
 *
 * @since JDK/JRE 1.2
 */
context(env: JniEnv)
public fun ensureLocalCapacity(capacity: Int) {
    JNI.safeCall {
        env.EnsureLocalCapacity!!(env.ptr, capacity)
    }
}

/**
 * Creates a new local reference frame, in which at least a given number of local references can be created.
 *
 * Note that local references already created in previous local frames are still valid in the current local frame.
 *
 * As with [ensureLocalCapacity], some Java Virtual Machine implementations may choose to limit the maximum capacity,
 * which may cause the function to throw an exception.
 *
 * @param capacity the minimum number of required local references. Must be > 0.
 *
 * @throws OutOfMemoryError if fails.
 *
 * @since JDK/JRE 1.2
 */
context(env: JniEnv)
public fun pushLocalFrame(capacity: Int) {
    JNI.safeCall {
        env.PushLocalFrame!!(env.ptr, capacity)
    }
}

/**
 * Pops off the current local reference frame, frees all the local references and returns a local reference in the
 * previous local reference frame for the given [result] object.
 *
 * Pass `null` as [result] if you do not need to return a reference to the previous frame.
 *
 * @param result an object to be passed to the previous local reference frame, may be `null`.
 *
 * @return a local reference in the previous local reference frame for the given [result] object, or `null` if the given
 * [result] object was `null`.
 *
 * @since JDK/JRE 1.2
 */
context(env: JniEnv)
public fun <T : JRef<O>, O : _jobject> popLocalFrame(result: T? = null): T? {
    return env.PopLocalFrame!!(env.ptr, result.c).wrap()
}

/**
 * Pops off the current local reference frame and frees all the local references.
 *
 * @since JDK/JRE 1.2
 */
@Suppress("NOTHING_TO_INLINE")
context(env: JniEnv)
public inline fun popLocalFrame() {
    popLocalFrame<JObject, _>(null)
}

/**
 * Executes the [block] in a new local reference frame, in which at least a given number of local references can be
 * created. Then pops the frame, freeing all the local references except the result of the [block] and return it.
 *
 * Note that local references already created in previous local frames are still valid in the current local frame.
 *
 * If you need to use one of the local references created in the [block] outside of the [block], you must return this
 * refence from the lambda. It will be accessible as a result of [fromRefFrame].
 *
 * Some Java Virtual Machine implementations may choose to limit the maximum capacity, which may cause the function to
 * throw an exception.
 *
 * @param capacity the minimum number of required local references. Must be > 0.
 * @param block the block to execute in the local reference frame.
 *
 * @since JDK/JRE 1.2
 */
context(env: JniEnv)
public inline fun <T : JRef<O>, O : _jobject> fromRefFrame(capacity: Int, block: () -> T?): T? {
    pushLocalFrame(capacity)
    var result: T? = null
    try {
        result = block()
    } finally {
        result = popLocalFrame(result)
    }
    return result
}

/**
 * Executes the [block] in a new local reference frame, in which at least a given number of local references can be
 * created. Then pops the frame, freeing all the local references and returns the result of [block].
 *
 * Note that local references already created in previous local frames are still valid in the current local frame.
 *
 * Some Java Virtual Machine implementations may choose to limit the maximum capacity, which may cause the function to
 * throw an exception.
 *
 * @param capacity the minimum number of required local references. Must be > 0.
 * @param block the block to execute in the local reference frame.
 * @return the result of [block]. You should not return any local reference as it will be invalid at this point.
 * To return local refence, use [fromRefFrame].
 *
 * @since JDK/JRE 1.2
 */
context(env: JniEnv)
public inline fun <T> refFrame(capacity: Int, block: () -> T): T {
    pushLocalFrame(capacity)
    try {
        return block()
    } finally {
        popLocalFrame()
    }
}

/**
 * Creates and returns a new local reference that refers to the same object.
 * The receiver may be a global or a local reference.
 *
 * May return `null` if:
 * - the system has run out of memory
 * - the receiver was a weak global reference and has already been garbage collected.
 *
 * @throws OutOfMemoryError if the system runs out of memory.
 */
context(env: JniEnv)
public fun <T : JRef<O>, O : _jobject> T.newLocalRef(): T? {
    return env.NewLocalRef!!(env.ptr, c).wrap()
}

/**
 * Creates and returns a new global reference to the object referred to by the receiver.
 * The receiver may be a global or local reference.
 *
 * Global references must be explicitly disposed of by calling [deleteGlobalRef].
 *
 * May return `null` if:
 * - the system has run out of memory
 * - the receiver was a weak global reference and has already been garbage collected.
 *
 * @throws OutOfMemoryError if the system runs out of memory.
 */
context(env: JniEnv)
public fun <T : JRef<O>, O : _jobject> T.newGlobalRef(): T? {
    return env.NewGlobalRef!!(env.ptr, c).wrap()
}

/**
 * Creates a new weak global reference.
 * The weak global reference will not prevent garbage collection of the given object.
 *
 * [isSame] may be used to test if the object referred to by the reference has been freed.
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
public fun <T : JRef<O>, O : _jobject> T.newWeakGlobalRef(): T? {
    return env.NewWeakGlobalRef!!(env.ptr, c).wrap()
}

/**
 * Deletes the global reference pointed to by the receiver.
 */
context(env: JniEnv)
public fun JObject.deleteGlobalRef() {
    env.DeleteGlobalRef!!(env.ptr, c)
}

/**
 * Delete the VM resources needed for the given weak global reference.
 *
 * @since JDK/JRE 1.2
 */
context(env: JniEnv)
public fun JWeak.deleteWeakGlobalRef() {
    env.DeleteWeakGlobalRef!!(env.ptr, c)
}

/**
 * Allocates a new Java object without invoking any of the constructors for the object.
 *
 * Note: The Java Language Specification, "Implementing Finalization" (JLS §12.6.1) states: "An object o is not
 * finalizable until its constructor has invoked the constructor for Object on o and that invocation has completed
 * successfully". Since [allocObject] does not invoke a constructor, objects created with this function are not eligible
 * for finalization.
 *
 * This class argument must not refer to an array class.
 *
 * @return a Java object, or `null` if the object cannot be constructed.
 *
 * @throws InstantiationException if the class is an interface or an abstract class.
 * @throws OutOfMemoryError if the system runs out of memory.
 */
context(env: JniEnv)
public fun JClass.allocObject(): JObject? {
    return env.AllocObject!!(env.ptr, c).wrap()
}

/**
 * Constructs a new Java object.
 * The [methodId] indicates which constructor method to invoke.
 * This ID must be obtained by calling [methodId] with `"<init>"` as the method name and void (`V`) as the return type.
 *
 * The receiver must not refer to an array class.
 *
 * @return a Java object, or `null` if the object cannot be constructed.
 *
 * @throws InstantiationException if the class is an interface or an abstract class.
 * @throws OutOfMemoryError if the system runs out of memory.
 * @throws other Any exceptions thrown by the constructor.
 */
context(env: JniEnv)
public fun JClass.newObject(methodId: JMethodID, args: JArguments): JObject? {
    return env.NewObjectA!!(env.ptr, c, methodId.c, args).wrap()
}

/**
 * The class of the object.
 */
context(env: JniEnv)
public val JObject.javaClass: JClass get() {
    return env.GetObjectClass!!(env.ptr, c).wrap()!!
}

/**
 * Returns the type of the object referred to by the obj argument.
 *
 * @since JDK/JRE 1.6
 */
context(env: JniEnv)
public val JObject?.refType: JObjectRefType get() {
    val ordinal = env.GetObjectRefType!!(env.ptr, c)
    return JObjectRefType.entries[ordinal.toInt()]
}

/**
 * Tests whether an object is an instance of a class.
 *
 * @return `true` if the receiver can be cast to [clazz], false otherwise. A `null` can be cast to any class.
 */
context(env: JniEnv)
public infix fun JObject?.instanceOf(clazz: JClass): Boolean {
    return env.IsInstanceOf!!(env.ptr, c, clazz.c).toKBoolean()
}

/**
 * Tests whether two references point to the same Java object.
 *
 * @return `true` if the receiver and [other] refer to the same Java object, or both are `null`, false otherwise.
 */
context(env: JniEnv)
public infix fun JObject?.isSame(other: JObject?): Boolean {
    return env.IsSameObject!!(env.ptr, c, other.c).toKBoolean()
}

/**
 * Returns the method ID for an instance (nonstatic) method of a class or interface.
 * The method may be defined in one of the [this@GetMethodID]’s supertypes and inherited by [this@GetMethodID].
 * The method is determined by its name and signature.
 *
 * [methodId] causes an uninitialized class to be initialized.
 *
 * To get the method ID of a constructor, supply `"<init>"` as the method name and void (`V`) as the return type.
 *
 * @param this@GetMethodID a Java class object.
 * @param name the method name in the null-terminated modified UTF-8.
 * @param sig the method signaturein in the null-terminated modified UTF-8. Use [String.modifiedUtf8] to get it, or **if
 * you are sure that your string doesn't have illegal characters** you may use optimized [String.utf8].
 *
 * @return a method ID, or `null` if the specified method cannot be found.
 *
 * @throws NoSuchMethodError if the specified method cannot be found.
 * @throws ExceptionInInitializerError if the class initializer fails due to an exception.
 * @throws OutOfMemoryError if the system runs out of memory.
 */
context(env: JniEnv, autofreeScope: AutofreeScope)
public fun JClass.methodId(name: CValuesRef<ByteVar>, sig: CValuesRef<ByteVar>): JMethodID? {
    return env.GetMethodID!!(env.ptr, c, name.getPointer(autofreeScope), sig.getPointer(autofreeScope))?.wrap()
}

/**
 * Invokes an instance (nonstatic) method on a Java object, according to the specified method ID.
 * The [methodId] argument must be obtained by calling [methodId].
 *
 * When used to call private methods and constructors, the method ID must be derived from the real class of the
 * receiver, not from one of its superclasses.
 *
 * You should use this function only if the Java method you are calling returns `Object` values.
 *
 * @return the result of calling the Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public fun JObject.callObjectMethod(methodId: JMethodID, args: JArguments): JObject? {
    return env.CallObjectMethodA!!(env.ptr, c, methodId.c, args).wrap()
}

/**
 * Invokes an instance (nonstatic) method on a Java object, according to the specified method ID.
 * The [methodId] argument must be obtained by calling [methodId].
 *
 * When used to call private methods and constructors, the method ID must be derived from the real class of the
 * receiver, not from one of its superclasses.
 *
 * You should use this function only if the Java method you are calling returns `boolean` values.
 *
 * @return the result of calling the Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public fun JObject.callBooleanMethod(methodId: JMethodID, args: JArguments): Boolean {
    return env.CallBooleanMethodA!!(env.ptr, c, methodId.c, args).toKBoolean()
}

/**
 * Invokes an instance (nonstatic) method on a Java object, according to the specified method ID.
 * The [methodId] argument must be obtained by calling [methodId].
 *
 * When used to call private methods and constructors, the method ID must be derived from the real class of the
 * receiver, not from one of its superclasses.
 *
 * You should use this function only if the Java method you are calling returns `byte` values.
 *
 * @return the result of calling the Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public fun JObject.callByteMethod(methodId: JMethodID, args: JArguments): Byte {
    return env.CallByteMethodA!!(env.ptr, c, methodId.c, args)
}

/**
 * Invokes an instance (nonstatic) method on a Java object, according to the specified method ID.
 * The [methodId] argument must be obtained by calling [methodId].
 *
 * When used to call private methods and constructors, the method ID must be derived from the real class of the
 * receiver, not from one of its superclasses.
 *
 * You should use this function only if the Java method you are calling returns `char` values.
 *
 * @return the result of calling the Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public fun JObject.callCharMethod(methodId: JMethodID, args: JArguments): Char {
    return env.CallCharMethodA!!(env.ptr, c, methodId.c, args).toKChar()
}

/**
 * Invokes an instance (nonstatic) method on a Java object, according to the specified method ID.
 * The [methodId] argument must be obtained by calling [methodId].
 *
 * When used to call private methods and constructors, the method ID must be derived from the real class of the
 * receiver, not from one of its superclasses.
 *
 * You should use this function only if the Java method you are calling returns `short` values.
 *
 * @return the result of calling the Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public fun JObject.callShortMethod(methodId: JMethodID, args: JArguments): Short {
    return env.CallShortMethodA!!(env.ptr, c, methodId.c, args)
}

/**
 * Invokes an instance (nonstatic) method on a Java object, according to the specified method ID.
 * The [methodId] argument must be obtained by calling [methodId].
 *
 * When used to call private methods and constructors, the method ID must be derived from the real class of the
 * receiver, not from one of its superclasses.
 *
 * You should use this function only if the Java method you are calling returns `int` values.
 *
 * @return the result of calling the Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public fun JObject.callIntMethod(methodId: JMethodID, args: JArguments): Int {
    return env.CallIntMethodA!!(env.ptr, c, methodId.c, args)
}

/**
 * Invokes an instance (nonstatic) method on a Java object, according to the specified method ID.
 * The [methodId] argument must be obtained by calling [methodId].
 *
 * When used to call private methods and constructors, the method ID must be derived from the real class of the
 * receiver, not from one of its superclasses.
 *
 * You should use this function only if the Java method you are calling returns `long` values.
 *
 * @return the result of calling the Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public fun JObject.callLongMethod(methodId: JMethodID, args: JArguments): Long {
    return env.CallLongMethodA!!(env.ptr, c, methodId.c, args)
}

/**
 * Invokes an instance (nonstatic) method on a Java object, according to the specified method ID.
 * The [methodId] argument must be obtained by calling [methodId].
 *
 * When used to call private methods and constructors, the method ID must be derived from the real class of the
 * receiver, not from one of its superclasses.
 *
 * You should use this function only if the Java method you are calling returns `float` values.
 *
 * @return the result of calling the Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public fun JObject.callFloatMethod(methodId: JMethodID, args: JArguments): Float {
    return env.CallFloatMethodA!!(env.ptr, c, methodId.c, args)
}

/**
 * Invokes an instance (nonstatic) method on a Java object, according to the specified method ID.
 * The [methodId] argument must be obtained by calling [methodId].
 *
 * When used to call private methods and constructors, the method ID must be derived from the real class of the
 * receiver, not from one of its superclasses.
 *
 * You should use this function only if the Java method you are calling returns `double` values.
 *
 * @return the result of calling the Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public fun JObject.callDoubleMethod(methodId: JMethodID, args: JArguments): Double {
    return env.CallDoubleMethodA!!(env.ptr, c, methodId.c, args)
}

/**
 * Invokes an instance (nonstatic) method on a Java object, according to the specified method ID.
 * The [methodId] argument must be obtained by calling [methodId].
 *
 * When used to call private methods and constructors, the method ID must be derived from the real class of the
 * receiver, not from one of its superclasses.
 *
 * You should use this function only if the Java method you are calling doesn't return values.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public fun JObject.callVoidMethod(methodId: JMethodID, args: JArguments) {
    return env.CallVoidMethodA!!(env.ptr, c, methodId.c, args)
}

/**
 * Unifies usage of `call<type>Method` methods.
 *
 * If type [R] is primitive type, it cannot be nullable, otherwise it must be nullable.
 */
context(env: JniEnv)
public inline fun <reified R> JObject.callMethod(methodId: JMethodID, args: JArguments): R {
    return when (R::class) {
        CPointer::class -> callObjectMethod(methodId, args) as R
        Boolean::class -> callBooleanMethod(methodId, args) as R
        Byte::class -> callByteMethod(methodId, args) as R
        Char::class -> callCharMethod(methodId, args) as R
        Short::class -> callShortMethod(methodId, args) as R
        Int::class -> callIntMethod(methodId, args) as R
        Long::class -> callLongMethod(methodId, args) as R
        Float::class -> callFloatMethod(methodId, args) as R
        Double::class -> callDoubleMethod(methodId, args) as R
        Unit::class -> callVoidMethod(methodId, args) as R
        else -> throw IllegalArgumentException("Unsupported return type: ${R::class.qualifiedName}")
    }
}

/**
 * Invokes an instance (nonstatic) method on a Java object, according to the specified class and method ID.
 * The [methodId] argument must be obtained by calling [methodId] on the class clazz.
 *
 * Unlike [callObjectMethod] which invokes the method based on the class or interface of the object, this method invokes
 * the method based on the class, designated by the [clazz] parameter, from which the method ID is obtained.
 *
 * The method ID must be obtained from the real class of the object or from one of its supertypes.
 *
 * `callNonvirtual<type>Method` routines are the mechanism for invoking "default interface methods" introduced in Java
 * 8.
 *
 * You should use this function only if the Java method you are calling returns `Object` values.
 *
 * @return the result of calling the Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public fun JObject.callNonvirtualObjectMethod(clazz: JClass, methodId: JMethodID, args: JArguments): JObject? {
    return env.CallNonvirtualObjectMethodA!!(env.ptr, clazz.c, c, methodId.c, args).wrap()
}

/**
 * Invokes an instance (nonstatic) method on a Java object, according to the specified class and method ID.
 * The [methodId] argument must be obtained by calling [methodId] on the class clazz.
 *
 * Unlike [callBooleanMethod] which invokes the method based on the class or interface of the object, this method
 * invokes the method based on the class, designated by the [clazz] parameter, from which the method ID is obtained.
 *
 * The method ID must be obtained from the real class of the object or from one of its supertypes.
 *
 * `callNonvirtual<type>Method` routines are the mechanism for invoking "default interface methods" introduced in Java
 * 8.
 *
 * You should use this function only if the Java method you are calling returns `boolean` values.
 *
 * @return the result of calling the Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public fun JObject.callNonvirtualBooleanMethod(clazz: JClass, methodId: JMethodID, args: JArguments): Boolean {
    return env.CallNonvirtualBooleanMethodA!!(env.ptr, clazz.c, c, methodId.c, args).toKBoolean()
}

/**
 * Invokes an instance (nonstatic) method on a Java object, according to the specified class and method ID.
 * The [methodId] argument must be obtained by calling [methodId] on the class clazz.
 *
 * Unlike [callByteMethod] which invokes the method based on the class or interface of the object, this method invokes
 * the method based on the class, designated by the [clazz] parameter, from which the method ID is obtained.
 *
 * The method ID must be obtained from the real class of the object or from one of its supertypes.
 *
 * `callNonvirtual<type>Method` routines are the mechanism for invoking "default interface methods" introduced in Java
 * 8.
 *
 * You should use this function only if the Java method you are calling returns `byte` values.
 *
 * @return the result of calling the Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public fun JObject.callNonvirtualByteMethod(clazz: JClass, methodId: JMethodID, args: JArguments): Byte {
    return env.CallNonvirtualByteMethodA!!(env.ptr, clazz.c, c, methodId.c, args)
}

/**
 * Invokes an instance (nonstatic) method on a Java object, according to the specified class and method ID.
 * The [methodId] argument must be obtained by calling [methodId] on the class clazz.
 *
 * Unlike [callCharMethod] which invokes the method based on the class or interface of the object, this method invokes
 * the method based on the class, designated by the [clazz] parameter, from which the method ID is obtained.
 *
 * The method ID must be obtained from the real class of the object or from one of its supertypes.
 *
 * `callNonvirtual<type>Method` routines are the mechanism for invoking "default interface methods" introduced in Java 8.
 *
 * You should use this function only if the Java method you are calling returns `char` values.
 *
 * @return the result of calling the Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public fun JObject.callNonvirtualCharMethod(clazz: JClass, methodId: JMethodID, args: JArguments): Char {
    return env.CallNonvirtualCharMethodA!!(env.ptr, clazz.c, c, methodId.c, args).toKChar()
}

/**
 * Invokes an instance (nonstatic) method on a Java object, according to the specified class and method ID.
 * The [methodId] argument must be obtained by calling [methodId] on the class clazz.
 *
 * Unlike [callShortMethod] which invokes the method based on the class or interface of the object, this method invokes
 * the method based on the class, designated by the [clazz] parameter, from which the method ID is obtained.
 *
 * The method ID must be obtained from the real class of the object or from one of its supertypes.
 *
 * `callNonvirtual<type>Method` routines are the mechanism for invoking "default interface methods" introduced in Java 8.
 *
 * You should use this function only if the Java method you are calling returns `short` values.
 *
 * @return the result of calling the Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public fun JObject.callNonvirtualShortMethod(clazz: JClass, methodId: JMethodID, args: JArguments): Short {
    return env.CallNonvirtualShortMethodA!!(env.ptr, clazz.c, c, methodId.c, args)
}

/**
 * Invokes an instance (nonstatic) method on a Java object, according to the specified class and method ID.
 * The [methodId] argument must be obtained by calling [methodId] on the class clazz.
 *
 * Unlike [callIntMethod] which invokes the method based on the class or interface of the object, this method invokes
 * the method based on the class, designated by the [clazz] parameter, from which the method ID is obtained.
 *
 * The method ID must be obtained from the real class of the object or from one of its supertypes.
 *
 * `callNonvirtual<type>Method` routines are the mechanism for invoking "default interface methods" introduced in Java 8.
 *
 * You should use this function only if the Java method you are calling returns `int` values.
 *
 * @return the result of calling the Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public fun JObject.callNonvirtualIntMethod(clazz: JClass, methodId: JMethodID, args: JArguments): Int {
    return env.CallNonvirtualIntMethodA!!(env.ptr, clazz.c, c, methodId.c, args)
}

/**
 * Invokes an instance (nonstatic) method on a Java object, according to the specified class and method ID.
 * The [methodId] argument must be obtained by calling [methodId] on the class clazz.
 *
 * Unlike [callLongMethod] which invokes the method based on the class or interface of the object, this method invokes
 * the method based on the class, designated by the [clazz] parameter, from which the method ID is obtained.
 *
 * The method ID must be obtained from the real class of the object or from one of its supertypes.
 *
 * `callNonvirtual<type>Method` routines are the mechanism for invoking "default interface methods" introduced in Java 8.
 *
 * You should use this function only if the Java method you are calling returns `long` values.
 *
 * @return the result of calling the Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public fun JObject.callNonvirtualLongMethod(clazz: JClass, methodId: JMethodID, args: JArguments): Long {
    return env.CallNonvirtualLongMethodA!!(env.ptr, clazz.c, c, methodId.c, args)
}

/**
 * Invokes an instance (nonstatic) method on a Java object, according to the specified class and method ID.
 * The [methodId] argument must be obtained by calling [methodId] on the class clazz.
 *
 * Unlike [callFloatMethod] which invokes the method based on the class or interface of the object, this method invokes
 * the method based on the class, designated by the [clazz] parameter, from which the method ID is obtained.
 *
 * The method ID must be obtained from the real class of the object or from one of its supertypes.
 *
 * `callNonvirtual<type>Method` routines are the mechanism for invoking "default interface methods" introduced in Java 8.
 *
 * You should use this function only if the Java method you are calling returns `float` values.
 *
 * @return the result of calling the Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public fun JObject.callNonvirtualFloatMethod(clazz: JClass, methodId: JMethodID, args: JArguments): Float {
    return env.CallNonvirtualFloatMethodA!!(env.ptr, clazz.c, c, methodId.c, args)
}

/**
 * Invokes an instance (nonstatic) method on a Java object, according to the specified class and method ID.
 * The [methodId] argument must be obtained by calling [methodId] on the class clazz.
 *
 * Unlike [callDoubleMethod] which invokes the method based on the class or interface of the object, this method invokes
 * the method based on the class, designated by the [clazz] parameter, from which the method ID is obtained.
 *
 * The method ID must be obtained from the real class of the object or from one of its supertypes.
 *
 * `callNonvirtual<type>Method` routines are the mechanism for invoking "default interface methods" introduced in Java 8.
 *
 * You should use this function only if the Java method you are calling returns `double` values.
 *
 * @return the result of calling the Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public fun JObject.callNonvirtualDoubleMethod(clazz: JClass, methodId: JMethodID, args: JArguments): Double {
    return env.CallNonvirtualDoubleMethodA!!(env.ptr, clazz.c, c, methodId.c, args)
}

/**
 * Invokes an instance (nonstatic) method on a Java object, according to the specified class and method ID.
 * The [methodId] argument must be obtained by calling [methodId] on the class clazz.
 *
 * Unlike [callVoidMethod] which invokes the method based on the class or interface of the object, this method invokes
 * the method based on the class, designated by the [clazz] parameter, from which the method ID is obtained.
 *
 * The method ID must be obtained from the real class of the object or from one of its supertypes.
 *
 * `callNonvirtual<type>Method` routines are the mechanism for invoking "default interface methods" introduced in Java 8.
 *
 * You should use this function only if the Java method you are calling doesn't return values.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public fun JObject.callNonvirtualVoidMethod(clazz: JClass, methodId: JMethodID, args: JArguments) {
    return env.CallNonvirtualVoidMethodA!!(env.ptr, clazz.c, c, methodId.c, args)
}

/**
 * Unifies usage of `callNonvirtual<type>Method` methods, returning method with the appropriate `type` wrapped in
 * lambda.
 *
 * If type [R] is primitive type, it cannot be nullable, otherwise it must be nullable.
 */
context(env: JniEnv)
public inline fun <reified R> JObject.callNonvirtualMethod(clazz: JClass, methodId: JMethodID, args: JArguments): R {
    return when (R::class) {
        CPointer::class -> callNonvirtualObjectMethod(clazz, methodId, args) as R
        Boolean::class -> callNonvirtualBooleanMethod(clazz, methodId, args) as R
        Byte::class -> callNonvirtualByteMethod(clazz, methodId, args) as R
        Char::class -> callNonvirtualCharMethod(clazz, methodId, args) as R
        Short::class -> callNonvirtualShortMethod(clazz, methodId, args) as R
        Int::class -> callNonvirtualIntMethod(clazz, methodId, args) as R
        Long::class -> callNonvirtualLongMethod(clazz, methodId, args) as R
        Float::class -> callNonvirtualFloatMethod(clazz, methodId, args) as R
        Double::class -> callNonvirtualDoubleMethod(clazz, methodId, args) as R
        Unit::class -> callNonvirtualVoidMethod(clazz, methodId, args) as R
        else -> throw IllegalArgumentException("Unsupported return type: ${R::class.qualifiedName}")
    }
}

/**
 * Returns the field ID for an instance (nonstatic) field of a class.
 * The field is specified by its name and signature.
 * The `get<type>Field` and `set<type>Field` families of accessor functions use field IDs to retrieve object fields.
 *
 * [fieldId] causes an uninitialized class to be initialized.
 *
 * [fieldId] cannot be used to get the length field of an array. Use [JArray.length] instead.
 *
 * @param this@GetFieldID a Java class object.
 * @param name the field name in the null-terminated modified UTF-8.
 * @param sig the field signature in the null-terminated modified UTF-8. Use [String.modifiedUtf8] to get it, or **if
 * you are sure that your string doesn't have illegal characters** you may use optimized [String.utf8].
 *
 * @return a field ID, or `null` if the operation fails.
 *
 * @throws NoSuchFieldError if the specified field cannot be found.
 * @throws ExceptionInInitializerError if the class initializer fails due to an exception.
 * @throws OutOfMemoryError if the system runs out of memory.
 */
context(env: JniEnv, autofreeScope: AutofreeScope)
public fun JClass.fieldId(name: CValuesRef<ByteVar>, sig: CValuesRef<ByteVar>): JFieldID? {
    return env.GetFieldID!!(env.ptr, c, name.getPointer(autofreeScope), sig.getPointer(autofreeScope))?.wrap()
}

/**
 * Returns the value of an instance (nonstatic) field of an object.
 * The field to access is specified by a field ID obtained by calling [fieldId].
 *
 * You should use this function only if the Java field you are reading has `Object` type.
 *
 * @return the content of the field.
 */
context(env: JniEnv)
public fun JObject.getObjectField(fieldID: JFieldID): JObject? {
    return env.GetObjectField!!(env.ptr, c, fieldID.c).wrap()
}

/**
 * Returns the value of an instance (nonstatic) field of an object.
 * The field to access is specified by a field ID obtained by calling [fieldId].
 *
 * You should use this function only if the Java field you are reading has `boolean` type.
 *
 * @return the content of the field.
 */
context(env: JniEnv)
public fun JObject.getBooleanField(fieldID: JFieldID): Boolean {
    return env.GetBooleanField!!(env.ptr, c, fieldID.c).toKBoolean()
}

/**
 * Returns the value of an instance (nonstatic) field of an object.
 * The field to access is specified by a field ID obtained by calling [fieldId].
 *
 * You should use this function only if the Java field you are reading has `byte` type.
 *
 * @return the content of the field.
 */
context(env: JniEnv)
public fun JObject.getByteField(fieldID: JFieldID): Byte {
    return env.GetByteField!!(env.ptr, c, fieldID.c)
}

/**
 * Returns the value of an instance (nonstatic) field of an object.
 * The field to access is specified by a field ID obtained by calling [fieldId].
 *
 * You should use this function only if the Java field you are reading has `char` type.
 *
 * @return the content of the field.
 */
context(env: JniEnv)
public fun JObject.getCharField(fieldID: JFieldID): Char {
    return env.GetCharField!!(env.ptr, c, fieldID.c).toKChar()
}

/**
 * Returns the value of an instance (nonstatic) field of an object.
 * The field to access is specified by a field ID obtained by calling [fieldId].
 *
 * You should use this function only if the Java field you are reading has `short` type.
 *
 * @return the content of the field.
 */
context(env: JniEnv)
public fun JObject.getShortField(fieldID: JFieldID): Short {
    return env.GetShortField!!(env.ptr, c, fieldID.c)
}

/**
 * Returns the value of an instance (nonstatic) field of an object.
 * The field to access is specified by a field ID obtained by calling [fieldId].
 *
 * You should use this function only if the Java field you are reading has `int` type.
 *
 * @return the content of the field.
 */
context(env: JniEnv)
public fun JObject.getIntField(fieldID: JFieldID): Int {
    return env.GetIntField!!(env.ptr, c, fieldID.c)
}

/**
 * Returns the value of an instance (nonstatic) field of an object.
 * The field to access is specified by a field ID obtained by calling [fieldId].
 *
 * You should use this function only if the Java field you are reading has `long` type.
 *
 * @return the content of the field.
 */
context(env: JniEnv)
public fun JObject.getLongField(fieldID: JFieldID): Long {
    return env.GetLongField!!(env.ptr, c, fieldID.c)
}

/**
 * Returns the value of an instance (nonstatic) field of an object.
 * The field to access is specified by a field ID obtained by calling [fieldId].
 *
 * You should use this function only if the Java field you are reading has `float` type.
 *
 * @return the content of the field.
 */
context(env: JniEnv)
public fun JObject.getFloatField(fieldID: JFieldID): Float {
    return env.GetFloatField!!(env.ptr, c, fieldID.c)
}

/**
 * Returns the value of an instance (nonstatic) field of an object.
 * The field to access is specified by a field ID obtained by calling [fieldId].
 *
 * You should use this function only if the Java field you are reading has `double` type.
 *
 * @return the content of the field.
 */
context(env: JniEnv)
public fun JObject.getDoubleField(fieldID: JFieldID): Double {
    return env.GetDoubleField!!(env.ptr, c, fieldID.c)
}

/**
 * Sets the value of an instance (nonstatic) field of an object.
 * The field to access is specified by a field ID obtained by calling [fieldId].
 *
 * You should use this function only if the Java field you are writing has `Object` type.
 */
context(env: JniEnv)
public fun JObject.setObjectField(fieldID: JFieldID, value: JObject?) {
    env.SetObjectField!!(env.ptr, c, fieldID.c, value.c)
}

/**
 * Sets the value of an instance (nonstatic) field of an object.
 * The field to access is specified by a field ID obtained by calling [fieldId].
 *
 * You should use this function only if the Java field you are writing has `boolean` type.
 */
context(env: JniEnv)
public fun JObject.setBooleanField(fieldID: JFieldID, value: Boolean) {
    env.SetBooleanField!!(env.ptr, c, fieldID.c, value.toJBoolean())
}

/**
 * Sets the value of an instance (nonstatic) field of an object.
 * The field to access is specified by a field ID obtained by calling [fieldId].
 *
 * You should use this function only if the Java field you are writing has `byte` type.
 */
context(env: JniEnv)
public fun JObject.setByteField(fieldID: JFieldID, value: Byte) {
    env.SetByteField!!(env.ptr, c, fieldID.c, value)
}

/**
 * Sets the value of an instance (nonstatic) field of an object.
 * The field to access is specified by a field ID obtained by calling [fieldId].
 *
 * You should use this function only if the Java field you are writing has `char` type.
 */
context(env: JniEnv)
public fun JObject.setCharField(fieldID: JFieldID, value: Char) {
    env.SetCharField!!(env.ptr, c, fieldID.c, value.toJChar())
}

/**
 * Sets the value of an instance (nonstatic) field of an object.
 * The field to access is specified by a field ID obtained by calling [fieldId].
 *
 * You should use this function only if the Java field you are writing has `short` type.
 */
context(env: JniEnv)
public fun JObject.setShortField(fieldID: JFieldID, value: Short) {
    env.SetShortField!!(env.ptr, c, fieldID.c, value)
}

/**
 * Sets the value of an instance (nonstatic) field of an object.
 * The field to access is specified by a field ID obtained by calling [fieldId].
 *
 * You should use this function only if the Java field you are writing has `int` type.
 */
context(env: JniEnv)
public fun JObject.setIntField(fieldID: JFieldID, value: Int) {
    env.SetIntField!!(env.ptr, c, fieldID.c, value)
}

/**
 * Sets the value of an instance (nonstatic) field of an object.
 * The field to access is specified by a field ID obtained by calling [fieldId].
 *
 * You should use this function only if the Java field you are writing has `long` type.
 */
context(env: JniEnv)
public fun JObject.setLongField(fieldID: JFieldID, value: Long) {
    env.SetLongField!!(env.ptr, c, fieldID.c, value)
}

/**
 * Sets the value of an instance (nonstatic) field of an object.
 * The field to access is specified by a field ID obtained by calling [fieldId].
 *
 * You should use this function only if the Java field you are writing has `float` type.
 */
context(env: JniEnv)
public fun JObject.setFloatField(fieldID: JFieldID, value: Float) {
    env.SetFloatField!!(env.ptr, c, fieldID.c, value)
}

/**
 * Sets the value of an instance (nonstatic) field of an object.
 * The field to access is specified by a field ID obtained by calling [fieldId].
 *
 * You should use this function only if the Java field you are writing has `double` type.
 */
context(env: JniEnv)
public fun JObject.setDoubleField(fieldID: JFieldID, value: Double) {
    env.SetDoubleField!!(env.ptr, c, fieldID.c, value)
}

/**
 * Returns the method ID for a static method of a class. The method is specified by its name and signature.
 *
 * [staticMethodId] causes an uninitialized class to be initialized.
 *
 * @receiver a Java class object.
 * @param name the static method name in the null-terminated modified UTF-8.
 * @param sig the method signature in the null-terminated modified UTF-8. Use [String.modifiedUtf8] to get it, or **if
 * you are sure that your string doesn't have illegal characters** you may use optimized [String.utf8].
 *
 * @return a method ID, or `null` if the operation fails.
 *
 * @throws NoSuchMethodError if the specified static method cannot be found.
 * @throws ExceptionInInitializerError if the class initializer fails due to an exception.
 * @throws OutOfMemoryError if the system runs out of memory.
 */
context(env: JniEnv, autofreeScope: AutofreeScope)
public fun JClass.staticMethodId(name: CValuesRef<ByteVar>, sig: CValuesRef<ByteVar>): JMethodID? {
    return env.GetStaticMethodID!!(env.ptr, c, name.getPointer(autofreeScope), sig.getPointer(autofreeScope))?.wrap()
}

/**
 * Invokes a static method on a Java object, according to the specified method ID.
 * The [methodId] argument must be obtained by calling [staticMethodId].
 *
 * The method ID must be derived from the class at receiver, not from one of its superclasses.
 *
 * You should use this function only if the Java method you are calling returns `Object` values.
 *
 * @return the result of calling the static Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public fun JClass.callStaticObjectMethod(methodId: JMethodID, args: JArguments): JObject? {
    return env.CallStaticObjectMethodA!!(env.ptr, c, methodId.c, args).wrap()
}

/**
 * Invokes a static method on a Java object, according to the specified method ID.
 * The [methodId] argument must be obtained by calling [staticMethodId].
 *
 * The method ID must be derived from the class at receiver, not from one of its superclasses.
 *
 * You should use this function only if the Java method you are calling returns `boolean` values.
 *
 * @return the result of calling the static Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public fun JClass.callStaticBooleanMethod(methodId: JMethodID, args: JArguments): Boolean {
    return env.CallStaticBooleanMethodA!!(env.ptr, c, methodId.c, args).toKBoolean()
}

/**
 * Invokes a static method on a Java object, according to the specified method ID.
 * The [methodId] argument must be obtained by calling [staticMethodId].
 *
 * The method ID must be derived from the class at receiver, not from one of its superclasses.
 *
 * You should use this function only if the Java method you are calling returns `byte` values.
 *
 * @return the result of calling the static Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public fun JClass.callStaticByteMethod(methodId: JMethodID, args: JArguments): Byte {
    return env.CallStaticByteMethodA!!(env.ptr, c, methodId.c, args)
}

/**
 * Invokes a static method on a Java object, according to the specified method ID.
 * The [methodId] argument must be obtained by calling [staticMethodId].
 *
 * The method ID must be derived from the class at receiver, not from one of its superclasses.
 *
 * You should use this function only if the Java method you are calling returns `char` values.
 *
 * @return the result of calling the static Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public fun JClass.callStaticCharMethod(methodId: JMethodID, args: JArguments): Char {
    return env.CallStaticCharMethodA!!(env.ptr, c, methodId.c, args).toKChar()
}

/**
 * Invokes a static method on a Java object, according to the specified method ID.
 * The [methodId] argument must be obtained by calling [staticMethodId].
 *
 * The method ID must be derived from the class at receiver, not from one of its superclasses.
 *
 * You should use this function only if the Java method you are calling returns `short` values.
 *
 * @return the result of calling the static Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public fun JClass.callStaticShortMethod(methodId: JMethodID, args: JArguments): Short {
    return env.CallStaticShortMethodA!!(env.ptr, c, methodId.c, args)
}

/**
 * Invokes a static method on a Java object, according to the specified method ID.
 * The [methodId] argument must be obtained by calling [staticMethodId].
 *
 * The method ID must be derived from the class at receiver, not from one of its superclasses.
 *
 * You should use this function only if the Java method you are calling returns `int` values.
 *
 * @return the result of calling the static Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public fun JClass.callStaticIntMethod(methodId: JMethodID, args: JArguments): Int {
    return env.CallStaticIntMethodA!!(env.ptr, c, methodId.c, args)
}

/**
 * Invokes a static method on a Java object, according to the specified method ID.
 * The [methodId] argument must be obtained by calling [staticMethodId].
 *
 * The method ID must be derived from the class at receiver, not from one of its superclasses.
 *
 * You should use this function only if the Java method you are calling returns `long` values.
 *
 * @return the result of calling the static Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public fun JClass.callStaticLongMethod(methodId: JMethodID, args: JArguments): Long {
    return env.CallStaticLongMethodA!!(env.ptr, c, methodId.c, args)
}

/**
 * Invokes a static method on a Java object, according to the specified method ID.
 * The [methodId] argument must be obtained by calling [staticMethodId].
 *
 * The method ID must be derived from the class at receiver, not from one of its superclasses.
 *
 * You should use this function only if the Java method you are calling returns `float` values.
 *
 * @return the result of calling the static Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public fun JClass.callStaticFloatMethod(methodId: JMethodID, args: JArguments): Float {
    return env.CallStaticFloatMethodA!!(env.ptr, c, methodId.c, args)
}

/**
 * Invokes a static method on a Java object, according to the specified method ID.
 * The [methodId] argument must be obtained by calling [staticMethodId].
 *
 * The method ID must be derived from the class at receiver, not from one of its superclasses.
 *
 * You should use this function only if the Java method you are calling returns `double` values.
 *
 * @return the result of calling the static Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public fun JClass.callStaticDoubleMethod(methodId: JMethodID, args: JArguments): Double {
    return env.CallStaticDoubleMethodA!!(env.ptr, c, methodId.c, args)
}

/**
 * Invokes a static method on a Java object, according to the specified method ID.
 * The [methodId] argument must be obtained by calling [staticMethodId].
 *
 * The method ID must be derived from the class at receiver, not from one of its superclasses.
 *
 * You should use this function only if the Java method you are calling returns `void` values.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public fun JClass.callStaticVoidMethod(methodId: JMethodID, args: JArguments) {
    return env.CallStaticVoidMethodA!!(env.ptr, c, methodId.c, args)
}

/**
 * Unifies usage of `callStatic<type>Method` methods, returning method with the appropriate `type` wrapped in lambda.
 *
 * If type [R] is primitive type, it cannot be nullable, otherwise it must be nullable.
 */
context(env: JniEnv)
public inline fun <reified R> JClass.callStaticMethod(methodId: JMethodID, args: JArguments): R {
    return when (R::class) {
        CPointer::class -> callStaticObjectMethod(methodId, args) as R
        Boolean::class -> callStaticBooleanMethod(methodId, args) as R
        Byte::class -> callStaticByteMethod(methodId, args) as R
        Char::class -> callStaticCharMethod(methodId, args) as R
        Short::class -> callStaticShortMethod(methodId, args) as R
        Int::class -> callStaticIntMethod(methodId, args) as R
        Long::class -> callStaticLongMethod(methodId, args) as R
        Float::class -> callStaticFloatMethod(methodId, args) as R
        Double::class -> callStaticDoubleMethod(methodId, args) as R
        Unit::class -> callStaticVoidMethod(methodId, args) as R
        else -> throw IllegalArgumentException("Unsupported return type: ${R::class.qualifiedName}")
    }
}

/**
 * Returns the field ID for a static field of a class. The field is specified by its name and signature.
 * The `getStatic<type>Field` and `setStatic<type>Field` families of accessor functions use field IDs to retrieve static
 * fields.
 *
 * [staticFieldId] causes an uninitialized class to be initialized.
 *
 * @receiver a Java class object.
 * @param name the static field name in the null-terminated modified UTF-8.
 * @param sig the field signature in the null-terminated modified UTF-8. Use [String.modifiedUtf8] to get it, or **if
 * you are sure that your string doesn't have illegal characters** you may use optimized [String.utf8].
 *
 * @return a field ID, or `null` if the specified static field cannot be found.
 *
 * @throws NoSuchFieldError if the specified static field cannot be found.
 * @throws ExceptionInInitializerError if the class initializer fails due to an exception.
 * @throws OutOfMemoryError if the system runs out of memory.
 */
context(env: JniEnv, autofreeScope: AutofreeScope)
public fun JClass.staticFieldId(name: CValuesRef<ByteVar>, sig: CValuesRef<ByteVar>): JFieldID? {
    return env.GetStaticFieldID!!(env.ptr, c, name.getPointer(autofreeScope), sig.getPointer(autofreeScope))?.wrap()
}

/**
 * Returns the value of a static field of an object.
 * The field to access is specified by a field ID, which is obtained by calling [staticFieldId].
 *
 * You should use this function only if the Java field you are reading has `Object` type.
 *
 * @return the content of the static field.
 */
context(env: JniEnv)
public fun JClass.getStaticObjectField(fieldID: JFieldID): JObject? {
    return env.GetStaticObjectField!!(env.ptr, c, fieldID.c).wrap()
}

/**
 * Returns the value of a static field of an object.
 * The field to access is specified by a field ID, which is obtained by calling [staticFieldId].
 *
 * You should use this function only if the Java field you are reading has `boolean` type.
 *
 * @return the content of the static field.
 */
context(env: JniEnv)
public fun JClass.getStaticBooleanField(fieldID: JFieldID): Boolean {
    return env.GetStaticBooleanField!!(env.ptr, c, fieldID.c).toKBoolean()
}

/**
 * Returns the value of a static field of an object.
 * The field to access is specified by a field ID, which is obtained by calling [staticFieldId].
 *
 * You should use this function only if the Java field you are reading has `byte` type.
 *
 * @return the content of the static field.
 */
context(env: JniEnv)
public fun JClass.getStaticByteField(fieldID: JFieldID): Byte {
    return env.GetStaticByteField!!(env.ptr, c, fieldID.c)
}

/**
 * Returns the value of a static field of an object.
 * The field to access is specified by a field ID, which is obtained by calling [staticFieldId].
 *
 * You should use this function only if the Java field you are reading has `char` type.
 *
 * @return the content of the static field.
 */
context(env: JniEnv)
public fun JClass.getStaticCharField(fieldID: JFieldID): Char {
    return env.GetStaticCharField!!(env.ptr, c, fieldID.c).toKChar()
}

/**
 * Returns the value of a static field of an object.
 * The field to access is specified by a field ID, which is obtained by calling [staticFieldId].
 *
 * You should use this function only if the Java field you are reading has `short` type.
 *
 * @return the content of the static field.
 */
context(env: JniEnv)
public fun JClass.getStaticShortField(fieldID: JFieldID): Short {
    return env.GetStaticShortField!!(env.ptr, c, fieldID.c)
}

/**
 * Returns the value of a static field of an object.
 * The field to access is specified by a field ID, which is obtained by calling [staticFieldId].
 *
 * You should use this function only if the Java field you are reading has `int` type.
 *
 * @return the content of the static field.
 */
context(env: JniEnv)
public fun JClass.getStaticIntField(fieldID: JFieldID): Int {
    return env.GetStaticIntField!!(env.ptr, c, fieldID.c)
}

/**
 * Returns the value of a static field of an object.
 * The field to access is specified by a field ID, which is obtained by calling [staticFieldId].
 *
 * You should use this function only if the Java field you are reading has `long` type.
 *
 * @return the content of the static field.
 */
context(env: JniEnv)
public fun JClass.getStaticLongField(fieldID: JFieldID): Long {
    return env.GetStaticLongField!!(env.ptr, c, fieldID.c)
}

/**
 * Returns the value of a static field of an object.
 * The field to access is specified by a field ID, which is obtained by calling [staticFieldId].
 *
 * You should use this function only if the Java field you are reading has `float` type.
 *
 * @return the content of the static field.
 */
context(env: JniEnv)
public fun JClass.getStaticFloatField(fieldID: JFieldID): Float {
    return env.GetStaticFloatField!!(env.ptr, c, fieldID.c)
}

/**
 * Returns the value of a static field of an object.
 * The field to access is specified by a field ID, which is obtained by calling [staticFieldId].
 *
 * You should use this function only if the Java field you are reading has `double` type.
 *
 * @return the content of the static field.
 */
context(env: JniEnv)
public fun JClass.getStaticDoubleField(fieldID: JFieldID): Double {
    return env.GetStaticDoubleField!!(env.ptr, c, fieldID.c)
}

/**
 * Sets the value of a static field of an object.
 * The field to access is specified by a field ID, which is obtained by calling [staticFieldId].
 *
 * You should use this function only if the Java field you are writing has `Object` type.
 */
context(env: JniEnv)
public fun JClass.setStaticObjectField(fieldID: JFieldID, value: JObject?) {
    env.SetStaticObjectField!!(env.ptr, c, fieldID.c, value.c)
}

/**
 * Sets the value of a static field of an object.
 * The field to access is specified by a field ID, which is obtained by calling [staticFieldId].
 *
 * You should use this function only if the Java field you are writing has `boolean` type.
 */
context(env: JniEnv)
public fun JClass.setStaticBooleanField(fieldID: JFieldID, value: Boolean) {
    env.SetStaticBooleanField!!(env.ptr, c, fieldID.c, value.toJBoolean())
}

/**
 * Sets the value of a static field of an object.
 * The field to access is specified by a field ID, which is obtained by calling [staticFieldId].
 *
 * You should use this function only if the Java field you are writing has `byte` type.
 */
context(env: JniEnv)
public fun JClass.setStaticByteField(fieldID: JFieldID, value: Byte) {
    env.SetStaticByteField!!(env.ptr, c, fieldID.c, value)
}

/**
 * Sets the value of a static field of an object.
 * The field to access is specified by a field ID, which is obtained by calling [staticFieldId].
 *
 * You should use this function only if the Java field you are writing has `char` type.
 */
context(env: JniEnv)
public fun JClass.setStaticCharField(fieldID: JFieldID, value: Char) {
    env.SetStaticCharField!!(env.ptr, c, fieldID.c, value.toJChar())
}

/**
 * Sets the value of a static field of an object.
 * The field to access is specified by a field ID, which is obtained by calling [staticFieldId].
 *
 * You should use this function only if the Java field you are writing has `short` type.
 */
context(env: JniEnv)
public fun JClass.setStaticShortField(fieldID: JFieldID, value: Short) {
    env.SetStaticShortField!!(env.ptr, c, fieldID.c, value)
}

/**
 * Sets the value of a static field of an object.
 * The field to access is specified by a field ID, which is obtained by calling [staticFieldId].
 *
 * You should use this function only if the Java field you are writing has `int` type.
 */
context(env: JniEnv)
public fun JClass.setStaticIntField(fieldID: JFieldID, value: Int) {
    env.SetStaticIntField!!(env.ptr, c, fieldID.c, value)
}

/**
 * Sets the value of a static field of an object.
 * The field to access is specified by a field ID, which is obtained by calling [staticFieldId].
 *
 * You should use this function only if the Java field you are writing has `long` type.
 */
context(env: JniEnv)
public fun JClass.setStaticLongField(fieldID: JFieldID, value: Long) {
    env.SetStaticLongField!!(env.ptr, c, fieldID.c, value)
}

/**
 * Sets the value of a static field of an object.
 * The field to access is specified by a field ID, which is obtained by calling [staticFieldId].
 *
 * You should use this function only if the Java field you are writing has `float` type.
 */
context(env: JniEnv)
public fun JClass.setStaticFloatField(fieldID: JFieldID, value: Float) {
    env.SetStaticFloatField!!(env.ptr, c, fieldID.c, value)
}

/**
 * Sets the value of a static field of an object.
 * The field to access is specified by a field ID, which is obtained by calling [staticFieldId].
 *
 * You should use this function only if the Java field you are writing has `double` type.
 */
context(env: JniEnv)
public fun JClass.setStaticDoubleField(fieldID: JFieldID, value: Double) {
    env.SetStaticDoubleField!!(env.ptr, c, fieldID.c, value)
}

/**
 * Constructs a new `java.lang.String` object from an array of Unicode characters.
 *
 * @param unicodeChars pointer to a Unicode string. May be `null`, in which case len must be 0.
 * @param len length of the Unicode string. May be zero.
 *
 * @return a Java string object, or `null` if the string cannot be constructed.
 *
 * @throws OutOfMemoryError if the system runs out of memory.
 */
context(env: JniEnv)
public fun newString(unicodeChars: CArrayPointer<UShortVar>, len: Int): JString? {
    return env.NewString!!(env.ptr, unicodeChars, len).wrap()
}

/**
 * The length (the count of Unicode characters) of a Java string.
 */
context(env: JniEnv)
public val JString.length: Int get() {
    return env.GetStringLength!!(env.ptr, c)
}

/**
 * Returns a pointer to the array of Unicode characters and a boolean value `isCopy` which specifies whether the array
 * is a copy (`true` - a copy, `false - the underlying array of the string, which means that any changes to it will be
 * reflected on the original string).
 *
 * This pointer is valid until [releaseChars] is called.
 *
 * @return a pointer to a Unicode string and a copy marker, or `null` if the operation fails.
 *
 * @see getRegion
 */
context(env: JniEnv, placement: NativePlacement)
public fun JString.getChars(): Pair<CArrayPointer<UShortVar>, Boolean>? {
    val isCopy = placement.alloc<UByteVar>()
    val chars = env.GetStringChars!!(env.ptr, c, isCopy.ptr)
    return chars?.let { it to isCopy.value.toKBoolean() }
}

/**
 * Informs the VM that the native code no longer needs access to chars. The [chars] argument is a pointer obtained from
 * string using [getChars].
 */
context(env: JniEnv)
public fun JString.releaseChars(chars: CArrayPointer<UShortVar>) {
    env.ReleaseStringChars!!(env.ptr, c, chars)
}

/**
 * Constructs a new `java.lang.String` object from an array of characters in modified UTF-8 encoding.
 *
 * @return a Java string object, or `null` if the string cannot be constructed.
 *
 * @throws OutOfMemoryError if the system runs out of memory.
 */
context(env: JniEnv)
public fun newStringUTF(bytes: CArrayPointer<ByteVar>): JString? {
    return env.NewStringUTF!!(env.ptr, bytes).wrap()
}

/**
 * The length in bytes of the modified UTF-8 representation of a string.
 *
 * ### Since JDK 24:
 *
 * As the capacity of a [Int] variable is not sufficient to hold the length of all possible modified UTF-8 string
 * representations (due to multibyte encodings), this is deprecated in favor of [utfLengthLong].
 *
 * If the modified UTF-8 representation of string has a length that exceeds the capacity of a [Int] variable, then this
 * returns the number of bytes up to and including the last character that could be fully encoded without exceeding that
 * capacity.
 */
context(env: JniEnv)
public val JString.utfLength: Int get() {
    return env.GetStringUTFLength!!(env.ptr, c)
}

/**
 * Returns a pointer to an array of bytes representing the string in modified UTF-8 encoding and a boolean value
 * `isCopy` which specifies whether the array is a copy (`true` - a copy, `false - the underlying array of the string,
 * which means that any changes to it will be reflected on the original string).
 *
 * This array is valid until it is released by [releaseUTFChars].
 *
 * @return a pointer to a modified UTF-8 string and a copy marker, or `null` if the operation fails.
 */
context(env: JniEnv, placement: NativePlacement)
public fun JString.getUTFChars(): Pair<CArrayPointer<ByteVar>, Boolean>? {
    val isCopy = placement.alloc<UByteVar>()
    val utf = env.GetStringUTFChars!!(env.ptr, c, isCopy.ptr)
    return utf?.let { it to isCopy.value.toKBoolean() }
}

/**
 * Informs the VM that the native code no longer needs access to [utf]. The [utf] argument is a pointer derived from string
 * using [getUTFChars].
 *
 * @since JDK/JRE 1.2
 *
 * @see getUTFRegion
 */
context(env: JniEnv)
public fun JString.releaseUTFChars(utf: CArrayPointer<ByteVar>) {
    env.ReleaseStringUTFChars!!(env.ptr, c, utf)
}

/**
 * The number of elements in the array.
 */
context(env: JniEnv)
public val JArray.length: Int get() {
    return env.GetArrayLength!!(env.ptr, c)
}

/**
 * Constructs a new array holding objects in class [elementClass]. All elements are initially set to [initialElement].
 *
 * @param length array size; must be >= 0.
 * @param elementClass array element class.
 * @param initialElement initialization value.
 *
 * @return a Java array object, or `null` if the array cannot be constructed.
 *
 * @throws OutOfMemoryError if the system runs out of memory.
 */
context(env: JniEnv)
public fun newObjectArray(length: Int, elementClass: JClass, initialElement: JObject? = null): JObjectArray? {
    return env.NewObjectArray!!(env.ptr, length, elementClass.c, initialElement.c).wrap()
}

/**
 * Returns an element of an Object array. The [index] must be within the range of the array.
 *
 * @return a Java object.
 *
 * @throws ArrayIndexOutOfBoundsException if [index] does not specify a valid index in the array.
 */
context(env: JniEnv)
public operator fun JObjectArray.get(index: Int): JObject? {
    return env.GetObjectArrayElement!!(env.ptr, c, index).wrap()
}

/**
 * Sets an element of an Object array. The [index] must be within the range of the array.
 *
 * @throws ArrayIndexOutOfBoundsException if [index] does not specify a valid index in the array.
 * @throws ArrayStoreException if the class of value is not a subclass of the element class of the array.
 */
context(env: JniEnv)
public operator fun JObjectArray.set(index: Int, value: JObject?) {
    env.SetObjectArrayElement!!(env.ptr, c, index, value.c)
}

/**
 * Constructs a new `boolean[]` array object.
 */
context(env: JniEnv)
public fun newBooleanArray(length: Int): JBooleanArray? {
    return env.NewBooleanArray!!(env.ptr, length).wrap()
}

/**
 * Constructs a new `byte[]` array object.
 */
context(env: JniEnv)
public fun newByteArray(length: Int): JByteArray? {
    return env.NewByteArray!!(env.ptr, length).wrap()
}

/**
 * Constructs a new `char[]` array object.
 */
context(env: JniEnv)
public fun newCharArray(length: Int): JCharArray? {
    return env.NewCharArray!!(env.ptr, length).wrap()
}

/**
 * Constructs a new `short[]` array object.
 */
context(env: JniEnv)
public fun newShortArray(length: Int): JShortArray? {
    return env.NewShortArray!!(env.ptr, length).wrap()
}

/**
 * Constructs a new `int[]` array object.
 */
context(env: JniEnv)
public fun newIntArray(length: Int): JIntArray? {
    return env.NewIntArray!!(env.ptr, length).wrap()
}

/**
 * Constructs a new `long[]` array object.
 */
context(env: JniEnv)
public fun newLongArray(length: Int): JLongArray? {
    return env.NewLongArray!!(env.ptr, length).wrap()
}

/**
 * Constructs a new `float[]` array object.
 */
context(env: JniEnv)
public fun newFloatArray(length: Int): JFloatArray? {
    return env.NewFloatArray!!(env.ptr, length).wrap()
}

/**
 * Constructs a new `double[]` array object.
 */
context(env: JniEnv)
public fun newDoubleArray(length: Int): JDoubleArray? {
    return env.NewDoubleArray!!(env.ptr, length).wrap()
}

/**
 * Returns the body of the primitive array and a boolean value `isCopy` which specifies whether the array is a copy
 * (`true` - a copy, `false - the real array body, which means that any changes to it will be reflected on the jvm
 * array).
 *
 * The result is valid until the [releaseElements] function is called.
 * Since the returned array may be a copy of the Java array, changes made to the returned array will not necessarily be
 * reflected in the original array until [releaseElements] is called.
 *
 * Regardless of how boolean arrays are represented in the Java VM, [getElements] always returns a pointer
 * to [UByte]s, with each byte denoting an element (the unpacked representation).
 * All arrays of other types are guaranteed to be contiguous in memory.
 */
context(env: JniEnv, placement: NativePlacement)
public fun JBooleanArray.getElements(): Pair<CArrayPointer<UByteVar>, Boolean>? {
    val isCopy = placement.alloc<UByteVar>()
    val elements = env.GetBooleanArrayElements!!(env.ptr, c, isCopy.ptr)
    return elements?.let { it to isCopy.value.toKBoolean() }
}

/**
 * Returns the body of the primitive array and a boolean value `isCopy` which specifies whether the array is a copy
 * (`true` - a copy, `false - the real array body, which means that any changes to it will be reflected on the jvm
 * array).
 *
 * The result is valid until the corresponding [releaseElements] function is called.
 * Since the returned array may be a copy of the Java array, changes made to the returned array will not necessarily be
 * reflected in the original array until [releaseElements] is called.
 */
context(env: JniEnv, placement: NativePlacement)
public fun JByteArray.getElements(): Pair<CArrayPointer<ByteVar>, Boolean>? {
    val isCopy = placement.alloc<UByteVar>()
    val elements = env.GetByteArrayElements!!(env.ptr, c, isCopy.ptr)
    return elements?.let { it to isCopy.value.toKBoolean() }
}

/**
 * Returns the body of the primitive array and a boolean value `isCopy` which specifies whether the array is a copy
 * (`true` - a copy, `false - the real array body, which means that any changes to it will be reflected on the jvm
 * array).
 *
 * The result is valid until the corresponding [releaseElements] function is called.
 * Since the returned array may be a copy of the Java array, changes made to the returned array will not necessarily be
 * reflected in the original array until [releaseElements] is called.
 */
context(env: JniEnv, placement: NativePlacement)
public fun JCharArray.getElements(): Pair<CArrayPointer<UShortVar>, Boolean>? {
    val isCopy = placement.alloc<UByteVar>()
    val elements = env.GetCharArrayElements!!(env.ptr, c, isCopy.ptr)
    return elements?.let { it to isCopy.value.toKBoolean() }
}

/**
 * Returns the body of the primitive array and a boolean value `isCopy` which specifies whether the array is a copy
 * (`true` - a copy, `false - the real array body, which means that any changes to it will be reflected on the jvm
 * array).
 *
 * The result is valid until the corresponding [releaseElements] function is called.
 * Since the returned array may be a copy of the Java array, changes made to the returned array will not necessarily be
 * reflected in the original array until [releaseElements] is called.
 */
context(env: JniEnv, placement: NativePlacement)
public fun JShortArray.getElements(): Pair<CArrayPointer<ShortVar>, Boolean>? {
    val isCopy = placement.alloc<UByteVar>()
    val elements = env.GetShortArrayElements!!(env.ptr, c, isCopy.ptr)
    return elements?.let { it to isCopy.value.toKBoolean() }
}

/**
 * Returns the body of the primitive array and a boolean value `isCopy` which specifies whether the array is a copy
 * (`true` - a copy, `false - the real array body, which means that any changes to it will be reflected on the jvm
 * array).
 *
 * The result is valid until the corresponding [releaseElements] function is called.
 * Since the returned array may be a copy of the Java array, changes made to the returned array will not necessarily be
 * reflected in the original array until [releaseElements] is called.
 */
context(env: JniEnv, placement: NativePlacement)
public fun JIntArray.getElements(): Pair<CArrayPointer<IntVar>, Boolean>? {
    val isCopy = placement.alloc<UByteVar>()
    val elements = env.GetIntArrayElements!!(env.ptr, c, isCopy.ptr)
    return elements?.let { it to isCopy.value.toKBoolean() }
}

/**
 * Returns the body of the primitive array and a boolean value `isCopy` which specifies whether the array is a copy
 * (`true` - a copy, `false - the real array body, which means that any changes to it will be reflected on the jvm
 * array).
 *
 * The result is valid until the corresponding [releaseElements] function is called.
 * Since the returned array may be a copy of the Java array, changes made to the returned array will not necessarily be
 * reflected in the original array until [releaseElements] is called.
 */
context(env: JniEnv, placement: NativePlacement)
public fun JLongArray.getElements(): Pair<CArrayPointer<LongVar>, Boolean>? {
    val isCopy = placement.alloc<UByteVar>()
    val elements = env.GetLongArrayElements!!(env.ptr, c, isCopy.ptr)
    return elements?.let { it to isCopy.value.toKBoolean() }
}

/**
 * Returns the body of the primitive array and a boolean value `isCopy` which specifies whether the array is a copy
 * (`true` - a copy, `false - the real array body, which means that any changes to it will be reflected on the jvm
 * array).
 *
 * The result is valid until the corresponding [releaseElements] function is called.
 * Since the returned array may be a copy of the Java array, changes made to the returned array will not necessarily be
 * reflected in the original array until [releaseElements] is called.
 */
context(env: JniEnv, placement: NativePlacement)
public fun JFloatArray.getElements(): Pair<CArrayPointer<FloatVar>, Boolean>? {
    val isCopy = placement.alloc<UByteVar>()
    val elements = env.GetFloatArrayElements!!(env.ptr, c, isCopy.ptr)
    return elements?.let { it to isCopy.value.toKBoolean() }
}

/**
 * Returns the body of the primitive array and a boolean value `isCopy` which specifies whether the array is a copy
 * (`true` - a copy, `false - the real array body, which means that any changes to it will be reflected on the jvm
 * array).
 *
 * The result is valid until the corresponding [releaseElements] function is called.
 * Since the returned array may be a copy of the Java array, changes made to the returned array will not necessarily be
 * reflected in the original array until [releaseElements] is called.
 */
context(env: JniEnv, placement: NativePlacement)
public fun JDoubleArray.getElements(): Pair<CArrayPointer<DoubleVar>, Boolean>? {
    val isCopy = placement.alloc<UByteVar>()
    val elements = env.GetDoubleArrayElements!!(env.ptr, c, isCopy.ptr)
    return elements?.let { it to isCopy.value.toKBoolean() }
}

/**
 * Informs the VM that the native code no longer needs access to [elems].
 * The [elems] argument is a pointer derived from the receiver using the [getElements] function.
 * If necessary, this function copies back all changes made to elems to the original array.
 *
 * The [mode] argument provides information on how the array buffer should be released. [mode] has no effect if [elems]
 * is not a copy of the elements in the receiver. Otherwise, mode has the following impact:
 * - [ApplyChangesMode.FinalCommit] -> copy back the content and free the elems buffer.
 * - [ApplyChangesMode.Commit]      -> copy back the content but do not free the elems buffer.
 * - [ApplyChangesMode.Abort]       -> free the buffer without copying back the possible changes.
 */
context(env: JniEnv)
public fun JBooleanArray.releaseElements(elems: CArrayPointer<UByteVar>, mode: ApplyChangesMode) {
    env.ReleaseBooleanArrayElements!!(env.ptr, c, elems, mode.ordinal)
}

/**
 * Informs the VM that the native code no longer needs access to [elems].
 * The [elems] argument is a pointer derived from the receiver using the [getElements] function.
 * If necessary, this function copies back all changes made to elems to the original array.
 *
 * The [mode] argument provides information on how the array buffer should be released. [mode] has no effect if [elems]
 * is not a copy of the elements in the receiver. Otherwise, mode has the following impact:
 * - [ApplyChangesMode.FinalCommit] -> copy back the content and free the elems buffer.
 * - [ApplyChangesMode.Commit]      -> copy back the content but do not free the elems buffer.
 * - [ApplyChangesMode.Abort]       -> free the buffer without copying back the possible changes.
 */
context(env: JniEnv)
public fun JByteArray.releaseElements(elems: CArrayPointer<ByteVar>, mode: ApplyChangesMode) {
    env.ReleaseByteArrayElements!!(env.ptr, c, elems, mode.ordinal)
}

/**
 * Informs the VM that the native code no longer needs access to [elems].
 * The [elems] argument is a pointer derived from the receiver using the [getElements] function.
 * If necessary, this function copies back all changes made to elems to the original array.
 *
 * The [mode] argument provides information on how the array buffer should be released. [mode] has no effect if [elems]
 * is not a copy of the elements in the receiver Otherwise, mode has the following impact:
 * - [ApplyChangesMode.FinalCommit] -> copy back the content and free the elems buffer.
 * - [ApplyChangesMode.Commit]      -> copy back the content but do not free the elems buffer.
 * - [ApplyChangesMode.Abort]       -> free the buffer without copying back the possible changes.
 */
context(env: JniEnv)
public fun JCharArray.releaseElements(elems: CArrayPointer<UShortVar>, mode: ApplyChangesMode) {
    env.ReleaseCharArrayElements!!(env.ptr, c, elems, mode.ordinal)
}

/**
 * Informs the VM that the native code no longer needs access to [elems].
 * The [elems] argument is a pointer derived from the receiver using the [getElements] function.
 * If necessary, this function copies back all changes made to elems to the original array.
 *
 * The [mode] argument provides information on how the array buffer should be released. [mode] has no effect if [elems]
 * is not a copy of the elements in the receiver Otherwise, mode has the following impact:
 * - [ApplyChangesMode.FinalCommit] -> copy back the content and free the elems buffer.
 * - [ApplyChangesMode.Commit]      -> copy back the content but do not free the elems buffer.
 * - [ApplyChangesMode.Abort]       -> free the buffer without copying back the possible changes.
 */
context(env: JniEnv)
public fun JShortArray.releaseElements(elems: CArrayPointer<ShortVar>, mode: ApplyChangesMode) {
    env.ReleaseShortArrayElements!!(env.ptr, c, elems, mode.ordinal)
}

/**
 * Informs the VM that the native code no longer needs access to [elems].
 * The [elems] argument is a pointer derived from the receiver using the [getElements] function.
 * If necessary, this function copies back all changes made to elems to the original array.
 *
 * The [mode] argument provides information on how the array buffer should be released. [mode] has no effect if [elems]
 * is not a copy of the elements in the receiver. Otherwise, mode has the following impact:
 * - [ApplyChangesMode.FinalCommit] -> copy back the content and free the elems buffer.
 * - [ApplyChangesMode.Commit]      -> copy back the content but do not free the elems buffer.
 * - [ApplyChangesMode.Abort]       -> free the buffer without copying back the possible changes.
 */
context(env: JniEnv)
public fun JIntArray.releaseElements(elems: CPointer<IntVar>, mode: ApplyChangesMode) {
    env.ReleaseIntArrayElements!!(env.ptr, c, elems, mode.ordinal)
}

/**
 * Informs the VM that the native code no longer needs access to [elems].
 * The [elems] argument is a pointer derived from the receiver using the [getElements] function.
 * If necessary, this function copies back all changes made to elems to the original array.
 *
 * The [mode] argument provides information on how the array buffer should be released. [mode] has no effect if [elems]
 * is not a copy of the elements in the receiver. Otherwise, mode has the following impact:
 * - [ApplyChangesMode.FinalCommit] -> copy back the content and free the elems buffer.
 * - [ApplyChangesMode.Commit]      -> copy back the content but do not free the elems buffer.
 * - [ApplyChangesMode.Abort]       -> free the buffer without copying back the possible changes.
 */
context(env: JniEnv)
public fun JLongArray.releaseElements(elems: CPointer<LongVar>, mode: ApplyChangesMode) {
    env.ReleaseLongArrayElements!!(env.ptr, c, elems, mode.ordinal)
}

/**
 * Informs the VM that the native code no longer needs access to [elems].
 * The [elems] argument is a pointer derived from the receiver using the [getElements] function.
 * If necessary, this function copies back all changes made to elems to the original array.
 *
 * The [mode] argument provides information on how the array buffer should be released. [mode] has no effect if [elems]
 * is not a copy of the elements in the receiver. Otherwise, mode has the following impact:
 * - [ApplyChangesMode.FinalCommit] -> copy back the content and free the elems buffer.
 * - [ApplyChangesMode.Commit]      -> copy back the content but do not free the elems buffer.
 * - [ApplyChangesMode.Abort]       -> free the buffer without copying back the possible changes.
 */
context(env: JniEnv)
public fun JFloatArray.releaseElements(elems: CPointer<FloatVar>, mode: ApplyChangesMode) {
    env.ReleaseFloatArrayElements!!(env.ptr, c, elems, mode.ordinal)
}

/**
 * Informs the VM that the native code no longer needs access to [elems].
 * The [elems] argument is a pointer derived from the receiver using the [getElements] function.
 * If necessary, this function copies back all changes made to elems to the original array.
 *
 * The [mode] argument provides information on how the array buffer should be released. [mode] has no effect if [elems]
 * is not a copy of the elements in the receiver. Otherwise, mode has the following impact:
 * - [ApplyChangesMode.FinalCommit] -> copy back the content and free the elems buffer.
 * - [ApplyChangesMode.Commit]      -> copy back the content but do not free the elems buffer.
 * - [ApplyChangesMode.Abort]       -> free the buffer without copying back the possible changes.
 */
context(env: JniEnv)
public fun JDoubleArray.releaseElements(elems: CPointer<DoubleVar>, mode: ApplyChangesMode) {
    env.ReleaseDoubleArrayElements!!(env.ptr, c, elems, mode.ordinal)
}

/**
 * Copies a region of a `boolean[]` array into a buffer [buf].
 *
 * @receiver a `boolean[]` Java array.
 * @param start the starting index; must be greater than or equal to zero and less than the array length.
 * @param len the number of elements to be copied, must be greater than or equal to zero and `start + len` must be less
 * than or equal to array length.
 * @param buf the destination buffer.
 *
 * @throws ArrayIndexOutOfBoundsException if one of the indexes in the region is not valid.
 */
context(env: JniEnv)
public fun JBooleanArray.getRegion(start: Int, len: Int, buf: CArrayPointer<UByteVar>) {
    env.GetBooleanArrayRegion!!(env.ptr, c, start, len, buf)
}

/**
 * Copies a region of a `byte[]` array into a buffer [buf].
 *
 * @receiver a `byte[]` Java array.
 * @param start the starting index; must be greater than or equal to zero and less than the array length.
 * @param len the number of elements to be copied, must be greater than or equal to zero and `start + len` must be less
 * than or equal to array length.
 * @param buf the destination buffer.
 *
 * @throws ArrayIndexOutOfBoundsException if one of the indexes in the region is not valid.
 */
context(env: JniEnv)
public fun JByteArray.getRegion(start: Int, len: Int, buf: CArrayPointer<ByteVar>) {
    env.GetByteArrayRegion!!(env.ptr, c, start, len, buf)
}

/**
 * Copies a region of a `char[]` array into a buffer [buf].
 *
 * @receiver a `char[]` Java array.
 * @param start the starting index; must be greater than or equal to zero and less than the array length.
 * @param len the number of elements to be copied, must be greater than or equal to zero and `start + len` must be less
 * than or equal to array length.
 * @param buf the destination buffer.
 *
 * @throws ArrayIndexOutOfBoundsException if one of the indexes in the region is not valid.
 */
context(env: JniEnv)
public fun JCharArray.getRegion(start: Int, len: Int, buf: CArrayPointer<UShortVar>) {
    env.GetCharArrayRegion!!(env.ptr, c, start, len, buf)
}

/**
 * Copies a region of a `short[]` array into a buffer [buf].
 *
 * @receiver a `short[]` Java array.
 * @param start the starting index; must be greater than or equal to zero and less than the array length.
 * @param len the number of elements to be copied, must be greater than or equal to zero and `start + len` must be less
 * than or equal to array length.
 * @param buf the destination buffer.
 *
 * @throws ArrayIndexOutOfBoundsException if one of the indexes in the region is not valid.
 */
context(env: JniEnv)
public fun JShortArray.getRegion(start: Int, len: Int, buf: CArrayPointer<ShortVar>) {
    env.GetShortArrayRegion!!(env.ptr, c, start, len, buf)
}

/**
 * Copies a region of a `int[]` array into a buffer [buf].
 *
 * @receiver a `int[]` Java array.
 * @param start the starting index; must be greater than or equal to zero and less than the array length.
 * @param len the number of elements to be copied, must be greater than or equal to zero and `start + len` must be less
 * than or equal to array length.
 * @param buf the destination buffer.
 *
 * @throws ArrayIndexOutOfBoundsException if one of the indexes in the region is not valid.
 */
context(env: JniEnv)
public fun JIntArray.getRegion(start: Int, len: Int, buf: CPointer<IntVar>) {
    env.GetIntArrayRegion!!(env.ptr, c, start, len, buf)
}

/**
 * Copies a region of a `long[]` array into a buffer [buf].
 *
 * @receiver a `long[]` Java array.
 * @param start the starting index; must be greater than or equal to zero and less than the array length.
 * @param len the number of elements to be copied, must be greater than or equal to zero and `start + len` must be less
 * than or equal to array length.
 * @param buf the destination buffer.
 *
 * @throws ArrayIndexOutOfBoundsException if one of the indexes in the region is not valid.
 */
context(env: JniEnv)
public fun JLongArray.getRegion(start: Int, len: Int, buf: CPointer<LongVar>) {
    env.GetLongArrayRegion!!(env.ptr, c, start, len, buf)
}

/**
 * Copies a region of a `float[]` array into a buffer [buf].
 *
 * @receiver a `float[]` Java array.
 * @param start the starting index; must be greater than or equal to zero and less than the array length.
 * @param len the number of elements to be copied, must be greater than or equal to zero and `start + len` must be less
 * than or equal to array length.
 * @param buf the destination buffer.
 *
 * @throws ArrayIndexOutOfBoundsException if one of the indexes in the region is not valid.
 */
context(env: JniEnv)
public fun JFloatArray.getRegion(start: Int, len: Int, buf: CPointer<FloatVar>) {
    env.GetFloatArrayRegion!!(env.ptr, c, start, len, buf)
}

/**
 * Copies a region of a `double[]` array into a buffer [buf].
 *
 * @receiver a `double[]` Java array.
 * @param start the starting index; must be greater than or equal to zero and less than the array length.
 * @param len the number of elements to be copied, must be greater than or equal to zero and `start + len` must be less
 * than or equal to array length.
 * @param buf the destination buffer.
 *
 * @throws ArrayIndexOutOfBoundsException if one of the indexes in the region is not valid.
 */
context(env: JniEnv)
public fun JDoubleArray.getRegion(start: Int, len: Int, buf: CPointer<DoubleVar>) {
    env.GetDoubleArrayRegion!!(env.ptr, c, start, len, buf)
}

/**
 * Copies back a region of a `boolean[]` array from a buffer [buf].
 *
 * @receiver a `boolean[]` Java array.
 * @param start the starting index; must be greater than or equal to zero and less than the array length.
 * @param len the number of elements to be copied; must be greater than or equal to zero and `start + len` must be less
 * than or equal to the array length.
 * @param buf the source buffer.
 *
 * @throws ArrayIndexOutOfBoundsException if one of the indexes in the region is not valid.
 */
context(env: JniEnv)
public fun JBooleanArray.setRegion(start: Int, len: Int, buf: CArrayPointer<UByteVar>) {
    env.SetBooleanArrayRegion!!(env.ptr, c, start, len, buf)
}

/**
 * Copies back a region of a `byte[]` array from a buffer [buf].
 *
 * @receiver a `byte[]` Java array.
 * @param start the starting index; must be greater than or equal to zero and less than the array length.
 * @param len the number of elements to be copied; must be greater than or equal to zero and `start + len` must be less
 * than or equal to the array length.
 * @param buf the source buffer.
 *
 * @throws ArrayIndexOutOfBoundsException if one of the indexes in the region is not valid.
 */
context(env: JniEnv)
public fun JByteArray.setRegion(start: Int, len: Int, buf: CArrayPointer<ByteVar>) {
    env.SetByteArrayRegion!!(env.ptr, c, start, len, buf)
}

/**
 * Copies back a region of a `char[]` array from a buffer [buf].
 *
 * @receiver a `char[]` Java array.
 * @param start the starting index; must be greater than or equal to zero and less than the array length.
 * @param len the number of elements to be copied; must be greater than or equal to zero and `start + len` must be less
 * than or equal to the array length.
 * @param buf the source buffer.
 *
 * @throws ArrayIndexOutOfBoundsException if one of the indexes in the region is not valid.
 */
context(env: JniEnv)
public fun JCharArray.setRegion(start: Int, len: Int, buf: CArrayPointer<UShortVar>) {
    env.SetCharArrayRegion!!(env.ptr, c, start, len, buf)
}

/**
 * Copies back a region of a `short[]` array from a buffer [buf].
 *
 * @receiver a `short[]` Java array.
 * @param start the starting index; must be greater than or equal to zero and less than the array length.
 * @param len the number of elements to be copied; must be greater than or equal to zero and `start + len` must be less
 * than or equal to the array length.
 * @param buf the source buffer.
 *
 * @throws ArrayIndexOutOfBoundsException if one of the indexes in the region is not valid.
 */
context(env: JniEnv)
public fun JShortArray.setRegion(start: Int, len: Int, buf: CArrayPointer<ShortVar>) {
    env.SetShortArrayRegion!!(env.ptr, c, start, len, buf)
}

/**
 * Copies back a region of a `int[]` array from a buffer [buf].
 *
 * @receiver a `int[]` Java array.
 * @param start the starting index; must be greater than or equal to zero and less than the array length.
 * @param len the number of elements to be copied; must be greater than or equal to zero and `start + len` must be less
 * than or equal to the array length.
 * @param buf the source buffer.
 *
 * @throws ArrayIndexOutOfBoundsException if one of the indexes in the region is not valid.
 */
context(env: JniEnv)
public fun JIntArray.setRegion(start: Int, len: Int, buf: CPointer<IntVar>) {
    env.SetIntArrayRegion!!(env.ptr, c, start, len, buf)
}

/**
 * Copies back a region of a `long[]` array from a buffer [buf].
 *
 * @receiver a `long[]` Java array.
 * @param start the starting index; must be greater than or equal to zero and less than the array length.
 * @param len the number of elements to be copied; must be greater than or equal to zero and `start + len` must be less
 * than or equal to the array length.
 * @param buf the source buffer.
 *
 * @throws ArrayIndexOutOfBoundsException if one of the indexes in the region is not valid.
 */
context(env: JniEnv)
public fun JLongArray.setRegion(start: Int, len: Int, buf: CPointer<LongVar>) {
    env.SetLongArrayRegion!!(env.ptr, c, start, len, buf)
}

/**
 * Copies back a region of a `float[]` array from a buffer [buf].
 *
 * @receiver a `float[]` Java array.
 * @param start the starting index; must be greater than or equal to zero and less than the array length.
 * @param len the number of elements to be copied; must be greater than or equal to zero and `start + len` must be less
 * than or equal to the array length.
 * @param buf the source buffer.
 *
 * @throws ArrayIndexOutOfBoundsException if one of the indexes in the region is not valid.
 */
context(env: JniEnv)
public fun JFloatArray.setRegion(start: Int, len: Int, buf: CPointer<FloatVar>) {
    env.SetFloatArrayRegion!!(env.ptr, c, start, len, buf)
}

/**
 * Copies back a region of a `double[]` array from a buffer [buf].
 *
 * @receiver a `double[]` Java array.
 * @param start the starting index; must be greater than or equal to zero and less than the array length.
 * @param len the number of elements to be copied; must be greater than or equal to zero and `start + len` must be less
 * than or equal to the array length.
 * @param buf the source buffer.
 *
 * @throws ArrayIndexOutOfBoundsException if one of the indexes in the region is not valid.
 */
context(env: JniEnv)
public fun JDoubleArray.setRegion(start: Int, len: Int, buf: CPointer<DoubleVar>) {
    env.SetDoubleArrayRegion!!(env.ptr, c, start, len, buf)
}

/**
 * Registers native methods with the class specified by the receiver.
 * The [methods] parameter specifies a list of triples that contain the names, signatures and function pointers of the
 * native methods.
 *
 * The function pointers nominally must have the following signature:
 * ```
 * (env: JniEnv, objectOrClass: JObject, ...) -> JBoolean|Byte|JChar|Short|Int|Long|Float|Double|JObject|Unit
 * ```
 *
 * Be aware that [registerNatives] can change the documented behavior of the JVM (including cryptographic algorithms,
 * correctness, security, type safety), by changing the native code to be executed for a given native Java method.
 * Therefore, use applications that have native libraries utilizing the [registerNatives] function with caution.
 *
 * @throws NoSuchMethodError if a specified method cannot be found or if the method is not native.
 */
context(env: JniEnv)
public fun JClass.registerNatives(methods: CArrayPointer<JniNativeMethod>, methodsCount: Int) {
    JNI.safeCall {
        env.RegisterNatives!!(env.ptr, c, methods, methodsCount)
    }
}

/**
 * Special type that exposes method [JNINativeMethodRegistry.register] to register native methods.
 * Is used in [registerNatives] function.
 */
public typealias JNINativeMethodRegistry = (JniNativeMethod.() -> Unit) -> Unit

/**
 * Register new [JniNativeMethod] with specified [name], [signature] and [functionPointer].
 *
 * Name and signature must be in the null-terminated modified UTF-8. Use [String.modifiedUtf8] to get it, or **if you
 * are sure that your string doesn't have illegal characters** you may use optimized [String.utf8].
 */
@Suppress("NOTHING_TO_INLINE")
context(autofreeScope: AutofreeScope)
public inline fun JNINativeMethodRegistry.register(name: CValuesRef<ByteVar>, signature: CValuesRef<ByteVar>, functionPointer: JRef<CFunction<*>>): Unit = this {
    this.name = name.getPointer(autofreeScope)
    this.signature = signature.getPointer(autofreeScope)
    this.fnPtr = functionPointer
}

/**
 * Registers native methods with the class specified by the [clazz] argument.
 *
 * The function pointers nominally must have the following signature:
 * ```
 * (env: JniEnv, objectOrClass: JObject, ...) -> JBoolean|Byte|JChar|Short|Int|Long|Float|Double|JObject|Unit
 * ```
 *
 * Example:
 * ```
 * clazz.registerNatives(1) {
 *     register("myGetStringBytes".utf8, "(Ljava/lang/String;II[B)V".utf8, staticCFunction { env: JniEnv, obj: JObject, jstr: JString, offset: Int, len: Int, buf: JByteArray ->
 *         ...
 *     })
 * }
 * ```
 *
 * Be aware that [registerNatives] can change the documented behavior of the JVM (including cryptographic algorithms,
 * correctness, security, type safety), by changing the native code to be executed for a given native Java method.
 * Therefore, use applications that have native libraries utilizing the [registerNatives] function with caution.
 *
 * @throws NoSuchMethodError if a specified method cannot be found or if the method is not native.
 */
context(env: JniEnv, autofreeScope: AutofreeScope)
public inline fun JClass.registerNatives(count: Int, block: JNINativeMethodRegistry.() -> Unit) {
    val methods = autofreeScope.allocArray<JniNativeMethod>(count)
    var index = 0
    block { init ->
        methods[index++].init()
    }

    this.registerNatives(methods, index)
}

/**
 * Unregisters native methods of a receiver class. The class goes back to the state before it was linked or registered
 * with its native method functions.
 *
 * This function should not be used in normal native code. Instead, it provides special programs a way to reload and
 * relink native libraries.
 */
context(env: JniEnv)
public fun JClass.unregisterNatives() {
    JNI.safeCall {
        env.UnregisterNatives!!(env.ptr, c)
    }
}

/**
 * Enters the monitor associated with the underlying Java object referred to by [obj].
 *
 * Enters the monitor associated with the object referred to by [obj].
 *
 * Each Java object has a monitor associated with it. If the current thread already owns the monitor associated with
 * [obj], it increments a counter in the monitor indicating the number of times this thread has entered the monitor.
 * If the monitor associated with [obj] is not owned by any thread, the current thread becomes the owner of the monitor,
 * setting the entry count of this monitor to 1. If another thread already owns the monitor associated with [obj], the
 * current thread waits until the monitor is released, then tries again to gain ownership.
 *
 * A monitor entered through a [monitorEnter] JNI function call cannot be exited using the `monitorexit` Java virtual
 * machine instruction or a synchronized method return. A [monitorEnter] JNI function call and a `monitorenter` Java
 * virtual machine instruction may race to enter the monitor associated with the same object.
 *
 * To avoid deadlocks, a monitor entered through a [monitorEnter] JNI function call must be exited using the
 * [monitorExit] JNI call, unless the [JavaVM.detachCurrentThread] call is used to implicitly release JNI monitors.
 */
context(env: JniEnv)
public fun monitorEnter(obj: JObject) {
    JNI.safeCall {
        env.MonitorEnter!!(env.ptr, obj.c)
    }
}

/**
 * The current thread must be the owner of the monitor associated with the underlying Java object referred to by [obj].
 * The thread decrements the counter indicating the number of times it has entered this monitor. If the value of the
 * counter becomes zero, the current thread releases the monitor.
 *
 * Native code must not use [monitorExit] to exit a monitor entered through a synchronized method or a `monitorenter`
 * Java virtual machine instruction.
 */
context(env: JniEnv)
public fun monitorExit(obj: JObject) {
    JNI.safeCall {
        env.MonitorExit!!(env.ptr, obj.c)
    }
}

/**
 * [JavaVM] interface associated with the current thread.
 */
context(env: JniEnv, placement: NativePlacement)
public val javaVM: JavaVM get() {
    val vm = placement.allocPointerTo<JavaVM>()
    JNI.safeCall {
        env.GetJavaVM!!(env.ptr, vm.ptr)
    }
    return vm.pointed!!
}

/**
 * Copies len number of Unicode characters beginning at offset start to the given buffer [buf].
 *
 * @receiver a Java string object.
 * @param start the index of the first Unicode character in the string to copy. Must be greater than or equal to zero,
 * and less than string length.
 * @param len the number of Unicode characters to copy. Must be greater than or equal to zero and `start + len` must be
 * less than string length.
 * @param buf the Unicode character buffer into which to copy the string region.
 *
 * @throws StringIndexOutOfBoundsException on index overflow.
 *
 * @since JDK/JRE 1.2
 */
context(env: JniEnv)
public fun JString.getRegion(start: Int, len: Int, buf: CArrayPointer<UShortVar>) {
    env.GetStringRegion!!(env.ptr, c, start, len, buf)
}

/**
 * Translates len number of Unicode characters beginning at offset start into modified UTF-8 encoding and place the
 * result in the given buffer [buf].
 *
 * The [len] argument specifies the number of Unicode characters. The resulting number modified UTF-8 encoding
 * characters may be greater than the given [len] argument. [utfLength] may be used to determine the maximum
 * size of the required character buffer.
 *
 * Since this specification does not require the resulting string copy be NULL terminated, it is advisable to clear the
 * given character buffer (e.g. `memset()`) before using this function to safely perform `strlen()`.
 *
 * @receiver a Java string object.
 * @param start the index of the first Unicode character in the string to copy. Must be greater than or equal to zero,
 * and less than the string length.
 * @param len the number of Unicode characters to copy. Must be greater than zero and `start + len` must be less than
 * string length.
 * @param buf the Unicode character buffer into which to copy the string region.
 *
 * @throws StringIndexOutOfBoundsException on index overflow.
 *
 * @since JDK/JRE 1.2
 */
context(env: JniEnv)
public fun JString.getUTFRegion(start: Int, len: Int, buf: CArrayPointer<ByteVar>) {
    env.GetStringUTFRegion!!(env.ptr, c, start, len, buf)
}

/**
 * The semantics of this function is very similar to the [getElements]. If possible, the VM returns a pointer to the
 * primitive array; otherwise, a copy is made. However, there are significant restrictions on how these functions can be
 * used.
 *
 * After calling [getElementsCritical], the native code should not run for an extended period of time before it
 * calls [releaseElementsCritical]. We must treat the code inside this pair of functions as running in a
 * "critical region." Inside a critical region, native code must not call other JNI functions or any system call that
 * may cause the current thread to block and wait for another Java thread. (For example, the current thread must not
 * call read on a stream being written by another Java thread.)
 *
 * These restrictions make it more likely that the native code will get an uncopied version of the array, even if the VM
 * does not support pinning. For example, a VM may temporarily disable garbage collection when the native code is
 * holding a pointer to an array obtained via [getElementsCritical].
 *
 * Multiple pairs of [getElementsCritical] and [releaseElementsCritical] may be nested. For example:
 * ```
 * val arr1: JByteArray = // ...
 * val arr2: JByteArray = // ...
 * val len = arr1.length
 * val (a1, _) = arr1.getElementsCritical() ?: error("out of memory exception thrown")
 * val (a2, _) = arr2.getElementsCritical() ?: error("out of memory exception thrown")
 * memcpy(a1, a2, len.toULong())
 * arr1.releaseElementsCritical(a1, ApplyChangesMode.FinalCommit)
 * arr2.releaseElementsCritical(a2, ApplyChangesMode.FinalCommit)
 * ```
 *
 * Note that [getElementsCritical] might still make a copy of the array if the VM internally represents arrays in a
 * different format. Therefore, we need to check its return value against null for possible out-of-memory situations.
 *
 * @since JDK/JRE 1.2
 */
context(env: JniEnv, placement: NativePlacement)
public fun <T : CPrimitiveVar> JPrimitiveArray<T>.getElementsCritical(): Pair<CArrayPointer<T>, Boolean>? {
    val isCopy = placement.alloc<UByteVar>()
    val carray = env.GetPrimitiveArrayCritical!!(env.ptr, c, isCopy.ptr)
    return carray?.let { it.reinterpret<T>() to isCopy.value.toKBoolean() }
}

/**
 * The semantics of this function is very similar to the [releaseElements].
 *
 * Informs the VM that the native code no longer needs access to [carray].
 * The [carray] argument is a pointer derived from the receiver using the [getElementsCritical] function.
 * If necessary, this function copies back all changes made to elems to the original array.
 *
 * The [mode] argument provides information on how the array buffer should be released. [mode] has no effect if [carray]
 * is not a copy of the elements in the receiver. Otherwise, mode has the following impact:
 * - [ApplyChangesMode.FinalCommit] -> copy back the content and free the elems buffer.
 * - [ApplyChangesMode.Commit]      -> copy back the content but do not free the elems buffer.
 * - [ApplyChangesMode.Abort]       -> free the buffer without copying back the possible changes.
 *
 * After calling [getElementsCritical], the native code should not run for an extended period of time before it
 * calls [releaseElementsCritical]. We must treat the code inside this pair of functions as running in a
 * "critical region." Inside a critical region, native code must not call other JNI functions or any system call that
 * may cause the current thread to block and wait for another Java thread. (For example, the current thread must not
 * call read on a stream being written by another Java thread.)
 *
 * These restrictions make it more likely that the native code will get an uncopied version of the array, even if the VM
 * does not support pinning. For example, a VM may temporarily disable garbage collection when the native code is
 * holding a pointer to an array obtained via [getElementsCritical].
 *
 * @since JDK/JRE 1.2
 */
context(env: JniEnv)
public fun <T : CPrimitiveVar> JPrimitiveArray<T>.releaseElementsCritical(carray: CArrayPointer<T>, mode: ApplyChangesMode) {
    env.ReleasePrimitiveArrayCritical!!(env.ptr, c, carray, mode.ordinal)
}

/**
 * The semantics of this function is similar to [getChars] function. If possible, the VM returns a pointer to
 * string elements; otherwise, a copy is made. However, there are significant restrictions on how these functions can be
 * used. In a code segment enclosed by [getCharsCritical]/[releaseCharsCritical] calls, the native code must not issue
 * arbitrary JNI calls, or cause the current thread to block.
 *
 * The restrictions on [getCharsCritical]/[releaseCharsCritical] are similar to those on
 * [getElementsCritical]/[releaseElementsCritical].
 *
 * @since JDK/JRE 1.2
 */
context(env: JniEnv, placement: NativePlacement)
public fun JString.getCharsCritical(): Pair<CArrayPointer<UShortVar>, Boolean>? {
    val isCopy = placement.alloc<UByteVar>()
    val carray = env.GetStringCritical!!(env.ptr, c, isCopy.ptr)
    return carray?.let { it to isCopy.value.toKBoolean() }
}

/**
 * The semantics of this function is similar to [releaseChars] function. Informs the VM that the native code no longer
 * needs access to [carray]. However, there are significant restrictions on how these functions can be
 * used. In a code segment enclosed by [getCharsCritical]/[releaseCharsCritical] calls, the native code must not issue
 * arbitrary JNI calls, or cause the current thread to block.
 *
 * The restrictions on [getCharsCritical]/[releaseCharsCritical] are similar to those on
 * [getElementsCritical]/[releaseElementsCritical].
 *
 * @since JDK/JRE 1.2
 */
context(env: JniEnv)
public fun JString.releaseCharsCritical(carray: CArrayPointer<UShortVar>) {
    env.ReleaseStringCritical!!(env.ptr, c, carray)
}

/**
 * Allocates and returns a direct `java.nio.ByteBuffer` referring to the block of memory starting at the memory address
 * [address] and extending capacity bytes. The byte order of the returned buffer is always big-endian (high byte first;
 * `java.nio.ByteOrder.BIG_ENDIAN`).
 *
 * Native code that calls this function and returns the resulting byte-buffer object to Java-level code should ensure
 * that the buffer refers to a valid region of memory that is accessible for reading and, if appropriate, writing. An
 * attempt to access an invalid memory location from Java code will either return an arbitrary value, have no visible
 * effect or cause an unspecified exception to be thrown.
 *
 * @param address the starting address of the memory region.
 * @param capacity the size in bytes of the memory region; must be positive.
 *
 * @return a local reference to the newly instantiated `java.nio.ByteBuffer` object. Returns `null` if an exception
 * occurs, or if this virtual machine does not support JNI access to direct buffers.
 *
 * @throws OutOfMemoryError if allocation of the ByteBuffer object fails.
 *
 * @since JDK/JRE 1.4
 */
context(env: JniEnv)
public fun newDirectByteBuffer(address: COpaquePointer, capacity: Long): JByteBuffer? {
    return env.NewDirectByteBuffer!!(env.ptr, address, capacity).wrap()
}

/**
 * Fetches and returns the starting address of the memory region referenced by the given direct `java.nio.Buffer`.
 *
 * This function allows native code to access the same memory region that is accessible to Java code via the buffer
 * object.
 *
 * @receiver a direct `java.nio.Buffer` object.
 *
 * @return the starting address of the memory region referenced by the buffer. Returns `null` if the memory region is
 * undefined, if the given object is not a direct `java.nio.Buffer`, or if this virtual machine does not support JNI
 * access to direct buffers.
 *
 * @since JDK/JRE 1.4
 */
context(env: JniEnv)
public val JByteBuffer.address: COpaquePointer? get() {
    return env.GetDirectBufferAddress!!(env.ptr, c)
}

/**
 * Fetches and returns the capacity of the memory region referenced by the given direct `java.nio.Buffer`. The capacity
 * is the number of elements that the memory region contains.
 *
 * @receiver a direct `java.nio.Buffer` object.
 *
 * @return the capacity of the memory region associated with the buffer. Returns -1 if the given object is not a direct
 * `java.nio.Buffer`, if the object is an unaligned view buffer and the processor architecture does not support
 * unaligned access or if this virtual machine does not support JNI access to direct buffers.
 *
 * @since JDK/JRE 1.4
 */
context(env: JniEnv)
public val JByteBuffer.capacity: Long get() {
    return env.GetDirectBufferCapacity!!(env.ptr, c)
}