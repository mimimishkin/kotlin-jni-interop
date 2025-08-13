@file:Suppress("NOTHING_TO_INLINE", "FunctionName")

package io.github.mimimishkin.jni

import io.github.mimimishkin.jni.annotations.WithJvmType
import io.github.mimimishkin.jni.internal.raw.*
import kotlinx.cinterop.*
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlin.String
import kotlin.collections.component1
import kotlin.collections.component2

/**
 * Functions and utilities to work with JNI (Java Native Interface).
 *
 * Note: every `throws` in documentation is about java side.
 */
public object JNI

/**
 * Executes a [block] containing a JNI call that return one of `JNI_OK`, `JNI_ERR`, `JNI_EDETACHED`, `JNI_EVERSION`,
 * `JNI_ENOMEM`, `JNI_EEXIST`, `JNI_EINVAL`.
 *
 * If the returned value is not `JNI_OK` throws corresponding exception.
 *
 * If you use [JniEnv] you don't need this.
 */
public inline fun JNI.safeCall(block: () -> Int) {
    when (block()) {
        JNI_OK -> {}
        JNI_EDETACHED -> throw IllegalStateException("Thread detached from the VM")
        JNI_EVERSION -> throw IllegalArgumentException("JNI version error")
        JNI_ENOMEM -> throw IllegalStateException("Not enough memory")
        JNI_EEXIST -> throw IllegalStateException("VM already created")
        JNI_EINVAL -> throw IllegalArgumentException()
        else -> throw Exception()
    }
}

/**
 * Specifies the mode for applying changes in `Release<type>ArrayElements` and
 * [ReleasePrimitiveArrayCritical] functions.
 */
public enum class ApplyChangesMode(public val nativeCode: Int) {
    /** Commit changes back to the original array and safe the buffer. */
    Commit(JNI_COMMIT),
    /** Commit changes back to the original array and release the buffer. */
    FinalCommit(0),
    /** Abort changes and release the buffer. */
    Abort(JNI_ABORT),
}

/**
 * Represents the type of JNI reference.
 ** context(env: JniEnv)
 * @see [GetObjectRefType]
 */
public enum class JObjectRefType {
    /** Invalid reference type. */
    Invalid,
    /** Local reference. */
    Local,
    /** Global reference. */
    Global,
    /** Weak global reference. */
    WeakGlobal
}

/**
 * Java version that corresponds to the new JNI API.
 * 
 * By default, JNI functions get JNI of version [JNI.compatibilityVersion]. To request later versions, you need to
 * expose [JNI_OnLoad](https://docs.oracle.com/javase/8/docs/technotes/guides/jni/spec/invocation.html#JNJI_OnLoad).
 */
public typealias JniVersion = Int

/**
 * Java version *1.1*.
 */
public inline val JNI.v1: JniVersion get() = JNI_VERSION_1_1

/**
 * Java version *1.2*.
 */
public inline val JNI.v2: JniVersion get() = JNI_VERSION_1_2

/**
 * Java version *1.4*.
 */
public inline val JNI.v3: JniVersion get() = JNI_VERSION_1_4

/**
 * Java version *1.6*.
 */
public inline val JNI.v6: JniVersion get() = JNI_VERSION_1_6

/**
 * Java version *1.8*
 */
public inline val JNI.v8: JniVersion get() = JNI_VERSION_1_8

/**
 * Java version *9*.
 */
public inline val JNI.v9: JniVersion get() = JNI_VERSION_9

/**
 * Java version *10* and later.
 */
public inline val JNI.v10: JniVersion get() = JNI_VERSION_10

/**
 * Latest version of the JNI API.
 * 
 * This is always [JNI.v10].
 */
public inline val JNI.lastVersion: JniVersion get() = JNI.v10

/**
 * The version of the JNI that is available in JNI functions by default if not override it via
 * [JNI_OnLoad](https://docs.oracle.com/javase/8/docs/technotes/guides/jni/spec/invocation.html#JNJI_OnLoad).
 * 
 * This is always [JNI.v1].
 */
public inline val JNI.compatibilityVersion: JniVersion get() = JNI.v1

/**
 * Retrieves the default initialization arguments for the Java VM.
 *
 * Before calling this function, native code must set the [args]`.version` field to the JNI version it expects the
 * VM to support.
 * After this function returns, [args]`.version` will be set to the actual JNI version the VM supports.
 *
 * No need to call it in new JDK/JRE.
 *
 * @param args The [JavaVMInitArgs] structure to fill with default values.
 */
public inline fun JNI.GetDefaultJavaVMInitArgs(args: JavaVMInitArgs) {
    JNI.safeCall {
        JNI_GetDefaultJavaVMInitArgs(args)
    }
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
 * @see io.github.mimimishkin.jni.ext.javaVMInitArgs
 */
context(memScope: NativePlacement)
public inline fun JNI.CreateJavaVM(args: JavaVMInitArgs): Pair<JavaVM, JniEnv> {
    val vm = memScope.alloc<CPointerVar<JavaVM>>()
    val env = memScope.alloc<CPointerVar<JniEnv>>()

    JNI.safeCall {
        JNI_CreateJavaVM(
            pvm = vm.ptr,
            penv = env.ptr.reinterpret(),
            args = args
        )
    }

    return vm.pointed!! to env.pointed!!
}


/**
 * Returns all Java VMs that have been created in the order they are created.
 *
 * Creation of multiple VMs in a single process is not supported.
 */
context(memScope: NativePlacement)
public inline fun JNI.GetCreatedJavaVMs(): List<JavaVM> {
    // test invocation to get VMs count
    val count = memScope.alloc<IntVar>()
    JNI.safeCall {
        JNI_GetCreatedJavaVMs(null, 0, count.ptr)
    }

    val vms = memScope.allocArray<CPointerVar<JavaVM>>(count.value)
    JNI.safeCall {
        JNI_GetCreatedJavaVMs(vms, count.value, count.ptr)
    }

    return List(count.value) { i -> vms[i]!!.pointed }
}

/**
 * Underlying structure for [JavaVMInitArgs].
 */
internal typealias JavaVMInitArgsStruct = io.github.mimimishkin.jni.internal.raw.JavaVMInitArgs

/**
 * Java VM initialization arguments structure.
 */
public typealias JavaVMInitArgs = CPointer<JavaVMInitArgsStruct>

/**
 * Java VM initialization option.
 */
public typealias JavaVMOption = io.github.mimimishkin.jni.internal.raw.JavaVMOption

/**
 * The JNI version for this [JavaVMInitArgs].
 */
public inline var JavaVMInitArgs.version: JniVersion
    get() = pointed.version
    set(value) { pointed.version = value }

/**
 * Whether unrecognized options are ignored for this [JavaVMInitArgs].
 */
public inline var JavaVMInitArgs.ignoreUnrecognized: Boolean
    get() = pointed.ignoreUnrecognized.toKBoolean()
    set(value) { pointed.ignoreUnrecognized = value.toJBoolean() }

/**
 * optionString/extraInfo pairs list for this [JavaVMInitArgs].
 */
context(memScope: AutofreeScope)
public inline var JavaVMInitArgs.options: Map<String, COpaquePointer?>
    get() = (0 until pointed.nOptions).associate { i -> 
        pointed.options!![i].optionString!!.toKString() to pointed.options!![i].extraInfo
    }
    set(value) {
        pointed.nOptions = value.size
        pointed.options = memScope.allocArray<JavaVMOption>(value.size).apply {
            value.asIterable().forEachIndexed { index, (option, extra) ->
                this[index].optionString = option.cstr.getPointer(memScope)
                this[index].extraInfo = extra
            }
        }
    }

/**
 * Converts a String into a null-terminated, modified UTF-8 encoded byte sequence for interoperation
 * with JNI functions.
 */
public val String.modifiedUtf8: CValues<ByteVar> get() {
    // Estimate the max possible length: 3 bytes per char + 1 for null terminator
    val bytes = ByteArray(this.length * 3 + 1)
    var pos = 0
    for (ch in this) {
        when (ch) {
            '\u0000' -> {
                // Null char is encoded as 0xC0 0x80
                bytes[pos++] = 0xC0.toByte()
                bytes[pos++] = 0x80.toByte()
            }
            in '\u0001'..'\u007F' -> {
                // 1-byte encoding
                bytes[pos++] = ch.code.toByte()
            }
            in '\u0080'..'\u07FF' -> {
                // 2-byte encoding
                bytes[pos++] = (0xC0 or (ch.code shr 6)).toByte()
                bytes[pos++] = (0x80 or (ch.code and 0x3F)).toByte()
            }
            else -> {
                // 3-byte encoding (including surrogates)
                bytes[pos++] = (0xE0 or (ch.code shr 12)).toByte()
                bytes[pos++] = (0x80 or ((ch.code shr 6) and 0x3F)).toByte()
                bytes[pos++] = (0x80 or (ch.code and 0x3F)).toByte()
            }
        }
    }

    return object : CValues<ByteVar>() {
        override val size = pos + 1
        override val align = 1

        override fun place(placement: CPointer<ByteVar>): CPointer<ByteVar> {
            for (i in 0..<size) placement[i] = bytes[i]
            // Null-terminate
            placement[size] = 0
            return placement
        }
    }
}

/**
 * JVM interprets `boolean` values as unsigned byte.
 */
@WithJvmType("boolean")
public typealias JBoolean = UByte

/**
 * Converts Kotlin `boolean` to JVM `boolean`.
 */
public inline fun Boolean.toJBoolean(): JBoolean = if (this) JNI_TRUE.toUByte() else JNI_FALSE.toUByte()

/**
 * Converts JVM `boolean` to Kotlin `boolean`.
 */
public inline fun JBoolean.toKBoolean(): Boolean = this == JNI_TRUE.toUByte()

/**
 * JVM interprets `byte` values the same way as Kotlin.
 */
@WithJvmType("byte")
public typealias JByte = Byte

/**
 * JVM interprets `char` values as unsigned short.
 */
@WithJvmType("char")
public typealias JChar = UShort

/**
 * Converts Kotlin `char` to JVM `char`.
 */
public inline fun Char.toJChar(): JChar = code.toUShort()

/**
 * Converts JVM `char` to Kotlin `char`.
 */
public inline fun JChar.toKChar(): Char = Char(this)

/**
 * JVM interprets `short` values the same way as Kotlin.
 */
@WithJvmType("short")
public typealias JShort = Short

/**
 * JVM interprets `int` values the same way as Kotlin.
 */
@WithJvmType("int")
public typealias JInt = Int

/**
 * JVM interprets `long` values the same way as Kotlin.
 */
@WithJvmType("long")
public typealias JLong = Long

/**
 * JVM interprets `float` values the same way as Kotlin.
 */
@WithJvmType("float")
public typealias JFloat = Float

/**
 * JVM interprets `double` values the same way as Kotlin.
 */
@WithJvmType("double")
public typealias JDouble = Double

/**
 * Allows explicitly specifying that a function doesn't return any value in the same way as if it returned something and
 * we've used [JBoolean], [JByte], [JChar] or others.
 */
@WithJvmType("void")
public typealias JVoid = Unit

/**
 * Pointer to `java.lang.Object`.
 */
@WithJvmType("java.lang.Object")
public typealias JObject = CPointer<_jobject>

/**
 * Pointer to `java.lang.Class`.
 */
@WithJvmType("java.lang.Class")
public typealias JClass = JObject

/**
 * Pointer to `java.lang.Throwable`.
 */
@WithJvmType("java.lang.Throwable")
public typealias JThrowable = JObject

/**
 * Pointer to `java.lang.String`.
 */
@WithJvmType("java.lang.String")
public typealias JString = JObject

/**
 * Pointer to an array.
 */
public typealias JArray = JObject

/**
 * Pointer to a boolean[] object.
 */
@WithJvmType("boolean[]")
public typealias JBooleanArray = JArray

/**
 * Pointer to a byte[] object.
 */
@WithJvmType("byte[]")
public typealias JByteArray = JArray

/**
 * Pointer to a char[] object.
 */
@WithJvmType("char[]")
public typealias JCharArray = JArray

/**
 * Pointer to a short[] object.
 */
@WithJvmType("short[]")
public typealias JShortArray = JArray

/**
 * Pointer to an int[] object.
 */
@WithJvmType("int[]")
public typealias JIntArray = JArray

/**
 * Pointer to a long[] object.
 */
@WithJvmType("long[]")
public typealias JLongArray = JArray

/**
 * Pointer to a float[] object.
 */
@WithJvmType("float[]")
public typealias JFloatArray = JArray

/**
 * Pointer to a double[] object.
 */
@WithJvmType("double[]")
public typealias JDoubleArray = JArray

/**
 * Pointer to an array of an object type.
 */
@WithJvmType("java.lang.Object[]")
public typealias JObjectArray = JArray

/**
 * Pointer to `java.lang.Object` which is not counted by GC.
 */
public typealias JWeak = JObject

/**
 * Union in which JNI expect arguments to pass to Java functions.
 */
public typealias JValue = jvalue

/**
 * C array of [JValue].
 *
 * Example:
 * ```
 * val clazz = FindClass("path/to/Class")!!
 * val method = GetMethodID(clazz, "myMethod", "(Ljava/lang/String;I)Ljava/lang/String;")!!
 * val javaString = "Meow".toJString()
 * val javaRes = CallStaticObjectMethod(clazz, method, jArgs { ref(javaString); int(42) })
 * val res = javaRes?.toKString()
 * ```
 *
 * @see io.github.mimimishkin.jni.ext.jArgs
 */
public typealias JArguments = CArrayPointer<JValue>

/**
 * Java unique field ID.
 */
public typealias JFieldID = CPointer<_jfieldID>

/**
 * Java unique method ID.
 */
public typealias JMethodID = CPointer<_jmethodID>

/**
 * Struct to operate with Invocation API.
 */
public typealias JavaVM = CPointerVar<JNIInvokeInterface_>

/**
 * Unloads a Java VM and reclaims its resources.
 *
 * Any thread, whether attached or not, can invoke this function.
 * If the current thread is attached, the VM waits until the current thread is the only non-daemon user-level Java
 * thread.
 * If the current thread is not attached, the VM attaches the current thread and then waits until the current thread is
 * the only non-daemon user-level thread.
 */
public inline fun JavaVM.DestroyJavaVM() {
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
 * **if you are sure that your string doesn't have illegal characters** you may use optimized [String.utf8].
 * @param group global ref of a `ThreadGroup` object.
 */
context(memScope: AutofreeScope)
public inline fun JavaVM.AttachCurrentThread(
    version: JniVersion = JNI.lastVersion,
    name: CValuesRef<ByteVar>? = null,
    group: JObject? = null
): JniEnv {
    val env = memScope.alloc<COpaquePointerVar>()

    val args = memScope.alloc<JavaVMAttachArgs> {
        this.version = version
        this.name = name?.getPointer(memScope)
        this.group = group
    }

    JNI.safeCall {
        pointed!!.AttachCurrentThread!!(ptr, env.ptr, args.ptr)
    }

    return env.reinterpret<CPointerVar<JniEnv>>().pointed!!
}

/**
 * Same semantics as [AttachCurrentThread], but the newly created java.lang.Thread instance is a daemon.
 *
 * If the thread has already been attached via either AttachCurrentThread or AttachCurrentThreadAsDaemon, this routine
 * simply returns JNIEnv of the current thread. In this case neither AttachCurrentThread nor this routine have any
 * effect on the daemon status of the thread.
 *
 * @param version the requested JNI version.
 * @param name the name of the thread in the null-terminated modified UTF-8. Use [String.modifiedUtf8] to get it, or
 * **if you are sure that your string doesn't have illegal characters** you may use optimized [String.utf8].
 * @param group global ref of a ThreadGroup object.
 */
context(memScope: AutofreeScope)
public inline fun JavaVM.AttachCurrentThreadAsDaemon(
    version: JniVersion = JNI.lastVersion,
    name: CValuesRef<ByteVar>? = null,
    group: JObject? = null
): JniEnv {
    val env = memScope.alloc<COpaquePointerVar>()
    val args = memScope.alloc<JavaVMAttachArgs> {
        this.version = version
        this.name = name?.getPointer(memScope)
        this.group = group
    }

    JNI.safeCall {
        pointed!!.AttachCurrentThreadAsDaemon!!(ptr, env.ptr, args.ptr)
    }

    return env.reinterpret<CPointerVar<JniEnv>>().pointed!!
}

/**
 * Detaches the current thread from a Java VM.
 * All Java monitors held by this thread are released. All Java threads waiting for this thread to die are notified.
 *
 * The main thread can be detached from the VM.
 */
public inline fun JavaVM.DetachCurrentThread() {
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
context(memScope: NativePlacement)
public inline fun JavaVM.GetEnv(version: JniVersion = JNI.lastVersion): JniEnv {
    val env = memScope.alloc<COpaquePointerVar>()
    JNI.safeCall {
        pointed!!.GetEnv!!(ptr, env.ptr, version)
    }
    return env.reinterpret<CPointerVar<JniEnv>>().pointed!!
}

/**
 * Struct to operate with Native JNI API.
 */
public typealias JniEnv = CPointerVar<JNINativeInterface_>

/**
 * Returns the version of the native method interface.
 * For Java SE Platform 10 and later, it returns [JNI.v10].
 */
context(env: JniEnv)
public inline fun GetVersion(): JniVersion {
    return env.pointed!!.GetVersion!!(env.ptr)
}

/**
 * Loads a class from a [classBuf] of raw class data.
 *
 * The buffer containing the raw class data is not referenced by the VM after the [DefineClass] call returns, and it may
 * be discarded if desired.
 *
 * @param name the name of the class or interface to be defined. May be `null`, or it must match the name encoded within
 * the class file data. Must be encoded in the null-terminated modified UTF-8. Use [String.modifiedUtf8] to get it or
 * **if you are sure that your string doesn't have illegal characters** you may use optimized [String.utf8].
 * @param loader a class loader assigned to the defined class. May be `null`, indicating the "null class loader" (or
 * "bootstrap class loader").
 * @param classBuf buffer containing the `.class` file data.
 * @param classBufLen buffer length.
 *
 * @return a Java class object or `null` if an error occurs.
 *
 * @throws ClassFormatError if the class data does not specify a valid class.
 * @throws ClassCircularityError if a class or interface is its own superclass or superinterface.
 * @throws OutOfMemoryError if the system runs out of memory.
 * @throws SecurityException if the caller attempts to define a class in the "java" package tree.
 */
context(env: JniEnv, memScope: AutofreeScope)
public inline fun DefineClass(name: CValuesRef<ByteVar>?, loader: JObject?, classBuf: CPointer<ByteVar>, classBufLen: Int): JClass? {
    return env.pointed!!.DefineClass!!(env.ptr, name?.getPointer(memScope), loader, classBuf, classBufLen)
}

/**
 * In JDK release 1.1, this function loads a locally defined class. It searches the directories and zip files specified
 * by the CLASSPATH environment variable for the class with the specified name.
 *
 * Since JDK 1.2, the Java security model allows non-system classes to load and call native methods. [FindClass] locates
 * the class loader associated with the current native method; that is, the class loader of the class that declared the
 * native method. If the native method belongs to a system class, no class loader will be involved.
 * Otherwise, the proper class loader will be invoked to load, link and initialize the named class.
 *
 * Since JDK 1.2, when [FindClass] is called through the Invocation Interface, there is no current native method or its
 * associated class loader. In that case, the result of `ClassLoader.getSystemClassLoader` is used. This is the class
 * loader the virtual machine creates for applications, and is able to locate classes listed in the
 * `java.class.path` property.
 *
 * If [FindClass] is called from a library lifecycle function hook, the class loader is determined as follows:
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
context(env: JniEnv, memScope: AutofreeScope)
public inline fun FindClass(name: CValuesRef<ByteVar>): JClass? {
    return env.pointed!!.FindClass!!(env.ptr, name.getPointer(memScope))
}

/**
 * Converts a `java.lang.reflect.Method` or `java.lang.reflect.Constructor` object to a method ID.
 *
 * @return A JNI method ID that corresponds to the given Java reflection method, or `null` if the operation fails.
 *
 * @since JDK/JRE 1.2
 */
context(env: JniEnv)
public inline fun FromReflectedMethod(method: JObject): JMethodID? {
    return env.pointed!!.FromReflectedMethod!!(env.ptr, method)
}

/**
 * Converts a `java.lang.reflect.Field` to a field ID.
 *
 * @return A JNI field ID that corresponds to the given Java reflection field, or `null` if the operation fails.
 *
 * @since JDK/JRE 1.2
 */
context(env: JniEnv)
public inline fun FromReflectedField(field: JObject): JFieldID? {
    return env.pointed!!.FromReflectedField!!(env.ptr, field)
}

/**
 * Converts a method ID derived from [cls] to a `java.lang.reflect.Method` or `java.lang.reflect.Constructor` object.
 * [isStatic] must be set to `true` if the method ID refers to a static field.
 *
 * @return Returns an instance of the `java.lang.reflect.Method` or `java.lang.reflect.Constructor` which corresponds to
 * the given methodID, or `null` if the operation fails.
 *
 * @throws OutOfMemoryError if fails.
 *
 * @since JDK/JRE 1.2
 */
context(env: JniEnv)
public inline fun ToReflectedMethod(cls: JClass, methodID: JMethodID, isStatic: Boolean): JObject? {
    return env.pointed!!.ToReflectedMethod!!(env.ptr, cls, methodID, isStatic.toJBoolean())
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
 */
context(env: JniEnv)
public inline fun ToReflectedField(cls: JClass, fieldID: JFieldID, isStatic: Boolean): JObject? {
    return env.pointed!!.ToReflectedField!!(env.ptr, cls, fieldID, isStatic.toJBoolean())
}

/**
 * If [clazz] represents any class other than the class Object, then this function returns the object that represents
 * the superclass of the class specified by [clazz].
 *
 * If [clazz] specifies the class Object, or [clazz] represents an interface, this function returns `null`.
 *
 * @return the superclass of the class represented by clazz, or `null`.
 */
context(env: JniEnv)
public inline fun GetSuperclass(clazz: JClass): JClass? {
    return env.pointed!!.GetSuperclass!!(env.ptr, clazz)
}

/**
 * Determines whether an object of [clazz1] can be safely cast to [clazz2].
 *
 * Returns `true` if either of the following is true:
 * - The first and the second class arguments refer to the same Java class.
 * - The first class is a subclass of the second class.
 * - The first class has the second class as one of its interfaces.
 */
context(env: JniEnv)
public inline fun IsAssignableFrom(clazz1: JClass, clazz2: JClass): Boolean {
    return env.pointed!!.IsAssignableFrom!!(env.ptr, clazz1, clazz2).toKBoolean()
}

/**
 * Causes a `java.lang.Throwable` object to be thrown.
 */
context(env: JniEnv)
public inline fun Throw(throwable: JThrowable) {
    JNI.safeCall {
        env.pointed!!.Throw!!(env.ptr, throwable)
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
context(env: JniEnv, memScope: AutofreeScope)
public inline fun ThrowNew(clazz: JClass, message: CValuesRef<ByteVar>?) {
    JNI.safeCall {
        env.pointed!!.ThrowNew!!(env.ptr, clazz, message?.getPointer(memScope))
    }
}

/**
 * Determines if an exception is being thrown. The exception stays being thrown until either the native code calls
 * [ExceptionClear], or the Java code handles the exception.
 *
 * @return Returns the exception object currently in the process of being thrown, or `null` if there is no one.
 *
 * @see ExceptionCheck
 */
context(env: JniEnv)
public inline fun ExceptionOccurred(): JThrowable? {
    return env.pointed!!.ExceptionOccurred!!(env.ptr)
}

/**
 * Prints an exception and a backtrace of the stack to a system error-reporting channel, such as stderr.
 *
 * The pending exception is cleared as a side effect of calling this function.
 * This is a convenience routine provided for debugging.
 */
context(env: JniEnv)
public inline fun ExceptionDescribe() {
    env.pointed!!.ExceptionDescribe!!(env.ptr)
}

/**
 * Clears any exception that is currently being thrown.
 * If no exception is currently being thrown, this routine has no effect.
 */
context(env: JniEnv)
public inline fun ExceptionClear() {
    env.pointed!!.ExceptionClear!!(env.ptr)
}

/**
 * Raises a fatal error and does not expect the VM to recover.
 *
 * This function does not return.
 *
 * @param message an error message in the null-terminated modified UTF-8. Use [String.modifiedUtf8] to get it, or **if
 * you are sure that your string doesn't have illegal characters** you may use optimized [String.utf8].
 */
context(env: JniEnv, memScope: AutofreeScope)
public inline fun FatalError(message: CValuesRef<ByteVar>?): Nothing {
    env.pointed!!.FatalError!!(env.ptr, message?.getPointer(memScope))
    throw Error()
}

/**
 * A convenience function to check for pending exceptions without creating a local reference to the exception object.
 *
 * @return `true` when there is a pending exception, `false` otherwise.
 */
context(env: JniEnv)
public inline fun ExceptionCheck(): Boolean {
    return env.pointed!!.ExceptionCheck!!(env.ptr).toKBoolean()
}

/**
 * Deletes the local reference pointed to by [localRef].
 *
 * Note: JDK/JRE 1.1 provides the DeleteLocalRef function above so that programmers can manually delete local
 * references. For example, if native code iterates through a potentially large array of objects and uses one element
 * in each iteration, it is a good practice to delete the local reference to the no-longer-used array element before a
 * new local reference is created in the next iteration.
 *
 * As of JDK/JRE 1.2 an additional set of functions are provided: [EnsureLocalCapacity], [PushLocalFrame],
 * [PopLocalFrame] and [NewLocalRef].
 */
context(env: JniEnv)
public inline fun DeleteLocalRef(localRef: JObject) {
    env.pointed!!.DeleteLocalRef!!(env.ptr, localRef)
}

/**
 * Ensures that at least a given number of local references can be created in the current thread.
 *
 * Before it enters a native method, the VM automatically ensures that at least 16 local references can be created.
 *
 * For backward compatibility, the VM allocates local references beyond the ensured capacity. (As a debugging support,
 * the VM may give the user warnings that too many local references are being created. In the JDK, the programmer can
 * supply the `-verbose:jni` command line option to turn on these messages.)
 * The VM calls [FatalError] if no more local references can be created beyond the ensured capacity.
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
public inline fun EnsureLocalCapacity(capacity: Int) {
    JNI.safeCall {
        env.pointed!!.EnsureLocalCapacity!!(env.ptr, capacity)
    }
}

/**
 * Creates a new local reference frame, in which at least a given number of local references can be created.
 *
 * Note that local references already created in previous local frames are still valid in the current local frame.
 *
 * As with [EnsureLocalCapacity], some Java Virtual Machine implementations may choose to limit the maximum capacity,
 * which may cause the function to throw an exception.
 *
 * @param capacity the minimum number of required local references. Must be > 0.
 *
 * @throws OutOfMemoryError if fails.
 *
 * @since JDK/JRE 1.2
 */
context(env: JniEnv)
public inline fun PushLocalFrame(capacity: Int) {
    JNI.safeCall {
        env.pointed!!.PushLocalFrame!!(env.ptr, capacity)
    }
}

/**
 * Pops off the current local reference frame, frees all the local references and returns a local reference in the
 * previous local reference frame for the given result object.
 *
 * Pass `null` as [result] if you do not need to return a reference to the previous frame.
 *
 * @since JDK/JRE 1.2
 */
context(env: JniEnv)
public inline fun PopLocalFrame(result: JObject? = null): JObject? {
    return env.pointed!!.PopLocalFrame!!(env.ptr, result)
}

/**
 * Creates and returns a new local reference that refers to the same object as [ref].
 * The given [ref] may be a global or a local reference.
 *
 * May return `null` if:
 * - the system has run out of memory
 * - [ref] was a weak global reference and has already been garbage collected.
 */
context(env: JniEnv)
public inline fun NewLocalRef(ref: JObject): JObject? {
    return env.pointed!!.NewLocalRef!!(env.ptr, ref)
}

/**
 * Creates and returns a new global reference to the object referred to by the [obj] argument.
 * The [obj] argument may be a global or local reference.
 *
 * Global references must be explicitly disposed of by calling [DeleteGlobalRef].
 *
 * May return `null` if:
 * - the system has run out of memory
 * - [obj] was a weak global reference and has already been garbage collected.
 */
context(env: JniEnv)
public inline fun NewGlobalRef(obj: JObject): JObject? {
    return env.pointed!!.NewGlobalRef!!(env.ptr, obj)
}

/**
 * Deletes the global reference pointed to by globalRef.
 */
context(env: JniEnv)
public inline fun DeleteGlobalRef(globalRef: JObject) {
    env.pointed!!.DeleteGlobalRef!!(env.ptr, globalRef)
}

/**
 * Allocates a new Java object without invoking any of the constructors for the object.
 *
 * Note: The Java Language Specification, "Implementing Finalization" (JLS §12.6.1) states: "An object o is not
 * finalizable until its constructor has invoked the constructor for Object on o and that invocation has completed
 * successfully". Since [AllocObject] does not invoke a constructor, objects created with this function are not eligible
 * for finalization.
 *
 * The clazz argument must not refer to an array class.
 *
 * @return a Java object, or `null` if the object cannot be constructed.
 *
 * @throws InstantiationException if the class is an interface or an abstract class.
 * @throws OutOfMemoryError if the system runs out of memory.
 */
context(env: JniEnv)
public inline fun AllocObject(clazz: JClass): JObject? {
    return env.pointed!!.AllocObject!!(env.ptr, clazz)
}

/**
 * Constructs a new Java object.
 * The [methodID] indicates which constructor method to invoke.
 * This ID must be obtained by calling [GetMethodID] with `"<init>"` as the method name and void (`V`) as the return type.
 *
 * The [clazz] argument must not refer to an array class.
 *
 * @return a Java object, or `null` if the object cannot be constructed.
 *
 * @throws InstantiationException if the class is an interface or an abstract class.
 * @throws OutOfMemoryError if the system runs out of memory.
 * @throws other Any exceptions thrown by the constructor.
 */
context(env: JniEnv)
public inline fun NewObject(clazz: JClass, methodID: JMethodID, args: JArguments): JObject? {
    return env.pointed!!.NewObjectA!!(env.ptr, clazz, methodID, args)
}

/**
 * Returns the class of an object.
 */
context(env: JniEnv)
public inline fun GetObjectClass(obj: JObject): JClass {
    return env.pointed!!.GetObjectClass!!(env.ptr, obj)!!
}

/**
 * Returns the type of the object referred to by the obj argument.
 *
 * @since JDK/JRE 1.6
 */
context(env: JniEnv)
public inline fun GetObjectRefType(obj: JObject?): JObjectRefType {
    return when (env.pointed!!.GetObjectRefType!!(env.ptr, obj)) {
        JNIWeakGlobalRefType -> JObjectRefType.WeakGlobal
        JNIGlobalRefType -> JObjectRefType.Global
        JNILocalRefType -> JObjectRefType.Local
        else -> JObjectRefType.Invalid
    }
}

/**
 * Tests whether an object is an instance of a class.
 *
 * @return `true` if [obj] can be cast to [clazz], false otherwise. A `null` can be cast to any class.
 */
context(env: JniEnv)
public inline fun IsInstanceOf(obj: JObject?, clazz: JClass): Boolean {
    return env.pointed!!.IsInstanceOf!!(env.ptr, obj, clazz).toKBoolean()
}

/**
 * Tests whether two references point to the same Java object.
 *
 * @return `true` if `ref1` and `ref2` refer to the same Java object, or both are null, false otherwise.
 */
context(env: JniEnv)
public inline fun IsSameObject(ref1: JObject?, ref2: JObject?): Boolean {
    return env.pointed!!.IsSameObject!!(env.ptr, ref1, ref2).toKBoolean()
}

/**
 * Returns the method ID for an instance (nonstatic) method of a class or interface.
 * The method may be defined in one of the [clazz]’s supertypes and inherited by [clazz].
 * The method is determined by its name and signature.
 *
 * [GetMethodID] causes an uninitialized class to be initialized.
 *
 * To get the method ID of a constructor, supply `"<init>"` as the method name and void (`V`) as the return type.
 *
 * @param clazz a Java class object.
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
context(env: JniEnv, memScope: AutofreeScope)
public inline fun GetMethodID(clazz: JClass, name: CValuesRef<ByteVar>, sig: CValuesRef<ByteVar>): JMethodID? {
    return env.pointed!!.GetMethodID!!(env.ptr, clazz, name.getPointer(memScope), sig.getPointer(memScope))
}

/**
 * Invokes an instance (nonstatic) method on a Java object, according to the specified method ID.
 * The [methodID] argument must be obtained by calling [GetMethodID].
 *
 * When used to call private methods and constructors, the method ID must be derived from the real class of [obj], not
 * from one of its superclasses.
 *
 * You should use this function only if the Java method you are calling returns `Object` values.
 *
 * @return the result of calling the Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public inline fun CallObjectMethod(obj: JObject, methodID: JMethodID, args: JArguments): JObject? {
    return env.pointed!!.CallObjectMethodA!!(env.ptr, obj, methodID, args)
}

/**
 * Invokes an instance (nonstatic) method on a Java object, according to the specified method ID.
 * The [methodID] argument must be obtained by calling [GetMethodID].
 *
 * When used to call private methods and constructors, the method ID must be derived from the real class of [obj], not
 * from one of its superclasses.
 *
 * You should use this function only if the Java method you are calling returns `boolean` values.
 *
 * @return the result of calling the Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public inline fun CallBooleanMethod(obj: JObject, methodID: JMethodID, args: JArguments): Boolean {
    return env.pointed!!.CallBooleanMethodA!!(env.ptr, obj, methodID, args).toKBoolean()
}

/**
 * Invokes an instance (nonstatic) method on a Java object, according to the specified method ID.
 * The [methodID] argument must be obtained by calling [GetMethodID].
 *
 * When used to call private methods and constructors, the method ID must be derived from the real class of [obj], not
 * from one of its superclasses.
 *
 * You should use this function only if the Java method you are calling returns `byte` values.
 *
 * @return the result of calling the Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public inline fun CallByteMethod(obj: JObject, methodID: JMethodID, args: JArguments): Byte {
    return env.pointed!!.CallByteMethodA!!(env.ptr, obj, methodID, args)
}

/**
 * Invokes an instance (nonstatic) method on a Java object, according to the specified method ID.
 * The [methodID] argument must be obtained by calling [GetMethodID].
 *
 * When used to call private methods and constructors, the method ID must be derived from the real class of [obj], not
 * from one of its superclasses.
 *
 * You should use this function only if the Java method you are calling returns `char` values.
 *
 * @return the result of calling the Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public inline fun CallCharMethod(obj: JObject, methodID: JMethodID, args: JArguments): Char {
    return env.pointed!!.CallCharMethodA!!(env.ptr, obj, methodID, args).toKChar()
}

/**
 * Invokes an instance (nonstatic) method on a Java object, according to the specified method ID.
 * The [methodID] argument must be obtained by calling [GetMethodID].
 *
 * When used to call private methods and constructors, the method ID must be derived from the real class of [obj], not
 * from one of its superclasses.
 *
 * You should use this function only if the Java method you are calling returns `short` values.
 *
 * @return the result of calling the Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public inline fun CallShortMethod(obj: JObject, methodID: JMethodID, args: JArguments): Short {
    return env.pointed!!.CallShortMethodA!!(env.ptr, obj, methodID, args)
}

/**
 * Invokes an instance (nonstatic) method on a Java object, according to the specified method ID.
 * The [methodID] argument must be obtained by calling [GetMethodID].
 *
 * When used to call private methods and constructors, the method ID must be derived from the real class of [obj], not
 * from one of its superclasses.
 *
 * You should use this function only if the Java method you are calling returns `int` values.
 *
 * @return the result of calling the Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public inline fun CallIntMethod(obj: JObject, methodID: JMethodID, args: JArguments): Int {
    return env.pointed!!.CallIntMethodA!!(env.ptr, obj, methodID, args)
}

/**
 * Invokes an instance (nonstatic) method on a Java object, according to the specified method ID.
 * The [methodID] argument must be obtained by calling [GetMethodID].
 *
 * When used to call private methods and constructors, the method ID must be derived from the real class of [obj], not
 * from one of its superclasses.
 *
 * You should use this function only if the Java method you are calling returns `long` values.
 *
 * @return the result of calling the Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public inline fun CallLongMethod(obj: JObject, methodID: JMethodID, args: JArguments): Long {
    return env.pointed!!.CallLongMethodA!!(env.ptr, obj, methodID, args)
}

/**
 * Invokes an instance (nonstatic) method on a Java object, according to the specified method ID.
 * The [methodID] argument must be obtained by calling [GetMethodID].
 *
 * When used to call private methods and constructors, the method ID must be derived from the real class of [obj], not
 * from one of its superclasses.
 *
 * You should use this function only if the Java method you are calling returns `float` values.
 *
 * @return the result of calling the Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public inline fun CallFloatMethod(obj: JObject, methodID: JMethodID, args: JArguments): Float {
    return env.pointed!!.CallFloatMethodA!!(env.ptr, obj, methodID, args)
}

/**
 * Invokes an instance (nonstatic) method on a Java object, according to the specified method ID.
 * The [methodID] argument must be obtained by calling [GetMethodID].
 *
 * When used to call private methods and constructors, the method ID must be derived from the real class of [obj], not
 * from one of its superclasses.
 *
 * You should use this function only if the Java method you are calling returns `double` values.
 *
 * @return the result of calling the Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public inline fun CallDoubleMethod(obj: JObject, methodID: JMethodID, args: JArguments): Double {
    return env.pointed!!.CallDoubleMethodA!!(env.ptr, obj, methodID, args)
}

/**
 * Invokes an instance (nonstatic) method on a Java object, according to the specified method ID.
 * The [methodID] argument must be obtained by calling [GetMethodID].
 *
 * When used to call private methods and constructors, the method ID must be derived from the real class of [obj], not
 * from one of its superclasses.
 *
 * You should use this function only if the Java method you are calling doesn't return values.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public inline fun CallVoidMethod(obj: JObject, methodID: JMethodID, args: JArguments) {
    return env.pointed!!.CallVoidMethodA!!(env.ptr, obj, methodID, args)
}

/**
 * Invokes an instance (nonstatic) method on a Java object, according to the specified class and method ID.
 * The [methodID] argument must be obtained by calling [GetMethodID] on the class clazz.
 *
 * Unlike [CallObjectMethod] which invokes the method based on the class or interface of the object, this method invokes
 * the method based on the class, designated by the [clazz] parameter, from which the method ID is obtained.
 *
 * The method ID must be obtained from the real class of the object or from one of its supertypes.
 *
 * `CallNonvirtual<type>Method` routines are the mechanism for invoking "default interface methods" introduced in Java 8.
 *
 * You should use this function only if the Java method you are calling returns `Object` values.
 *
 * @return the result of calling the Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public inline fun CallNonvirtualObjectMethod(clazz: JClass, obj: JObject, methodID: JMethodID, args: JArguments): JObject? {
    return env.pointed!!.CallNonvirtualObjectMethodA!!(env.ptr, clazz, obj, methodID, args)
}

/**
 * Invokes an instance (nonstatic) method on a Java object, according to the specified class and method ID.
 * The [methodID] argument must be obtained by calling [GetMethodID] on the class clazz.
 *
 * Unlike [CallBooleanMethod] which invokes the method based on the class or interface of the object, this method invokes
 * the method based on the class, designated by the [clazz] parameter, from which the method ID is obtained.
 *
 * The method ID must be obtained from the real class of the object or from one of its supertypes.
 *
 * `CallNonvirtual<type>Method` routines are the mechanism for invoking "default interface methods" introduced in Java 8.
 *
 * You should use this function only if the Java method you are calling returns `boolean` values.
 *
 * @return the result of calling the Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */ context(env: JniEnv)
public inline fun CallNonvirtualBooleanMethod(clazz: JClass, obj: JObject, methodID: JMethodID, args: JArguments): Boolean {
    return env.pointed!!.CallNonvirtualBooleanMethodA!!(env.ptr, clazz, obj, methodID, args).toKBoolean()
}

/**
 * Invokes an instance (nonstatic) method on a Java object, according to the specified class and method ID.
 * The [methodID] argument must be obtained by calling [GetMethodID] on the class clazz.
 *
 * Unlike [CallByteMethod] which invokes the method based on the class or interface of the object, this method invokes
 * the method based on the class, designated by the [clazz] parameter, from which the method ID is obtained.
 *
 * The method ID must be obtained from the real class of the object or from one of its supertypes.
 *
 * `CallNonvirtual<type>Method` routines are the mechanism for invoking "default interface methods" introduced in Java 8.
 *
 * You should use this function only if the Java method you are calling returns `byte` values.
 *
 * @return the result of calling the Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public inline fun CallNonvirtualByteMethod(clazz: JClass, obj: JObject, methodID: JMethodID, args: JArguments): Byte {
    return env.pointed!!.CallNonvirtualByteMethodA!!(env.ptr, clazz, obj, methodID, args)
}

/**
 * Invokes an instance (nonstatic) method on a Java object, according to the specified class and method ID.
 * The [methodID] argument must be obtained by calling [GetMethodID] on the class clazz.
 *
 * Unlike [CallCharMethod] which invokes the method based on the class or interface of the object, this method invokes
 * the method based on the class, designated by the [clazz] parameter, from which the method ID is obtained.
 *
 * The method ID must be obtained from the real class of the object or from one of its supertypes.
 *
 * `CallNonvirtual<type>Method` routines are the mechanism for invoking "default interface methods" introduced in Java 8.
 *
 * You should use this function only if the Java method you are calling returns `char` values.
 *
 * @return the result of calling the Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public inline fun CallNonvirtualCharMethod(clazz: JClass, obj: JObject, methodID: JMethodID, args: JArguments): Char {
    return env.pointed!!.CallNonvirtualCharMethodA!!(env.ptr, clazz, obj, methodID, args).toKChar()
}

/**
 * Invokes an instance (nonstatic) method on a Java object, according to the specified class and method ID.
 * The [methodID] argument must be obtained by calling [GetMethodID] on the class clazz.
 *
 * Unlike [CallShortMethod] which invokes the method based on the class or interface of the object, this method invokes
 * the method based on the class, designated by the [clazz] parameter, from which the method ID is obtained.
 *
 * The method ID must be obtained from the real class of the object or from one of its supertypes.
 *
 * `CallNonvirtual<type>Method` routines are the mechanism for invoking "default interface methods" introduced in Java 8.
 *
 * You should use this function only if the Java method you are calling returns `short` values.
 *
 * @return the result of calling the Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public inline fun CallNonvirtualShortMethod(clazz: JClass, obj: JObject, methodID: JMethodID, args: JArguments): Short {
    return env.pointed!!.CallNonvirtualShortMethodA!!(env.ptr, clazz, obj, methodID, args)
}

/**
 * Invokes an instance (nonstatic) method on a Java object, according to the specified class and method ID.
 * The [methodID] argument must be obtained by calling [GetMethodID] on the class clazz.
 *
 * Unlike [CallIntMethod] which invokes the method based on the class or interface of the object, this method invokes
 * the method based on the class, designated by the [clazz] parameter, from which the method ID is obtained.
 *
 * The method ID must be obtained from the real class of the object or from one of its supertypes.
 *
 * `CallNonvirtual<type>Method` routines are the mechanism for invoking "default interface methods" introduced in Java 8.
 *
 * You should use this function only if the Java method you are calling returns `int` values.
 *
 * @return the result of calling the Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public inline fun CallNonvirtualIntMethod(clazz: JClass, obj: JObject, methodID: JMethodID, args: JArguments): Int {
    return env.pointed!!.CallNonvirtualIntMethodA!!(env.ptr, clazz, obj, methodID, args)
}

/**
 * Invokes an instance (nonstatic) method on a Java object, according to the specified class and method ID.
 * The [methodID] argument must be obtained by calling [GetMethodID] on the class clazz.
 *
 * Unlike [CallLongMethod] which invokes the method based on the class or interface of the object, this method invokes
 * the method based on the class, designated by the [clazz] parameter, from which the method ID is obtained.
 *
 * The method ID must be obtained from the real class of the object or from one of its supertypes.
 *
 * `CallNonvirtual<type>Method` routines are the mechanism for invoking "default interface methods" introduced in Java 8.
 *
 * You should use this function only if the Java method you are calling returns `long` values.
 *
 * @return the result of calling the Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public inline fun CallNonvirtualLongMethod(clazz: JClass, obj: JObject, methodID: JMethodID, args: JArguments): Long {
    return env.pointed!!.CallNonvirtualLongMethodA!!(env.ptr, clazz, obj, methodID, args)
}

/**
 * Invokes an instance (nonstatic) method on a Java object, according to the specified class and method ID.
 * The [methodID] argument must be obtained by calling [GetMethodID] on the class clazz.
 *
 * Unlike [CallFloatMethod] which invokes the method based on the class or interface of the object, this method invokes
 * the method based on the class, designated by the [clazz] parameter, from which the method ID is obtained.
 *
 * The method ID must be obtained from the real class of the object or from one of its supertypes.
 *
 * `CallNonvirtual<type>Method` routines are the mechanism for invoking "default interface methods" introduced in Java 8.
 *
 * You should use this function only if the Java method you are calling returns `float` values.
 *
 * @return the result of calling the Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public inline fun CallNonvirtualFloatMethod(clazz: JClass, obj: JObject, methodID: JMethodID, args: JArguments): Float {
    return env.pointed!!.CallNonvirtualFloatMethodA!!(env.ptr, clazz, obj, methodID, args)
}

/**
 * Invokes an instance (nonstatic) method on a Java object, according to the specified class and method ID.
 * The [methodID] argument must be obtained by calling [GetMethodID] on the class clazz.
 *
 * Unlike [CallDoubleMethod] which invokes the method based on the class or interface of the object, this method invokes
 * the method based on the class, designated by the [clazz] parameter, from which the method ID is obtained.
 *
 * The method ID must be obtained from the real class of the object or from one of its supertypes.
 *
 * `CallNonvirtual<type>Method` routines are the mechanism for invoking "default interface methods" introduced in Java 8.
 *
 * You should use this function only if the Java method you are calling returns `double` values.
 *
 * @return the result of calling the Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public inline fun CallNonvirtualDoubleMethod(clazz: JClass, obj: JObject, methodID: JMethodID, args: JArguments): Double {
    return env.pointed!!.CallNonvirtualDoubleMethodA!!(env.ptr, clazz, obj, methodID, args)
}

/**
 * Invokes an instance (nonstatic) method on a Java object, according to the specified class and method ID.
 * The [methodID] argument must be obtained by calling [GetMethodID] on the class clazz.
 *
 * Unlike [CallVoidMethod] which invokes the method based on the class or interface of the object, this method invokes
 * the method based on the class, designated by the [clazz] parameter, from which the method ID is obtained.
 *
 * The method ID must be obtained from the real class of the object or from one of its supertypes.
 *
 * `CallNonvirtual<type>Method` routines are the mechanism for invoking "default interface methods" introduced in Java 8.
 *
 * You should use this function only if the Java method you are calling doesn't return values.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public inline fun CallNonvirtualVoidMethod(clazz: JClass, obj: JObject, methodID: JMethodID, args: JArguments) {
    return env.pointed!!.CallNonvirtualVoidMethodA!!(env.ptr, clazz, obj, methodID, args)
}

/**
 * Returns the field ID for an instance (nonstatic) field of a class.
 * The field is specified by its name and signature.
 * The `Get<type>Field` and `Set<type>Field` families of accessor functions use field IDs to retrieve object fields.
 *
 * [GetFieldID] causes an uninitialized class to be initialized.
 *
 * [GetFieldID] cannot be used to get the length field of an array. Use [GetArrayLength] instead.
 *
 * @param clazz a Java class object.
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
context(env: JniEnv, memScope: AutofreeScope)
public inline fun GetFieldID(clazz: JClass, name: CValuesRef<ByteVar>, sig: CValuesRef<ByteVar>): JFieldID? {
    return env.pointed!!.GetFieldID!!(env.ptr, clazz, name.getPointer(memScope), sig.getPointer(memScope))
}

/**
 * Returns the value of an instance (nonstatic) field of an object.
 * The field to access is specified by a field ID obtained by calling [GetFieldID].
 *
 * You should use this function only if the Java field you are reading has `Object` type.
 *
 * @return the content of the field.
 */
context(env: JniEnv)
public inline fun GetObjectField(obj: JObject, fieldID: JFieldID): JObject? {
    return env.pointed!!.GetObjectField!!(env.ptr, obj, fieldID)
}

/**
 * Returns the value of an instance (nonstatic) field of an object.
 * The field to access is specified by a field ID obtained by calling [GetFieldID].
 *
 * You should use this function only if the Java field you are reading has `boolean` type.
 *
 * @return the content of the field.
 */
context(env: JniEnv)
public inline fun GetBooleanField(obj: JObject, fieldID: JFieldID): Boolean {
    return env.pointed!!.GetBooleanField!!(env.ptr, obj, fieldID).toKBoolean()
}

/**
 * Returns the value of an instance (nonstatic) field of an object.
 * The field to access is specified by a field ID obtained by calling [GetFieldID].
 *
 * You should use this function only if the Java field you are reading has `byte` type.
 *
 * @return the content of the field.
 */
context(env: JniEnv)
public inline fun GetByteField(obj: JObject, fieldID: JFieldID): Byte {
    return env.pointed!!.GetByteField!!(env.ptr, obj, fieldID)
}

/**
 * Returns the value of an instance (nonstatic) field of an object.
 * The field to access is specified by a field ID obtained by calling [GetFieldID].
 *
 * You should use this function only if the Java field you are reading has `char` type.
 *
 * @return the content of the field.
 */
context(env: JniEnv)
public inline fun GetCharField(obj: JObject, fieldID: JFieldID): Char {
    return env.pointed!!.GetCharField!!(env.ptr, obj, fieldID).toKChar()
}

/**
 * Returns the value of an instance (nonstatic) field of an object.
 * The field to access is specified by a field ID obtained by calling [GetFieldID].
 *
 * You should use this function only if the Java field you are reading has `short` type.
 *
 * @return the content of the field.
 */
context(env: JniEnv)
public inline fun GetShortField(obj: JObject, fieldID: JFieldID): Short {
    return env.pointed!!.GetShortField!!(env.ptr, obj, fieldID)
}

/**
 * Returns the value of an instance (nonstatic) field of an object.
 * The field to access is specified by a field ID obtained by calling [GetFieldID].
 *
 * You should use this function only if the Java field you are reading has `int` type.
 *
 * @return the content of the field.
 */
context(env: JniEnv)
public inline fun GetIntField(obj: JObject, fieldID: JFieldID): Int {
    return env.pointed!!.GetIntField!!(env.ptr, obj, fieldID)
}

/**
 * Returns the value of an instance (nonstatic) field of an object.
 * The field to access is specified by a field ID obtained by calling [GetFieldID].
 *
 * You should use this function only if the Java field you are reading has `long` type.
 *
 * @return the content of the field.
 */
context(env: JniEnv)
public inline fun GetLongField(obj: JObject, fieldID: JFieldID): Long {
    return env.pointed!!.GetLongField!!(env.ptr, obj, fieldID)
}

/**
 * Returns the value of an instance (nonstatic) field of an object.
 * The field to access is specified by a field ID obtained by calling [GetFieldID].
 *
 * You should use this function only if the Java field you are reading has `float` type.
 *
 * @return the content of the field.
 */
context(env: JniEnv)
public inline fun GetFloatField(obj: JObject, fieldID: JFieldID): Float {
    return env.pointed!!.GetFloatField!!(env.ptr, obj, fieldID)
}

/**
 * Returns the value of an instance (nonstatic) field of an object.
 * The field to access is specified by a field ID obtained by calling [GetFieldID].
 *
 * You should use this function only if the Java field you are reading has `double` type.
 *
 * @return the content of the field.
 */
context(env: JniEnv)
public inline fun GetDoubleField(obj: JObject, fieldID: JFieldID): Double {
    return env.pointed!!.GetDoubleField!!(env.ptr, obj, fieldID)
}

/**
 * Sets the value of an instance (nonstatic) field of an object.
 * The field to access is specified by a field ID obtained by calling [GetFieldID].
 *
 * You should use this function only if the Java field you are writing has `Object` type.
 */
context(env: JniEnv)
public inline fun SetObjectField(obj: JObject, fieldID: JFieldID, value: JObject?) {
    env.pointed!!.SetObjectField!!(env.ptr, obj, fieldID, value)
}

/**
 * Sets the value of an instance (nonstatic) field of an object.
 * The field to access is specified by a field ID obtained by calling [GetFieldID].
 *
 * You should use this function only if the Java field you are writing has `boolean` type.
 */
context(env: JniEnv)
public inline fun SetBooleanField(obj: JObject, fieldID: JFieldID, value: Boolean) {
    env.pointed!!.SetBooleanField!!(env.ptr, obj, fieldID, value.toJBoolean())
}

/**
 * Sets the value of an instance (nonstatic) field of an object.
 * The field to access is specified by a field ID obtained by calling [GetFieldID].
 *
 * You should use this function only if the Java field you are writing has `byte` type.
 */
context(env: JniEnv)
public inline fun SetByteField(obj: JObject, fieldID: JFieldID, value: Byte) {
    env.pointed!!.SetByteField!!(env.ptr, obj, fieldID, value)
}

/**
 * Sets the value of an instance (nonstatic) field of an object.
 * The field to access is specified by a field ID obtained by calling [GetFieldID].
 *
 * You should use this function only if the Java field you are writing has `char` type.
 */
context(env: JniEnv)
public inline fun SetCharField(obj: JObject, fieldID: JFieldID, value: Char) {
    env.pointed!!.SetCharField!!(env.ptr, obj, fieldID, value.toJChar())
}

/**
 * Sets the value of an instance (nonstatic) field of an object.
 * The field to access is specified by a field ID obtained by calling [GetFieldID].
 *
 * You should use this function only if the Java field you are writing has `short` type.
 */
context(env: JniEnv)
public inline fun SetShortField(obj: JObject, fieldID: JFieldID, value: Short) {
    env.pointed!!.SetShortField!!(env.ptr, obj, fieldID, value)
}

/**
 * Sets the value of an instance (nonstatic) field of an object.
 * The field to access is specified by a field ID obtained by calling [GetFieldID].
 *
 * You should use this function only if the Java field you are writing has `int` type.
 */ context(env: JniEnv)
public inline fun SetIntField(obj: JObject, fieldID: JFieldID, value: Int) {
    env.pointed!!.SetIntField!!(env.ptr, obj, fieldID, value)
}

/**
 * Sets the value of an instance (nonstatic) field of an object.
 * The field to access is specified by a field ID obtained by calling [GetFieldID].
 *
 * You should use this function only if the Java field you are writing has `long` type.
 */
context(env: JniEnv)
public inline fun SetLongField(obj: JObject, fieldID: JFieldID, value: Long) {
    env.pointed!!.SetLongField!!(env.ptr, obj, fieldID, value)
}

/**
 * Sets the value of an instance (nonstatic) field of an object.
 * The field to access is specified by a field ID obtained by calling [GetFieldID].
 *
 * You should use this function only if the Java field you are writing has `float` type.
 */
context(env: JniEnv)
public inline fun SetFloatField(obj: JObject, fieldID: JFieldID, value: Float) {
    env.pointed!!.SetFloatField!!(env.ptr, obj, fieldID, value)
}

/**
 * Sets the value of an instance (nonstatic) field of an object.
 * The field to access is specified by a field ID obtained by calling [GetFieldID].
 *
 * You should use this function only if the Java field you are writing has `double` type.
 */
context(env: JniEnv)
public inline fun SetDoubleField(obj: JObject, fieldID: JFieldID, value: Double) {
    env.pointed!!.SetDoubleField!!(env.ptr, obj, fieldID, value)
}

/**
 * Returns the method ID for a static method of a class. The method is specified by its name and signature.
 *
 * [GetStaticMethodID] causes an uninitialized class to be initialized.
 *
 * @param clazz a Java class object.
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
context(env: JniEnv, memScope: AutofreeScope)
public inline fun GetStaticMethodID(clazz: JClass, name: CValuesRef<ByteVar>, sig: CValuesRef<ByteVar>): JMethodID? {
    return env.pointed!!.GetStaticMethodID!!(env.ptr, clazz, name.getPointer(memScope), sig.getPointer(memScope))
}

/**
 * Invokes a static method on a Java object, according to the specified method ID.
 * The methodID argument must be obtained by calling [GetStaticMethodID].
 *
 * The method ID must be derived from [clazz], not from one of its superclasses.
 *
 * You should use this function only if the Java method you are calling returns `Object` values.
 *
 * @return the result of calling the static Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public inline fun CallStaticObjectMethod(clazz: JClass, methodID: JMethodID, args: JArguments): JObject? {
    return env.pointed!!.CallStaticObjectMethodA!!(env.ptr, clazz, methodID, args)
}

/**
 * Invokes a static method on a Java object, according to the specified method ID.
 * The methodID argument must be obtained by calling [GetStaticMethodID].
 *
 * The method ID must be derived from [clazz], not from one of its superclasses.
 *
 * You should use this function only if the Java method you are calling returns `boolean` values.
 *
 * @return the result of calling the static Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public inline fun CallStaticBooleanMethod(clazz: JClass, methodID: JMethodID, args: JArguments): Boolean {
    return env.pointed!!.CallStaticBooleanMethodA!!(env.ptr, clazz, methodID, args).toKBoolean()
}

/**
 * Invokes a static method on a Java object, according to the specified method ID.
 * The methodID argument must be obtained by calling [GetStaticMethodID].
 *
 * The method ID must be derived from [clazz], not from one of its superclasses.
 *
 * You should use this function only if the Java method you are calling returns `byte` values.
 *
 * @return the result of calling the static Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public inline fun CallStaticByteMethod(clazz: JClass, methodID: JMethodID, args: JArguments): Byte {
    return env.pointed!!.CallStaticByteMethodA!!(env.ptr, clazz, methodID, args)
}

/**
 * Invokes a static method on a Java object, according to the specified method ID.
 * The methodID argument must be obtained by calling [GetStaticMethodID].
 *
 * The method ID must be derived from [clazz], not from one of its superclasses.
 *
 * You should use this function only if the Java method you are calling returns `char` values.
 *
 * @return the result of calling the static Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public inline fun CallStaticCharMethod(clazz: JClass, methodID: JMethodID, args: JArguments): Char {
    return env.pointed!!.CallStaticCharMethodA!!(env.ptr, clazz, methodID, args).toKChar()
}

/**
 * Invokes a static method on a Java object, according to the specified method ID.
 * The methodID argument must be obtained by calling [GetStaticMethodID].
 *
 * The method ID must be derived from [clazz], not from one of its superclasses.
 *
 * You should use this function only if the Java method you are calling returns `short` values.
 *
 * @return the result of calling the static Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public inline fun CallStaticShortMethod(clazz: JClass, methodID: JMethodID, args: JArguments): Short {
    return env.pointed!!.CallStaticShortMethodA!!(env.ptr, clazz, methodID, args)
}

/**
 * Invokes a static method on a Java object, according to the specified method ID.
 * The methodID argument must be obtained by calling [GetStaticMethodID].
 *
 * The method ID must be derived from [clazz], not from one of its superclasses.
 *
 * You should use this function only if the Java method you are calling returns `int` values.
 *
 * @return the result of calling the static Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public inline fun CallStaticIntMethod(clazz: JClass, methodID: JMethodID, args: JArguments): Int {
    return env.pointed!!.CallStaticIntMethodA!!(env.ptr, clazz, methodID, args)
}

/**
 * Invokes a static method on a Java object, according to the specified method ID.
 * The methodID argument must be obtained by calling [GetStaticMethodID].
 *
 * The method ID must be derived from [clazz], not from one of its superclasses.
 *
 * You should use this function only if the Java method you are calling returns `long` values.
 *
 * @return the result of calling the static Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public inline fun CallStaticLongMethod(clazz: JClass, methodID: JMethodID, args: JArguments): Long {
    return env.pointed!!.CallStaticLongMethodA!!(env.ptr, clazz, methodID, args)
}

/**
 * Invokes a static method on a Java object, according to the specified method ID.
 * The methodID argument must be obtained by calling [GetStaticMethodID].
 *
 * The method ID must be derived from [clazz], not from one of its superclasses.
 *
 * You should use this function only if the Java method you are calling returns `float` values.
 *
 * @return the result of calling the static Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */ context(env: JniEnv)
public inline fun CallStaticFloatMethod(clazz: JClass, methodID: JMethodID, args: JArguments): Float {
    return env.pointed!!.CallStaticFloatMethodA!!(env.ptr, clazz, methodID, args)
}

/**
 * Invokes a static method on a Java object, according to the specified method ID.
 * The methodID argument must be obtained by calling [GetStaticMethodID].
 *
 * The method ID must be derived from [clazz], not from one of its superclasses.
 *
 * You should use this function only if the Java method you are calling returns `double` values.
 *
 * @return the result of calling the static Java method.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public inline fun CallStaticDoubleMethod(clazz: JClass, methodID: JMethodID, args: JArguments): Double {
    return env.pointed!!.CallStaticDoubleMethodA!!(env.ptr, clazz, methodID, args)
}

/**
 * Invokes a static method on a Java object, according to the specified method ID.
 * The methodID argument must be obtained by calling [GetStaticMethodID].
 *
 * The method ID must be derived from [clazz], not from one of its superclasses.
 *
 * You should use this function only if the Java method you are calling returns `void` values.
 *
 * @throws any Exceptions raised during the execution of the Java method.
 */
context(env: JniEnv)
public inline fun CallStaticVoidMethod(clazz: JClass, methodID: JMethodID, args: JArguments) {
    return env.pointed!!.CallStaticVoidMethodA!!(env.ptr, clazz, methodID, args)
}

/**
 * Returns the field ID for a static field of a class. The field is specified by its name and signature.
 * The `GetStatic<type>Field` and `SetStatic<type>Field` families of accessor functions use field IDs to retrieve static
 * fields.
 *
 * [GetStaticFieldID] causes an uninitialized class to be initialized.
 *
 * @param clazz a Java class object.
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
context(env: JniEnv, memScope: AutofreeScope)
public inline fun GetStaticFieldID(clazz: JClass, name: CValuesRef<ByteVar>, sig: CValuesRef<ByteVar>): JFieldID? {
    return env.pointed!!.GetStaticFieldID!!(env.ptr, clazz, name.getPointer(memScope), sig.getPointer(memScope))
}

/**
 * Returns the value of a static field of an object.
 * The field to access is specified by a field ID, which is obtained by calling [GetStaticFieldID].
 *
 * You should use this function only if the Java field you are reading has `Object` type.
 *
 * @return the content of the static field.
 */
context(env: JniEnv)
public inline fun GetStaticObjectField(clazz: JClass, fieldID: JFieldID): JObject? {
    return env.pointed!!.GetStaticObjectField!!(env.ptr, clazz, fieldID)
}

/**
 * Returns the value of a static field of an object.
 * The field to access is specified by a field ID, which is obtained by calling [GetStaticFieldID].
 *
 * You should use this function only if the Java field you are reading has `boolean` type.
 *
 * @return the content of the static field.
 */
context(env: JniEnv)
public inline fun GetStaticBooleanField(clazz: JClass, fieldID: JFieldID): Boolean {
    return env.pointed!!.GetStaticBooleanField!!(env.ptr, clazz, fieldID).toKBoolean()
}

/**
 * Returns the value of a static field of an object.
 * The field to access is specified by a field ID, which is obtained by calling [GetStaticFieldID].
 *
 * You should use this function only if the Java field you are reading has `byte` type.
 *
 * @return the content of the static field.
 */
context(env: JniEnv)
public inline fun GetStaticByteField(clazz: JClass, fieldID: JFieldID): Byte {
    return env.pointed!!.GetStaticByteField!!(env.ptr, clazz, fieldID)
}

/**
 * Returns the value of a static field of an object.
 * The field to access is specified by a field ID, which is obtained by calling [GetStaticFieldID].
 *
 * You should use this function only if the Java field you are reading has `char` type.
 *
 * @return the content of the static field.
 */
context(env: JniEnv)
public inline fun GetStaticCharField(clazz: JClass, fieldID: JFieldID): Char {
    return env.pointed!!.GetStaticCharField!!(env.ptr, clazz, fieldID).toKChar()
}

/**
 * Returns the value of a static field of an object.
 * The field to access is specified by a field ID, which is obtained by calling [GetStaticFieldID].
 *
 * You should use this function only if the Java field you are reading has `short` type.
 *
 * @return the content of the static field.
 */
context(env: JniEnv)
public inline fun GetStaticShortField(clazz: JClass, fieldID: JFieldID): Short {
    return env.pointed!!.GetStaticShortField!!(env.ptr, clazz, fieldID)
}

/**
 * Returns the value of a static field of an object.
 * The field to access is specified by a field ID, which is obtained by calling [GetStaticFieldID].
 *
 * You should use this function only if the Java field you are reading has `int` type.
 *
 * @return the content of the static field.
 */
context(env: JniEnv)
public inline fun GetStaticIntField(clazz: JClass, fieldID: JFieldID): Int {
    return env.pointed!!.GetStaticIntField!!(env.ptr, clazz, fieldID)
}

/**
 * Returns the value of a static field of an object.
 * The field to access is specified by a field ID, which is obtained by calling [GetStaticFieldID].
 *
 * You should use this function only if the Java field you are reading has `long` type.
 *
 * @return the content of the static field.
 */
context(env: JniEnv)
public inline fun GetStaticLongField(clazz: JClass, fieldID: JFieldID): Long {
    return env.pointed!!.GetStaticLongField!!(env.ptr, clazz, fieldID)
}

/**
 * Returns the value of a static field of an object.
 * The field to access is specified by a field ID, which is obtained by calling [GetStaticFieldID].
 *
 * You should use this function only if the Java field you are reading has `float` type.
 *
 * @return the content of the static field.
 */
context(env: JniEnv)
public inline fun GetStaticFloatField(clazz: JClass, fieldID: JFieldID): Float {
    return env.pointed!!.GetStaticFloatField!!(env.ptr, clazz, fieldID)
}

/**
 * Returns the value of a static field of an object.
 * The field to access is specified by a field ID, which is obtained by calling [GetStaticFieldID].
 *
 * You should use this function only if the Java field you are reading has `double` type.
 *
 * @return the content of the static field.
 */
context(env: JniEnv)
public inline fun GetStaticDoubleField(clazz: JClass, fieldID: JFieldID): Double {
    return env.pointed!!.GetStaticDoubleField!!(env.ptr, clazz, fieldID)
}

/**
 * Sets the value of a static field of an object.
 * The field to access is specified by a field ID, which is obtained by calling [GetStaticFieldID].
 *
 * You should use this function only if the Java field you are writing has `Object` type.
 */
context(env: JniEnv)
public inline fun SetStaticObjectField(clazz: JClass, fieldID: JFieldID, value: JObject?) {
    env.pointed!!.SetStaticObjectField!!(env.ptr, clazz, fieldID, value)
}

/**
 * Sets the value of a static field of an object.
 * The field to access is specified by a field ID, which is obtained by calling [GetStaticFieldID].
 *
 * You should use this function only if the Java field you are writing has `boolean` type.
 */
context(env: JniEnv)
public inline fun SetStaticBooleanField(clazz: JClass, fieldID: JFieldID, value: Boolean) {
    env.pointed!!.SetStaticBooleanField!!(env.ptr, clazz, fieldID, value.toJBoolean())
}

/**
 * Sets the value of a static field of an object.
 * The field to access is specified by a field ID, which is obtained by calling [GetStaticFieldID].
 *
 * You should use this function only if the Java field you are writing has `byte` type.
 */
context(env: JniEnv)
public inline fun SetStaticByteField(clazz: JClass, fieldID: JFieldID, value: Byte) {
    env.pointed!!.SetStaticByteField!!(env.ptr, clazz, fieldID, value)
}

/**
 * Sets the value of a static field of an object.
 * The field to access is specified by a field ID, which is obtained by calling [GetStaticFieldID].
 *
 * You should use this function only if the Java field you are writing has `char` type.
 */
context(env: JniEnv)
public inline fun SetStaticCharField(clazz: JClass, fieldID: JFieldID, value: Char) {
    env.pointed!!.SetStaticCharField!!(env.ptr, clazz, fieldID, value.toJChar())
}

/**
 * Sets the value of a static field of an object.
 * The field to access is specified by a field ID, which is obtained by calling [GetStaticFieldID].
 *
 * You should use this function only if the Java field you are writing has `short` type.
 */
context(env: JniEnv)
public inline fun SetStaticShortField(clazz: JClass, fieldID: JFieldID, value: Short) {
    env.pointed!!.SetStaticShortField!!(env.ptr, clazz, fieldID, value)
}

/**
 * Sets the value of a static field of an object.
 * The field to access is specified by a field ID, which is obtained by calling [GetStaticFieldID].
 *
 * You should use this function only if the Java field you are writing has `int` type.
 */
context(env: JniEnv)
public inline fun SetStaticIntField(clazz: JClass, fieldID: JFieldID, value: Int) {
    env.pointed!!.SetStaticIntField!!(env.ptr, clazz, fieldID, value)
}

/**
 * Sets the value of a static field of an object.
 * The field to access is specified by a field ID, which is obtained by calling [GetStaticFieldID].
 *
 * You should use this function only if the Java field you are writing has `long` type.
 */
context(env: JniEnv)
public inline fun SetStaticLongField(clazz: JClass, fieldID: JFieldID, value: Long) {
    env.pointed!!.SetStaticLongField!!(env.ptr, clazz, fieldID, value)
}

/**
 * Sets the value of a static field of an object.
 * The field to access is specified by a field ID, which is obtained by calling [GetStaticFieldID].
 *
 * You should use this function only if the Java field you are writing has `float` type.
 */
context(env: JniEnv)
public inline fun SetStaticFloatField(clazz: JClass, fieldID: JFieldID, value: Float) {
    env.pointed!!.SetStaticFloatField!!(env.ptr, clazz, fieldID, value)
}

/**
 * Sets the value of a static field of an object.
 * The field to access is specified by a field ID, which is obtained by calling [GetStaticFieldID].
 *
 * You should use this function only if the Java field you are writing has `double` type.
 */
context(env: JniEnv)
public inline fun SetStaticDoubleField(clazz: JClass, fieldID: JFieldID, value: Double) {
    env.pointed!!.SetStaticDoubleField!!(env.ptr, clazz, fieldID, value)
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
public inline fun NewString(unicodeChars: CArrayPointer<UShortVar>, len: Int): JString? {
    return env.pointed!!.NewString!!(env.ptr, unicodeChars, len)
}

/**
 * Returns the length (the count of Unicode characters) of a Java string.
 */
context(env: JniEnv)
public inline fun GetStringLength(string: JString): Int {
    return env.pointed!!.GetStringLength!!(env.ptr, string)
}

/**
 * Returns a pointer to the array of Unicode characters and a boolean value `isCopy` which specifies whether the array
 * is a copy (`true` - a copy, `false - the underlying array of the string, which means that any changes to it will be
 * reflected on the original string).
 *
 * This pointer is valid until [ReleaseStringChars] is called.
 *
 * @return a pointer to a Unicode string and a copy marker, or `null` if the operation fails.
 *
 * @see GetStringRegion
 */
context(env: JniEnv, memScope: NativePlacement)
public inline fun GetStringChars(string: JString): Pair<CArrayPointer<UShortVar>, Boolean>? {
    val isCopy = memScope.alloc<UByteVar>()
    val chars = env.pointed!!.GetStringChars!!(env.ptr, string, isCopy.ptr)
    return chars?.let { it to isCopy.value.toKBoolean() }
}

/**
 * Informs the VM that the native code no longer needs access to chars. The [chars] argument is a pointer obtained from
 * string using [GetStringChars].
 */ context(env: JniEnv)
public inline fun ReleaseStringChars(string: JString, chars: CArrayPointer<UShortVar>) {
    env.pointed!!.ReleaseStringChars!!(env.ptr, string, chars)
}

/**
 * Constructs a new `java.lang.String` object from an array of characters in modified UTF-8 encoding.
 *
 * @return a Java string object, or `null` if the string cannot be constructed.
 *
 * @throws OutOfMemoryError if the system runs out of memory.
 */
context(env: JniEnv)
public inline fun NewStringUTF(bytes: CArrayPointer<ByteVar>): JString? {
    return env.pointed!!.NewStringUTF!!(env.ptr, bytes)
}

/**
 * Returns the length in bytes of the modified UTF-8 representation of a string.
 */
context(env: JniEnv)
public inline fun GetStringUTFLength(string: JString): Int {
    return env.pointed!!.GetStringUTFLength!!(env.ptr, string)
}

/**
 * Returns a pointer to an array of bytes representing the string in modified UTF-8 encoding and a boolean value
 * `isCopy` which specifies whether the array is a copy (`true` - a copy, `false - the underlying array of the string,
 * which means that any changes to it will be reflected on the original string).
 *
 * This array is valid until it is released by [ReleaseStringUTFChars].
 *
 * @return a pointer to a modified UTF-8 string and a copy marker, or `null` if the operation fails.
 */
context(env: JniEnv, memScope: NativePlacement)
public inline fun GetStringUTFChars(string: JString): Pair<CArrayPointer<ByteVar>, Boolean>? {
    val isCopy = memScope.alloc<UByteVar>()
    val utf = env.pointed!!.GetStringUTFChars!!(env.ptr, string, isCopy.ptr)
    return utf?.let { it to isCopy.value.toKBoolean() }
}

/**
 * Informs the VM that the native code no longer needs access to [utf]. The [utf] argument is a pointer derived from string
 * using [GetStringUTFChars].
 *
 * @since JDK/JRE 1.2
 *
 * @see GetStringUTFRegion
 */
context(env: JniEnv)
public inline fun ReleaseStringUTFChars(string: JString, utf: CArrayPointer<ByteVar>) {
    env.pointed!!.ReleaseStringUTFChars!!(env.ptr, string, utf)
}

/**
 * Returns the number of elements in the [array].
 */
context(env: JniEnv)
public inline fun GetArrayLength(array: JArray): Int {
    return env.pointed!!.GetArrayLength!!(env.ptr, array)
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
public inline fun NewObjectArray(length: Int, elementClass: JClass, initialElement: JObject?): JObjectArray? {
    return env.pointed!!.NewObjectArray!!(env.ptr, length, elementClass, initialElement)
}

/**
 * Returns an element of an Object array. The [index] must be within the range of the array.
 *
 * @return a Java object.
 *
 * @throws ArrayIndexOutOfBoundsException if [index] does not specify a valid index in the array.
 */
context(env: JniEnv)
public inline fun GetObjectArrayElement(array: JObjectArray, index: Int): JObject? {
    return env.pointed!!.GetObjectArrayElement!!(env.ptr, array, index)
}

/**
 * Sets an element of an Object array. The [index] must be within the range of the array.
 *
 * @throws ArrayIndexOutOfBoundsException if [index] does not specify a valid index in the array.
 * @throws ArrayStoreException if the class of value is not a subclass of the element class of the array.
 */
context(env: JniEnv)
public inline fun SetObjectArrayElement(array: JObjectArray, index: Int, value: JObject?) {
    env.pointed!!.SetObjectArrayElement!!(env.ptr, array, index, value)
}

/**
 * Constructs a new `boolean[]` array object.
 */
context(env: JniEnv)
public inline fun NewBooleanArray(length: Int): JBooleanArray? {
    return env.pointed!!.NewBooleanArray!!(env.ptr, length)
}

/**
 * Constructs a new `byte[]` array object.
 */
context(env: JniEnv)
public inline fun NewByteArray(length: Int): JByteArray? {
    return env.pointed!!.NewByteArray!!(env.ptr, length)
}

/**
 * Constructs a new `char[]` array object.
 */ context(env: JniEnv)
public inline fun NewCharArray(length: Int): JCharArray? {
    return env.pointed!!.NewCharArray!!(env.ptr, length)
}

/**
 * Constructs a new `short[]` array object.
 */
context(env: JniEnv)
public inline fun NewShortArray(length: Int): JShortArray? {
    return env.pointed!!.NewShortArray!!(env.ptr, length)
}

/**
 * Constructs a new `int[]` array object.
 */
context(env: JniEnv)
public inline fun NewIntArray(length: Int): JIntArray? {
    return env.pointed!!.NewIntArray!!(env.ptr, length)
}

/**
 * Constructs a new `long[]` array object.
 */
context(env: JniEnv)
public inline fun NewLongArray(length: Int): JLongArray? {
    return env.pointed!!.NewLongArray!!(env.ptr, length)
}

/**
 * Constructs a new `float[]` array object.
 */
context(env: JniEnv)
public inline fun NewFloatArray(length: Int): JFloatArray? {
    return env.pointed!!.NewFloatArray!!(env.ptr, length)
}

/**
 * Constructs a new `double[]` array object.
 */
context(env: JniEnv)
public inline fun NewDoubleArray(length: Int): JDoubleArray? {
    return env.pointed!!.NewDoubleArray!!(env.ptr, length)
}

/**
 * Returns the body of the primitive array and a boolean value `isCopy` which specifies whether the array is a copy
 * (`true` - a copy, `false - the real array body, which means that any changes to it will be reflected on the jvm
 * array).
 *
 * The result is valid until the [ReleaseBooleanArrayElements] function is called.
 * Since the returned array may be a copy of the Java array, changes made to the returned array will not necessarily be
 * reflected in the original array until [ReleaseBooleanArrayElements] is called.
 *
 * Regardless of how boolean arrays are represented in the Java VM, [GetBooleanArrayElements] always returns a pointer
 * to [UByte]s, with each byte denoting an element (the unpacked representation).
 * All arrays of other types are guaranteed to be contiguous in memory.
 */
context(env: JniEnv, memScope: NativePlacement)
public inline fun GetBooleanArrayElements(array: JBooleanArray): Pair<CArrayPointer<UByteVar>, Boolean>? {
    val isCopy = memScope.alloc<UByteVar>()
    val elements = env.pointed!!.GetBooleanArrayElements!!(env.ptr, array, isCopy.ptr)
    return elements?.let { it to isCopy.value.toKBoolean() }
}

/**
 * Returns the body of the primitive array and a boolean value `isCopy` which specifies whether the array is a copy
 * (`true` - a copy, `false - the real array body, which means that any changes to it will be reflected on the jvm
 * array).
 *
 * The result is valid until the corresponding [ReleaseByteArrayElements] function is called.
 * Since the returned array may be a copy of the Java array, changes made to the returned array will not necessarily be
 * reflected in the original array until [ReleaseByteArrayElements] is called.
 */
context(env: JniEnv, memScope: NativePlacement)
public inline fun GetByteArrayElements(array: JByteArray): Pair<CArrayPointer<ByteVar>, Boolean>? {
    val isCopy = memScope.alloc<UByteVar>()
    val elements = env.pointed!!.GetByteArrayElements!!(env.ptr, array, isCopy.ptr)
    return elements?.let { it to isCopy.value.toKBoolean() }
}

/**
 * Returns the body of the primitive array and a boolean value `isCopy` which specifies whether the array is a copy
 * (`true` - a copy, `false - the real array body, which means that any changes to it will be reflected on the jvm
 * array).
 *
 * The result is valid until the corresponding [ReleaseCharArrayElements] function is called.
 * Since the returned array may be a copy of the Java array, changes made to the returned array will not necessarily be
 * reflected in the original array until [ReleaseCharArrayElements] is called.
 */
context(env: JniEnv, memScope: NativePlacement)
public inline fun GetCharArrayElements(array: JCharArray): Pair<CArrayPointer<UShortVar>, Boolean>? {
    val isCopy = memScope.alloc<UByteVar>()
    val elements = env.pointed!!.GetCharArrayElements!!(env.ptr, array, isCopy.ptr)
    return elements?.let { it to isCopy.value.toKBoolean() }
}

/**
 * Returns the body of the primitive array and a boolean value `isCopy` which specifies whether the array is a copy
 * (`true` - a copy, `false - the real array body, which means that any changes to it will be reflected on the jvm
 * array).
 *
 * The result is valid until the corresponding [ReleaseShortArrayElements] function is called.
 * Since the returned array may be a copy of the Java array, changes made to the returned array will not necessarily be
 * reflected in the original array until [ReleaseShortArrayElements] is called.
 */
context(env: JniEnv, memScope: NativePlacement)
public inline fun GetShortArrayElements(array: JShortArray): Pair<CArrayPointer<ShortVar>, Boolean>? {
    val isCopy = memScope.alloc<UByteVar>()
    val elements = env.pointed!!.GetShortArrayElements!!(env.ptr, array, isCopy.ptr)
    return elements?.let { it to isCopy.value.toKBoolean() }
}

/**
 * Returns the body of the primitive array and a boolean value `isCopy` which specifies whether the array is a copy
 * (`true` - a copy, `false - the real array body, which means that any changes to it will be reflected on the jvm
 * array).
 *
 * The result is valid until the corresponding [ReleaseIntArrayElements] function is called.
 * Since the returned array may be a copy of the Java array, changes made to the returned array will not necessarily be
 * reflected in the original array until [ReleaseIntArrayElements] is called.
 */
context(env: JniEnv, memScope: NativePlacement)
public inline fun GetIntArrayElements(array: JIntArray): Pair<CArrayPointer<IntVar>, Boolean>? {
    val isCopy = memScope.alloc<UByteVar>()
    val elements = env.pointed!!.GetIntArrayElements!!(env.ptr, array, isCopy.ptr)
    return elements?.let { it to isCopy.value.toKBoolean() }
}

/**
 * Returns the body of the primitive array and a boolean value `isCopy` which specifies whether the array is a copy
 * (`true` - a copy, `false - the real array body, which means that any changes to it will be reflected on the jvm
 * array).
 *
 * The result is valid until the corresponding [ReleaseLongArrayElements] function is called.
 * Since the returned array may be a copy of the Java array, changes made to the returned array will not necessarily be
 * reflected in the original array until [ReleaseLongArrayElements] is called.
 */
context(env: JniEnv, memScope: NativePlacement)
public inline fun GetLongArrayElements(array: JLongArray): Pair<CArrayPointer<LongVar>, Boolean>? {
    val isCopy = memScope.alloc<UByteVar>()
    val elements = env.pointed!!.GetLongArrayElements!!(env.ptr, array, isCopy.ptr)
    return elements?.let { it to isCopy.value.toKBoolean() }
}

/**
 * Returns the body of the primitive array and a boolean value `isCopy` which specifies whether the array is a copy
 * (`true` - a copy, `false - the real array body, which means that any changes to it will be reflected on the jvm
 * array).
 *
 * The result is valid until the corresponding [ReleaseFloatArrayElements] function is called.
 * Since the returned array may be a copy of the Java array, changes made to the returned array will not necessarily be
 * reflected in the original array until [ReleaseFloatArrayElements] is called.
 */
context(env: JniEnv, memScope: NativePlacement)
public inline fun GetFloatArrayElements(array: JFloatArray): Pair<CArrayPointer<FloatVar>, Boolean>? {
    val isCopy = memScope.alloc<UByteVar>()
    val elements = env.pointed!!.GetFloatArrayElements!!(env.ptr, array, isCopy.ptr)
    return elements?.let { it to isCopy.value.toKBoolean() }
}

/**
 * Returns the body of the primitive array and a boolean value `isCopy` which specifies whether the array is a copy
 * (`true` - a copy, `false - the real array body, which means that any changes to it will be reflected on the jvm
 * array).
 *
 * The result is valid until the corresponding [ReleaseDoubleArrayElements] function is called.
 * Since the returned array may be a copy of the Java array, changes made to the returned array will not necessarily be
 * reflected in the original array until [ReleaseDoubleArrayElements] is called.
 */
context(env: JniEnv, memScope: NativePlacement)
public inline fun GetDoubleArrayElements(array: JDoubleArray): Pair<CArrayPointer<DoubleVar>, Boolean>? {
    val isCopy = memScope.alloc<UByteVar>()
    val elements = env.pointed!!.GetDoubleArrayElements!!(env.ptr, array, isCopy.ptr)
    return elements?.let { it to isCopy.value.toKBoolean() }
}

/**
 * Informs the VM that the native code no longer needs access to [elems].
 * The [elems] argument is a pointer derived from [array] using the [GetBooleanArrayElements] function.
 * If necessary, this function copies back all changes made to elems to the original array.
 *
 * The [mode] argument provides information on how the array buffer should be released. [mode] has no effect if [elems]
 * is not a copy of the elements in [array]. Otherwise, mode has the following impact:
 * - `ApplyChangesMode.Commit`      -> copy back the content but do not free the elems buffer.
 * - `ApplyChangesMode.FinalCommit` -> copy back the content and free the elems buffer.
 * - `ApplyChangesMode.Abort`       -> free the buffer without copying back the possible changes.
 */
context(env: JniEnv)
public inline fun ReleaseBooleanArrayElements(array: JBooleanArray, elems: CArrayPointer<UByteVar>, mode: ApplyChangesMode) {
    env.pointed!!.ReleaseBooleanArrayElements!!(env.ptr, array, elems, mode.nativeCode)
}

/**
 * Informs the VM that the native code no longer needs access to [elems].
 * The [elems] argument is a pointer derived from [array] using the [GetByteArrayElements] function.
 * If necessary, this function copies back all changes made to elems to the original array.
 *
 * The [mode] argument provides information on how the array buffer should be released. [mode] has no effect if [elems]
 * is not a copy of the elements in [array]. Otherwise, mode has the following impact:
 * - `ApplyChangesMode.Commit`      -> copy back the content but do not free the elems buffer.
 * - `ApplyChangesMode.FinalCommit` -> copy back the content and free the elems buffer.
 * - `ApplyChangesMode.Abort`       -> free the buffer without copying back the possible changes.
 */
context(env: JniEnv)
public inline fun ReleaseByteArrayElements(array: JByteArray, elems: CArrayPointer<ByteVar>, mode: ApplyChangesMode) {
    env.pointed!!.ReleaseByteArrayElements!!(env.ptr, array, elems, mode.nativeCode)
}

/**
 * Informs the VM that the native code no longer needs access to [elems].
 * The [elems] argument is a pointer derived from [array] using the [GetCharArrayElements] function.
 * If necessary, this function copies back all changes made to elems to the original array.
 *
 * The [mode] argument provides information on how the array buffer should be released. [mode] has no effect if [elems]
 * is not a copy of the elements in [array]. Otherwise, mode has the following impact:
 * - `ApplyChangesMode.Commit`      -> copy back the content but do not free the elems buffer.
 * - `ApplyChangesMode.FinalCommit` -> copy back the content and free the elems buffer.
 * - `ApplyChangesMode.Abort`       -> free the buffer without copying back the possible changes.
 */
context(env: JniEnv)
public inline fun ReleaseCharArrayElements(array: JCharArray, elems: CArrayPointer<UShortVar>, mode: ApplyChangesMode) {
    env.pointed!!.ReleaseCharArrayElements!!(env.ptr, array, elems, mode.nativeCode)
}

/**
 * Informs the VM that the native code no longer needs access to [elems].
 * The [elems] argument is a pointer derived from [array] using the [GetShortArrayElements] function.
 * If necessary, this function copies back all changes made to elems to the original array.
 *
 * The [mode] argument provides information on how the array buffer should be released. [mode] has no effect if [elems]
 * is not a copy of the elements in [array]. Otherwise, mode has the following impact:
 * - `ApplyChangesMode.Commit`      -> copy back the content but do not free the elems buffer.
 * - `ApplyChangesMode.FinalCommit` -> copy back the content and free the elems buffer.
 * - `ApplyChangesMode.Abort`       -> free the buffer without copying back the possible changes.
 */
context(env: JniEnv)
public inline fun ReleaseShortArrayElements(array: JShortArray, elems: CArrayPointer<ShortVar>, mode: ApplyChangesMode) {
    env.pointed!!.ReleaseShortArrayElements!!(env.ptr, array, elems, mode.nativeCode)
}

/**
 * Informs the VM that the native code no longer needs access to [elems].
 * The [elems] argument is a pointer derived from [array] using the [GetIntArrayElements] function.
 * If necessary, this function copies back all changes made to elems to the original array.
 *
 * The [mode] argument provides information on how the array buffer should be released. [mode] has no effect if [elems]
 * is not a copy of the elements in [array]. Otherwise, mode has the following impact:
 * - `ApplyChangesMode.Commit`      -> copy back the content but do not free the elems buffer.
 * - `ApplyChangesMode.FinalCommit` -> copy back the content and free the elems buffer.
 * - `ApplyChangesMode.Abort`       -> free the buffer without copying back the possible changes.
 */
context(env: JniEnv)
public inline fun ReleaseIntArrayElements(array: JIntArray, elems: CPointer<IntVar>, mode: ApplyChangesMode) {
    env.pointed!!.ReleaseIntArrayElements!!(env.ptr, array, elems, mode.nativeCode)
}

/**
 * Informs the VM that the native code no longer needs access to [elems].
 * The [elems] argument is a pointer derived from [array] using the [GetLongArrayElements] function.
 * If necessary, this function copies back all changes made to elems to the original array.
 *
 * The [mode] argument provides information on how the array buffer should be released. [mode] has no effect if [elems]
 * is not a copy of the elements in [array]. Otherwise, mode has the following impact:
 * - `ApplyChangesMode.Commit`      -> copy back the content but do not free the elems buffer.
 * - `ApplyChangesMode.FinalCommit` -> copy back the content and free the elems buffer.
 * - `ApplyChangesMode.Abort`       -> free the buffer without copying back the possible changes.
 */
context(env: JniEnv)
public inline fun ReleaseLongArrayElements(array: JLongArray, elems: CPointer<LongVar>, mode: ApplyChangesMode) {
    env.pointed!!.ReleaseLongArrayElements!!(env.ptr, array, elems, mode.nativeCode)
}

/**
 * Informs the VM that the native code no longer needs access to [elems].
 * The [elems] argument is a pointer derived from [array] using the [GetFloatArrayElements] function.
 * If necessary, this function copies back all changes made to elems to the original array.
 *
 * The [mode] argument provides information on how the array buffer should be released. [mode] has no effect if [elems]
 * is not a copy of the elements in [array]. Otherwise, mode has the following impact:
 * - `ApplyChangesMode.Commit`      -> copy back the content but do not free the elems buffer.
 * - `ApplyChangesMode.FinalCommit` -> copy back the content and free the elems buffer.
 * - `ApplyChangesMode.Abort`       -> free the buffer without copying back the possible changes.
 */
context(env: JniEnv)
public inline fun ReleaseFloatArrayElements(array: JFloatArray, elems: CPointer<FloatVar>, mode: ApplyChangesMode) {
    env.pointed!!.ReleaseFloatArrayElements!!(env.ptr, array, elems, mode.nativeCode)
}

/**
 * Informs the VM that the native code no longer needs access to [elems].
 * The [elems] argument is a pointer derived from [array] using the [GetDoubleArrayElements] function.
 * If necessary, this function copies back all changes made to elems to the original array.
 *
 * The [mode] argument provides information on how the array buffer should be released. [mode] has no effect if [elems]
 * is not a copy of the elements in [array]. Otherwise, mode has the following impact:
 * - `ApplyChangesMode.Commit`      -> copy back the content but do not free the elems buffer.
 * - `ApplyChangesMode.FinalCommit` -> copy back the content and free the elems buffer.
 * - `ApplyChangesMode.Abort`       -> free the buffer without copying back the possible changes.
 */
context(env: JniEnv)
public inline fun ReleaseDoubleArrayElements(array: JDoubleArray, elems: CPointer<DoubleVar>, mode: ApplyChangesMode) {
    env.pointed!!.ReleaseDoubleArrayElements!!(env.ptr, array, elems, mode.nativeCode)
}

/**
 * Copies a region of a `boolean[]` array into a buffer [buf].
 *
 * @param array a Java array.
 * @param start the starting index; must be greater than or equal to zero, and less than the array length.
 * @param len the number of elements to be copied, must be greater than or equal to zero, and `start + len` must be less
 * than array length.
 * @param buf the destination buffer.
 *
 * @throws ArrayIndexOutOfBoundsException if one of the indexes in the region is not valid.
 */
context(env: JniEnv)
public inline fun GetBooleanArrayRegion(array: JBooleanArray, start: Int, len: Int, buf: CArrayPointer<UByteVar>) {
    env.pointed!!.GetBooleanArrayRegion!!(env.ptr, array, start, len, buf)
}

/**
 * Copies a region of a `byte[]` array into a buffer [buf].
 *
 * @param array a Java array.
 * @param start the starting index; must be greater than or equal to zero, and less than the array length.
 * @param len the number of elements to be copied, must be greater than or equal to zero, and `start + len` must be less
 * than array length.
 * @param buf the destination buffer.
 *
 * @throws ArrayIndexOutOfBoundsException if one of the indexes in the region is not valid.
 */ context(env: JniEnv)
public inline fun GetByteArrayRegion(array: JByteArray, start: Int, len: Int, buf: CArrayPointer<ByteVar>) {
    env.pointed!!.GetByteArrayRegion!!(env.ptr, array, start, len, buf)
}

/**
 * Copies a region of a `char[]` array into a buffer [buf].
 *
 * @param array a Java array.
 * @param start the starting index; must be greater than or equal to zero, and less than the array length.
 * @param len the number of elements to be copied, must be greater than or equal to zero, and `start + len` must be less
 * than array length.
 * @param buf the destination buffer.
 *
 * @throws ArrayIndexOutOfBoundsException if one of the indexes in the region is not valid.
 */
context(env: JniEnv)
public inline fun GetCharArrayRegion(array: JCharArray, start: Int, len: Int, buf: CArrayPointer<UShortVar>) {
    env.pointed!!.GetCharArrayRegion!!(env.ptr, array, start, len, buf)
}

/**
 * Copies a region of a `short[]` array into a buffer [buf].
 *
 * @param array a Java array.
 * @param start the starting index; must be greater than or equal to zero, and less than the array length.
 * @param len the number of elements to be copied, must be greater than or equal to zero, and `start + len` must be less
 * than array length.
 * @param buf the destination buffer.
 *
 * @throws ArrayIndexOutOfBoundsException if one of the indexes in the region is not valid.
 */
context(env: JniEnv)
public inline fun GetShortArrayRegion(array: JShortArray, start: Int, len: Int, buf: CArrayPointer<ShortVar>) {
    env.pointed!!.GetShortArrayRegion!!(env.ptr, array, start, len, buf)
}

/**
 * Copies a region of a `int[]` array into a buffer [buf].
 *
 * @param array a Java array.
 * @param start the starting index; must be greater than or equal to zero, and less than the array length.
 * @param len the number of elements to be copied, must be greater than or equal to zero, and `start + len` must be less
 * than array length.
 * @param buf the destination buffer.
 *
 * @throws ArrayIndexOutOfBoundsException if one of the indexes in the region is not valid.
 */ context(env: JniEnv)
public inline fun GetIntArrayRegion(array: JIntArray, start: Int, len: Int, buf: CPointer<IntVar>) {
    env.pointed!!.GetIntArrayRegion!!(env.ptr, array, start, len, buf)
}

/**
 * Copies a region of a `long[]` array into a buffer [buf].
 *
 * @param array a Java array.
 * @param start the starting index; must be greater than or equal to zero, and less than the array length.
 * @param len the number of elements to be copied, must be greater than or equal to zero, and `start + len` must be less
 * than array length.
 * @param buf the destination buffer.
 *
 * @throws ArrayIndexOutOfBoundsException if one of the indexes in the region is not valid.
 */ context(env: JniEnv)
public inline fun GetLongArrayRegion(array: JLongArray, start: Int, len: Int, buf: CPointer<LongVar>) {
    env.pointed!!.GetLongArrayRegion!!(env.ptr, array, start, len, buf)
}

/**
 * Copies a region of a `float[]` array into a buffer [buf].
 *
 * @param array a Java array.
 * @param start the starting index; must be greater than or equal to zero, and less than the array length.
 * @param len the number of elements to be copied, must be greater than or equal to zero, and `start + len` must be less
 * than array length.
 * @param buf the destination buffer.
 *
 * @throws ArrayIndexOutOfBoundsException if one of the indexes in the region is not valid.
 */ context(env: JniEnv)
public inline fun GetFloatArrayRegion(array: JFloatArray, start: Int, len: Int, buf: CPointer<FloatVar>) {
    env.pointed!!.GetFloatArrayRegion!!(env.ptr, array, start, len, buf)
}

/**
 * Copies a region of a `double[]` array into a buffer [buf].
 *
 * @param array a Java array.
 * @param start the starting index; must be greater than or equal to zero, and less than the array length.
 * @param len the number of elements to be copied, must be greater than or equal to zero, and `start + len` must be less
 * than array length.
 * @param buf the destination buffer.
 *
 * @throws ArrayIndexOutOfBoundsException if one of the indexes in the region is not valid.
 */ context(env: JniEnv)
public inline fun GetDoubleArrayRegion(array: JDoubleArray, start: Int, len: Int, buf: CPointer<DoubleVar>) {
    env.pointed!!.GetDoubleArrayRegion!!(env.ptr, array, start, len, buf)
}

/**
 * Copies back a region of a `boolean[]` array from a buffer [buf].
 *
 * @param array a Java array.
 * @param start the starting index; must be greater than or equal to zero, and less than the array length.
 * @param len the number of elements to be copied; must be greater than or equal to zero, and "start + len" must be less
 * than the array length.
 * @param buf the source buffer.
 *
 * @throws ArrayIndexOutOfBoundsException if one of the indexes in the region is not valid.
 */
context(env: JniEnv)
public inline fun SetBooleanArrayRegion(array: JBooleanArray, start: Int, len: Int, buf: CArrayPointer<UByteVar>) {
    env.pointed!!.SetBooleanArrayRegion!!(env.ptr, array, start, len, buf)
}

/**
 * Copies back a region of a `byte[]` array from a buffer [buf].
 *
 * @param array a Java array.
 * @param start the starting index; must be greater than or equal to zero, and less than the array length.
 * @param len the number of elements to be copied; must be greater than or equal to zero, and "start + len" must be less
 * than the array length.
 * @param buf the source buffer.
 *
 * @throws ArrayIndexOutOfBoundsException if one of the indexes in the region is not valid.
 */
context(env: JniEnv)
public inline fun SetByteArrayRegion(array: JByteArray, start: Int, len: Int, buf: CArrayPointer<ByteVar>) {
    env.pointed!!.SetByteArrayRegion!!(env.ptr, array, start, len, buf)
}

/**
 * Copies back a region of a `char[]` array from a buffer [buf].
 *
 * @param array a Java array.
 * @param start the starting index; must be greater than or equal to zero, and less than the array length.
 * @param len the number of elements to be copied; must be greater than or equal to zero, and "start + len" must be less
 * than the array length.
 * @param buf the source buffer.
 *
 * @throws ArrayIndexOutOfBoundsException if one of the indexes in the region is not valid.
 */
context(env: JniEnv)
public inline fun SetCharArrayRegion(array: JCharArray, start: Int, len: Int, buf: CArrayPointer<UShortVar>) {
    env.pointed!!.SetCharArrayRegion!!(env.ptr, array, start, len, buf)
}

/**
 * Copies back a region of a `short[]` array from a buffer [buf].
 *
 * @param array a Java array.
 * @param start the starting index; must be greater than or equal to zero, and less than the array length.
 * @param len the number of elements to be copied; must be greater than or equal to zero, and "start + len" must be less
 * than the array length.
 * @param buf the source buffer.
 *
 * @throws ArrayIndexOutOfBoundsException if one of the indexes in the region is not valid.
 */
context(env: JniEnv)
public inline fun SetShortArrayRegion(array: JShortArray, start: Int, len: Int, buf: CArrayPointer<ShortVar>) {
    env.pointed!!.SetShortArrayRegion!!(env.ptr, array, start, len, buf)
}

/**
 * Copies back a region of a `int[]` array from a buffer [buf].
 *
 * @param array a Java array.
 * @param start the starting index; must be greater than or equal to zero, and less than the array length.
 * @param len the number of elements to be copied; must be greater than or equal to zero, and "start + len" must be less
 * than the array length.
 * @param buf the source buffer.
 *
 * @throws ArrayIndexOutOfBoundsException if one of the indexes in the region is not valid.
 */
context(env: JniEnv)
public inline fun SetIntArrayRegion(array: JIntArray, start: Int, len: Int, buf: CPointer<IntVar>) {
    env.pointed!!.SetIntArrayRegion!!(env.ptr, array, start, len, buf)
}

/**
 * Copies back a region of a `long[]` array from a buffer [buf].
 *
 * @param array a Java array.
 * @param start the starting index; must be greater than or equal to zero, and less than the array length.
 * @param len the number of elements to be copied; must be greater than or equal to zero, and "start + len" must be less
 * than the array length.
 * @param buf the source buffer.
 *
 * @throws ArrayIndexOutOfBoundsException if one of the indexes in the region is not valid.
 */
context(env: JniEnv)
public inline fun SetLongArrayRegion(array: JLongArray, start: Int, len: Int, buf: CPointer<LongVar>) {
    env.pointed!!.SetLongArrayRegion!!(env.ptr, array, start, len, buf)
}

/**
 * Copies back a region of a `float[]` array from a buffer [buf].
 *
 * @param array a Java array.
 * @param start the starting index; must be greater than or equal to zero, and less than the array length.
 * @param len the number of elements to be copied; must be greater than or equal to zero, and "start + len" must be less
 * than the array length.
 * @param buf the source buffer.
 *
 * @throws ArrayIndexOutOfBoundsException if one of the indexes in the region is not valid.
 */
context(env: JniEnv)
public inline fun SetFloatArrayRegion(array: JFloatArray, start: Int, len: Int, buf: CPointer<FloatVar>) {
    env.pointed!!.SetFloatArrayRegion!!(env.ptr, array, start, len, buf)
}

/**
 * Copies back a region of a `double[]` array from a buffer [buf].
 *
 * @param array a Java array.
 * @param start the starting index; must be greater than or equal to zero, and less than the array length.
 * @param len the number of elements to be copied; must be greater than or equal to zero, and "start + len" must be less
 * than the array length.
 * @param buf the source buffer.
 *
 * @throws ArrayIndexOutOfBoundsException if one of the indexes in the region is not valid.
 */
context(env: JniEnv)
public inline fun SetDoubleArrayRegion(array: JDoubleArray, start: Int, len: Int, buf: CPointer<DoubleVar>) {
    env.pointed!!.SetDoubleArrayRegion!!(env.ptr, array, start, len, buf)
}

/**
 * A struct of name, signature and function pointer.
 */
public typealias JniNativeMethod = io.github.mimimishkin.jni.internal.raw.JNINativeMethod

/**
 * Registers native methods with the class specified by the [clazz] argument.
 * The [methods] parameter specifies a list of triples that contain the names, signatures and function pointers of the
 * native methods.
 *
 * The function pointers nominally must have the following signature:
 * ```
 * ReturnType (*fnPtr)(JNIEnv *env, jobject objectOrClass, ...);
 * ```
 *
 * Be aware that [RegisterNatives] can change the documented behavior of the JVM (including cryptographic algorithms,
 * correctness, security, type safety), by changing the native code to be executed for a given native Java method.
 * Therefore, use applications that have native libraries utilizing the [RegisterNatives] function with caution.
 *
 * @throws NoSuchMethodError if a specified method cannot be found or if the method is not native.
 *
 * @see io.github.mimimishkin.jni.ext.registerNativesFor
 */
context(env: JniEnv)
public inline fun RegisterNatives(clazz: JClass, methods: CArrayPointer<JniNativeMethod>, methodsCount: Int) {
    JNI.safeCall {
        env.pointed!!.RegisterNatives!!(env.ptr, clazz, methods, methodsCount)
    }
}

/**
 * Unregisters native methods of a [clazz]. The class goes back to the state before it was linked or registered with its
 * native method functions.
 *
 * This function should not be used in normal native code. Instead, it provides special programs a way to reload and
 * relink native libraries.
 */
context(env: JniEnv)
public inline fun UnregisterNatives(clazz: JClass) {
    JNI.safeCall {
        env.pointed!!.UnregisterNatives!!(env.ptr, clazz)
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
 * A monitor entered through a [MonitorEnter] JNI function call cannot be exited using the `monitorexit` Java virtual
 * machine instruction or a synchronized method return. A [MonitorEnter] JNI function call and a `monitorenter` Java
 * virtual machine instruction may race to enter the monitor associated with the same object.
 *
 * To avoid deadlocks, a monitor entered through a [MonitorEnter] JNI function call must be exited using the
 * [MonitorExit] JNI call, unless the DetachCurrentThread call is used to implicitly release JNI monitors.
 */
context(env: JniEnv)
public inline fun MonitorEnter(obj: JObject) {
    JNI.safeCall {
        env.pointed!!.MonitorEnter!!(env.ptr, obj)
    }
}

/**
 * The current thread must be the owner of the monitor associated with the underlying Java object referred to by [obj].
 * The thread decrements the counter indicating the number of times it has entered this monitor. If the value of the
 * counter becomes zero, the current thread releases the monitor.
 *
 * Native code must not use [MonitorExit] to exit a monitor entered through a synchronized method or a `monitorenter`
 * Java virtual machine instruction.
 */
context(env: JniEnv)
public inline fun MonitorExit(obj: JObject) {
    JNI.safeCall {
        env.pointed!!.MonitorExit!!(env.ptr, obj)
    }
}

/**
 * Returns the [JavaVM] interface associated with the current thread.
 */
context(env: JniEnv, memScope: NativePlacement)
public inline fun GetJavaVM(): JavaVM {
    val vm = memScope.alloc<CPointerVar<JavaVM>>()
    JNI.safeCall {
        env.pointed!!.GetJavaVM!!(env.ptr, vm.ptr)
    }
    return vm.pointed!!
}

/**
 * Copies len number of Unicode characters beginning at offset start to the given buffer [buf].
 *
 * @param str a Java string object.
 * @param start the index of the first Unicode character in the string to copy. Must be greater than or equal to zero,
 * and less than string length.
 * @param len the number of Unicode characters to copy. Must be greater than or equal to zero, and "start + len" must be
 * less than string length.
 * @param buf the Unicode character buffer into which to copy the string region.
 *
 * @throws StringIndexOutOfBoundsException on index overflow.
 *
 * @since JDK/JRE 1.2
 */ context(env: JniEnv)
public inline fun GetStringRegion(str: JString, start: Int, len: Int, buf: CArrayPointer<UShortVar>) {
    env.pointed!!.GetStringRegion!!(env.ptr, str, start, len, buf)
}

/**
 * Translates len number of Unicode characters beginning at offset start into modified UTF-8 encoding and place the
 * result in the given buffer [buf].
 *
 * The [len] argument specifies the number of Unicode characters. The resulting number modified UTF-8 encoding
 * characters may be greater than the given [len] argument. [GetStringUTFLength] may be used to determine the maximum
 * size of the required character buffer.
 *
 * Since this specification does not require the resulting string copy be NULL terminated, it is advisable to clear the
 * given character buffer (e.g. `memset()`) before using this function to safely perform `strlen()`.
 *
 * @param str a Java string object.
 * @param start the index of the first Unicode character in the string to copy. Must be greater than or equal to zero,
 * and less than the string length.
 * @param len the number of Unicode characters to copy. Must be greater than zero, and "start + len" must be less than
 * string length.
 * @param buf the Unicode character buffer into which to copy the string region.
 *
 * @throws StringIndexOutOfBoundsException on index overflow.
 *
 * @since JDK/JRE 1.2
 */
context(env: JniEnv)
public inline fun GetStringUTFRegion(str: JString, start: Int, len: Int, buf: CArrayPointer<ByteVar>) {
    env.pointed!!.GetStringUTFRegion!!(env.ptr, str, start, len, buf)
}

/**
 * The semantics of this function is very similar to the `Get<primitivetype>ArrayElements`. If possible, the VM returns
 * a pointer to the primitive array; otherwise, a copy is made. However, there are significant restrictions on how these
 * functions can be used.
 *
 * After calling [GetPrimitiveArrayCritical], the native code should not run for an extended period of time before it
 * calls [ReleasePrimitiveArrayCritical]. We must treat the code inside this pair of functions as running in a
 * "critical region." Inside a critical region, native code must not call other JNI functions or any system call that
 * may cause the current thread to block and wait for another Java thread. (For example, the current thread must not
 * call read on a stream being written by another Java thread.)
 *
 * These restrictions make it more likely that the native code will get an uncopied version of the array, even if the VM
 * does not support pinning. For example, a VM may temporarily disable garbage collection when the native code is
 * holding a pointer to an array obtained via [GetPrimitiveArrayCritical].
 *
 * Multiple pairs of [GetPrimitiveArrayCritical] and [ReleasePrimitiveArrayCritical] may be nested. For example:
 * ```
 * val len = env.GetArrayLength(arr1)
 * val a1 = env.GetPrimitiveArrayCritical(arr1)
 * val a1 = env.GetPrimitiveArrayCritical(arr2)
 * // We need to check in case the VM tried to make a copy.
 * if (a1 == null || a2 == null) {
 *     // out of memory exception thrown
 * }
 * memcpy(a1.first, a2.first, len.toUlong())
 * env.ReleasePrimitiveArrayCritical(arr2, a2.first)
 * env.ReleasePrimitiveArrayCritical(arr1, a1.first)
 * ```
 *
 * Note that GetPrimitiveArrayCritical might still make a copy of the array if the VM internally represents arrays in a
 * different format. Therefore, we need to check its return value against null for possible out-of-memory situations.

 * @since JDK/JRE 1.2
 */
context(env: JniEnv, memScope: NativePlacement)
public inline fun GetPrimitiveArrayCritical(array: JArray): Pair<CArrayPointer<*>, Boolean>? {
    val isCopy = memScope.alloc<UByteVar>()
    val carray = env.pointed!!.GetPrimitiveArrayCritical!!(env.ptr, array, isCopy.ptr)
    return carray?.let { it to isCopy.value.toKBoolean() }
}

/**
 * The semantics of this function is very similar to the `Release<primitivetype>ArrayElements`.
 *
 * Informs the VM that the native code no longer needs access to [carray].
 * The [carray] argument is a pointer derived from [array] using the [GetPrimitiveArrayCritical] function.
 * If necessary, this function copies back all changes made to elems to the original array.
 *
 * The [mode] argument provides information on how the array buffer should be released. [mode] has no effect if [carray]
 * is not a copy of the elements in [array]. Otherwise, mode has the following impact:
 * - `ApplyChangesMode.Commit`      -> copy back the content but do not free the elems buffer.
 * - `ApplyChangesMode.FinalCommit` -> copy back the content and free the elems buffer.
 * - `ApplyChangesMode.Abort`       -> free the buffer without copying back the possible changes.
 *
 * After calling [GetPrimitiveArrayCritical], the native code should not run for an extended period of time before it
 * calls [ReleasePrimitiveArrayCritical]. We must treat the code inside this pair of functions as running in a
 * "critical region." Inside a critical region, native code must not call other JNI functions or any system call that
 * may cause the current thread to block and wait for another Java thread. (For example, the current thread must not
 * call read on a stream being written by another Java thread.)
 *
 * These restrictions make it more likely that the native code will get an uncopied version of the array, even if the VM
 * does not support pinning. For example, a VM may temporarily disable garbage collection when the native code is
 * holding a pointer to an array obtained via [GetPrimitiveArrayCritical].

 * @since JDK/JRE 1.2
 */
context(env: JniEnv)
public inline fun ReleasePrimitiveArrayCritical(array: JArray, carray: CArrayPointer<*>, mode: ApplyChangesMode) {
    env.pointed!!.ReleasePrimitiveArrayCritical!!(env.ptr, array, carray, mode.nativeCode)
}

/**
 * The semantics of this function is similar to [GetStringChars] function. If possible, the VM returns a pointer to
 * string elements; otherwise, a copy is made. However, there are significant restrictions on how these functions can be
 * used. In a code segment enclosed by `Get/ReleaseStringCritical` calls, the native code must not issue arbitrary JNI
 * calls, or cause the current thread to block.
 *
 * The restrictions on `Get/ReleaseStringCritical` are similar to those on `Get/ReleasePrimitiveArrayCritical`.

 * @since JDK/JRE 1.2
 */
context(env: JniEnv, memScope: NativePlacement)
public inline fun GetStringCritical(string: JString): Pair<CArrayPointer<UShortVar>, Boolean>? {
    val isCopy = memScope.alloc<UByteVar>()
    val carray = env.pointed!!.GetStringCritical!!(env.ptr, string, isCopy.ptr)
    return carray?.let { it to isCopy.value.toKBoolean() }
}

/**
 * The semantics of this function is similar to [GetStringChars] function. Informs the VM that the native code no longer
 * needs access to [carray]. However, there are significant restrictions on how these functions can be
 * used. In a code segment enclosed by `Get/ReleaseStringCritical` calls, the native code must not issue arbitrary JNI
 * calls, or cause the current thread to block.
 *
 * The restrictions on `Get/ReleaseStringCritical` are similar to those on `Get/ReleasePrimitiveArrayCritical`.

 * @since JDK/JRE 1.2
 */
context(env: JniEnv)
public inline fun ReleaseStringCritical(string: JString, carray: CArrayPointer<UShortVar>) {
    env.pointed!!.ReleaseStringCritical!!(env.ptr, string, carray)
}

/**
 * Creates a new weak global reference.
 * The weak global reference will not prevent garbage collection of the given object.
 *
 * IsSameObject may be used to test if the object referred to by the reference has been freed.
 *
 * May return `null` if:
 * - the system has run out of memory
 * - [obj] was a weak global reference and has already been garbage collected.
 *
 * @throws OutOfMemoryError if the system runs out of memory.

 * @since JDK/JRE 1.2
 */
context(env: JniEnv)
public inline fun NewWeakGlobalRef(obj: JObject): JWeak? {
    return env.pointed!!.NewWeakGlobalRef!!(env.ptr, obj)
}

/**
 * Delete the VM resources needed for the given weak global reference.
 *
 * @since JDK/JRE 1.2
 */
context(env: JniEnv)
public inline fun DeleteWeakGlobalRef(obj: JWeak) {
    env.pointed!!.DeleteWeakGlobalRef!!(env.ptr, obj)
}

/**
 * Allocates and returns a direct java.nio.ByteBuffer referring to the block of memory starting at the memory address
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
 * @return a local reference to the newly instantiated java.nio.ByteBuffer object. Returns `null` if an exception
 * occurs, or if this virtual machine does not support JNI access to direct buffers.
 *
 * @throws OutOfMemoryError if allocation of the ByteBuffer object fails.
 *
 * @since JDK/JRE 1.4
 */
context(env: JniEnv)
public inline fun NewDirectByteBuffer(address: COpaquePointer, capacity: Long): JObject? {
    return env.pointed!!.NewDirectByteBuffer!!(env.ptr, address, capacity)
}

/**
 * Fetches and returns the starting address of the memory region referenced by the given direct `java.nio.Buffer`.
 *
 * This function allows native code to access the same memory region that is accessible to Java code via the buffer
 * object.
 *
 * @param buf a direct `java.nio.Buffer` object.
 *
 * @return the starting address of the memory region referenced by the buffer. Returns `null` if the memory region is
 * undefined, if the given object is not a direct `java.nio.Buffer`, or if this virtual machine does not support JNI
 * access to direct buffers.
 *
 * @since JDK/JRE 1.4
 */
context(env: JniEnv)
public inline fun GetDirectBufferAddress(buf: JObject): COpaquePointer? {
    return env.pointed!!.GetDirectBufferAddress!!(env.ptr, buf)
}

/**
 * Fetches and returns the capacity of the memory region referenced by the given direct `java.nio.Buffer`. The capacity
 * is the number of elements that the memory region contains.
 *
 * @param buf a direct `java.nio.Buffer` object.
 *
 * @return the capacity of the memory region associated with the buffer. Returns -1 if the given object is not a direct
 * `java.nio.Buffer`, if the object is an unaligned view buffer and the processor architecture does not support
 * unaligned access or if this virtual machine does not support JNI access to direct buffers.
 *
 * @since JDK/JRE 1.4
 */
context(env: JniEnv)
public inline fun GetDirectBufferCapacity(buf: JObject): Long {
    return env.pointed!!.GetDirectBufferCapacity!!(env.ptr, buf)
}

/**
 * Returns the `java.lang.Module` object for the module that the class is a member of. If the class is not in a named
 * module, then the unnamed module of the class loader for the class is returned.
 * If the class represents an array type, then this function returns the Module object for the element type. If the
 * class represents a primitive type or void, then the Module object for the `java.base` module is returned.
 *
 * @return the module that the class or interface is a member of.
 *
 * @since JDK/JRE 9
 */
context(env: JniEnv)
public inline fun GetModule(clazz: JClass): JObject {
    return env.pointed!!.GetModule!!(env.ptr, clazz)!!
}