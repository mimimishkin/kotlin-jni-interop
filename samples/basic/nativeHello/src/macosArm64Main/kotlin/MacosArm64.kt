import io.github.mimimishkin.jni.binding.JString
import io.github.mimimishkin.jni.binding.JniEnv
import io.github.mimimishkin.jni.binding.annotation.JniActual
import io.github.mimimishkin.jni.binding.toJString
import kotlinx.cinterop.memScoped

@JniActual(className = "org.sample.ImplByTargetsKt")
context(env: JniEnv)
fun macosArm64Hello(): JString? = memScoped {
    return "Hello from MacOS AArch64".toJString()
}