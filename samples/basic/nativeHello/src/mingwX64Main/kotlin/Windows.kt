import io.github.mimimishkin.jni.binding.JString
import io.github.mimimishkin.jni.binding.JniEnv
import io.github.mimimishkin.jni.binding.annotation.JniActual
import io.github.mimimishkin.jni.binding.toJString
import kotlinx.cinterop.memScoped

// Unlike `samples/basic/native`, this module exports a DIFFERENT binding on every platform.
// The matching `@JniExpect` on the JVM side (see `ImplByTargets.kt`) is restricted to this
// target via the `targets` parameter, so each platform only needs to provide its own method.
@JniActual(className = "org.sample.ImplByTargetsKt")
context(env: JniEnv)
fun windowsHello(): JString? = memScoped {
    return "Hello from Windows x86_64".toJString()
}