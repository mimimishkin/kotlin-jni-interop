package io.github.mimimishkin.jni.binding.plugin.producer

import com.google.devtools.ksp.symbol.KSTypeAlias
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.github.mimimishkin.jni.binding.plugin.JavaType
import io.github.mimimishkin.jni.binding.plugin.guess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class BindingGeneratorTest {
    @Test
    fun `simple names are mapped correctly`() {
        with(BindingGenerator) {
            val type = ClassName("io.github.mimimishkin.jni.binding", "TestType")
            val nativeFunName = type.mapNativeFunName("testMethod")
            assertEquals("Java_io_github_mimimishkin_jni_binding_TestType_testMethod", nativeFunName)
        }
    }

    @Test
    fun `complex names are mapped correctly`() {
        with(BindingGenerator) {
            val type = ClassName("cra😒zy.UnReAlIsTiC.package.n_a_m_e.😍😭😎", "__my type 💕__")
            val nativeFunName = type.mapNativeFunName("[╰(*°▽°*)╯];")
            assertEquals("Java_cra_0d83d_0de12zy_UnReAlIsTiC_package_n_1a_1m_1e__0d83d_0de0d_0d83d_0de2d_0d83d_0de0e__1_1my type _0d83d_0dc95_1_1__3_02570(*_000b0_025bd_000b0*)_0256f]_2", nativeFunName)
        }
    }

    @Test
    fun `functions are exported correctly`() {
        val info = JniActualInfo(
            container = ClassName("native.package", "MyObject"),
            methodQualifier = "nativeMethod",
            parameters = listOf(
                JniActualInfo.Parameter(JavaType.Boolean),
                JniActualInfo.Parameter(JavaType.BooleanArray, originalObjectType = ClassName("my.package", "MyClass").parameterizedBy(ClassName("paket", "Type"))),
                JniActualInfo.Parameter(JavaType.Char),
                JniActualInfo.Parameter(JavaType.Long),
                JniActualInfo.Parameter(JavaType.Object, originalObjectType = ClassName("my.package", "MyClass").parameterizedBy(ClassName("paket", "Type"))),
            ),
            returns = JniActualInfo.Parameter(JavaType.Void),
            isStatic = true,
            jvmClass = ClassName("my.package", "MyClass"),
            jvmMethod = "myMethod",
            objOrClsType = ClassName("io.github.mimimishkin.jni.binding", "JClass"),
            envType = null
        )

        val funSpec = BindingGenerator.jniFunction(info)
        val fileSpec = FileSpec.builder("native.packege", "file.kt").addFunction(funSpec).build()
        fileSpec.writeTo(System.out)
    }
}