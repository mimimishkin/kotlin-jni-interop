package io.github.mimimishkin.jni.binding.producer.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class JniActualInfoSerializationTest {
    @Test
    fun `serialize round-trips all fields`() {
        val info = JniActualInfo(
            needEnv = true,
            isStatic = false,
            className = "com.example.Native",
            methodName = "value",
            parameterTypes = listOf("int", "@Nullable java.lang.String"),
            returnType = "boolean",
            source = "Main.kt",
        )

        val text = Json.encodeToString(JniActualInfo.serializer(), info)
        assertEquals(info, Json.decodeFromString(JniActualInfo.serializer(), text))
    }
}
