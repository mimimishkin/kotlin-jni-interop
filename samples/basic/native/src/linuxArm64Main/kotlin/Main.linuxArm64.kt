import io.github.mimimishkin.jni.binding.JString
import io.github.mimimishkin.jni.binding.JniEnv
import io.github.mimimishkin.jni.binding.annotation.JniActual
import io.github.mimimishkin.jni.binding.toJString
import kotlinx.cinterop.memScoped

@JniActual(className = "org.sample.MainKt", methodName = "outerFun")
context(env: JniEnv)
actual fun outerFun(): JString? = memScoped {
    return "Hello from linuxArm64".toJString()
}