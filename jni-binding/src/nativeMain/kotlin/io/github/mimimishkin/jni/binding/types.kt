@file:Suppress("NOTHING_TO_INLINE", "ClassName")

package io.github.mimimishkin.jni.binding

import io.github.mimimishkin.jni.binding.JNI.safeCall
import io.github.mimimishkin.jni.binding.annotation.WithJvmType
import kotlinx.cinterop.*

/**
 * Specifies the mode for applying changes in [releaseElements] and [releaseElementsCritical] functions.
 */
public enum class ApplyChangesMode {
    /** Commit changes back to the original array and release the buffer. */
    FinalCommit,

    /** Commit changes back to the original array and safe the buffer. */
    Commit,

    /** Abort changes and release the buffer. */
    Abort,
}

/**
 * Represents the type of JNI reference.
 *
 * @see [refType]
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
 * Java VM initialization option.
 *
 * *Standard Options*
 *
 * | optionString                 | meaning                                                                                                                                                                                                                                                                                                                                                  |
 * |------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
 * | `-D<name>=<value>`           | Set a system property                                                                                                                                                                                                                                                                                                                                    |
 * | `-verbose[:class\|gc \|jni]` | Enable verbose output. The options can be followed by a comma-separated list of names indicating what kind of messages will be printed by the VM. For example, "-verbose:gc,class" instructs the VM to print GC and class loading related messages. Standard names include: gc, class, and  All nonstandard (VM-specific) names must begin with "X". |
 * | `vfprintf`                   | extraInfo is a pointer to the `vfprintf` hook.                                                                                                                                                                                                                                                                                                           |
 * | `exit`                       | extraInfo is a pointer to the `exit` hook.                                                                                                                                                                                                                                                                                                               |
 * | `abort`                      | extraInfo is a pointer to the `abort` hook.                                                                                                                                                                                                                                                                                                              |
 */
public expect class JavaVMOption : CStructVar {
    /**
     * The option as a string in the default platform encoding.
     */
    public var optionString: CPointer<ByteVar>?

    /**
     * Any addition info required by option.
     */
    public var extraInfo: COpaquePointer?
}

/**
 * The option as a Kotlin string.
 *
 * Alias to [JavaVMOption.optionString].
 */
context(autofreeScope: AutofreeScope)
public inline var JavaVMOption.option: String?
    get() = optionString?.toKString()
    set(value) { optionString = value?.cstr?.getPointer(autofreeScope) }

/**
 * Java VM initialization arguments structure.
 */
public expect class JavaVMInitArgs : CStructVar {
    /**
     * The JNI version.
     */
    public var version: JniVersion

    /**
     * The option array.
     */
    public var options: CPointer<JavaVMOption>?

    /**
     * The size of [options] array.
     */
    public var nOptions: Int

    @PublishedApi internal var ignoreUnrecognized: UByte
}

/**
 * Whether unrecognized options are ignored.
 */
public inline var JavaVMInitArgs.ignoreUnknown: Boolean
    get() = ignoreUnrecognized.toKBoolean()
    set(value) { ignoreUnrecognized = value.toJBoolean() }

/**
 * Thread attachment arguments.
 */
public expect class JavaVMAttachArgs : CStructVar {
    /**
     * The requested JNI version.
     */
    public var version: JniVersion

    /**
     * The name of the thread in the null-terminated modified UTF-8.
     */
    public var name: CPointer<ByteVar>?
}

/**
 * Global ref of a `ThreadGroup` object.
 *
 * Kept out of [JavaVMAttachArgs] itself, because it is a `jobject` and the two JNI headers spell that differently.
 */
internal expect inline var JavaVMAttachArgs.group: COpaquePointer?

/**
 * The option list.
 */
public inline val JavaVMInitArgs.optionList: List<JavaVMOption>
    get() = List(nOptions) { i -> options!![i] }

/**
 * Retrieves the default initialization arguments for the Java VM.
 *
 * Before calling this function, native code must set the [JavaVMInitArgs.version] field to the JNI version it
 * expects the VM to support.
 * After this function returns, [JavaVMInitArgs.version] will be set to the actual JNI version the VM supports.
 *
 * Note: calling this function is not necessary since JDK/JRE 1.6.
 *
 * @receiver the [JavaVMInitArgs] structure to fill with default values.
 */
public fun JavaVMInitArgs.setDefault() {
    safeCall {
        platformGetDefaultJavaVMInitArgs(ptr)
    }
}

/**
 * Special type that exposes [option] method to add VM options.
 * It's used in [buildJavaVMInitArgs] function.
 */
public typealias JavaVMInitArgsBuilder = (JavaVMOption.() -> Unit) -> Unit

/**
 * Add [optionString] and [extraInfo] pair to [JavaVMInitArgs].
 */
context(autofreeScope: AutofreeScope)
public inline fun JavaVMInitArgsBuilder.option(optionString: String, extraInfo: COpaquePointer? = null): Unit = this {
    this.option = optionString
    this.extraInfo = extraInfo
}

/**
 * Allocates and initializes [JavaVMInitArgs].
 *
 * Instead of writing
 * ```
 * val options = allocArray<JavaVMOption>(4)
 * var i = 0
 * options[i++].option = "-Djava.compiler=NONE"                // disable JIT
 * options[i++].option = "-Djava.class.path=C:\\myclasses"     // user classes
 * options[i++].option = "-Djava.library.path=C:\\mylibs"      // set native library path
 * options[i++].option = "-verbose:jni"                        // print JNI-related messages
 *
 * val args = alloc<JavaVMInitArgsStruct>()
 * args.version = JNI.v21
 * args.ignoreUnrecognized = false.toJBoolean()
 * args.nOptions = i
 * args.options = options
 *
 * ```
 * You may write
 * ```
 * val args = buildJavaVMInitArgs(JNI.v21, 4) {
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
context(placement: NativePlacement)
public inline fun buildJavaVMInitArgs(
    version: JniVersion,
    optionsCount: Int,
    ignoreUnrecognized: Boolean = false,
    block: (JavaVMInitArgsBuilder).() -> Unit
): JavaVMInitArgs {
    val options = placement.allocArray<JavaVMOption>(optionsCount)
    var index = 0
    block { optionInit ->
        options[index++].optionInit()
    }

    val args = placement.alloc<JavaVMInitArgs>()
    args.version = version
    args.ignoreUnrecognized = ignoreUnrecognized.toJBoolean()
    args.nOptions = index
    args.options = options
    return args
}

/**
 * JVM interprets `boolean` values as unsigned byte.
 */
public typealias JBoolean = @WithJvmType("boolean") UByte

/**
 * Converts Kotlin `boolean` to JVM `boolean`.
 */
public inline fun Boolean.toJBoolean(): JBoolean = if (this) 1u else 0u

/**
 * Converts JVM `boolean` to Kotlin `boolean`.
 */
public inline fun JBoolean.toKBoolean(): Boolean = this == 1u.toUByte()

/**
 * JVM interprets `byte` values the same way as Kotlin.
 */
public typealias JByte = @WithJvmType("byte") Byte

/**
 * JVM interprets `char` values as unsigned short.
 */
public typealias JChar = @WithJvmType("char") UShort

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
public typealias JShort = @WithJvmType("short") Short

/**
 * JVM interprets `int` values the same way as Kotlin.
 */
public typealias JInt = @WithJvmType("int") Int

/**
 * JVM interprets `long` values the same way as Kotlin.
 */
public typealias JLong = @WithJvmType("long") Long

/**
 * JVM interprets `float` values the same way as Kotlin.
 */
public typealias JFloat = @WithJvmType("float") Float

/**
 * JVM interprets `double` values the same way as Kotlin.
 */
public typealias JDouble = @WithJvmType("double") Double

/**
 * Allows explicitly specifying that a function doesn't return any value in the same way as if it returned something and
 * we've used [JBoolean], [JByte], [JChar] or others.
 */
public typealias JVoid = @WithJvmType("void") Unit

/**
 * The underlying `open` opaque type for [JObject], allowing to create typesafe API
 * when, for example [JClass] can be safely cast to [JObject] but not vise versa.
 * 
 * This type must not be used directly, it's only for type-safety of [JRef] usage.
 */
public open class _jobject(rawPtr: NativePtr) : CPointed(rawPtr)

/**
 * Helper typealias to simplify working with [JObject] and its descendants.
 */
public typealias JRef<T> = CPointer<out T>

/**
 * Pointer to `java.lang.Object`.
 */
public typealias JObject = @WithJvmType("java.lang.Object") JRef<_jobject>

/**
 * Casts to any [JObject] descendant.
 */
@Suppress("UNCHECKED_CAST")
public inline fun <T : JRef<O>, O : _jobject> JObject.unsafeCast(): T = this as T

/**
 * Converts this [JObject] to the `jobject` pointer of the underlying platform bindings.
 */
@Suppress("UNCHECKED_CAST")
public inline val JObject?.c: COpaquePointer? get() = this as COpaquePointer?

/**
 * Converts the platform's `jobject` pointer to [JObject] or its descendant.
 */
@Suppress("UNCHECKED_CAST")
public inline fun <T : JRef<O>, O : _jobject> COpaquePointer?.wrap(): T? = this as T?

/**
 * The underlying opaque type for [JClass].
 *
 * This type must not be used directly, it's only for type-safety of [JRef] usage.
 */
public class _jclass(rawPtr: NativePtr) : _jobject(rawPtr)

/**
 * Pointer to `java.lang.Class`.
 */
public typealias JClass = @WithJvmType("java.lang.Class") JRef<_jclass>

/**
 * The underlying `open` opaque type for [JThrowable].
 *
 * This type must not be used directly, it's only for type-safety of [JRef] usage.
 */
public open class _jthrowable(rawPtr: NativePtr) : _jobject(rawPtr)

/**
 * Pointer to `java.lang.Throwable`.
 */
public typealias JThrowable = @WithJvmType("java.lang.Throwable") JRef<_jthrowable>

/**
 * The underlying opaque type for [JString].
 *
 * This type must not be used directly, it's only for type-safety of [JRef] usage.
 */
public class _jstring(rawPtr: NativePtr) : _jobject(rawPtr)

/**
 * Pointer to `java.lang.String`.
 */
public typealias JString = @WithJvmType("java.lang.String") JRef<_jstring>

/**
 * Converts JVM string into Kotlin string.
 *
 * @return converted string or `null` if fails.
 */
context(env: JniEnv, placement: NativePlacement)
public inline fun JString.toKString(): String? {
    val length = this.length
    val (chars, _) = this.getChars() ?: return null
    val result = CharArray(length)
    var index = 0
    while (index < length) {
        result[index] = chars[index].toInt().toChar()
        ++index
    }
    this.releaseChars(chars)
    return result.concatToString()
}

/**
 * Converts Kotlin string into JVM string.
 *
 * @return converted string or `null` if fails.
 *
 * @throws OutOfMemoryError if the system runs out of memory.
 */
context(env: JniEnv, autofreeScope: AutofreeScope)
public inline fun String.toJString(): JString? {
    return newString(utf16.getPointer(autofreeScope), length)
}

/**
 * The underlying `open` opaque type for [JArray].
 *
 * This type must not be used directly, it's only for type-safety of [JRef] usage.
 */
public sealed class _jarray(rawPtr: NativePtr) : _jobject(rawPtr)

/**
 * Pointer to an array.
 */
public typealias JArray = JRef<_jarray>

/**
 * The underlying `sealed` opaque type for [JPrimitiveArray].
 *
 * This type must not be used directly, it's only for type-safety of [JRef] usage.
 */
public sealed class _jprimitiveArray<T : CPrimitiveVar>(rawPtr: NativePtr) : _jarray(rawPtr)

/**
 * Pointer to a primitive array.
 */
public typealias JPrimitiveArray<T> = JRef<_jprimitiveArray<T>>

/**
 * The underlying opaque type for [JBooleanArray].
 *
 * This type must not be used directly, it's only for type-safety of [JRef] usage.
 */
public class _jbooleanArray(rawPtr: NativePtr) : _jprimitiveArray<UByteVar>(rawPtr)

/**
 * Pointer to a boolean[] object.
 */
public typealias JBooleanArray = @WithJvmType("boolean[]") JRef<_jbooleanArray>

/**
 * Converts Kotlin [UByteArray] into JVM `boolean[]` using [setRegion].
 *
 * @return converted array or `null` if array creation fails.
 */
context(env: JniEnv)
public inline fun UByteArray.toJArray(): JBooleanArray? {
    val array = newBooleanArray(size) ?: return null
    usePinned { array.setRegion(0, size, it.addressOf(0)) }
    return array
}

/**
 * Converts JVM `boolean[]` into Kotlin [UByteArray] using [getRegion].
 *
 * @throws IndexOutOfBoundsException if [length] is greater than array size.
 */
context(env: JniEnv)
public inline fun JBooleanArray.toKArray(length: Int = this.length): UByteArray {
    val array = UByteArray(length)
    array.usePinned { getRegion(0, length, it.addressOf(0)) }
    return array
}

/**
 * The underlying opaque type for [JByteArray].
 *
 * This type must not be used directly, it's only for type-safety of [JRef] usage.
 */
public class _jbyteArray(rawPtr: NativePtr) : _jprimitiveArray<ByteVar>(rawPtr)

/**
 * Pointer to a byte[] object.
 */
public typealias JByteArray = @WithJvmType("byte[]") JRef<_jbyteArray>

/**
 * Converts Kotlin [ByteArray] into JVM `byte[]` using [setRegion].
 *
 * @return converted array or `null` if array creation fails.
 */
context(env: JniEnv)
public inline fun ByteArray.toJArray(): JByteArray? {
    val array = newByteArray(size) ?: return null
    usePinned { array.setRegion(0, size, it.addressOf(0)) }
    return array
}

/**
 * Converts JVM `byte[]` into Kotlin [ByteArray] using [getRegion].
 *
 * @throws IndexOutOfBoundsException if [length] is greater than array size.
 */
context(env: JniEnv)
public inline fun JByteArray.toKArray(length: Int = this.length): ByteArray {
    val array = ByteArray(length)
    array.usePinned { getRegion(0, length, it.addressOf(0)) }
    return array
}

/**
 * The underlying opaque type for [JCharArray].
 *
 * This type must not be used directly, it's only for type-safety of [JRef] usage.
 */
public class _jcharArray(rawPtr: NativePtr) : _jprimitiveArray<UShortVar>(rawPtr)

/**
 * Pointer to a char[] object.
 */
public typealias JCharArray = @WithJvmType("char[]") JRef<_jcharArray>

/**
 * Converts Kotlin [UShortArray] into JVM `char[]` using [setRegion].
 *
 * @return converted array or `null` if array creation fails.
 */
context(env: JniEnv)
public inline fun UShortArray.toJArray(): JCharArray? {
    val array = newCharArray(size) ?: return null
    usePinned { array.setRegion(0, size, it.addressOf(0)) }
    return array
}

/**
 * Converts JVM `char[]` into Kotlin [UShortArray] using [getRegion].
 *
 * @throws IndexOutOfBoundsException if [length] is greater than array size.
 */
context(env: JniEnv)
public inline fun JCharArray.toKArray(length: Int = this.length): UShortArray {
    val array = UShortArray(length)
    array.usePinned { getRegion(0, length, it.addressOf(0)) }
    return array
}

/**
 * The underlying opaque type for [JShortArray].
 *
 * This type must not be used directly, it's only for type-safety of [JRef] usage.
 */
public class _jshortArray(rawPtr: NativePtr) : _jprimitiveArray<ShortVar>(rawPtr)

/**
 * Pointer to a short[] object.
 */
public typealias JShortArray = @WithJvmType("short[]") JRef<_jshortArray>

/**
 * Converts Kotlin [ShortArray] into JVM `short[]` using [setRegion].
 *
 * @return converted array or `null` if array creation fails.
 */
context(env: JniEnv)
public inline fun ShortArray.toJArray(): JShortArray? {
    val array = newShortArray(size) ?: return null
    usePinned { array.setRegion(0, size, it.addressOf(0)) }
    return array
}

/**
 * Converts JVM `short[]` into Kotlin [ShortArray] using [getRegion].
 *
 * @throws IndexOutOfBoundsException if [length] is greater than array size.
 */
context(env: JniEnv)
public inline fun JShortArray.toKArray(length: Int = this.length): ShortArray {
    val array = ShortArray(length)
    array.usePinned { getRegion(0, length, it.addressOf(0)) }
    return array
}

/**
 * The underlying opaque type for [JIntArray].
 *
 * This type must not be used directly, it's only for type-safety of [JRef] usage.
 */
public class _jintArray(rawPtr: NativePtr) : _jprimitiveArray<IntVar>(rawPtr)

/**
 * Pointer to an int[] object.
 */
public typealias JIntArray = @WithJvmType("int[]") JRef<_jintArray>

/**
 * Converts Kotlin [IntArray] into JVM `int[]` using [setRegion].
 *
 * @return converted array or `null` if array creation fails.
 */
context(env: JniEnv)
public inline fun IntArray.toJArray(): JIntArray? {
    val array = newIntArray(size) ?: return null
    usePinned { array.setRegion(0, size, it.addressOf(0)) }
    return array
}

/**
 * Converts JVM `int[]` into Kotlin [IntArray] using [getRegion].
 *
 * @throws IndexOutOfBoundsException if [length] is greater than array size.
 */
context(env: JniEnv)
public inline fun JIntArray.toKArray(length: Int = this.length): IntArray {
    val array = IntArray(length)
    array.usePinned { getRegion(0, length, it.addressOf(0)) }
    return array
}

/**
 * The underlying opaque type for [JLongArray].
 *
 * This type must not be used directly, it's only for type-safety of [JRef] usage.
 */
public class _jlongArray(rawPtr: NativePtr) : _jprimitiveArray<LongVar>(rawPtr)

/**
 * Pointer to a long[] object.
 */
public typealias JLongArray = @WithJvmType("long[]") JRef<_jlongArray>

/**
 * Converts Kotlin [LongArray] into JVM `long[]` using [setRegion].
 *
 * @return converted array or `null` if array creation fails.
 */
context(env: JniEnv)
public inline fun LongArray.toJArray(): JLongArray? {
    val array = newLongArray(size) ?: return null
    usePinned { array.setRegion(0, size, it.addressOf(0)) }
    return array
}

/**
 * Converts JVM `long[]` into Kotlin [LongArray] using [getRegion].
 *
 * @throws IndexOutOfBoundsException if [length] is greater than array size.
 */
context(env: JniEnv)
public inline fun JLongArray.toKArray(length: Int = this.length): LongArray {
    val array = LongArray(length)
    array.usePinned { getRegion(0, length, it.addressOf(0)) }
    return array
}

/**
 * The underlying opaque type for [JFloatArray].
 *
 * This type must not be used directly, it's only for type-safety of [JRef] usage.
 */
public class _jfloatArray(rawPtr: NativePtr) : _jprimitiveArray<FloatVar>(rawPtr)

/**
 * Pointer to a float[] object.
 */
public typealias JFloatArray = @WithJvmType("float[]") JRef<_jfloatArray>

/**
 * Converts Kotlin [FloatArray] into JVM `float[]` using [setRegion].
 *
 * @return converted array or `null` if array creation fails.
 */
context(env: JniEnv)
public inline fun FloatArray.toJArray(): JFloatArray? {
    val array = newFloatArray(size) ?: return null
    usePinned { array.setRegion(0, size, it.addressOf(0)) }
    return array
}

/**
 * Converts JVM `float[]` into Kotlin [FloatArray] using [getRegion].
 *
 * @throws IndexOutOfBoundsException if [length] is greater than array size.
 */
context(env: JniEnv)
public inline fun JFloatArray.toKArray(length: Int = this.length): FloatArray {
    val array = FloatArray(length)
    array.usePinned { getRegion(0, length, it.addressOf(0)) }
    return array
}

/**
 * The underlying opaque type for [JDoubleArray].
 *
 * This type must not be used directly, it's only for type-safety of [JRef] usage.
 */
public class _jdoubleArray(rawPtr: NativePtr) : _jprimitiveArray<DoubleVar>(rawPtr)

/**
 * Pointer to a double[] object.
 */
public typealias JDoubleArray = @WithJvmType("double[]") JRef<_jdoubleArray>

/**
 * Converts Kotlin [DoubleArray] into JVM `double[]` using [setRegion].
 *
 * @return converted array or `null` if array creation fails.
 */
context(env: JniEnv)
public inline fun DoubleArray.toJArray(): JDoubleArray? {
    val array = newDoubleArray(size) ?: return null
    usePinned { array.setRegion(0, size, it.addressOf(0)) }
    return array
}

/**
 * Converts JVM `double[]` into Kotlin [DoubleArray] using [getRegion].
 *
 * @throws IndexOutOfBoundsException if [length] is greater than array size.
 */
context(env: JniEnv)
public inline fun JDoubleArray.toKArray(length: Int = this.length): DoubleArray {
    val array = DoubleArray(length)
    array.usePinned { getRegion(0, length, it.addressOf(0)) }
    return array
}

/**
 * The underlying `open` opaque type for [JObjectArray].
 *
 * This type must not be used directly, it's only for type-safety of [JRef] usage.
 */
public open class _jobjectArray(rawPtr: NativePtr) : _jarray(rawPtr)

/**
 * Pointer to an array of an object type.
 */
public typealias JObjectArray = @WithJvmType("java.lang.Object[]") JRef<_jobjectArray>

/**
 * Returns an iterator over the elements of the object array.
 * As the array may contain `null` elements or elements of a different type, this iterator is strongly typed.
 */
context(env: JniEnv)
public operator fun JObjectArray.iterator(): Iterator<JObject?> {
    val len = length
    var index = 0
    return object : Iterator<JObject?> {
        override fun next(): JObject? {
            return get(index++)
        }

        override fun hasNext(): Boolean {
            return index < len
        }
    }
}

/**
 * Returns an [Iterable] over the elements of the object array.
 * As the array may contain `null` elements or elements of a different type, this iterable is strongly typed.
 */
context(env: JniEnv)
public fun JObjectArray.asIterable(): Iterable<JObject?> = Iterable { iterator() }

/**
 * The underlying `open` opaque type for [JByteBuffer].
 *
 * This type must not be used directly, it's only for type-safety of [JRef] usage.
 */
public open class _jbyteBuffer(rawPtr: NativePtr) : _jobject(rawPtr)

/**
 * Pointer to `java.nio.ByteBuffer`.
 */
public typealias JByteBuffer = @WithJvmType("java.nio.ByteBuffer") JRef<_jbyteBuffer>

/**
 * Pointer to `java.lang.Object` which is not counted by GC.
 */
public typealias JWeak = JObject

/**
 * Union in which JNI expect arguments to pass to Java functions.
 */
public expect class JValue : CStructVar {
    @PublishedApi internal var f: Float
    @PublishedApi internal var c: UShort
    @PublishedApi internal var d: Double
    @PublishedApi internal var b: Byte
    @PublishedApi internal var j: Long
    @PublishedApi internal var s: Short
    @PublishedApi internal var z: UByte
    @PublishedApi internal var i: Int
}

/**
 * Union field of type `boolean`.
 */
public inline var JValue.boolean: Boolean
    get() = z.toKBoolean()
    set(value) { z = value.toJBoolean() }

/**
 * Union field of type `byte`.
 */
public inline var JValue.byte: Byte
    get() = b
    set(value) { b = value }

/**
 * Union field of type `char`.
 */
public inline var JValue.char: Char
    get() = c.toKChar()
    set(value) { c = value.toJChar() }

/**
 * Union field of type `short`.
 */
public inline var JValue.short: Short
    get() = s
    set(value) { s = value }

/**
 * Union field of type `int`.
 */
public inline var JValue.int: Int
    get() = i
    set(value) { i = value }

/**
 * Union field of type `long`.
 */
public inline var JValue.long: Long
    get() = j
    set(value) { j = value }

/**
 * Union field of type `float`.
 */
public inline var JValue.float: Float
    get() = f
    set(value) { f = value }

/**
 * Union field of type `double`.
 */
public inline var JValue.double: Double
    get() = d
    set(value) { d = value }

/**
 * Union field of an object type.
 */
public expect inline var JValue.ref: JObject?

/**
 * C array of [JValue].
 *
 * Example:
 * ```
 * val clazz = findClass("path/to/Class")!!
 * val methodId = clazz.methodId("myMethod".utf8, "(Ljava/lang/String;I)Ljava/lang/String;".utf8)!!
 * val jStr = "Meow".toJString()
 * val jRes: JString? = clazz.callStaticObjectMethod(methodId, jArgs(3) { int(42); ref(jStr); ref(null) })?.unsafeCast()
 * val res = jRes?.toKString()
 * jStr?.deleteLocalRef()
 * jRes?.deleteLocalRef()
 * ```
 *
 * @see jArgs
 */
public typealias JArguments = CArrayPointer<JValue>

/**
 * Special type that exposes methods to add arguments:
 * - [boolean], [byte], [char], [short], [int], [long], [float], [long] to pass primitives
 * - [ref] to pass objects.
 *
 * It's used in [jArgs] function.
 */
public typealias JArgumentsBuilder = (JValue.() -> Unit) -> Unit

/**
 * Add `boolean` value to arguments.
 */
public inline fun JArgumentsBuilder.boolean(value: Boolean): Unit = this { boolean = value }

/**
 * Add `byte` value to arguments.
 */
public inline fun JArgumentsBuilder.byte(value: Byte): Unit = this { byte = value }

/**
 * Add `char` value to arguments.
 */
public inline fun JArgumentsBuilder.char(value: Char): Unit = this { char = value }

/**
 * Add `short` value to arguments.
 */
public inline fun JArgumentsBuilder.short(value: Short): Unit = this { short = value }

/**
 * Add `int` value to arguments.
 */
public inline fun JArgumentsBuilder.int(value: Int): Unit = this { int = value }

/**
 * Add `long` value to arguments.
 */
public inline fun JArgumentsBuilder.long(value: Long): Unit = this { long = value }

/**
 * Add `float` value to arguments.
 */
public inline fun JArgumentsBuilder.float(value: Float): Unit = this { float = value }

/**
 * Add `long` value to arguments.
 */
public inline fun JArgumentsBuilder.long(value: Double): Unit = this { double = value }

/**
 * Add `Object` value to arguments.
 */
public inline fun JArgumentsBuilder.ref(value: JObject?): Unit = this { ref = value }

/**
 * Tries to add [value] of any type to arguments.
 * Only [JObject] and not-null primitives are supported.
 *
 * @throws IllegalArgumentException if [T] is neither primitive nor [CPointer].
 * @throws NullPointerException if [T] is nullable primitive.
 */
public inline fun <reified T> JArgumentsBuilder.any(value: T?) {
    when (T::class) {
        Boolean::class -> boolean(value as Boolean)
        Byte::class -> byte(value as Byte)
        Char::class -> char(value as Char)
        Short::class -> short(value as Short)
        Int::class -> int(value as Int)
        Long::class -> long(value as Long)
        Float::class -> float(value as Float)
        Double::class -> long(value as Double)
        CPointer::class -> @Suppress("UNCHECKED_CAST") ref(value as JObject?)
        else -> throw IllegalArgumentException("Unsupported type: ${T::class.qualifiedName}")
    }
}

/**
 * Allocates and initializes [JArguments].
 *
 * Instead of writing
 * ```
 * val args = allocArray<JValue>(4)
 * var i = 0
 * args[i++].int = 13
 * args[i++].ref = "string".toJString()
 * args[i++].char = 'c'
 * args[i++].boolean = true
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
context(placement: NativePlacement)
public inline fun jArgs(count: Int, block: JArgumentsBuilder.() -> Unit): JArguments {
    val args = placement.allocArray<JValue>(count)
    var index = 0
    block { argInit ->
        args[index++].argInit()
    }
    return args
}

/**
 * The underlying opaque type for [JFieldID].
 */
public class _jFieldID private constructor(rawPtr: NativePtr) : CPointed(rawPtr)

/**
 * Java unique field ID.
 *
 * Value of this type can be obtained from [fieldId] and [staticFieldId].
 */
public typealias JFieldID = CPointer<_jFieldID>

@Suppress("UNCHECKED_CAST")
public inline val JFieldID.c: CPointer<_jFieldID> get() = this

@Suppress("UNCHECKED_CAST")
public inline fun CPointer<_jFieldID>.wrap(): JFieldID = this

/**
 * The underlying opaque type for [JMethodID].
 */
public class _jMethodID private constructor(rawPtr: NativePtr) : CPointed(rawPtr)

/**
 * Java unique method ID.
 *
 * Value of this type can be obtained from [methodId] and [staticMethodId].
 */
public typealias JMethodID = CPointer<_jMethodID>

@Suppress("UNCHECKED_CAST")
public inline val JMethodID.c: CPointer<_jMethodID> get() = this

@Suppress("UNCHECKED_CAST")
public inline fun CPointer<_jMethodID>.wrap(): JMethodID = this

/**
 * The underlying type for [JavaVM].
 *
 * We need this type to be able to use [JavaVM] in consumer common code without cinterop commonization.
 */
public expect class Raw_JniInvokeInterface : CStructVar

/**
 * Type allowing to operate with Invocation API.
 */
public typealias JavaVM = CPointerVar<Raw_JniInvokeInterface>

/**
 * The underlying type for [JniEnv].
 *
 * We need this type to be able to use [JniEnv] in consumer common code without cinterop commonization.
 */
public expect class Raw_JniNativeInterface : CStructVar

/**
 * Type allowing to operate with Native JNI API.
 */
public typealias JniEnv = CPointerVar<Raw_JniNativeInterface>

/**
 * Represents a native method specification.
 */
public expect class JniNativeMethod : CStructVar {
    /**
     * The pointer to JNI function.
     */
    public var fnPtr: COpaquePointer?

    /**
     * The signature of the native method in modified UTF-8.
     */
    public var signature: CPointer<ByteVar>?

    /**
     * The name of the native method in modified UTF-8.
     */
    public var name: CPointer<ByteVar>?
}