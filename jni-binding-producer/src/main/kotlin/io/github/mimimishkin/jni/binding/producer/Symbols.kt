package io.github.mimimishkin.jni.binding.producer

internal object Symbols {
    val annotationPackage = "io.github.mimimishkin.jni.binding.annotation".pkg()
    val JniActual = annotationPackage.classId("JniActual")
    val JniActuals = annotationPackage.classId("JniActuals")
    val JniOnLoad = annotationPackage.classId("JniOnLoad")
    val JniOnUnload = annotationPackage.classId("JniOnUnload")
    val WithJvmSignature = annotationPackage.classId("WithJvmSignature")
    val WithJvmType = annotationPackage.classId("WithJvmType")

    val classNameArgument = "className".ident()
    val typeArgument = "type".ident()
    val parameterTypesArgument = "parameterTypes".ident()
    val returnTypeArgument = "returnType".ident()

    val cinteropPackage = "kotlinx.cinterop".pkg()
    val CPointer = cinteropPackage.classId("CPointer")
    val CPointerVarOf = cinteropPackage.classId("CPointerVarOf")
    val COpaquePointer = cinteropPackage.classId("COpaquePointer")
    val MemScope = cinteropPackage.classId("MemScope")
    val CValues = cinteropPackage.classId("CValues")
    val CValuesRef = cinteropPackage.classId("CValuesRef")
    val ByteVar = cinteropPackage.classId("ByteVar")
    val CFunction = cinteropPackage.classId("CFunction")
    val CName = "kotlin.native".pkg().classId("CName")

    val JNINativeInterface = "jni".pkg().classId("JNINativeInterface_")
    val JNIInvokeInterface = "jni".pkg().classId("JNIInvokeInterface_")
    val JObjectRaw = "jni".pkg().classId("_jobject")
    val JClassRaw = "jni".pkg().classId("_jclass")

    val bindingPackage = "io.github.mimimishkin.jni.binding".pkg()
    val JObjectWrapped = bindingPackage.classId("_jobject")
    val JClassWrapped = bindingPackage.classId("_jclass")
    val JavaVM = bindingPackage.classId("JavaVM")
    val JniEnv = bindingPackage.classId("JniEnv")
    val JClass = bindingPackage.classId("JClass")
    val JNINativeMethodRegistry = bindingPackage.classId("JNINativeMethodRegistry")
    val JRef = bindingPackage.classId("JRef")

    val generatedPackage = "jni.binding.generated".pkg()
    val entryPointBinding = generatedPackage.callableId("entryPointJniBinding")
    val exitPointBinding = generatedPackage.callableId("exitPointJniBinding")
}