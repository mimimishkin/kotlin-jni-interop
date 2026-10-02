package io.github.mimimishkin.jni.binding

import kotlinx.cinterop.*

internal expect inline val JniEnv.NewLongArray: CPointer<CFunction<(CPointer<JniEnv>?, Int) -> COpaquePointer?>>?
internal expect inline val JniEnv.GetByteArrayElements: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<UByteVar>?) -> CPointer<ByteVar>?>>?
internal expect inline val JniEnv.ExceptionClear: CPointer<CFunction<(CPointer<JniEnv>?) -> Unit>>?
internal expect inline val JniEnv.SetStaticByteField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?, Byte) -> Unit>>?
internal expect inline val JniEnv.CallStaticFloatMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> Float>>?
internal expect inline val JniEnv.CallStaticLongMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> Long>>?
internal expect inline val JniEnv.GetStringUTFLength: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?) -> Int>>?
internal expect inline val JniEnv.Throw: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?) -> Int>>?
internal expect inline val JniEnv.NewByteArray: CPointer<CFunction<(CPointer<JniEnv>?, Int) -> COpaquePointer?>>?
internal expect inline val JniEnv.CallLongMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> Long>>?
internal expect inline val JniEnv.FatalError: CPointer<CFunction<(CPointer<JniEnv>?, CPointer<ByteVar>?) -> Unit>>?
internal expect inline val JniEnv.NewCharArray: CPointer<CFunction<(CPointer<JniEnv>?, Int) -> COpaquePointer?>>?
internal expect inline val JniEnv.EnsureLocalCapacity: CPointer<CFunction<(CPointer<JniEnv>?, Int) -> Int>>?
internal expect inline val JniEnv.CallNonvirtualObjectMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> COpaquePointer?>>?
internal expect inline val JniEnv.AllocObject: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?) -> COpaquePointer?>>?
internal expect inline val JniEnv.ExceptionOccurred: CPointer<CFunction<(CPointer<JniEnv>?) -> COpaquePointer?>>?
internal expect inline val JniEnv.GetObjectField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?) -> COpaquePointer?>>?
internal expect inline val JniEnv.ReleaseIntArrayElements: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<IntVar>?, Int) -> Unit>>?
internal expect inline val JniEnv.SetIntField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?, Int) -> Unit>>?
internal expect inline val JniEnv.GetStaticBooleanField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?) -> UByte>>?
internal expect inline val JniEnv.GetStaticDoubleField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?) -> Double>>?
internal expect inline val JniEnv.GetStaticIntField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?) -> Int>>?
internal expect inline val JniEnv.GetJavaVM: CPointer<CFunction<(CPointer<JniEnv>?, CPointer<CPointerVarOf<CPointer<JavaVM>>>?) -> Int>>?
internal expect inline val JniEnv.GetFieldID: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<ByteVar>?, CPointer<ByteVar>?) -> CPointer<_jFieldID>?>>?
internal expect inline val JniEnv.GetLongArrayRegion: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, Int, Int, CPointer<LongVar>?) -> Unit>>?
internal expect inline val JniEnv.SetStaticLongField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?, Long) -> Unit>>?
internal expect inline val JniEnv.ExceptionDescribe: CPointer<CFunction<(CPointer<JniEnv>?) -> Unit>>?
internal expect inline val JniEnv.CallShortMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> Short>>?
internal expect inline val JniEnv.CallStaticVoidMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> Unit>>?
internal expect inline val JniEnv.ReleaseStringUTFChars: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<ByteVar>?) -> Unit>>?
internal expect inline val JniEnv.NewGlobalRef: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?) -> COpaquePointer?>>?
internal expect inline val JniEnv.SetCharArrayRegion: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, Int, Int, CPointer<UShortVar>?) -> Unit>>?
internal expect inline val JniEnv.ExceptionCheck: CPointer<CFunction<(CPointer<JniEnv>?) -> UByte>>?
internal expect inline val JniEnv.CallCharMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> UShort>>?
internal expect inline val JniEnv.GetByteField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?) -> Byte>>?
internal expect inline val JniEnv.FindClass: CPointer<CFunction<(CPointer<JniEnv>?, CPointer<ByteVar>?) -> COpaquePointer?>>?
internal expect inline val JniEnv.SetByteField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?, Byte) -> Unit>>?
internal expect inline val JniEnv.SetStaticShortField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?, Short) -> Unit>>?
internal expect inline val JniEnv.PopLocalFrame: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?) -> COpaquePointer?>>?
internal expect inline val JniEnv.SetStaticDoubleField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?, Double) -> Unit>>?
internal expect inline val JniEnv.GetBooleanField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?) -> UByte>>?
internal expect inline val JniEnv.GetStaticMethodID: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<ByteVar>?, CPointer<ByteVar>?) -> CPointer<_jMethodID>?>>?
internal expect inline val JniEnv.GetObjectArrayElement: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, Int) -> COpaquePointer?>>?
internal expect inline val JniEnv.CallVoidMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> Unit>>?
internal expect inline val JniEnv.NewShortArray: CPointer<CFunction<(CPointer<JniEnv>?, Int) -> COpaquePointer?>>?
internal expect inline val JniEnv.PushLocalFrame: CPointer<CFunction<(CPointer<JniEnv>?, Int) -> Int>>?
internal expect inline val JniEnv.ReleaseByteArrayElements: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<ByteVar>?, Int) -> Unit>>?
internal expect inline val JniEnv.CallNonvirtualFloatMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> Float>>?
internal expect inline val JniEnv.CallNonvirtualByteMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> Byte>>?
internal expect inline val JniEnv.GetPrimitiveArrayCritical: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<UByteVar>?) -> COpaquePointer?>>?
internal expect inline val JniEnv.DeleteWeakGlobalRef: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?) -> Unit>>?
internal expect inline val JniEnv.SetCharField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?, UShort) -> Unit>>?
internal expect inline val JniEnv.GetCharArrayElements: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<UByteVar>?) -> CPointer<UShortVar>?>>?
internal expect inline val JniEnv.NewString: CPointer<CFunction<(CPointer<JniEnv>?, CPointer<UShortVar>?, Int) -> COpaquePointer?>>?
internal expect inline val JniEnv.GetStringLength: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?) -> Int>>?
internal expect inline val JniEnv.CallNonvirtualShortMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> Short>>?
internal expect inline val JniEnv.GetByteArrayRegion: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, Int, Int, CPointer<ByteVar>?) -> Unit>>?
internal expect inline val JniEnv.GetBooleanArrayElements: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<UByteVar>?) -> CPointer<UByteVar>?>>?
internal expect inline val JniEnv.SetObjectField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?, COpaquePointer?) -> Unit>>?
internal expect inline val JniEnv.GetSuperclass: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?) -> COpaquePointer?>>?
internal expect inline val JniEnv.GetStringUTFRegion: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, Int, Int, CPointer<ByteVar>?) -> Unit>>?
internal expect inline val JniEnv.SetLongArrayRegion: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, Int, Int, CPointer<LongVar>?) -> Unit>>?
internal expect inline val JniEnv.RegisterNatives: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<JniNativeMethod>?, Int) -> Int>>?
internal expect inline val JniEnv.SetIntArrayRegion: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, Int, Int, CPointer<IntVar>?) -> Unit>>?
internal expect inline val JniEnv.SetFloatField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?, Float) -> Unit>>?
internal expect inline val JniEnv.NewBooleanArray: CPointer<CFunction<(CPointer<JniEnv>?, Int) -> COpaquePointer?>>?
internal expect inline val JniEnv.GetDoubleArrayElements: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<UByteVar>?) -> CPointer<DoubleVar>?>>?
internal expect inline val JniEnv.CallFloatMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> Float>>?
internal expect inline val JniEnv.SetByteArrayRegion: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, Int, Int, CPointer<ByteVar>?) -> Unit>>?
internal expect inline val JniEnv.GetStaticShortField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?) -> Short>>?
internal expect inline val JniEnv.CallStaticShortMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> Short>>?
internal expect inline val JniEnv.ReleaseBooleanArrayElements: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<UByteVar>?, Int) -> Unit>>?
internal expect inline val JniEnv.GetStaticObjectField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?) -> COpaquePointer?>>?
internal expect inline val JniEnv.MonitorEnter: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?) -> Int>>?
internal expect inline val JniEnv.SetLongField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?, Long) -> Unit>>?
internal expect inline val JniEnv.ReleaseStringCritical: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<UShortVar>?) -> Unit>>?
internal expect inline val JniEnv.GetFloatField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?) -> Float>>?
internal expect inline val JniEnv.ReleasePrimitiveArrayCritical: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, COpaquePointer?, Int) -> Unit>>?
internal expect inline val JniEnv.GetIntField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?) -> Int>>?
internal expect inline val JniEnv.GetObjectRefType: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?) -> UInt>>?
internal expect inline val JniEnv.GetFloatArrayRegion: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, Int, Int, CPointer<FloatVar>?) -> Unit>>?
internal expect inline val JniEnv.GetDoubleArrayRegion: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, Int, Int, CPointer<DoubleVar>?) -> Unit>>?
internal expect inline val JniEnv.SetDoubleField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?, Double) -> Unit>>?
internal expect inline val JniEnv.CallBooleanMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> UByte>>?
internal expect inline val JniEnv.GetShortArrayElements: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<UByteVar>?) -> CPointer<ShortVar>?>>?
internal expect inline val JniEnv.CallNonvirtualDoubleMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> Double>>?
internal expect inline val JniEnv.FromReflectedMethod: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?) -> CPointer<_jMethodID>?>>?
internal expect inline val JniEnv.DeleteLocalRef: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?) -> Unit>>?
internal expect inline val JniEnv.CallNonvirtualVoidMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> Unit>>?
internal expect inline val JniEnv.ReleaseShortArrayElements: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<ShortVar>?, Int) -> Unit>>?
internal expect inline val JniEnv.ToReflectedMethod: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jMethodID>?, UByte) -> COpaquePointer?>>?
internal expect inline val JniEnv.GetDirectBufferCapacity: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?) -> Long>>?
internal expect inline val JniEnv.CallNonvirtualCharMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> UShort>>?
internal expect inline val JniEnv.GetStaticCharField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?) -> UShort>>?
internal expect inline val JniEnv.NewDirectByteBuffer: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, Long) -> COpaquePointer?>>?
internal expect inline val JniEnv.UnregisterNatives: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?) -> Int>>?
internal expect inline val JniEnv.GetDirectBufferAddress: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?) -> COpaquePointer?>>?
internal expect inline val JniEnv.GetShortField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?) -> Short>>?
internal expect inline val JniEnv.NewFloatArray: CPointer<CFunction<(CPointer<JniEnv>?, Int) -> COpaquePointer?>>?
internal expect inline val JniEnv.FromReflectedField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?) -> CPointer<_jFieldID>?>>?
internal expect inline val JniEnv.GetStaticByteField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?) -> Byte>>?
internal expect inline val JniEnv.GetStaticLongField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?) -> Long>>?
internal expect inline val JniEnv.SetFloatArrayRegion: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, Int, Int, CPointer<FloatVar>?) -> Unit>>?
internal expect inline val JniEnv.CallNonvirtualIntMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> Int>>?
internal expect inline val JniEnv.GetStringUTFChars: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<UByteVar>?) -> CPointer<ByteVar>?>>?
internal expect inline val JniEnv.ReleaseFloatArrayElements: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<FloatVar>?, Int) -> Unit>>?
internal expect inline val JniEnv.IsAssignableFrom: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, COpaquePointer?) -> UByte>>?
internal expect inline val JniEnv.SetShortField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?, Short) -> Unit>>?
internal expect inline val JniEnv.CallByteMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> Byte>>?
internal expect inline val JniEnv.ReleaseCharArrayElements: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<UShortVar>?, Int) -> Unit>>?
internal expect inline val JniEnv.NewObjectArray: CPointer<CFunction<(CPointer<JniEnv>?, Int, COpaquePointer?, COpaquePointer?) -> COpaquePointer?>>?
internal expect inline val JniEnv.CallStaticIntMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> Int>>?
internal expect inline val JniEnv.GetObjectClass: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?) -> COpaquePointer?>>?
internal expect inline val JniEnv.GetStringCritical: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<UByteVar>?) -> CPointer<UShortVar>?>>?
internal expect inline val JniEnv.CallStaticByteMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> Byte>>?
internal expect inline val JniEnv.ReleaseDoubleArrayElements: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<DoubleVar>?, Int) -> Unit>>?
internal expect inline val JniEnv.GetStaticFieldID: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<ByteVar>?, CPointer<ByteVar>?) -> CPointer<_jFieldID>?>>?
internal expect inline val JniEnv.SetBooleanArrayRegion: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, Int, Int, CPointer<UByteVar>?) -> Unit>>?
internal expect inline val JniEnv.NewStringUTF: CPointer<CFunction<(CPointer<JniEnv>?, CPointer<ByteVar>?) -> COpaquePointer?>>?
internal expect inline val JniEnv.SetObjectArrayElement: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, Int, COpaquePointer?) -> Unit>>?
internal expect inline val JniEnv.GetCharArrayRegion: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, Int, Int, CPointer<UShortVar>?) -> Unit>>?
internal expect inline val JniEnv.GetFloatArrayElements: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<UByteVar>?) -> CPointer<FloatVar>?>>?
internal expect inline val JniEnv.CallObjectMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> COpaquePointer?>>?
internal expect inline val JniEnv.NewWeakGlobalRef: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?) -> COpaquePointer?>>?
internal expect inline val JniEnv.NewDoubleArray: CPointer<CFunction<(CPointer<JniEnv>?, Int) -> COpaquePointer?>>?
internal expect inline val JniEnv.SetStaticIntField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?, Int) -> Unit>>?
internal expect inline val JniEnv.ToReflectedField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?, UByte) -> COpaquePointer?>>?
internal expect inline val JniEnv.GetStringRegion: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, Int, Int, CPointer<UShortVar>?) -> Unit>>?
internal expect inline val JniEnv.GetShortArrayRegion: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, Int, Int, CPointer<ShortVar>?) -> Unit>>?
internal expect inline val JniEnv.MonitorExit: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?) -> Int>>?
internal expect inline val JniEnv.CallNonvirtualLongMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> Long>>?
internal expect inline val JniEnv.GetMethodID: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<ByteVar>?, CPointer<ByteVar>?) -> CPointer<_jMethodID>?>>?
internal expect inline val JniEnv.CallIntMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> Int>>?
internal expect inline val JniEnv.DefineClass: CPointer<CFunction<(CPointer<JniEnv>?, CPointer<ByteVar>?, COpaquePointer?, CPointer<ByteVar>?, Int) -> COpaquePointer?>>?
internal expect inline val JniEnv.GetStringChars: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<UByteVar>?) -> CPointer<UShortVar>?>>?
internal expect inline val JniEnv.GetIntArrayRegion: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, Int, Int, CPointer<IntVar>?) -> Unit>>?
internal expect inline val JniEnv.CallDoubleMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> Double>>?
internal expect inline val JniEnv.CallStaticObjectMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> COpaquePointer?>>?
internal expect inline val JniEnv.ReleaseStringChars: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<UShortVar>?) -> Unit>>?
internal expect inline val JniEnv.GetStaticFloatField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?) -> Float>>?
internal expect inline val JniEnv.GetIntArrayElements: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<UByteVar>?) -> CPointer<IntVar>?>>?
internal expect inline val JniEnv.GetBooleanArrayRegion: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, Int, Int, CPointer<UByteVar>?) -> Unit>>?
internal expect inline val JniEnv.GetVersion: CPointer<CFunction<(CPointer<JniEnv>?) -> Int>>?
internal expect inline val JniEnv.NewObjectA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> COpaquePointer?>>?
internal expect inline val JniEnv.SetStaticCharField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?, UShort) -> Unit>>?
internal expect inline val JniEnv.IsSameObject: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, COpaquePointer?) -> UByte>>?
internal expect inline val JniEnv.SetStaticBooleanField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?, UByte) -> Unit>>?
internal expect inline val JniEnv.SetStaticObjectField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?, COpaquePointer?) -> Unit>>?
internal expect inline val JniEnv.CallStaticBooleanMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> UByte>>?
internal expect inline val JniEnv.SetStaticFloatField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?, Float) -> Unit>>?
internal expect inline val JniEnv.CallStaticCharMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> UShort>>?
internal expect inline val JniEnv.DeleteGlobalRef: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?) -> Unit>>?
internal expect inline val JniEnv.IsInstanceOf: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, COpaquePointer?) -> UByte>>?
internal expect inline val JniEnv.NewLocalRef: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?) -> COpaquePointer?>>?
internal expect inline val JniEnv.CallStaticDoubleMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> Double>>?
internal expect inline val JniEnv.GetArrayLength: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?) -> Int>>?
internal expect inline val JniEnv.GetLongArrayElements: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<UByteVar>?) -> CPointer<LongVar>?>>?
internal expect inline val JniEnv.ThrowNew: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<ByteVar>?) -> Int>>?
internal expect inline val JniEnv.SetShortArrayRegion: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, Int, Int, CPointer<ShortVar>?) -> Unit>>?
internal expect inline val JniEnv.NewIntArray: CPointer<CFunction<(CPointer<JniEnv>?, Int) -> COpaquePointer?>>?
internal expect inline val JniEnv.GetCharField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?) -> UShort>>?
internal expect inline val JniEnv.GetLongField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?) -> Long>>?
internal expect inline val JniEnv.SetBooleanField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?, UByte) -> Unit>>?
internal expect inline val JniEnv.SetDoubleArrayRegion: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, Int, Int, CPointer<DoubleVar>?) -> Unit>>?
internal expect inline val JniEnv.GetDoubleField: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<_jFieldID>?) -> Double>>?
internal expect inline val JniEnv.ReleaseLongArrayElements: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, CPointer<LongVar>?, Int) -> Unit>>?
internal expect inline val JniEnv.CallNonvirtualBooleanMethodA: CPointer<CFunction<(CPointer<JniEnv>?, COpaquePointer?, COpaquePointer?, CPointer<_jMethodID>?, JArguments?) -> UByte>>?
internal expect inline val JavaVM.DestroyJavaVM: CPointer<CFunction<(CPointer<JavaVM>?) -> Int>>?
internal expect inline val JavaVM.AttachCurrentThread: CPointer<CFunction<(CPointer<JavaVM>?, CPointer<CPointerVar<JniEnv>>?, COpaquePointer?) -> Int>>?
internal expect inline val JavaVM.GetEnv: CPointer<CFunction<(CPointer<JavaVM>?, CPointer<CPointerVar<JniEnv>>?, Int) -> Int>>?
internal expect inline val JavaVM.AttachCurrentThreadAsDaemon: CPointer<CFunction<(CPointer<JavaVM>?, CPointer<CPointerVar<JniEnv>>?, COpaquePointer?) -> Int>>?
internal expect inline val JavaVM.DetachCurrentThread: CPointer<CFunction<(CPointer<JavaVM>?) -> Int>>?