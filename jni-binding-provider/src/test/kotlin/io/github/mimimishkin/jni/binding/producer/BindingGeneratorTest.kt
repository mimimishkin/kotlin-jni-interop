package io.github.mimimishkin.jni.binding.producer

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSVisitor
import com.google.devtools.ksp.symbol.Location
import com.google.devtools.ksp.symbol.NonExistLocation
import com.google.devtools.ksp.symbol.Origin
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.github.mimimishkin.jni.binding.producer.JniActualInfo.JvmSignature
import io.github.mimimishkin.jni.binding.producer.JniActualInfo.Parameter
import kotlin.test.Test
import kotlin.test.assertEquals

class BindingGeneratorTest {
    private val generator = BindingGenerator(JniInteropConfig.FromJniBinding)

    @Test
    fun `simple names are mapped correctly`() {
        with(generator) {
            val type = ClassName("io.github.mimimishkin.jni.binding", "TestType")
            val nativeFunName = type.mapNativeFunName("testMethod")
            assertEquals("Java_io_github_mimimishkin_jni_binding_TestType_testMethod", nativeFunName)
        }
    }

    @Test
    fun `complex names are mapped correctly`() {
        with(generator) {
            val type = ClassName("cra😒zy.UnReAlIsTiC.package.n_a_m_e.😍😭😎", "__my type 💕__")
            val nativeFunName = type.mapNativeFunName("[╰(*°▽°*)╯];")
            assertEquals("Java_cra_0d83d_0de12zy_UnReAlIsTiC_package_n_1a_1m_1e__0d83d_0de0d_0d83d_0de2d_0d83d_0de0e__1_1my type _0d83d_0dc95_1_1__3_02570(*_000b0_025bd_000b0*)_0256f]_2", nativeFunName)
        }
    }

    private val testFile: KSFile = object : KSFile {
        override val fileName: String = "test.kt"
        override val filePath: String = "test.kt"
        override val packageName: KSName = object : KSName {
            override fun asString(): String = "test.package"
            override fun getQualifier(): String = "test.package"
            override fun getShortName(): String = "package"
        }
        override val declarations: Sequence<KSDeclaration> = emptySequence()
        override val location: Location = NonExistLocation
        override val origin: Origin = Origin.KOTLIN_LIB
        override val parent: KSNode? = null
        override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R = visitor.visitFile(this, data)
        override val annotations: Sequence<KSAnnotation> = emptySequence()
    }

    private val actualInfos: List<JniActualInfo>
        get() {
            val actuals = listOf(
                // without container, with object parameter, instance method
                JniActualInfo(
                    source = testFile,
                    method = MemberName("native.package1", "nativeMethod1"),
                    parameters = listOf(
                        Parameter.Ref(
                            ClassName("one.package", "MyClass").parameterizedBy(ClassName("paket", "Type"))
                        ),
                        Parameter.Ref(
                            ClassName("two.package", "MyClass2").parameterizedBy(ClassName("paket", "Type2"))
                        ),
                    ),
                    returns = Parameter.Unit,
                    needEnv = true,
                    needObjOrCls = true,
                    isStatic = false,
                    jvmClass = ClassName("my.package", "MyClass"),
                    jvmMethod = "myMethod1",
                    jvmSignature = JvmSignature(
                        parameters = listOf("one.jvm.package.MyClass", "two.jvm.package.MyClass2"),
                        returnType = "void"
                    ),
                ),
                // with container, with primitive parameters, with return type, with conversions
                JniActualInfo(
                    source = testFile,
                    method = MemberName(ClassName("native.package2", "MyObject"), "nativeMethod2"),
                    parameters = listOf(
                        Parameter.Long,
                        Parameter.Float,
                        Parameter.Double,
                        Parameter.UByte,
                        Parameter.CharIn,
                        Parameter.BooleanIn,
                    ),
                    returns = Parameter.BooleanOut,
                    needEnv = true,
                    needObjOrCls = true,
                    isStatic = true,
                    jvmClass = ClassName("my.package", "MyClass"),
                    jvmMethod = "myMethod2",
                    jvmSignature = JvmSignature(
                        parameters = listOf("long", "float", "double", "boolean", "char", "boolean"),
                        returnType = "boolean"
                    ),
                ),
                // without objOrCls, without env, with signature
                JniActualInfo(
                    source = testFile,
                    method = MemberName("native.package3", "nativeMethod3"),
                    parameters = listOf(
                        Parameter.Double,
                        Parameter.Float,
                        Parameter.BooleanIn,
                        Parameter.CharIn,
                        Parameter.Ref(
                            ClassName("my.package", "MyClass")
                                .parameterizedBy(ClassName("paket", "Type"))
                                .copy(nullable = true)
                        ),
                    ),
                    returns = Parameter.UByte,
                    needEnv = false,
                    needObjOrCls = false,
                    isStatic = null,
                    jvmClass = ClassName("my.package", "OtherClass"),
                    jvmMethod = "myMethod3",
                    jvmSignature = JvmSignature(
                        parameters = listOf("double", "float", "boolean", "char", "checked.Class"),
                        returnType = "boolean"
                    ),
                ),
            )
            return actuals
        }

    @Test
    fun `jni functions are generated correctly`() {
        val actuals = actualInfos

        val funSpecs = actuals.map { generator.jniFunction(it) }
        val fileSpec = FileSpec.builder("native.packege", "file.kt").addFunctions(funSpecs).build()
        fileSpec.writeTo(System.out)
    }

    @Test
    fun `hooks are generated correctly`() {
        val beforeLoad = listOf(
            JniHookInfo(testFile, MemberName("native.package1", "beforeLoad1"), needVm = true),
            JniHookInfo(testFile, MemberName("native.package2", "beforeLoad2"), needVm = false),
        )

        val hook = generator.onLoad(JniVersion.V10, beforeLoad, actualInfos)
        val fileSpec = FileSpec.builder("io.github.mimimishkin.jni.binding", "OnLoadHook").addFunction(hook).build()
        fileSpec.writeTo(System.out)
    }
}