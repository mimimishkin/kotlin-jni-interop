package io.github.mimimishkin.jni.binding.producer

import io.github.mimimishkin.jni.binding.producer.model.ActualParameterType
import kotlin.test.Test
import kotlin.test.assertEquals

class JniCNameTest {
    @Test
    fun `simple class and method names are mapped correctly`() {
        assertEquals(
            "Java_io_github_mimimishkin_jni_binding_TestType_testMethod",
            jniCName("io.github.mimimishkin.jni.binding.TestType", "testMethod"),
        )
    }

    @Test
    fun `complex names are mapped correctly`() {
        // Every non-alphanumeric character is escaped so the resulting symbol is a valid C identifier,
        // yielding `_0hhhh` sequences for ASCII punctuation just as for unicode.
        assertEquals(
            "Java_cra_0d83d_0de12zy_UnReAlIsTiC_package_n_1a_1m_1e__0d83d_0de0d_0d83d_0de2d_0d83d_0de0e" +
                "__3_02570_00028_0002a_000b0_025bd_000b0_0002a_00029_0256f_0005d_2",
            jniCName("cra\uD83D\uDE12zy.UnReAlIsTiC.package.n_a_m_e.\uD83D\uDE0D\uD83D\uDE2D\uD83D\uDE0E", "[╰(*°▽°*)╯];"),
        )
    }

    @Test
    fun `underscores in the method name are escaped`() {
        assertEquals("Java_com_example_Native_do_1work", jniCName("com.example.Native", "do_work"))
    }

    @Test
    fun `unicode characters are encoded as _0xxxx sequences`() {
        assertEquals("Java_foo_Bar__000e9", jniCName("foo.Bar", "é"))
        assertEquals("Java_foo__000e9_Bar_x", jniCName("foo.é.Bar", "x"))
    }

    @Test
    fun `array and semicolon characters are escaped per the JNI spec`() {
        assertEquals("Java_com_example__3Arr_2type_1X", jniCName("com.example", "[Arr;type_X"))
    }

    @Test
    fun `non-unicode ASCII characters are left untouched`() {
        assertEquals("Java_a_b_c_11_12_13", jniCName("a.b", "c_1_2_3"))
    }

    @Test
    fun `overloaded method names carry the mangled parameter signature`() {
        assertEquals(
            "Java_com_example_Native_add__II",
            jniCName("com.example.Native", "add", listOf(ActualParameterType.Int, ActualParameterType.Int)),
        )
        assertEquals(
            "Java_com_example_Native_add__FF",
            jniCName("com.example.Native", "add", listOf(ActualParameterType.Float, ActualParameterType.Float)),
        )
    }

    @Test
    fun `reference parameter signatures escape slashes with a single underscore`() {
        assertEquals(
            "Java_com_example_Native_edit__Ljava_lang_String_2",
            jniCName("com.example.Native", "edit", listOf(ActualParameterType.Ref("@NonNull java.lang.String"))),
        )
    }

    @Test
    fun `empty parameter list keeps the short method name`() {
        assertEquals("Java_com_example_Native_call", jniCName("com.example.Native", "call", emptyList()))
    }
}
