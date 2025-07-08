@file:Suppress("NOTHING_TO_INLINE")

package io.github.mimimishkin.jni

import io.github.mimimishkin.jni.internal.raw.JavaVMOption
import kotlinx.cinterop.AutofreeScope
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.NativePlacement
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.utf16
import kotlinx.cinterop.utf8
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
import kotlin.code
import kotlin.reflect.KFunction
import kotlin.toUShort

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
public inline fun <T> JavaVM.withEnv(version: JniVersion = JNI.lastVersion, block: JniEnv.() -> T): T {
    return with(GetEnv(version), block)
}

/**
 * Attaches the current thread to the Java VM, executes the provided block of code within the attached environment
 * and then detaches the thread upon completion.
 *
 * @param version the requested JNI version to be used for attaching the current thread.
 * @param name the name of the thread.
 * @param group a global ref of a ThreadGroup object.
 */
context(memScope: AutofreeScope)
public inline fun <T> JavaVM.withEnvAttaching(
    version: JniVersion = JNI.lastVersion,
    name: String? = null,
    group: JObject? = null,
    block: JniEnv.() -> T
): T {
    val env = AttachCurrentThread(version, name?.modifiedUtf8, group)
    try {
        return with(env, block)
    } finally {
        DetachCurrentThread()
    }
}

/**
 * Converts JVM string into Kotlin Native string.
 *
 * @return converted string or `null` if fails.
 */
context(env: JniEnv, memScope: NativePlacement)
public inline fun JString.toKString(): String? {
    val (chars, _) = env.GetStringChars(this) ?: return null
    val res = chars.toKString()
    env.ReleaseStringChars(this, chars)
    return res
}

/**
 * Converts Kotlin Native string into JVM string.
 *
 * @return converted string or `null` if fails.
 */
context(env: JniEnv, memScope: AutofreeScope)
public inline fun String.toJString(): JString? {
    return env.NewString(utf16.getPointer(memScope), length)
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
public inline fun JArgumentsBuilder.char(value: Char): Unit = this { this.c = value.code.toUShort() }

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
 */
public inline fun <T> JniEnv.refFrame(capacity: Int, block: () -> T): T {
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
public typealias JNINativeMethodRegistry = (JNINativeMethod.() -> Unit) -> Unit

/**
 * Register new [JNINativeMethod] with specified [name], [signature] and [functionPointer].
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
context(memScope: AutofreeScope)
public inline fun JniEnv.registerNativesFor(clazz: JClass, count: Int, block: JNINativeMethodRegistry.() -> Unit) {
    val methods = memScope.allocArray<JNINativeMethod>(count)
    var index = 0
    block { init ->
        methods[index++].init()
    }

    RegisterNatives(clazz, methods, index)
}