import io.github.mimimishkin.jni.binding.JString
import io.github.mimimishkin.jni.binding.JniEnv
import io.github.mimimishkin.jni.binding.annotation.JniActual
import io.github.mimimishkin.jni.binding.toJString
import kotlinx.cinterop.memScoped

@JniActual(className = "org.sample.ImplByTargetsKt")
context(env: JniEnv)
fun linuxX64Hello(): JString? = memScoped {
    return "Hello from Linux x86_64".toJString()
}