package io.github.mimimishkin.jni.binding.consumer

internal object Symbols {
    val annotationPackage = "io.github.mimimishkin.jni.binding.annotation".pkg()
    val JniExpect = annotationPackage.classId("JniExpect")
    val JniExpects = annotationPackage.classId("JniExpects")
    val LoadMethod = annotationPackage.classId("LoadMethod")
    val CriticalNative = annotationPackage.classId("CriticalNative")
    val androidCriticalNative = "dalvik.annotation.optimization".pkg().classId("CriticalNative")

    val osParameter = "os".ident()
    val archParameter = "arch".ident()
    val vendorParameter = "vendor".ident()
    val loadMethodParameters = setOf(osParameter, archParameter, vendorParameter)

    val javaLangPackage = "java.lang".pkg()
    val SystemClass = javaLangPackage.classId("System")
    val StringClass = javaLangPackage.classId("String")
    val systemGetProperty = SystemClass.callableId("getProperty")
    val stringToLowerCase = StringClass.callableId("toLowerCase")
    val stringContains = StringClass.callableId("contains")

    val javaUtilPackage = "java.util".pkg()
    val ObjectsClass = javaUtilPackage.classId("Objects")
    val objectsEquals = ObjectsClass.callableId("equals")
}