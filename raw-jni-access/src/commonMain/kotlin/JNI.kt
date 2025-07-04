@file:Suppress("NOTHING_TO_INLINE")

package io.github.mimimishkin.jni

import kotlinx.cinterop.*
import raw_jni.*
import kotlin.String

public object JNI {
    public enum class ApplyChangesMode(@PublishedApi internal val nativeCode: Int) {
        Commit(JNI_COMMIT),
        FinalCommit(0),
        Abort(JNI_ABORT),
    }

    public enum class RefType {
        Invalid,
        Local,
        Global,
        WeakGlobal
    }

    public enum class Version(@PublishedApi internal val nativeCode: Int) {
        VERSION_1_1(JNI_VERSION_1_1),
        VERSION_1_2(JNI_VERSION_1_2),
        VERSION_1_4(JNI_VERSION_1_4),
        VERSION_1_6(JNI_VERSION_1_6),
        VERSION_1_8(JNI_VERSION_1_8),
        VERSION_9(JNI_VERSION_9),
        VERSION_10(JNI_VERSION_10),
    }

    public inline fun GetDefaultJavaVMInitArgs(args: JavaVMInitArgs) {
        checkJniResult(JNI_GetDefaultJavaVMInitArgs(args.ptr))
    }

    public inline fun CreateJavaVM(args: JavaVMInitArgs): Pair<JavaVM, JNIEnv> {
        val vm = CPointerVar<JavaVM>(NativePtr.NULL)
        val env = CPointerVar<JNIEnv>(NativePtr.NULL)

        val res = JNI_CreateJavaVM(
            pvm = vm.ptr,
            penv = env.ptr.reinterpret(),
            args = args.ptr
        )

        checkJniResult(res)
        return vm.pointed!! to env.pointed!!
    }

    public inline fun GetCreatedJavaVMs(): List<JavaVM> {
        return memScoped {
            // test invocation to get VMs count
            var vms = allocArray<CPointerVar<JavaVM>>(0)
            val count = alloc<IntVar>()
            checkJniResult(JNI_GetCreatedJavaVMs(vms, 0, count.ptr))

            vms = allocArray(count.value)
            checkJniResult(JNI_GetCreatedJavaVMs(vms, count.value, count.ptr))
            List(count.value) { i -> vms[i]!!.pointed }
        }
    }
}

public typealias JavaVMInitArgs = raw_jni.JavaVMInitArgs

public inline fun MemScope.JavaVMInitArgs(
    version: Int,
    options: List<Pair<String, CPointer<*>?>> = emptyList(),
    ignoreUnrecognized: Boolean = false,
): JavaVMInitArgs {
    return alloc<JavaVMInitArgs> {
        this.version = version
        this.ignoreUnrecognized = ignoreUnrecognized.toJBoolean()
        this.nOptions = options.size
        this.options = allocArray(options.size) { index ->
            this.optionString = options[index].first.cstr.ptr
            this.extraInfo = options[index].second
        }
    }
}

public inline var JavaVMInitArgs.ignoreUnrecognizedOptions: Boolean
    get() = ignoreUnrecognized.toKBoolean()
    set(value) { ignoreUnrecognized = value.toJBoolean() }

public inline val JavaVMInitArgs.preferences: List<Pair<String, CPointer<*>?>>
    get() = List(nOptions) { i -> options!![i].optionString!!.toKString() to options!![i].extraInfo }

@PublishedApi
internal fun checkJniResult(res: Int) {
    when (res) {
        JNI_OK -> {}
        JNI_ERR -> throw Exception()
        JNI_EDETACHED -> throw IllegalStateException("thread detached from the VM")
        JNI_EVERSION -> throw IllegalArgumentException("JNI version error")
        JNI_ENOMEM -> throw IllegalStateException("not enough memory")
        JNI_EEXIST -> throw IllegalStateException("VM already created")
        JNI_EINVAL -> throw IllegalArgumentException()
    }
}

@PublishedApi
internal inline fun Boolean.toJBoolean(): UByte = if (this) JNI_TRUE.toUByte() else JNI_FALSE.toUByte()

@PublishedApi
internal inline fun UByte.toKBoolean(): Boolean = this == JNI_TRUE.toUByte()

public val String.modifiedUtf8: CValues<ByteVar> get() {
    // Estimate the max possible length: 3 bytes per char + 1 for null terminator
    val byteArray = ByteArray(this.length * 3 + 1)
    var pos = 0
    for (ch in this) {
        when (ch) {
            '\u0000' -> {
                // Null char is encoded as 0xC0 0x80
                byteArray[pos++] = 0xC0.toByte()
                byteArray[pos++] = 0x80.toByte()
            }
            in '\u0001'..'\u007F' -> {
                // 1-byte encoding
                byteArray[pos++] = ch.code.toByte()
            }
            in '\u0080'..'\u07FF' -> {
                // 2-byte encoding
                byteArray[pos++] = (0xC0 or (ch.code shr 6)).toByte()
                byteArray[pos++] = (0x80 or (ch.code and 0x3F)).toByte()
            }
            else -> {
                // 3-byte encoding (including surrogates)
                byteArray[pos++] = (0xE0 or (ch.code shr 12)).toByte()
                byteArray[pos++] = (0x80 or ((ch.code shr 6) and 0x3F)).toByte()
                byteArray[pos++] = (0x80 or (ch.code and 0x3F)).toByte()
            }
        }
    }
    // Null-terminate
    byteArray[pos++] = 0
    return byteArray.copyOf(pos).toCValues()
}

public typealias JObject = CPointer<_jobject>
public typealias JClass = JObject
public typealias JThrowable = JObject
public typealias JString = JObject
public typealias JArray = JObject
public typealias JBooleanArray = JArray
public typealias JByteArray = JArray
public typealias JCharArray = JArray
public typealias JShortArray = JArray
public typealias JIntArray = JArray
public typealias JLongArray = JArray
public typealias JFloatArray = JArray
public typealias JDoubleArray = JArray
public typealias JObjectArray = JArray
public typealias JWeak = JObject

public typealias JValue = jvalue
public inline fun JValue(boolean: Boolean): JValue.() -> Unit = { z = boolean.toJBoolean() }
public inline fun JValue(byte: Byte): JValue.() -> Unit = { b = byte }
public inline fun JValue(char: Char): JValue.() -> Unit = { c = char.code.toUShort() }
public inline fun JValue(short: Short): JValue.() -> Unit = { s = short }
public inline fun JValue(int: Int): JValue.() -> Unit = { i = int }
public inline fun JValue(long: Long): JValue.() -> Unit = { j = long }
public inline fun JValue(float: Float): JValue.() -> Unit = { f = float }
public inline fun JValue(double: Double): JValue.() -> Unit = { d = double }
public inline fun JValue(obj: JObject?): JValue.() -> Unit = { l = obj }

public typealias JFieldID = CPointer<_jfieldID>

public typealias JMethodID = CPointer<_jmethodID>

public typealias JavaVM = CPointerVar<JNIInvokeInterface_>

public inline fun JavaVM.DestroyJavaVM() {
    checkJniResult(pointed!!.DestroyJavaVM!!(ptr))
}

public inline fun JavaVM.AttachCurrentThread(version: Int, name: String?, treadGroup: JObject?): JNIEnv {
    val env = JNIEnv(NativePtr.NULL)
    memScoped {
        val args = alloc<JavaVMAttachArgs> {
            this.version = version
            this.name = name?.modifiedUtf8?.ptr
            this.group = treadGroup
        }

        val res = pointed!!.AttachCurrentThread!!(ptr, env.ptr.reinterpret(), args.ptr)
        checkJniResult(res)
    }
    return env
}

public inline fun JavaVM.DetachCurrentThread() {
    checkJniResult(pointed!!.DetachCurrentThread!!(ptr))
}

public inline fun JavaVM.GetEnv(version: Int): JNIEnv {
    val env = JNIEnv(NativePtr.NULL)
    val res = pointed!!.GetEnv!!(ptr, env.ptr.reinterpret(), version)
    checkJniResult(res)
    return env
}

public inline fun JavaVM.AttachCurrentThreadAsDaemon(version: Int, name: String?, treadGroup: JObject?): JNIEnv {
    val env = JNIEnv(NativePtr.NULL)
    memScoped {
        val args = alloc<JavaVMAttachArgs> {
            this.version = version
            this.name = name?.modifiedUtf8?.ptr
            this.group = treadGroup
        }

        val res = pointed!!.AttachCurrentThreadAsDaemon!!(ptr, env.ptr.reinterpret(), args.ptr)
        checkJniResult(res)
    }
    return env
}

public typealias JNIEnv = CPointerVar<JNINativeInterface_>

public inline fun JNIEnv.GetVersion(): Int {
    return pointed!!.GetVersion!!(ptr)
}

public inline fun JNIEnv.DefineClass(name: String?, loader: JObject?, classBuf: CPointer<ByteVarOf<Byte>>, classBufLen: Int): JClass? {
    return memScoped {
        pointed!!.DefineClass!!(ptr, name?.modifiedUtf8?.ptr, loader, classBuf, classBufLen)
    }
}

public inline fun JNIEnv.FindClass(name: String): JClass? {
    return memScoped {
        pointed!!.FindClass!!(ptr, name.modifiedUtf8.ptr)
    }
}

public inline fun JNIEnv.FromReflectedMethod(method: JObject): JMethodID? {
    return pointed!!.FromReflectedMethod!!(ptr, method)
}

public inline fun JNIEnv.FromReflectedField(field: JObject): JFieldID? {
    return pointed!!.FromReflectedField!!(ptr, field)
}

public inline fun JNIEnv.ToReflectedMethod(cls: JClass, methodID: JMethodID, isStatic: Boolean): JObject? {
    return pointed!!.ToReflectedMethod!!(ptr, cls, methodID, isStatic.toJBoolean())
}

public inline fun JNIEnv.GetSuperclass(cls: JClass): JClass? {
    return pointed!!.GetSuperclass!!(ptr, cls)
}

public inline fun JNIEnv.IsAssignableFrom(from: JClass, to: JClass): Boolean {
    return pointed!!.IsAssignableFrom!!(ptr, from, to).toKBoolean()
}

public inline fun JNIEnv.ToReflectedField(cls: JClass, fieldID: JFieldID, isStatic: Boolean): JObject? {
    return pointed!!.ToReflectedField!!(ptr, cls, fieldID, isStatic.toJBoolean())
}

public inline fun JNIEnv.Throw(throwable: JThrowable) {
    checkJniResult(pointed!!.Throw!!(ptr, throwable))
}

public inline fun JNIEnv.ThrowNew(clazz: JClass, message: String) {
    memScoped {
        checkJniResult(pointed!!.ThrowNew!!(ptr, clazz, message.modifiedUtf8.ptr))
    }
}

public inline fun JNIEnv.ExceptionOccurred(): JThrowable? {
    return pointed!!.ExceptionOccurred!!(ptr)
}

public inline fun JNIEnv.ExceptionDescribe() {
    pointed!!.ExceptionDescribe!!(ptr)
}

public inline fun JNIEnv.ExceptionClear() {
    pointed!!.ExceptionClear!!(ptr)
}

public inline fun JNIEnv.FatalError(message: String) {
    memScoped {
        pointed!!.FatalError!!(ptr, message.modifiedUtf8.ptr)
    }
}

public inline fun JNIEnv.PushLocalFrame(capacity: Int) {
    checkJniResult(pointed!!.PushLocalFrame!!(ptr, capacity))
}

public inline fun JNIEnv.PopLocalFrame(result: JObject?): JObject? {
    return pointed!!.PopLocalFrame!!(ptr, result)
}

public inline fun JNIEnv.NewGlobalRef(obj: JObject): JObject? {
    return pointed!!.NewGlobalRef!!(ptr, obj)
}

public inline fun JNIEnv.DeleteGlobalRef(globalRef: JObject) {
    pointed!!.DeleteGlobalRef!!(ptr, globalRef)
}

public inline fun JNIEnv.DeleteLocalRef(localRef: JObject) {
    pointed!!.DeleteLocalRef!!(ptr, localRef)
}

public inline fun JNIEnv.IsSameObject(ref1: JObject, ref2: JObject): Boolean {
    return pointed!!.IsSameObject!!(ptr, ref1, ref2).toKBoolean()
}

public inline fun JNIEnv.NewLocalRef(ref: JObject): JObject? {
    return pointed!!.NewLocalRef!!(ptr, ref)
}

public inline fun JNIEnv.EnsureLocalCapacity(capacity: Int) {
    checkJniResult(pointed!!.EnsureLocalCapacity!!(ptr, capacity))
}

public inline fun JNIEnv.AllocObject(clazz: JClass): JObject? {
    return pointed!!.AllocObject!!(ptr, clazz)
}

@PublishedApi
internal inline fun MemScope.makeArgs(args: Array<out JValue.() -> Unit>): CArrayPointer<JValue> {
    val cargs = allocArray<JValue>(args.size)
    args.forEachIndexed { i, arg ->
        cargs[i].arg()
    }
    return cargs
}

public inline fun JNIEnv.NewObject(clazz: JClass, methodID: JMethodID, vararg args: JValue.() -> Unit): JObject? {
    return memScoped {
        pointed!!.NewObjectA!!(ptr, clazz, methodID, makeArgs(args))
    }
}

public inline fun JNIEnv.GetObjectClass(obj: JObject): JClass {
    return pointed!!.GetObjectClass!!(ptr, obj)!!
}

public inline fun JNIEnv.IsInstanceOf(obj: JObject?, clazz: JClass): Boolean {
    return pointed!!.IsInstanceOf!!(ptr, obj, clazz).toKBoolean()
}

public inline fun JNIEnv.GetMethodID(clazz: JClass, name: String, sig: String): JMethodID? {
    return memScoped {
        pointed!!.GetMethodID!!(ptr, clazz, name.modifiedUtf8.ptr, sig.modifiedUtf8.ptr)
    }
}

public inline fun JNIEnv.CallObjectMethod(obj: JObject, methodID: JMethodID, vararg args: JValue.() -> Unit): JObject? {
    return memScoped {
        pointed!!.CallObjectMethodA!!(ptr, obj, methodID, makeArgs(args))
    }
}

public inline fun JNIEnv.CallBooleanMethod(obj: JObject, methodID: JMethodID, vararg args: JValue.() -> Unit): Boolean {
    return memScoped {
        pointed!!.CallBooleanMethodA!!(ptr, obj, methodID, makeArgs(args)).toKBoolean()
    }
}

public inline fun JNIEnv.CallByteMethod(obj: JObject, methodID: JMethodID, vararg args: JValue.() -> Unit): Byte {
    return memScoped {
        pointed!!.CallByteMethodA!!(ptr, obj, methodID, makeArgs(args))
    }
}

public inline fun JNIEnv.CallCharMethod(obj: JObject, methodID: JMethodID, vararg args: JValue.() -> Unit): Char {
    return memScoped {
        Char(pointed!!.CallCharMethodA!!(ptr, obj, methodID, makeArgs(args)))
    }
}

public inline fun JNIEnv.CallShortMethod(obj: JObject, methodID: JMethodID, vararg args: JValue.() -> Unit): Short {
    return memScoped {
        pointed!!.CallShortMethodA!!(ptr, obj, methodID, makeArgs(args))
    }
}

public inline fun JNIEnv.CallIntMethod(obj: JObject, methodID: JMethodID, vararg args: JValue.() -> Unit): Int {
    return memScoped {
        pointed!!.CallIntMethodA!!(ptr, obj, methodID, makeArgs(args))
    }
}

public inline fun JNIEnv.CallLongMethod(obj: JObject, methodID: JMethodID, vararg args: JValue.() -> Unit): Long {
    return memScoped {
        pointed!!.CallLongMethodA!!(ptr, obj, methodID, makeArgs(args))
    }
}

public inline fun JNIEnv.CallFloatMethod(obj: JObject, methodID: JMethodID, vararg args: JValue.() -> Unit): Float {
    return memScoped {
        pointed!!.CallFloatMethodA!!(ptr, obj, methodID, makeArgs(args))
    }
}

public inline fun JNIEnv.CallDoubleMethod(obj: JObject, methodID: JMethodID, vararg args: JValue.() -> Unit): Double {
    return memScoped {
        pointed!!.CallDoubleMethodA!!(ptr, obj, methodID, makeArgs(args))
    }
}

public inline fun JNIEnv.CallVoidMethod(obj: JObject, methodID: JMethodID, vararg args: JValue.() -> Unit) {
    return memScoped {
        pointed!!.CallVoidMethodA!!(ptr, obj, methodID, makeArgs(args))
    }
}

public inline fun JNIEnv.CallNonvirtualObjectMethod(clazz: JClass, obj: JObject, methodID: JMethodID, vararg args: JValue.() -> Unit): JObject? {
    return memScoped {
        pointed!!.CallNonvirtualObjectMethodA!!(ptr, clazz, obj, methodID, makeArgs(args))
    }
}

public inline fun JNIEnv.CallNonvirtualBooleanMethod(clazz: JClass, obj: JObject, methodID: JMethodID, vararg args: JValue.() -> Unit): Boolean {
    return memScoped {
        pointed!!.CallNonvirtualBooleanMethodA!!(ptr, clazz, obj, methodID, makeArgs(args)).toKBoolean()
    }
}

public inline fun JNIEnv.CallNonvirtualByteMethod(clazz: JClass, obj: JObject, methodID: JMethodID, vararg args: JValue.() -> Unit): Byte {
    return memScoped {
        pointed!!.CallNonvirtualByteMethodA!!(ptr, clazz, obj, methodID, makeArgs(args))
    }
}

public inline fun JNIEnv.CallNonvirtualCharMethod(clazz: JClass, obj: JObject, methodID: JMethodID, vararg args: JValue.() -> Unit): Char {
    return memScoped {
        Char(pointed!!.CallNonvirtualCharMethodA!!(ptr, clazz, obj, methodID, makeArgs(args)))
    }
}

public inline fun JNIEnv.CallNonvirtualShortMethod(clazz: JClass, obj: JObject, methodID: JMethodID, vararg args: JValue.() -> Unit): Short {
    return memScoped {
        pointed!!.CallNonvirtualShortMethodA!!(ptr, clazz, obj, methodID, makeArgs(args))
    }
}

public inline fun JNIEnv.CallNonvirtualIntMethod(clazz: JClass, obj: JObject, methodID: JMethodID, vararg args: JValue.() -> Unit): Int {
    return memScoped {
        pointed!!.CallNonvirtualIntMethodA!!(ptr, clazz, obj, methodID, makeArgs(args))
    }
}

public inline fun JNIEnv.CallNonvirtualLongMethod(clazz: JClass, obj: JObject, methodID: JMethodID, vararg args: JValue.() -> Unit): Long {
    return memScoped {
        pointed!!.CallNonvirtualLongMethodA!!(ptr, clazz, obj, methodID, makeArgs(args))
    }
}

public inline fun JNIEnv.CallNonvirtualFloatMethod(clazz: JClass, obj: JObject, methodID: JMethodID, vararg args: JValue.() -> Unit): Float {
    return memScoped {
        pointed!!.CallNonvirtualFloatMethodA!!(ptr, clazz, obj, methodID, makeArgs(args))
    }
}

public inline fun JNIEnv.CallNonvirtualDoubleMethod(clazz: JClass, obj: JObject, methodID: JMethodID, vararg args: JValue.() -> Unit): Double {
    return memScoped {
        pointed!!.CallNonvirtualDoubleMethodA!!(ptr, clazz, obj, methodID, makeArgs(args))
    }
}

public inline fun JNIEnv.CallNonvirtualVoidMethod(clazz: JClass, obj: JObject, methodID: JMethodID, vararg args: JValue.() -> Unit) {
    return memScoped {
        pointed!!.CallNonvirtualVoidMethodA!!(ptr, clazz, obj, methodID, makeArgs(args))
    }
}

public inline fun JNIEnv.GetFieldID(clazz: JClass, name: String, sig: String): JFieldID? {
    return memScoped {
        pointed!!.GetFieldID!!(ptr, clazz, name.modifiedUtf8.ptr, sig.modifiedUtf8.ptr)
    }
}

public inline fun JNIEnv.GetObjectField(obj: JObject, fieldID: JFieldID): JObject? {
    return pointed!!.GetObjectField!!(ptr, obj, fieldID)
}

public inline fun JNIEnv.GetBooleanField(obj: JObject, fieldID: JFieldID): Boolean {
    return pointed!!.GetBooleanField!!(ptr, obj, fieldID).toKBoolean()
}

public inline fun JNIEnv.GetByteField(obj: JObject, fieldID: JFieldID): Byte {
    return pointed!!.GetByteField!!(ptr, obj, fieldID)
}

public inline fun JNIEnv.GetCharField(obj: JObject, fieldID: JFieldID): Char {
    return Char(pointed!!.GetCharField!!(ptr, obj, fieldID))
}

public inline fun JNIEnv.GetShortField(obj: JObject, fieldID: JFieldID): Short {
    return pointed!!.GetShortField!!(ptr, obj, fieldID)
}

public inline fun JNIEnv.GetIntField(obj: JObject, fieldID: JFieldID): Int {
    return pointed!!.GetIntField!!(ptr, obj, fieldID)
}

public inline fun JNIEnv.GetLongField(obj: JObject, fieldID: JFieldID): Long {
    return pointed!!.GetLongField!!(ptr, obj, fieldID)
}

public inline fun JNIEnv.GetFloatField(obj: JObject, fieldID: JFieldID): Float {
    return pointed!!.GetFloatField!!(ptr, obj, fieldID)
}

public inline fun JNIEnv.GetDoubleField(obj: JObject, fieldID: JFieldID): Double {
    return pointed!!.GetDoubleField!!(ptr, obj, fieldID)
}

public inline fun JNIEnv.SetObjectField(obj: JObject, fieldID: JFieldID, value: JObject?) {
    pointed!!.SetObjectField!!(ptr, obj, fieldID, value)
}

public inline fun JNIEnv.SetBooleanField(obj: JObject, fieldID: JFieldID, value: Boolean) {
    pointed!!.SetBooleanField!!(ptr, obj, fieldID, value.toJBoolean())
}

public inline fun JNIEnv.SetByteField(obj: JObject, fieldID: JFieldID, value: Byte) {
    pointed!!.SetByteField!!(ptr, obj, fieldID, value)
}

public inline fun JNIEnv.SetCharField(obj: JObject, fieldID: JFieldID, value: Char) {
    pointed!!.SetCharField!!(ptr, obj, fieldID, value.code.toUShort())
}

public inline fun JNIEnv.SetShortField(obj: JObject, fieldID: JFieldID, value: Short) {
    pointed!!.SetShortField!!(ptr, obj, fieldID, value)
}

public inline fun JNIEnv.SetIntField(obj: JObject, fieldID: JFieldID, value: Int) {
    pointed!!.SetIntField!!(ptr, obj, fieldID, value)
}

public inline fun JNIEnv.SetLongField(obj: JObject, fieldID: JFieldID, value: Long) {
    pointed!!.SetLongField!!(ptr, obj, fieldID, value)
}

public inline fun JNIEnv.SetFloatField(obj: JObject, fieldID: JFieldID, value: Float) {
    pointed!!.SetFloatField!!(ptr, obj, fieldID, value)
}

public inline fun JNIEnv.SetDoubleField(obj: JObject, fieldID: JFieldID, value: Double) {
    pointed!!.SetDoubleField!!(ptr, obj, fieldID, value)
}

public inline fun JNIEnv.GetStaticMethodID(clazz: JClass, name: String, sig: String): JMethodID? {
    return memScoped {
        pointed!!.GetStaticMethodID!!(ptr, clazz, name.modifiedUtf8.ptr, sig.modifiedUtf8.ptr)
    }
}

public inline fun JNIEnv.CallStaticObjectMethod(clazz: JClass, methodID: JMethodID, vararg args: JValue.() -> Unit): JObject? {
    return memScoped {
        pointed!!.CallStaticObjectMethodA!!(ptr, clazz, methodID, makeArgs(args))
    }
}

public inline fun JNIEnv.CallStaticBooleanMethod(clazz: JClass, methodID: JMethodID, vararg args: JValue.() -> Unit): Boolean {
    return memScoped {
        pointed!!.CallStaticBooleanMethodA!!(ptr, clazz, methodID, makeArgs(args)).toKBoolean()
    }
}

public inline fun JNIEnv.CallStaticByteMethod(clazz: JClass, methodID: JMethodID, vararg args: JValue.() -> Unit): Byte {
    return memScoped {
        pointed!!.CallStaticByteMethodA!!(ptr, clazz, methodID, makeArgs(args))
    }
}

public inline fun JNIEnv.CallStaticCharMethod(clazz: JClass, methodID: JMethodID, vararg args: JValue.() -> Unit): Char {
    return memScoped {
        Char(pointed!!.CallStaticCharMethodA!!(ptr, clazz, methodID, makeArgs(args)))
    }
}

public inline fun JNIEnv.CallStaticShortMethod(clazz: JClass, methodID: JMethodID, vararg args: JValue.() -> Unit): Short {
    return memScoped {
        pointed!!.CallStaticShortMethodA!!(ptr, clazz, methodID, makeArgs(args))
    }
}

public inline fun JNIEnv.CallStaticIntMethod(clazz: JClass, methodID: JMethodID, vararg args: JValue.() -> Unit): Int {
    return memScoped {
        pointed!!.CallStaticIntMethodA!!(ptr, clazz, methodID, makeArgs(args))
    }
}

public inline fun JNIEnv.CallStaticLongMethod(clazz: JClass, methodID: JMethodID, vararg args: JValue.() -> Unit): Long {
    return memScoped {
        pointed!!.CallStaticLongMethodA!!(ptr, clazz, methodID, makeArgs(args))
    }
}

public inline fun JNIEnv.CallStaticFloatMethod(clazz: JClass, methodID: JMethodID, vararg args: JValue.() -> Unit): Float {
    return memScoped {
        pointed!!.CallStaticFloatMethodA!!(ptr, clazz, methodID, makeArgs(args))
    }
}

public inline fun JNIEnv.CallStaticDoubleMethod(clazz: JClass, methodID: JMethodID, vararg args: JValue.() -> Unit): Double {
    return memScoped {
        pointed!!.CallStaticDoubleMethodA!!(ptr, clazz, methodID, makeArgs(args))
    }
}

public inline fun JNIEnv.CallStaticVoidMethod(clazz: JClass, methodID: JMethodID, vararg args: JValue.() -> Unit) {
    return memScoped {
        pointed!!.CallStaticVoidMethodA!!(ptr, clazz, methodID, makeArgs(args))
    }
}

public inline fun JNIEnv.GetStaticFieldID(clazz: JClass, name: String, sig: String): JFieldID? {
    return memScoped {
        pointed!!.GetStaticFieldID!!(ptr, clazz, name.modifiedUtf8.ptr, sig.modifiedUtf8.ptr)
    }
}

public inline fun JNIEnv.GetStaticObjectField(clazz: JClass, fieldID: JFieldID): JObject? {
    return pointed!!.GetStaticObjectField!!(ptr, clazz, fieldID)
}

public inline fun JNIEnv.GetStaticBooleanField(clazz: JClass, fieldID: JFieldID): Boolean {
    return pointed!!.GetStaticBooleanField!!(ptr, clazz, fieldID).toKBoolean()
}

public inline fun JNIEnv.GetStaticByteField(clazz: JClass, fieldID: JFieldID): Byte {
    return pointed!!.GetStaticByteField!!(ptr, clazz, fieldID)
}

public inline fun JNIEnv.GetStaticCharField(clazz: JClass, fieldID: JFieldID): Char {
    return Char(pointed!!.GetStaticCharField!!(ptr, clazz, fieldID))
}

public inline fun JNIEnv.GetStaticShortField(clazz: JClass, fieldID: JFieldID): Short {
    return pointed!!.GetStaticShortField!!(ptr, clazz, fieldID)
}

public inline fun JNIEnv.GetStaticIntField(clazz: JClass, fieldID: JFieldID): Int {
    return pointed!!.GetStaticIntField!!(ptr, clazz, fieldID)
}

public inline fun JNIEnv.GetStaticLongField(clazz: JClass, fieldID: JFieldID): Long {
    return pointed!!.GetStaticLongField!!(ptr, clazz, fieldID)
}

public inline fun JNIEnv.GetStaticFloatField(clazz: JClass, fieldID: JFieldID): Float {
    return pointed!!.GetStaticFloatField!!(ptr, clazz, fieldID)
}

public inline fun JNIEnv.GetStaticDoubleField(clazz: JClass, fieldID: JFieldID): Double {
    return pointed!!.GetStaticDoubleField!!(ptr, clazz, fieldID)
}

public inline fun JNIEnv.SetStaticObjectField(clazz: JClass, fieldID: JFieldID, value: JObject?) {
    pointed!!.SetStaticObjectField!!(ptr, clazz, fieldID, value)
}

public inline fun JNIEnv.SetStaticBooleanField(clazz: JClass, fieldID: JFieldID, value: Boolean) {
    pointed!!.SetStaticBooleanField!!(ptr, clazz, fieldID, value.toJBoolean())
}

public inline fun JNIEnv.SetStaticByteField(clazz: JClass, fieldID: JFieldID, value: Byte) {
    pointed!!.SetStaticByteField!!(ptr, clazz, fieldID, value)
}

public inline fun JNIEnv.SetStaticCharField(clazz: JClass, fieldID: JFieldID, value: Char) {
    pointed!!.SetStaticCharField!!(ptr, clazz, fieldID, value.code.toUShort())
}

public inline fun JNIEnv.SetStaticShortField(clazz: JClass, fieldID: JFieldID, value: Short) {
    pointed!!.SetStaticShortField!!(ptr, clazz, fieldID, value)
}

public inline fun JNIEnv.SetStaticIntField(clazz: JClass, fieldID: JFieldID, value: Int) {
    pointed!!.SetStaticIntField!!(ptr, clazz, fieldID, value)
}

public inline fun JNIEnv.SetStaticLongField(clazz: JClass, fieldID: JFieldID, value: Long) {
    pointed!!.SetStaticLongField!!(ptr, clazz, fieldID, value)
}

public inline fun JNIEnv.SetStaticFloatField(clazz: JClass, fieldID: JFieldID, value: Float) {
    pointed!!.SetStaticFloatField!!(ptr, clazz, fieldID, value)
}

public inline fun JNIEnv.SetStaticDoubleField(clazz: JClass, fieldID: JFieldID, value: Double) {
    pointed!!.SetStaticDoubleField!!(ptr, clazz, fieldID, value)
}

public inline fun JNIEnv.NewString(string: String): JString? {
    return memScoped {
        pointed!!.NewString!!(ptr, string.utf16.ptr, string.length)
    }
}

public inline fun JNIEnv.GetStringLength(string: JString): Int {
    return pointed!!.GetStringLength!!(ptr, string)
}

public inline fun JNIEnv.GetStringChars(string: JString): Pair<CPointer<UShortVar>, Boolean>? {
    return memScoped {
        val isCopy = alloc<UByteVar>()
        val chars = pointed!!.GetStringChars!!(ptr, string, isCopy.ptr)
        chars?.let { it to isCopy.value.toKBoolean() }
    }
}

public inline fun JNIEnv.ReleaseStringChars(string: JString, chars: CPointer<UShortVar>) {
    pointed!!.ReleaseStringChars!!(ptr, string, chars)
}

public inline fun JNIEnv.NewStringUTF(bytes: CPointer<ByteVar>): JString? {
    return pointed!!.NewStringUTF!!(ptr, bytes)
}

public inline fun JNIEnv.GetStringUTFLength(string: JString): Int {
    return pointed!!.GetStringUTFLength!!(ptr, string)
}

public inline fun JNIEnv.GetStringUTFChars(string: JString): Pair<CPointer<ByteVar>, Boolean>? {
    return memScoped {
        val isCopy = alloc<UByteVar>()
        val utf = pointed!!.GetStringUTFChars!!(ptr, string, isCopy.ptr)
        utf?.let { it to isCopy.value.toKBoolean() }
    }
}

public inline fun JNIEnv.ReleaseStringUTFChars(string: JString, utf: CPointer<ByteVar>) {
    pointed!!.ReleaseStringUTFChars!!(ptr, string, utf)
}

public inline fun JNIEnv.GetArrayLength(array: JArray): Int {
    return pointed!!.GetArrayLength!!(ptr, array)
}

public inline fun JNIEnv.NewObjectArray(length: Int, elementClass: JClass, initialElement: JObject?): JObjectArray? {
    return pointed!!.NewObjectArray!!(ptr, length, elementClass, initialElement)
}

public inline fun JNIEnv.GetObjectArrayElement(array: JObjectArray, index: Int): JObject? {
    return pointed!!.GetObjectArrayElement!!(ptr, array, index)
}

public inline fun JNIEnv.SetObjectArrayElement(array: JObjectArray, index: Int, value: JObject?) {
    pointed!!.SetObjectArrayElement!!(ptr, array, index, value)
}

public inline fun JNIEnv.NewBooleanArray(length: Int): JBooleanArray? {
    return pointed!!.NewBooleanArray!!(ptr, length)
}

public inline fun JNIEnv.NewByteArray(length: Int): JByteArray? {
    return pointed!!.NewByteArray!!(ptr, length)
}

public inline fun JNIEnv.NewCharArray(length: Int): JCharArray? {
    return pointed!!.NewCharArray!!(ptr, length)
}

public inline fun JNIEnv.NewShortArray(length: Int): JShortArray? {
    return pointed!!.NewShortArray!!(ptr, length)
}

public inline fun JNIEnv.NewIntArray(length: Int): JIntArray? {
    return pointed!!.NewIntArray!!(ptr, length)
}

public inline fun JNIEnv.NewLongArray(length: Int): JLongArray? {
    return pointed!!.NewLongArray!!(ptr, length)
}

public inline fun JNIEnv.NewFloatArray(length: Int): JFloatArray? {
    return pointed!!.NewFloatArray!!(ptr, length)
}

public inline fun JNIEnv.NewDoubleArray(length: Int): JDoubleArray? {
    return pointed!!.NewDoubleArray!!(ptr, length)
}

public inline fun JNIEnv.GetBooleanArrayElements(array: JBooleanArray): Pair<CPointer<UByteVar>, Boolean>? {
    return memScoped {
        val isCopy = alloc<UByteVar>()
        val elements = pointed!!.GetBooleanArrayElements!!(ptr, array, isCopy.ptr)
        elements?.let { it to isCopy.value.toKBoolean() }
    }
}

public inline fun JNIEnv.GetByteArrayElements(array: JByteArray): Pair<CPointer<ByteVar>, Boolean>? {
    return memScoped {
        val isCopy = alloc<UByteVar>()
        val elements = pointed!!.GetByteArrayElements!!(ptr, array, isCopy.ptr)
        elements?.let { it to isCopy.value.toKBoolean() }
    }
}

public inline fun JNIEnv.GetCharArrayElements(array: JCharArray): Pair<CPointer<UShortVar>, Boolean>? {
    return memScoped {
        val isCopy = alloc<UByteVar>()
        val elements = pointed!!.GetCharArrayElements!!(ptr, array, isCopy.ptr)
        elements?.let { it to isCopy.value.toKBoolean() }
    }
}

public inline fun JNIEnv.GetShortArrayElements(array: JShortArray): Pair<CPointer<ShortVar>, Boolean>? {
    return memScoped {
        val isCopy = alloc<UByteVar>()
        val elements = pointed!!.GetShortArrayElements!!(ptr, array, isCopy.ptr)
        elements?.let { it to isCopy.value.toKBoolean() }
    }
}

public inline fun JNIEnv.GetIntArrayElements(array: JIntArray): Pair<CPointer<IntVar>, Boolean>? {
    return memScoped {
        val isCopy = alloc<UByteVar>()
        val elements = pointed!!.GetIntArrayElements!!(ptr, array, isCopy.ptr)
        elements?.let { it to isCopy.value.toKBoolean() }
    }
}

public inline fun JNIEnv.GetLongArrayElements(array: JLongArray): Pair<CPointer<LongVar>, Boolean>? {
    return memScoped {
        val isCopy = alloc<UByteVar>()
        val elements = pointed!!.GetLongArrayElements!!(ptr, array, isCopy.ptr)
        elements?.let { it to isCopy.value.toKBoolean() }
    }
}

public inline fun JNIEnv.GetFloatArrayElements(array: JFloatArray): Pair<CPointer<FloatVar>, Boolean>? {
    return memScoped {
        val isCopy = alloc<UByteVar>()
        val elements = pointed!!.GetFloatArrayElements!!(ptr, array, isCopy.ptr)
        elements?.let { it to isCopy.value.toKBoolean() }
    }
}

public inline fun JNIEnv.GetDoubleArrayElements(array: JDoubleArray): Pair<CPointer<DoubleVar>, Boolean>? {
    return memScoped {
        val isCopy = alloc<UByteVar>()
        val elements = pointed!!.GetDoubleArrayElements!!(ptr, array, isCopy.ptr)
        elements?.let { it to isCopy.value.toKBoolean() }
    }
}

public inline fun JNIEnv.ReleaseBooleanArrayElements(array: JBooleanArray, elems: CPointer<UByteVar>, mode: JNI.ApplyChangesMode) {
    pointed!!.ReleaseBooleanArrayElements!!(ptr, array, elems, mode.nativeCode)
}

public inline fun JNIEnv.ReleaseByteArrayElements(array: JByteArray, elems: CPointer<ByteVar>, mode: JNI.ApplyChangesMode) {
    pointed!!.ReleaseByteArrayElements!!(ptr, array, elems, mode.nativeCode)
}

public inline fun JNIEnv.ReleaseCharArrayElements(array: JCharArray, elems: CPointer<UShortVar>, mode: JNI.ApplyChangesMode) {
    pointed!!.ReleaseCharArrayElements!!(ptr, array, elems, mode.nativeCode)
}

public inline fun JNIEnv.ReleaseShortArrayElements(array: JShortArray, elems: CPointer<ShortVar>, mode: JNI.ApplyChangesMode) {
    pointed!!.ReleaseShortArrayElements!!(ptr, array, elems, mode.nativeCode)
}

public inline fun JNIEnv.ReleaseIntArrayElements(array: JIntArray, elems: CPointer<IntVar>, mode: JNI.ApplyChangesMode) {
    pointed!!.ReleaseIntArrayElements!!(ptr, array, elems, mode.nativeCode)
}

public inline fun JNIEnv.ReleaseLongArrayElements(array: JLongArray, elems: CPointer<LongVar>, mode: JNI.ApplyChangesMode) {
    pointed!!.ReleaseLongArrayElements!!(ptr, array, elems, mode.nativeCode)
}

public inline fun JNIEnv.ReleaseFloatArrayElements(array: JFloatArray, elems: CPointer<FloatVar>, mode: JNI.ApplyChangesMode) {
    pointed!!.ReleaseFloatArrayElements!!(ptr, array, elems, mode.nativeCode)
}

public inline fun JNIEnv.ReleaseDoubleArrayElements(array: JDoubleArray, elems: CPointer<DoubleVar>, mode: JNI.ApplyChangesMode) {
    pointed!!.ReleaseDoubleArrayElements!!(ptr, array, elems, mode.nativeCode)
}

public inline fun JNIEnv.GetBooleanArrayRegion(array: JBooleanArray, start: Int, len: Int, buf: CPointer<UByteVar>) {
    pointed!!.GetBooleanArrayRegion!!(ptr, array, start, len, buf)
}

public inline fun JNIEnv.GetByteArrayRegion(array: JByteArray, start: Int, len: Int, buf: CPointer<ByteVar>) {
    pointed!!.GetByteArrayRegion!!(ptr, array, start, len, buf)
}

public inline fun JNIEnv.GetCharArrayRegion(array: JCharArray, start: Int, len: Int, buf: CPointer<UShortVar>) {
    pointed!!.GetCharArrayRegion!!(ptr, array, start, len, buf)
}

public inline fun JNIEnv.GetShortArrayRegion(array: JShortArray, start: Int, len: Int, buf: CPointer<ShortVar>) {
    pointed!!.GetShortArrayRegion!!(ptr, array, start, len, buf)
}

public inline fun JNIEnv.GetIntArrayRegion(array: JIntArray, start: Int, len: Int, buf: CPointer<IntVar>) {
    pointed!!.GetIntArrayRegion!!(ptr, array, start, len, buf)
}

public inline fun JNIEnv.GetLongArrayRegion(array: JLongArray, start: Int, len: Int, buf: CPointer<LongVar>) {
    pointed!!.GetLongArrayRegion!!(ptr, array, start, len, buf)
}

public inline fun JNIEnv.GetFloatArrayRegion(array: JFloatArray, start: Int, len: Int, buf: CPointer<FloatVar>) {
    pointed!!.GetFloatArrayRegion!!(ptr, array, start, len, buf)
}

public inline fun JNIEnv.GetDoubleArrayRegion(array: JDoubleArray, start: Int, len: Int, buf: CPointer<DoubleVar>) {
    pointed!!.GetDoubleArrayRegion!!(ptr, array, start, len, buf)
}

public inline fun JNIEnv.SetBooleanArrayRegion(array: JBooleanArray, start: Int, len: Int, buf: CPointer<UByteVar>) {
    pointed!!.SetBooleanArrayRegion!!(ptr, array, start, len, buf)
}

public inline fun JNIEnv.SetByteArrayRegion(array: JByteArray, start: Int, len: Int, buf: CPointer<ByteVar>) {
    pointed!!.SetByteArrayRegion!!(ptr, array, start, len, buf)
}

public inline fun JNIEnv.SetCharArrayRegion(array: JCharArray, start: Int, len: Int, buf: CPointer<UShortVar>) {
    pointed!!.SetCharArrayRegion!!(ptr, array, start, len, buf)
}

public inline fun JNIEnv.SetShortArrayRegion(array: JShortArray, start: Int, len: Int, buf: CPointer<ShortVar>) {
    pointed!!.SetShortArrayRegion!!(ptr, array, start, len, buf)
}

public inline fun JNIEnv.SetIntArrayRegion(array: JIntArray, start: Int, len: Int, buf: CPointer<IntVar>) {
    pointed!!.SetIntArrayRegion!!(ptr, array, start, len, buf)
}

public inline fun JNIEnv.SetLongArrayRegion(array: JLongArray, start: Int, len: Int, buf: CPointer<LongVar>) {
    pointed!!.SetLongArrayRegion!!(ptr, array, start, len, buf)
}

public inline fun JNIEnv.SetFloatArrayRegion(array: JFloatArray, start: Int, len: Int, buf: CPointer<FloatVar>) {
    pointed!!.SetFloatArrayRegion!!(ptr, array, start, len, buf)
}

public inline fun JNIEnv.SetDoubleArrayRegion(array: JDoubleArray, start: Int, len: Int, buf: CPointer<DoubleVar>) {
    pointed!!.SetDoubleArrayRegion!!(ptr, array, start, len, buf)
}

public inline fun JNIEnv.RegisterNatives(clazz: JClass, methods: List<Triple<String, String, CPointer<CFunction<*>>>>) {
    memScoped {
        val jniMethods = allocArray<JNINativeMethod>(methods.size)
        methods.forEachIndexed { i, (name, signature, fnPtr) ->
            val method = jniMethods[i]
            method.name = name.modifiedUtf8.ptr
            method.signature = signature.modifiedUtf8.ptr
            method.fnPtr = fnPtr
        }
        checkJniResult(pointed!!.RegisterNatives!!(ptr, clazz, jniMethods, methods.size))
    }
}

public inline fun JNIEnv.UnregisterNatives(clazz: JClass) {
    checkJniResult(pointed!!.UnregisterNatives!!(ptr, clazz))
}

public inline fun JNIEnv.MonitorEnter(obj: JObject) {
    checkJniResult(pointed!!.MonitorEnter!!(ptr, obj))
}

public inline fun JNIEnv.MonitorExit(obj: JObject) {
    checkJniResult(pointed!!.MonitorExit!!(ptr, obj))
}

public inline fun JNIEnv.GetJavaVM(): JavaVM {
    return memScoped {
        val vm = alloc<CPointerVar<JavaVM>>()
        checkJniResult(pointed!!.GetJavaVM!!(ptr, vm.ptr))
        vm.pointed!!
    }
}

public inline fun JNIEnv.GetStringRegion(str: JString, start: Int, len: Int, buf: CPointer<UShortVar>) {
    pointed!!.GetStringRegion!!(ptr, str, start, len, buf)
}

public inline fun JNIEnv.GetStringUTFRegion(str: JString, start: Int, len: Int, buf: CPointer<ByteVar>) {
    pointed!!.GetStringUTFRegion!!(ptr, str, start, len, buf)
}

public inline fun JNIEnv.GetPrimitiveArrayCritical(array: JArray): Pair<CArrayPointer<*>, Boolean>? {
    return memScoped {
        val isCopy = alloc<UByteVar>()
        val carray = pointed!!.GetPrimitiveArrayCritical!!(ptr, array, isCopy.ptr)
        carray?.let { it to isCopy.value.toKBoolean() }
    }
}

public inline fun JNIEnv.ReleasePrimitiveArrayCritical(array: JArray, carray: CArrayPointer<*>, mode: JNI.ApplyChangesMode) {
    pointed!!.ReleasePrimitiveArrayCritical!!(ptr, array, carray, mode.nativeCode)
}

public inline fun JNIEnv.GetStringCritical(string: JString): Pair<CPointer<UShortVar>, Boolean>? {
    return memScoped {
        val isCopy = alloc<UByteVar>()
        val carray = pointed!!.GetStringCritical!!(ptr, string, isCopy.ptr)
        carray?.let { it to isCopy.value.toKBoolean() }
    }
}

public inline fun JNIEnv.ReleaseStringCritical(string: JString, carray: CPointer<UShortVar>) {
    pointed!!.ReleaseStringCritical!!(ptr, string, carray)
}

public inline fun JNIEnv.NewWeakGlobalRef(obj: JObject): JWeak? {
    return pointed!!.NewWeakGlobalRef!!(ptr, obj)
}

public inline fun JNIEnv.DeleteWeakGlobalRef(obj: JWeak) {
    pointed!!.DeleteWeakGlobalRef!!(ptr, obj)
}

public inline fun JNIEnv.ExceptionCheck(): Boolean {
    return pointed!!.ExceptionCheck!!(ptr).toKBoolean()
}

public inline fun JNIEnv.NewDirectByteBuffer(address: COpaquePointer, capacity: Long): JObject? {
    return pointed!!.NewDirectByteBuffer!!(ptr, address, capacity)
}

public inline fun JNIEnv.GetDirectBufferAddress(buf: JObject): COpaquePointer? {
    return pointed!!.GetDirectBufferAddress!!(ptr, buf)
}

public inline fun JNIEnv.GetDirectBufferCapacity(buf: JObject): Long {
    return pointed!!.GetDirectBufferCapacity!!(ptr, buf)
}

public inline fun JNIEnv.GetObjectRefType(obj: JObject?): JNI.RefType {
    return when (pointed!!.GetObjectRefType!!(ptr, obj)) {
        JNIWeakGlobalRefType -> JNI.RefType.WeakGlobal
        JNIGlobalRefType -> JNI.RefType.Global
        JNILocalRefType -> JNI.RefType.Local
        else -> JNI.RefType.Invalid
    }
}

public inline fun JNIEnv.GetModule(clazz: JClass): JObject {
    return pointed!!.GetModule!!(ptr, clazz)!!
}


//public inline fun JNIEnv.GetKotlinString(string: JString): String? {
//    val (chars, _) = GetStringChars(string) ?: return null
//    val res = chars.toKString()
//    ReleaseStringChars(string, chars)
//    return res
//}