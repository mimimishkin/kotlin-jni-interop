package io.github.mimimishkin.jni.binding

import kotlinx.cinterop.*
import jni.*

internal actual inline val JniEnv.NewLongArray: CPointer<CFunction<(CPointer<JniEnv>?, Int) -> COpaquePointer?>>?
    get() = pointed!!.NewLongArray?.reinterpret()
internal actual inline val JniEnv.GetByteArrayElements: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<UByteVar>?) -> CPointer<ByteVar>?>>?
    get() = pointed!!.GetByteArrayElements?.reinterpret()
internal actual inline val JniEnv.ExceptionClear: CPointer<CFunction<(CPointer<JniEnv>?) -> Unit>>?
    get() = pointed!!.ExceptionClear
internal actual inline val JniEnv.SetStaticByteField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?, Byte) -> Unit>>?
    get() = pointed!!.SetStaticByteField?.reinterpret()
internal actual inline val JniEnv.CallStaticFloatMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> Float>>?
    get() = pointed!!.CallStaticFloatMethodA?.reinterpret()
internal actual inline val JniEnv.CallStaticLongMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> Long>>?
    get() = pointed!!.CallStaticLongMethodA?.reinterpret()
internal actual inline val JniEnv.GetStringUTFLength: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?) -> Int>>?
    get() = pointed!!.GetStringUTFLength?.reinterpret()
internal actual inline val JniEnv.Throw: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?) -> Int>>?
    get() = pointed!!.Throw?.reinterpret()
internal actual inline val JniEnv.NewByteArray: CPointer<CFunction<(CPointer<JniEnv>?, Int) -> COpaquePointer?>>?
    get() = pointed!!.NewByteArray?.reinterpret()
internal actual inline val JniEnv.CallLongMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> Long>>?
    get() = pointed!!.CallLongMethodA?.reinterpret()
internal actual inline val JniEnv.FatalError: CPointer<CFunction<(CPointer<JniEnv>?, CPointer<ByteVar>?) -> Unit>>?
    get() = pointed!!.FatalError
internal actual inline val JniEnv.NewCharArray: CPointer<CFunction<(CPointer<JniEnv>?, Int) -> COpaquePointer?>>?
    get() = pointed!!.NewCharArray?.reinterpret()
internal actual inline val JniEnv.EnsureLocalCapacity: CPointer<CFunction<(CPointer<JniEnv>?, Int) -> Int>>?
    get() = pointed!!.EnsureLocalCapacity
internal actual inline val JniEnv.CallNonvirtualObjectMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> COpaquePointer?>>?
    get() = pointed!!.CallNonvirtualObjectMethodA?.reinterpret()
internal actual inline val JniEnv.AllocObject: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?) -> COpaquePointer?>>?
    get() = pointed!!.AllocObject?.reinterpret()
internal actual inline val JniEnv.ExceptionOccurred: CPointer<CFunction<(CPointer<JniEnv>?) -> COpaquePointer?>>?
    get() = pointed!!.ExceptionOccurred?.reinterpret()
internal actual inline val JniEnv.GetObjectField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?) -> COpaquePointer?>>?
    get() = pointed!!.GetObjectField?.reinterpret()
internal actual inline val JniEnv.ReleaseIntArrayElements: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<IntVar>?, Int) -> Unit>>?
    get() = pointed!!.ReleaseIntArrayElements?.reinterpret()
internal actual inline val JniEnv.SetIntField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?, Int) -> Unit>>?
    get() = pointed!!.SetIntField?.reinterpret()
internal actual inline val JniEnv.GetStaticBooleanField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?) -> UByte>>?
    get() = pointed!!.GetStaticBooleanField?.reinterpret()
internal actual inline val JniEnv.GetStaticDoubleField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?) -> Double>>?
    get() = pointed!!.GetStaticDoubleField?.reinterpret()
internal actual inline val JniEnv.GetStaticIntField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?) -> Int>>?
    get() = pointed!!.GetStaticIntField?.reinterpret()
internal actual inline val JniEnv.GetJavaVM: CPointer<CFunction<(CPointer<JniEnv>?, CPointer<CPointerVarOf<CPointer<JavaVM>>>?) -> Int>>?
    get() = pointed!!.GetJavaVM
internal actual inline val JniEnv.GetFieldID: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<ByteVar>?, CPointer<ByteVar>?) -> CPointer<_jFieldID>?>>?
    get() = pointed!!.GetFieldID?.reinterpret()
internal actual inline val JniEnv.GetLongArrayRegion: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, Int, Int, CPointer<LongVar>?) -> Unit>>?
    get() = pointed!!.GetLongArrayRegion?.reinterpret()
internal actual inline val JniEnv.SetStaticLongField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?, Long) -> Unit>>?
    get() = pointed!!.SetStaticLongField?.reinterpret()
internal actual inline val JniEnv.ExceptionDescribe: CPointer<CFunction<(CPointer<JniEnv>?) -> Unit>>?
    get() = pointed!!.ExceptionDescribe
internal actual inline val JniEnv.CallShortMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> Short>>?
    get() = pointed!!.CallShortMethodA?.reinterpret()
internal actual inline val JniEnv.CallStaticVoidMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> Unit>>?
    get() = pointed!!.CallStaticVoidMethodA?.reinterpret()
internal actual inline val JniEnv.ReleaseStringUTFChars: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<ByteVar>?) -> Unit>>?
    get() = pointed!!.ReleaseStringUTFChars?.reinterpret()
internal actual inline val JniEnv.NewGlobalRef: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?) -> COpaquePointer?>>?
    get() = pointed!!.NewGlobalRef?.reinterpret()
internal actual inline val JniEnv.SetCharArrayRegion: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, Int, Int, CPointer<UShortVar>?) -> Unit>>?
    get() = pointed!!.SetCharArrayRegion?.reinterpret()
internal actual inline val JniEnv.ExceptionCheck: CPointer<CFunction<(CPointer<JniEnv>?) -> UByte>>?
    get() = pointed!!.ExceptionCheck
internal actual inline val JniEnv.CallCharMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> UShort>>?
    get() = pointed!!.CallCharMethodA?.reinterpret()
internal actual inline val JniEnv.GetByteField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?) -> Byte>>?
    get() = pointed!!.GetByteField?.reinterpret()
internal actual inline val JniEnv.FindClass: CPointer<CFunction<(CPointer<JniEnv>?, CPointer<ByteVar>?) -> COpaquePointer?>>?
    get() = pointed!!.FindClass?.reinterpret()
internal actual inline val JniEnv.SetByteField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?, Byte) -> Unit>>?
    get() = pointed!!.SetByteField?.reinterpret()
internal actual inline val JniEnv.SetStaticShortField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?, Short) -> Unit>>?
    get() = pointed!!.SetStaticShortField?.reinterpret()
internal actual inline val JniEnv.PopLocalFrame: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?) -> COpaquePointer?>>?
    get() = pointed!!.PopLocalFrame?.reinterpret()
internal actual inline val JniEnv.SetStaticDoubleField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?, Double) -> Unit>>?
    get() = pointed!!.SetStaticDoubleField?.reinterpret()
internal actual inline val JniEnv.GetBooleanField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?) -> UByte>>?
    get() = pointed!!.GetBooleanField?.reinterpret()
internal actual inline val JniEnv.GetStaticMethodID: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<ByteVar>?, CPointer<ByteVar>?) -> CPointer<_jMethodID>?>>?
    get() = pointed!!.GetStaticMethodID?.reinterpret()
internal actual inline val JniEnv.GetObjectArrayElement: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, Int) -> COpaquePointer?>>?
    get() = pointed!!.GetObjectArrayElement?.reinterpret()
internal actual inline val JniEnv.CallVoidMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> Unit>>?
    get() = pointed!!.CallVoidMethodA?.reinterpret()
internal actual inline val JniEnv.NewShortArray: CPointer<CFunction<(CPointer<JniEnv>?, Int) -> COpaquePointer?>>?
    get() = pointed!!.NewShortArray?.reinterpret()
internal actual inline val JniEnv.PushLocalFrame: CPointer<CFunction<(CPointer<JniEnv>?, Int) -> Int>>?
    get() = pointed!!.PushLocalFrame
internal actual inline val JniEnv.ReleaseByteArrayElements: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<ByteVar>?, Int) -> Unit>>?
    get() = pointed!!.ReleaseByteArrayElements?.reinterpret()
internal actual inline val JniEnv.CallNonvirtualFloatMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> Float>>?
    get() = pointed!!.CallNonvirtualFloatMethodA?.reinterpret()
internal actual inline val JniEnv.CallNonvirtualByteMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> Byte>>?
    get() = pointed!!.CallNonvirtualByteMethodA?.reinterpret()
internal actual inline val JniEnv.GetPrimitiveArrayCritical: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<UByteVar>?) -> COpaquePointer?>>?
    get() = pointed!!.GetPrimitiveArrayCritical?.reinterpret()
internal actual inline val JniEnv.DeleteWeakGlobalRef: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?) -> Unit>>?
    get() = pointed!!.DeleteWeakGlobalRef?.reinterpret()
internal actual inline val JniEnv.SetCharField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?, UShort) -> Unit>>?
    get() = pointed!!.SetCharField?.reinterpret()
internal actual inline val JniEnv.GetCharArrayElements: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<UByteVar>?) -> CPointer<UShortVar>?>>?
    get() = pointed!!.GetCharArrayElements?.reinterpret()
internal actual inline val JniEnv.NewString: CPointer<CFunction<(CPointer<JniEnv>?, CPointer<UShortVar>?, Int) -> COpaquePointer?>>?
    get() = pointed!!.NewString?.reinterpret()
internal actual inline val JniEnv.GetStringLength: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?) -> Int>>?
    get() = pointed!!.GetStringLength?.reinterpret()
internal actual inline val JniEnv.CallNonvirtualShortMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> Short>>?
    get() = pointed!!.CallNonvirtualShortMethodA?.reinterpret()
internal actual inline val JniEnv.GetByteArrayRegion: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, Int, Int, CPointer<ByteVar>?) -> Unit>>?
    get() = pointed!!.GetByteArrayRegion?.reinterpret()
internal actual inline val JniEnv.GetBooleanArrayElements: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<UByteVar>?) -> CPointer<UByteVar>?>>?
    get() = pointed!!.GetBooleanArrayElements?.reinterpret()
internal actual inline val JniEnv.SetObjectField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?, COpaquePointer?) -> Unit>>?
    get() = pointed!!.SetObjectField?.reinterpret()
internal actual inline val JniEnv.GetSuperclass: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?) -> COpaquePointer?>>?
    get() = pointed!!.GetSuperclass?.reinterpret()
internal actual inline val JniEnv.GetStringUTFRegion: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, Int, Int, CPointer<ByteVar>?) -> Unit>>?
    get() = pointed!!.GetStringUTFRegion?.reinterpret()
internal actual inline val JniEnv.SetLongArrayRegion: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, Int, Int, CPointer<LongVar>?) -> Unit>>?
    get() = pointed!!.SetLongArrayRegion?.reinterpret()
internal actual inline val JniEnv.RegisterNatives: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<JniNativeMethod>?, Int) -> Int>>?
    get() = pointed!!.RegisterNatives?.reinterpret()
internal actual inline val JniEnv.SetIntArrayRegion: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, Int, Int, CPointer<IntVar>?) -> Unit>>?
    get() = pointed!!.SetIntArrayRegion?.reinterpret()
internal actual inline val JniEnv.SetFloatField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?, Float) -> Unit>>?
    get() = pointed!!.SetFloatField?.reinterpret()
internal actual inline val JniEnv.NewBooleanArray: CPointer<CFunction<(CPointer<JniEnv>?, Int) -> COpaquePointer?>>?
    get() = pointed!!.NewBooleanArray?.reinterpret()
internal actual inline val JniEnv.GetDoubleArrayElements: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<UByteVar>?) -> CPointer<DoubleVar>?>>?
    get() = pointed!!.GetDoubleArrayElements?.reinterpret()
internal actual inline val JniEnv.CallFloatMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> Float>>?
    get() = pointed!!.CallFloatMethodA?.reinterpret()
internal actual inline val JniEnv.SetByteArrayRegion: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, Int, Int, CPointer<ByteVar>?) -> Unit>>?
    get() = pointed!!.SetByteArrayRegion?.reinterpret()
internal actual inline val JniEnv.GetStaticShortField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?) -> Short>>?
    get() = pointed!!.GetStaticShortField?.reinterpret()
internal actual inline val JniEnv.CallStaticShortMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> Short>>?
    get() = pointed!!.CallStaticShortMethodA?.reinterpret()
internal actual inline val JniEnv.ReleaseBooleanArrayElements: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<UByteVar>?, Int) -> Unit>>?
    get() = pointed!!.ReleaseBooleanArrayElements?.reinterpret()
internal actual inline val JniEnv.GetStaticObjectField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?) -> COpaquePointer?>>?
    get() = pointed!!.GetStaticObjectField?.reinterpret()
internal actual inline val JniEnv.MonitorEnter: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?) -> Int>>?
    get() = pointed!!.MonitorEnter?.reinterpret()
internal actual inline val JniEnv.SetLongField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?, Long) -> Unit>>?
    get() = pointed!!.SetLongField?.reinterpret()
internal actual inline val JniEnv.ReleaseStringCritical: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<UShortVar>?) -> Unit>>?
    get() = pointed!!.ReleaseStringCritical?.reinterpret()
internal actual inline val JniEnv.GetFloatField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?) -> Float>>?
    get() = pointed!!.GetFloatField?.reinterpret()
internal actual inline val JniEnv.ReleasePrimitiveArrayCritical: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, COpaquePointer?, Int) -> Unit>>?
    get() = pointed!!.ReleasePrimitiveArrayCritical?.reinterpret()
internal actual inline val JniEnv.GetIntField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?) -> Int>>?
    get() = pointed!!.GetIntField?.reinterpret()
internal actual inline val JniEnv.GetObjectRefType: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?) -> UInt>>?
    get() = pointed!!.GetObjectRefType?.reinterpret()
internal actual inline val JniEnv.GetFloatArrayRegion: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, Int, Int, CPointer<FloatVar>?) -> Unit>>?
    get() = pointed!!.GetFloatArrayRegion?.reinterpret()
internal actual inline val JniEnv.GetDoubleArrayRegion: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, Int, Int, CPointer<DoubleVar>?) -> Unit>>?
    get() = pointed!!.GetDoubleArrayRegion?.reinterpret()
internal actual inline val JniEnv.SetDoubleField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?, Double) -> Unit>>?
    get() = pointed!!.SetDoubleField?.reinterpret()
internal actual inline val JniEnv.CallBooleanMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> UByte>>?
    get() = pointed!!.CallBooleanMethodA?.reinterpret()
internal actual inline val JniEnv.GetShortArrayElements: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<UByteVar>?) -> CPointer<ShortVar>?>>?
    get() = pointed!!.GetShortArrayElements?.reinterpret()
internal actual inline val JniEnv.CallNonvirtualDoubleMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> Double>>?
    get() = pointed!!.CallNonvirtualDoubleMethodA?.reinterpret()
internal actual inline val JniEnv.FromReflectedMethod: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?) -> CPointer<_jMethodID>?>>?
    get() = pointed!!.FromReflectedMethod?.reinterpret()
internal actual inline val JniEnv.DeleteLocalRef: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?) -> Unit>>?
    get() = pointed!!.DeleteLocalRef?.reinterpret()
internal actual inline val JniEnv.CallNonvirtualVoidMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> Unit>>?
    get() = pointed!!.CallNonvirtualVoidMethodA?.reinterpret()
internal actual inline val JniEnv.ReleaseShortArrayElements: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<ShortVar>?, Int) -> Unit>>?
    get() = pointed!!.ReleaseShortArrayElements?.reinterpret()
internal actual inline val JniEnv.ToReflectedMethod: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jMethodID>?, UByte) -> COpaquePointer?>>?
    get() = pointed!!.ToReflectedMethod?.reinterpret()
internal actual inline val JniEnv.GetDirectBufferCapacity: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?) -> Long>>?
    get() = pointed!!.GetDirectBufferCapacity?.reinterpret()
internal actual inline val JniEnv.CallNonvirtualCharMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> UShort>>?
    get() = pointed!!.CallNonvirtualCharMethodA?.reinterpret()
internal actual inline val JniEnv.GetStaticCharField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?) -> UShort>>?
    get() = pointed!!.GetStaticCharField?.reinterpret()
internal actual inline val JniEnv.NewDirectByteBuffer: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, Long) -> COpaquePointer?>>?
    get() = pointed!!.NewDirectByteBuffer?.reinterpret()
internal actual inline val JniEnv.UnregisterNatives: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?) -> Int>>?
    get() = pointed!!.UnregisterNatives?.reinterpret()
internal actual inline val JniEnv.GetDirectBufferAddress: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?) -> COpaquePointer?>>?
    get() = pointed!!.GetDirectBufferAddress?.reinterpret()
internal actual inline val JniEnv.GetShortField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?) -> Short>>?
    get() = pointed!!.GetShortField?.reinterpret()
internal actual inline val JniEnv.NewFloatArray: CPointer<CFunction<(CPointer<JniEnv>?, Int) -> COpaquePointer?>>?
    get() = pointed!!.NewFloatArray?.reinterpret()
internal actual inline val JniEnv.FromReflectedField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?) -> CPointer<_jFieldID>?>>?
    get() = pointed!!.FromReflectedField?.reinterpret()
internal actual inline val JniEnv.GetStaticByteField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?) -> Byte>>?
    get() = pointed!!.GetStaticByteField?.reinterpret()
internal actual inline val JniEnv.GetStaticLongField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?) -> Long>>?
    get() = pointed!!.GetStaticLongField?.reinterpret()
internal actual inline val JniEnv.SetFloatArrayRegion: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, Int, Int, CPointer<FloatVar>?) -> Unit>>?
    get() = pointed!!.SetFloatArrayRegion?.reinterpret()
internal actual inline val JniEnv.CallNonvirtualIntMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> Int>>?
    get() = pointed!!.CallNonvirtualIntMethodA?.reinterpret()
internal actual inline val JniEnv.GetStringUTFChars: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<UByteVar>?) -> CPointer<ByteVar>?>>?
    get() = pointed!!.GetStringUTFChars?.reinterpret()
internal actual inline val JniEnv.ReleaseFloatArrayElements: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<FloatVar>?, Int) -> Unit>>?
    get() = pointed!!.ReleaseFloatArrayElements?.reinterpret()
internal actual inline val JniEnv.IsAssignableFrom: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, COpaquePointer?) -> UByte>>?
    get() = pointed!!.IsAssignableFrom?.reinterpret()
internal actual inline val JniEnv.SetShortField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?, Short) -> Unit>>?
    get() = pointed!!.SetShortField?.reinterpret()
internal actual inline val JniEnv.CallByteMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> Byte>>?
    get() = pointed!!.CallByteMethodA?.reinterpret()
internal actual inline val JniEnv.ReleaseCharArrayElements: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<UShortVar>?, Int) -> Unit>>?
    get() = pointed!!.ReleaseCharArrayElements?.reinterpret()
internal actual inline val JniEnv.NewObjectArray: CPointer<CFunction<(CPointer<JniEnv>?, Int, COpaquePointer?, COpaquePointer?) -> COpaquePointer?>>?
    get() = pointed!!.NewObjectArray?.reinterpret()
internal actual inline val JniEnv.CallStaticIntMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> Int>>?
    get() = pointed!!.CallStaticIntMethodA?.reinterpret()
internal actual inline val JniEnv.GetObjectClass: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?) -> COpaquePointer?>>?
    get() = pointed!!.GetObjectClass?.reinterpret()
internal actual inline val JniEnv.GetStringCritical: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<UByteVar>?) -> CPointer<UShortVar>?>>?
    get() = pointed!!.GetStringCritical?.reinterpret()
internal actual inline val JniEnv.CallStaticByteMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> Byte>>?
    get() = pointed!!.CallStaticByteMethodA?.reinterpret()
internal actual inline val JniEnv.ReleaseDoubleArrayElements: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<DoubleVar>?, Int) -> Unit>>?
    get() = pointed!!.ReleaseDoubleArrayElements?.reinterpret()
internal actual inline val JniEnv.GetStaticFieldID: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<ByteVar>?, CPointer<ByteVar>?) -> CPointer<_jFieldID>?>>?
    get() = pointed!!.GetStaticFieldID?.reinterpret()
internal actual inline val JniEnv.SetBooleanArrayRegion: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, Int, Int, CPointer<UByteVar>?) -> Unit>>?
    get() = pointed!!.SetBooleanArrayRegion?.reinterpret()
internal actual inline val JniEnv.NewStringUTF: CPointer<CFunction<(CPointer<JniEnv>?, CPointer<ByteVar>?) -> COpaquePointer?>>?
    get() = pointed!!.NewStringUTF?.reinterpret()
internal actual inline val JniEnv.SetObjectArrayElement: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, Int, COpaquePointer?) -> Unit>>?
    get() = pointed!!.SetObjectArrayElement?.reinterpret()
internal actual inline val JniEnv.GetCharArrayRegion: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, Int, Int, CPointer<UShortVar>?) -> Unit>>?
    get() = pointed!!.GetCharArrayRegion?.reinterpret()
internal actual inline val JniEnv.GetFloatArrayElements: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<UByteVar>?) -> CPointer<FloatVar>?>>?
    get() = pointed!!.GetFloatArrayElements?.reinterpret()
internal actual inline val JniEnv.CallObjectMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> COpaquePointer?>>?
    get() = pointed!!.CallObjectMethodA?.reinterpret()
internal actual inline val JniEnv.NewWeakGlobalRef: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?) -> COpaquePointer?>>?
    get() = pointed!!.NewWeakGlobalRef?.reinterpret()
internal actual inline val JniEnv.NewDoubleArray: CPointer<CFunction<(CPointer<JniEnv>?, Int) -> COpaquePointer?>>?
    get() = pointed!!.NewDoubleArray?.reinterpret()
internal actual inline val JniEnv.SetStaticIntField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?, Int) -> Unit>>?
    get() = pointed!!.SetStaticIntField?.reinterpret()
internal actual inline val JniEnv.ToReflectedField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?, UByte) -> COpaquePointer?>>?
    get() = pointed!!.ToReflectedField?.reinterpret()
internal actual inline val JniEnv.GetStringRegion: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, Int, Int, CPointer<UShortVar>?) -> Unit>>?
    get() = pointed!!.GetStringRegion?.reinterpret()
internal actual inline val JniEnv.GetShortArrayRegion: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, Int, Int, CPointer<ShortVar>?) -> Unit>>?
    get() = pointed!!.GetShortArrayRegion?.reinterpret()
internal actual inline val JniEnv.MonitorExit: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?) -> Int>>?
    get() = pointed!!.MonitorExit?.reinterpret()
internal actual inline val JniEnv.CallNonvirtualLongMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> Long>>?
    get() = pointed!!.CallNonvirtualLongMethodA?.reinterpret()
internal actual inline val JniEnv.GetMethodID: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<ByteVar>?, CPointer<ByteVar>?) -> CPointer<_jMethodID>?>>?
    get() = pointed!!.GetMethodID?.reinterpret()
internal actual inline val JniEnv.CallIntMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> Int>>?
    get() = pointed!!.CallIntMethodA?.reinterpret()
internal actual inline val JniEnv.GetStringChars: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<UByteVar>?) -> CPointer<UShortVar>?>>?
    get() = pointed!!.GetStringChars?.reinterpret()
internal actual inline val JniEnv.GetIntArrayRegion: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, Int, Int, CPointer<IntVar>?) -> Unit>>?
    get() = pointed!!.GetIntArrayRegion?.reinterpret()
internal actual inline val JniEnv.CallDoubleMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> Double>>?
    get() = pointed!!.CallDoubleMethodA?.reinterpret()
internal actual inline val JniEnv.CallStaticObjectMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> COpaquePointer?>>?
    get() = pointed!!.CallStaticObjectMethodA?.reinterpret()
internal actual inline val JniEnv.ReleaseStringChars: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<UShortVar>?) -> Unit>>?
    get() = pointed!!.ReleaseStringChars?.reinterpret()
internal actual inline val JniEnv.GetStaticFloatField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?) -> Float>>?
    get() = pointed!!.GetStaticFloatField?.reinterpret()
internal actual inline val JniEnv.GetIntArrayElements: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<UByteVar>?) -> CPointer<IntVar>?>>?
    get() = pointed!!.GetIntArrayElements?.reinterpret()
internal actual inline val JniEnv.GetBooleanArrayRegion: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, Int, Int, CPointer<UByteVar>?) -> Unit>>?
    get() = pointed!!.GetBooleanArrayRegion?.reinterpret()
internal actual inline val JniEnv.GetVersion: CPointer<CFunction<(CPointer<JniEnv>?) -> Int>>?
    get() = pointed!!.GetVersion
internal actual inline val JniEnv.NewObjectA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> COpaquePointer?>>?
    get() = pointed!!.NewObjectA?.reinterpret()
internal actual inline val JniEnv.SetStaticCharField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?, UShort) -> Unit>>?
    get() = pointed!!.SetStaticCharField?.reinterpret()
internal actual inline val JniEnv.IsSameObject: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, COpaquePointer?) -> UByte>>?
    get() = pointed!!.IsSameObject?.reinterpret()
internal actual inline val JniEnv.SetStaticBooleanField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?, UByte) -> Unit>>?
    get() = pointed!!.SetStaticBooleanField?.reinterpret()
internal actual inline val JniEnv.SetStaticObjectField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?, COpaquePointer?) -> Unit>>?
    get() = pointed!!.SetStaticObjectField?.reinterpret()
internal actual inline val JniEnv.CallStaticBooleanMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> UByte>>?
    get() = pointed!!.CallStaticBooleanMethodA?.reinterpret()
internal actual inline val JniEnv.SetStaticFloatField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?, Float) -> Unit>>?
    get() = pointed!!.SetStaticFloatField?.reinterpret()
internal actual inline val JniEnv.CallStaticCharMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> UShort>>?
    get() = pointed!!.CallStaticCharMethodA?.reinterpret()
internal actual inline val JniEnv.DeleteGlobalRef: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?) -> Unit>>?
    get() = pointed!!.DeleteGlobalRef?.reinterpret()
internal actual inline val JniEnv.IsInstanceOf: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, COpaquePointer?) -> UByte>>?
    get() = pointed!!.IsInstanceOf?.reinterpret()
internal actual inline val JniEnv.NewLocalRef: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?) -> COpaquePointer?>>?
    get() = pointed!!.NewLocalRef?.reinterpret()
internal actual inline val JniEnv.CallStaticDoubleMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> Double>>?
    get() = pointed!!.CallStaticDoubleMethodA?.reinterpret()
internal actual inline val JniEnv.GetArrayLength: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?) -> Int>>?
    get() = pointed!!.GetArrayLength?.reinterpret()
internal actual inline val JniEnv.GetLongArrayElements: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<UByteVar>?) -> CPointer<LongVar>?>>?
    get() = pointed!!.GetLongArrayElements?.reinterpret()
internal actual inline val JniEnv.ThrowNew: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<ByteVar>?) -> Int>>?
    get() = pointed!!.ThrowNew?.reinterpret()
internal actual inline val JniEnv.SetShortArrayRegion: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, Int, Int, CPointer<ShortVar>?) -> Unit>>?
    get() = pointed!!.SetShortArrayRegion?.reinterpret()
internal actual inline val JniEnv.NewIntArray: CPointer<CFunction<(CPointer<JniEnv>?, Int) -> COpaquePointer?>>?
    get() = pointed!!.NewIntArray?.reinterpret()
internal actual inline val JniEnv.GetCharField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?) -> UShort>>?
    get() = pointed!!.GetCharField?.reinterpret()
internal actual inline val JniEnv.GetLongField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?) -> Long>>?
    get() = pointed!!.GetLongField?.reinterpret()
internal actual inline val JniEnv.SetBooleanField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?, UByte) -> Unit>>?
    get() = pointed!!.SetBooleanField?.reinterpret()
internal actual inline val JniEnv.SetDoubleArrayRegion: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, Int, Int, CPointer<DoubleVar>?) -> Unit>>?
    get() = pointed!!.SetDoubleArrayRegion?.reinterpret()
internal actual inline val JniEnv.GetDoubleField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?) -> Double>>?
    get() = pointed!!.GetDoubleField?.reinterpret()
internal actual inline val JniEnv.ReleaseLongArrayElements: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<LongVar>?, Int) -> Unit>>?
    get() = pointed!!.ReleaseLongArrayElements?.reinterpret()
internal actual inline val JniEnv.CallNonvirtualBooleanMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> UByte>>?
    get() = pointed!!.CallNonvirtualBooleanMethodA?.reinterpret()

internal inline val JniEnv.DefineClass: CPointer<CFunction<(CPointer<JniEnv>?, CPointer<ByteVar>?, COpaquePointer?, CPointer<ByteVar>?, Int) -> COpaquePointer?>>? get() = pointed!!.DefineClass?.reinterpret()
internal inline val JniEnv.GetStringUTFLengthAsLong: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?) -> Long>>? get() = pointed!!.GetStringUTFLengthAsLong?.reinterpret()
internal inline val JniEnv.GetModule: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?) -> COpaquePointer?>>? get() = pointed!!.GetModule?.reinterpret()
internal inline val JniEnv.IsVirtualThread: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?) -> UByte>>? get() = pointed!!.IsVirtualThread?.reinterpret()

internal actual inline val JavaVM.DestroyJavaVM: CPointer<CFunction<(CPointer<JavaVM>?) -> Int>>?
    get() = pointed!!.DestroyJavaVM
internal actual inline val JavaVM.AttachCurrentThread: CPointer<CFunction<(CPointer<JavaVM>?, CPointer<CPointerVar<JniEnv>>?, COpaquePointer?) -> Int>>?
    get() = pointed!!.AttachCurrentThread?.reinterpret()
internal actual inline val JavaVM.GetEnv: CPointer<CFunction<(CPointer<JavaVM>?, CPointer<CPointerVar<JniEnv>>?, Int) -> Int>>?
    get() = pointed!!.GetEnv
internal actual inline val JavaVM.AttachCurrentThreadAsDaemon: CPointer<CFunction<(CPointer<JavaVM>?, CPointer<CPointerVar<JniEnv>>?, COpaquePointer?) -> Int>>?
    get() = pointed!!.AttachCurrentThreadAsDaemon?.reinterpret()
internal actual inline val JavaVM.DetachCurrentThread: CPointer<CFunction<(CPointer<JavaVM>?) -> Int>>?
    get() = pointed!!.DetachCurrentThread
