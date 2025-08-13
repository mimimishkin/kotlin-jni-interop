@file:Suppress("NOTHING_TO_INLINE")

package io.github.mimimishkin.jni.ext

import io.github.mimimishkin.jni.*
import io.github.mimimishkin.jni.ExceptionClear
import kotlinx.cinterop.AutofreeScope
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CArrayPointer
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.DoubleVar
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.NativePlacement
import kotlinx.cinterop.ShortVar
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.utf16
import kotlin.Boolean
import kotlin.Byte
import kotlin.Char
import kotlin.Double
import kotlin.Float
import kotlin.Int
import kotlin.Long
import kotlin.Short
import kotlin.String
import kotlin.Suppress
import kotlin.Unit

/**
 * Special type that exposes [option] method to add VM options.
 */
public typealias JavaVMInitArgsBuilder = (JavaVMOption.() -> Unit) -> Unit

/**
 * Add [optionString] and [extraInfo] pair to [JavaVMInitArgs].
 */
context(memScope: AutofreeScope)
public inline fun JavaVMInitArgsBuilder.option(optionString: String, extraInfo: COpaquePointer? = null): Unit = this {
    this.optionString = optionString.cstr.getPointer(memScope)
    this.extraInfo = extraInfo
}

/**
 * Allocates and initializes [JavaVMInitArgs].
 *
 * Instead of writing
 * ```
 * val options = allocArray<JavaVMOption>()
 * options[0].optionString = "-Djava.compiler=NONE".cstr.ptr                // disable JIT
 * options[1].optionString = "-Djava.class.path=C:\\myclasses".cstr.ptr     // user classes
 * options[2].optionString = "-Djava.library.path=C:\\mylibs".cstr.ptr      // set native library path
 * options[3].optionString = "-verbose:jni".cstr.ptr                        // print JNI-related messages
 *
 * val args = alloc<JavaVMInitArgs>()
 * args.version = JNI.lastVersion
 * args.ignoreUnrecognized = false.toJBoolean()
 * args.nOptions = 4
 * args.options = options
 *
 * ```
 * You may write
 * ```
 * val args = javaVMInitArgs {
 *     option("-Djava.compiler=NONE")               // disable JIT
 *     option("-Djava.class.path=C:\\myclasses")    // user classes
 *     option("-Djava.library.path=C:\\mylibs")     // set native library path
 *     option("-verbose:jni")                       // print JNI-related messages
 * }
 * ```
 * Which will be inlined in the code above.
 *
 * @param count size of an array to allocate
 * @param block array initializer block
 *
 * @see JArgumentsBuilder
 */
context(memScope: NativePlacement)
public inline fun javaVMInitArgs(
    optionsCount: Int,
    version: JniVersion = JNI.lastVersion,
    ignoreUnrecognized: Boolean = false,
    block: (JavaVMInitArgsBuilder).() -> Unit
): JavaVMInitArgs {
    val options = memScope.allocArray<JavaVMOption>(optionsCount)
    var index = 0
    block { optionInit ->
        options[index++].optionInit()
    }

    val args = memScope.alloc<JavaVMInitArgsStruct>()
    args.version = version
    args.ignoreUnrecognized = ignoreUnrecognized.toJBoolean()
    args.nOptions = index
    args.options = options
    return args.ptr
}

/**
 * Executes a block of code within the context of a JNI environment of version [version] of the current thread.
 * If the current thread is not attached to the JavaVM or the specified version is not supported, an exception will be
 * thrown.
 */
context(memScope: NativePlacement)
public inline fun <T> JavaVM.withEnv(version: JniVersion = JNI.lastVersion, block: context(JniEnv) () -> T): T {
    return context(GetEnv(version), block)
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
context(memScope: AutofreeScope)
public inline fun <T> JavaVM.withEnvAttaching(
    version: JniVersion = JNI.lastVersion,
    name: CValuesRef<ByteVar>? = null,
    group: JObject? = null,
    block: context(JniEnv) () -> T
): T {
    val env = AttachCurrentThread(version, name, group)
    try {
        return context(env, block)
    } finally {
        DetachCurrentThread()
    }
}

/**
 * Converts JVM string into Kotlin string.
 *
 * @return converted string or `null` if fails.
 */
context(env: JniEnv, memScope: NativePlacement)
public inline fun JString.toKString(): String? {
    val (chars, _) = GetStringChars(this) ?: return null
    val res = chars.toKString()
    ReleaseStringChars(this, chars)
    return res
}

/**
 * Converts Kotlin string into JVM string.
 *
 * @return converted string or `null` if fails.
 */
context(env: JniEnv, memScope: AutofreeScope)
public inline fun String.toJString(): JString? {
    return NewString(utf16.getPointer(memScope), length)
}

/**
 * Special type that exposes methods to add arguments:
 * - [boolean], [byte], [char], [short], [int], [long], [float], [long] to pass primitives
 * - [ref] to pass objects and its variant [str] to pass strings.
 */
public typealias JArgumentsBuilder = (JValue.() -> Unit) -> Unit

/**
 * Add `boolean` value to arguments.
 */
public inline fun JArgumentsBuilder.boolean(value: Boolean): Unit = this { this.z = value.toJBoolean() }

/**
 * Add `byte` value to arguments.
 */
public inline fun JArgumentsBuilder.byte(value: Byte): Unit = this { this.b = value }

/**
 * Add `char` value to arguments.
 */
public inline fun JArgumentsBuilder.char(value: Char): Unit = this { this.c = value.toJChar() }

/**
 * Add `short` value to arguments.
 */
public inline fun JArgumentsBuilder.short(value: Short): Unit = this { this.s = value }

/**
 * Add `int` value to arguments.
 */
public inline fun JArgumentsBuilder.int(value: Int): Unit = this { this.i = value }

/**
 * Add `long` value to arguments.
 */
public inline fun JArgumentsBuilder.long(value: Long): Unit = this { this.j = value }

/**
 * Add `float` value to arguments.
 */
public inline fun JArgumentsBuilder.float(value: Float): Unit = this { this.f = value }

/**
 * Add `long` value to arguments.
 */
public inline fun JArgumentsBuilder.long(value: Double): Unit = this { this.d = value }

/**
 * Add `Object` value to arguments.
 */
public inline fun JArgumentsBuilder.ref(value: JObject?): Unit = this { this.l = value }

/**
 * Converts [value] into JVM string and pass it to arguments.
 */
context(env: JniEnv, memScope: AutofreeScope)
public inline fun JArgumentsBuilder.str(value: String): Unit = ref(value.toJString())

/**
 * Allocates and initializes [JArguments].
 *
 * Instead of writing
 * ```
 * val args = allocArray<JValue>(4)
 * args[0].i = 13
 * args[1].l = "string".toJString()
 * args[2].c = 'c'.toJChar()
 * args[3].z = true.toJBoolean()
 * ```
 * You may write
 * ```
 * val args = jArgs(4) { int(13); str("string"); char('c'); boolean(true) }
 * ```
 * Which will be inlined in the code above.
 *
 * @param count size of an array to allocate
 * @param block array initializer block
 */
context(memScope: NativePlacement)
public inline fun jArgs(count: Int, block: JArgumentsBuilder.() -> Unit): JArguments {
    val args = memScope.allocArray<JValue>(count)
    var index = 0
    block { argInit ->
        args[index++].argInit()
    }
    return args
}

/**
 * Executes the [block] in a new local reference frame, in which at least a given number of local references can be
 * created. Then automatically pops the frame.
 *
 * Note that local references already created in previous local frames are still valid in the current local frame.
 *
 * Some Java Virtual Machine implementations may choose to limit the maximum capacity, which may cause the function to
 * throw an exception.
 *
 * @since JDK/JRE 1.2
 */
context(env: JniEnv)
public inline fun <T> refFrame(capacity: Int, block: () -> T): T {
    PushLocalFrame(capacity)
    try {
        return block()
    } finally {
        PopLocalFrame()
    }
}

/**
 * Type that exposes method [JNINativeMethodRegistry.register] to register native methods.
 */
public typealias JNINativeMethodRegistry = (JniNativeMethod.() -> Unit) -> Unit

/**
 * Register new [JniNativeMethod] with specified [name], [signature] and [functionPointer].
 *
 * Name and signature must be in the null-terminated modified UTF-8. Use [String.modifiedUtf8] to get it, or **if you
 * are sure that your string doesn't have illegal characters** you may use optimized [String.utf8].
 */
context(memScope: AutofreeScope)
public inline fun JNINativeMethodRegistry.register(name: CValuesRef<ByteVar>, signature: CValuesRef<ByteVar>, functionPointer: CPointer<out CFunction<*>>): Unit = this {
    this.name = name.getPointer(memScope)
    this.signature = signature.getPointer(memScope)
    this.fnPtr = functionPointer
}

/**
 * Registers native methods with the class specified by the [clazz] argument.
 *
 * The function pointers nominally must have the following signature:
 * ```
 * fun (env: JniEnv, objectOrClass: JObject, ...): JBoolean|Byte|JChar|Short|Int|Long|Float|Double|JObject|Unit
 * ```
 *
 * Example:
 * ```
 * registerNativesFor(clazz, 1) {
 *     register("myGetStringBytes".utf8, "(Ljava/lang/String;II[B)V".utf8, staticCFunction { env: JniEnv, obj: JObject, jstr: JString, offset: Int, len: Int, buf: JByteArray ->
 *         ...
 *     })
 * }
 * ```
 *
 * Be aware that [RegisterNatives] can change the documented behavior of the JVM (including cryptographic algorithms,
 * correctness, security, type safety), by changing the native code to be executed for a given native Java method.
 * Therefore, use applications that have native libraries utilizing the [RegisterNatives] function with caution.
 *
 * @throws NoSuchMethodError if a specified method cannot be found or if the method is not native.
 */
context(env: JniEnv, memScope: AutofreeScope)
public inline fun registerNativesFor(clazz: JClass, count: Int, block: JNINativeMethodRegistry.() -> Unit) {
    val methods = memScope.allocArray<JniNativeMethod>(count)
    var index = 0
    block { init ->
        methods[index++].init()
    }

    RegisterNatives(clazz, methods, index)
}

/**
 * Version of the native method interface. For Java SE Platform 10 and later, it returns [JNI.v10].
 */
public inline val JniEnv.version: JniVersion get() = GetVersion()

/**
 * Exception object currently in the process of being thrown, or `null` if there is no one.
 *
 * The exception stays being thrown until either the native code calls [ExceptionClear], or the Java code handles the
 * exception.
 */
public inline val JniEnv.pendingException: JThrowable? get() = ExceptionOccurred()

/**
 * Executes [block] in case there is pending exception. Then clear the exception.
 */
context(env: JniEnv)
public inline fun handleException(block: (JThrowable) -> Unit) {
    val throwable = env.pendingException
    if (throwable != null) {
        block(throwable)
        ExceptionClear()
    }
}

/**
 * Executes [block] in case there is pending exception of a class [clazz]. Then clear the exception.
 */
context(env: JniEnv)
public inline fun handleException(clazz: JClass, block: (JThrowable) -> Unit) {
    val throwable = env.pendingException
    if (throwable != null && IsInstanceOf(throwable, clazz)) {
        block(throwable)
        ExceptionClear()
    }
}

/**
 * `true` when there is a pending exception, `false` otherwise.
 */
public inline val JniEnv.isExceptionThrown: Boolean get() = ExceptionCheck()

/**
 * `JavaVM` interface associated with the current thread.
 */
context(memScope: NativePlacement)
public inline val JniEnv.virtualMachine: JavaVM get() = GetJavaVM()

/**
 * Alias for safely retrieving a string's chars via [GetStringChars] and [ReleaseStringChars].
 *
 * @return The result of the [block], or `null` if [GetStringChars] fails.
 */
context(env: JniEnv, memScope: NativePlacement)
public inline fun <T> JString.modifyStringChars(block: (chars: CArrayPointer<UShortVar>, isCopy: Boolean) -> T): T? {
    val (chars, isCopy) = GetStringChars(this) ?: return null
    try {
        return block(chars, isCopy)
    } finally {
        ReleaseStringChars(this, chars)
    }
}

/**
 * Alias for safely retrieving a string's chars via [GetStringCritical] and [ReleaseStringCritical].
 *
 * @return The result of the [block], or `null` if [GetStringCritical] fails.
 *
 * @since JDK/JRE 1.2
 */
context(env: JniEnv, memScope: NativePlacement)
public inline fun <T> JString.modifyStringCritical(block: (chars: CArrayPointer<UShortVar>, isCopy: Boolean) -> T): T? {
    val (chars, isCopy) = GetStringCritical(this) ?: return null
    try {
        return block(chars, isCopy)
    } finally {
        ReleaseStringCritical(this, chars)
    }
}

/**
 * Alias for safely retrieving a string's chars via [GetStringUTFChars] and [ReleaseStringUTFChars].
 *
 * @return The result of the [block], or `null` if [GetStringUTFChars] fails.
 */
context(env: JniEnv, memScope: NativePlacement)
public inline fun <T> JString.modifyStringUTFChars(block: (chars: CArrayPointer<ByteVar>, isCopy: Boolean) -> T): T? {
    val (chars, isCopy) = GetStringUTFChars(this) ?: return null
    try {
        return block(chars, isCopy)
    } finally {
        ReleaseStringUTFChars(this, chars)
    }
}

/**
 * Special type that exposes methods [commit], [finalize] and [abort] to simplify working with arrays.
 */
public typealias ModifyingArrayScope = (ApplyChangesMode) -> Unit

/**
 * Alias for releasing an array with [ApplyChangesMode.Commit].
 */
public inline fun ModifyingArrayScope.commit(): Unit = this(ApplyChangesMode.Commit)

/**
 * Alias for releasing an array with [ApplyChangesMode.Commit].
 */
public inline fun ModifyingArrayScope.finalize(): Unit = this(ApplyChangesMode.FinalCommit)

/**
 * Alias for releasing an array with [ApplyChangesMode.Commit].
 */
public inline fun ModifyingArrayScope.abort(): Unit = this(ApplyChangesMode.Abort)

/**
 * Alias for working with `boolean` array's elements via [GetBooleanArrayElements] and releasing it with
 * [ReleaseBooleanArrayElements].
 *
 * You can access [commit], [finalize] and [abort] methods inside [block].
 *
 * @return The result of the [block], or `null` if [GetBooleanArrayElements] fails.
 */
context(env: JniEnv, memScope: NativePlacement)
public inline fun <T> JArray.modifyBooleanArray(block: ModifyingArrayScope.(carray: CArrayPointer<UByteVar>, isCopy: Boolean) -> T): T? {
    val (carray, isCopy) = GetBooleanArrayElements(this) ?: return null
    return block({ mode -> ReleaseBooleanArrayElements(this, carray, mode) }, carray, isCopy)
}

/**
 * Alias for working with `byte` array's elements via [GetByteArrayElements] and releasing it with
 * [ReleaseByteArrayElements].
 *
 * You can access [commit], [finalize] and [abort] methods inside [block].
 *
 * @return The result of the [block], or `null` if [GetByteArrayElements] fails.
 */
context(env: JniEnv, memScope: NativePlacement)
public inline fun <T> JArray.modifyByteArray(block: ModifyingArrayScope.(carray: CArrayPointer<ByteVar>, isCopy: Boolean) -> T): T? {
    val (carray, isCopy) = GetByteArrayElements(this) ?: return null
    return block({ mode -> ReleaseByteArrayElements(this, carray, mode) }, carray, isCopy)
}

/**
 * Alias for working with `char` array's elements via [GetCharArrayElements] and releasing it with
 * [ReleaseCharArrayElements].
 *
 * You can access [commit], [finalize] and [abort] methods inside [block].
 *
 * @return The result of the [block], or `null` if [GetCharArrayElements] fails.
 */
context(env: JniEnv, memScope: NativePlacement)
public inline fun <T> JArray.modifyCharArray(block: ModifyingArrayScope.(carray: CArrayPointer<UShortVar>, isCopy: Boolean) -> T): T? {
    val (carray, isCopy) = GetCharArrayElements(this) ?: return null
    return block({ mode -> ReleaseCharArrayElements(this, carray, mode) }, carray, isCopy)
}

/**
 * Alias for working with  `short` array's elements via [GetShortArrayElements] and releasing it with
 * [ReleaseShortArrayElements].
 *
 * You can access [commit], [finalize] and [abort] methods inside [block].
 *
 * @return The result of the [block], or `null` if [GetShortArrayElements] fails.
 */
context(env: JniEnv, memScope: NativePlacement)
public inline fun <T> JArray.modifyShortArray(block: ModifyingArrayScope.(carray: CArrayPointer<ShortVar>, isCopy: Boolean) -> T): T? {
    val (carray, isCopy) = GetShortArrayElements(this) ?: return null
    return block({ mode -> ReleaseShortArrayElements(this, carray, mode) }, carray, isCopy)
}

/**
 * Alias for working with  `int` array's elements via [GetIntArrayElements] and releasing it with
 * [ReleaseIntArrayElements].
 *
 * You can access [commit], [finalize] and [abort] methods inside [block].
 *
 * @return The result of the [block], or `null` if [GetIntArrayElements] fails.
 */
context(env: JniEnv, memScope: NativePlacement)
public inline fun <T> JArray.modifyIntArray(block: ModifyingArrayScope.(carray: CArrayPointer<IntVar>, isCopy: Boolean) -> T): T? {
    val (carray, isCopy) = GetIntArrayElements(this) ?: return null
    return block({ mode -> ReleaseIntArrayElements(this, carray, mode) }, carray, isCopy)
}

/**
 * Alias for working with `long` array's elements via [GetLongArrayElements] and releasing it with
 * [ReleaseLongArrayElements].
 *
 * You can access [commit], [finalize] and [abort] methods inside [block].
 *
 * @return The result of the [block], or `null` if [GetLongArrayElements] fails.
 */
context(env: JniEnv, memScope: NativePlacement)
public inline fun <T> JArray.modifyLongArray(block: ModifyingArrayScope.(carray: CArrayPointer<LongVar>, isCopy: Boolean) -> T): T? {
    val (carray, isCopy) = GetLongArrayElements(this) ?: return null
    return block({ mode -> ReleaseLongArrayElements(this, carray, mode) }, carray, isCopy)
}

/**
 * Alias for working with `float` array's elements via [GetFloatArrayElements] and releasing it with
 * [ReleaseFloatArrayElements].
 *
 * You can access [commit], [finalize] and [abort] methods inside [block].
 *
 * @return The result of the [block], or `null` if [GetFloatArrayElements] fails.
 */
context(env: JniEnv, memScope: NativePlacement)
public inline fun <T> JArray.modifyFloatArray(block: ModifyingArrayScope.(carray: CArrayPointer<FloatVar>, isCopy: Boolean) -> T): T? {
    val (carray, isCopy) = GetFloatArrayElements(this) ?: return null
    return block({ mode -> ReleaseFloatArrayElements(this, carray, mode) }, carray, isCopy)
}

/**
 * Alias for working with `double` array's elements via [GetDoubleArrayElements] and releasing it with
 * [ReleaseDoubleArrayElements].
 *
 * You can access [commit], [finalize] and [abort] methods inside [block].
 *
 * @return The result of the [block], or `null` if [GetDoubleArrayElements] fails.
 */
context(env: JniEnv, memScope: NativePlacement)
public inline fun <T> JArray.modifyDoubleArray(block: ModifyingArrayScope.(carray: CArrayPointer<DoubleVar>, isCopy: Boolean) -> T): T? {
    val (carray, isCopy) = GetDoubleArrayElements(this) ?: return null
    return block({ mode -> ReleaseDoubleArrayElements(this, carray, mode) }, carray, isCopy)
}

/**
 * Alias for working with primitive array's elements via [GetPrimitiveArrayCritical] and releasing it with
 * [ReleasePrimitiveArrayCritical].
 *
 * You can access [commit], [finalize] and [abort] methods inside [block].
 *
 * @return The result of the [block], or `null` if [GetPrimitiveArrayCritical] fails.
 */
context(env: JniEnv, memScope: NativePlacement)
public inline fun <T> JArray.modifyPrimitiveArrayCritical(block: ModifyingArrayScope.(carray: CArrayPointer<*>, isCopy: Boolean) -> T): T? {
    val (carray, isCopy) = GetPrimitiveArrayCritical(this) ?: return null
    return block({ mode -> ReleasePrimitiveArrayCritical(this, carray, mode) }, carray, isCopy)
}