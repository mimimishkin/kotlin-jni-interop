import io.github.mimimishkin.jni.binding.JObject
import io.github.mimimishkin.jni.binding.JString
import io.github.mimimishkin.jni.binding.JThrowable
import io.github.mimimishkin.jni.binding.JniEnv
import io.github.mimimishkin.jni.binding.annotation.JniActuals
import io.github.mimimishkin.jni.binding.annotation.WithJvmType
import io.github.mimimishkin.jni.binding.clearException
import io.github.mimimishkin.jni.binding.deleteLocalRef
import io.github.mimimishkin.jni.binding.pendingException
import io.github.mimimishkin.jni.binding.printStackTrace
import io.github.mimimishkin.jni.binding.throwNew
import io.github.mimimishkin.jni.binding.toJString
import io.github.mimimishkin.jni.binding.toKString
import io.github.mimimishkin.jni.binding.accessors.asMethod
import io.github.mimimishkin.jni.binding.findClass
import kotlinx.cinterop.AutofreeScope

/**
 * The app's `org.sample.Thrower`, looked up by interface.
 */
private typealias Thrower = @WithJvmType("org.sample.Thrower") JObject

@JniActuals(className = "org.sample.Exceptions")
object Exceptions {

    /**
     * Raises an `IllegalStateException` with `ThrowNew`; the return value is never seen.
     */
    context(env: JniEnv, autofreeScope: AutofreeScope)
    fun throwNative(message: JString?): JString? {
        val clazz = findClass("java/lang/IllegalStateException")
        throwNew(clazz, (message?.toKString() ?: "native failure"))
        clazz.deleteLocalRef()
        return null
    }

    /**
     * Calls [Thrower.boom], catches the Java exception, describes it and clears it.
     */
    context(env: JniEnv, autofreeScope: AutofreeScope)
    fun catchFromJava(thrower: Thrower?): JString? {
        val target = thrower ?: return "null thrower".toJString()
        val boom = target.objectMethodId("boom", "()Ljava/lang/String;").asMethod<JString?>()
        boom(target)
        val pending = pendingException ?: return "no exception".toJString()
        clearException()
        val description = describe(pending)
        pending.deleteLocalRef()
        return description.toJString()
    }

    /**
     * Calls [Thrower.boom] and leaves the exception pending for the Kotlin caller.
     */
    context(env: JniEnv, autofreeScope: AutofreeScope)
    fun propagateFromJava(thrower: Thrower?): JString? {
        val target = thrower ?: return "null thrower".toJString()
        val boom = target.objectMethodId("boom", "()Ljava/lang/String;").asMethod<JString?>()
        boom(target)
        return null
    }

    /**
     * Raises an exception, prints it with `ExceptionDescribe` and returns its description.
     */
    context(env: JniEnv, autofreeScope: AutofreeScope)
    fun describePending(): JString? {
        val clazz = findClass("java/lang/IllegalStateException")
        throwNew(clazz, "described")
        clazz.deleteLocalRef()
        val pending = pendingException ?: return "no exception".toJString()
        printStackTrace()
        clearException()
        val description = describe(pending)
        pending.deleteLocalRef()
        return description.toJString()
    }
}

/**
 * The `Class: message` form of [throwable], as `Throwable.toString` defines it.
 */
context(env: JniEnv, autofreeScope: AutofreeScope)
private fun describe(throwable: JThrowable): String {
    val stringify = throwable.objectMethodId("toString", "()Ljava/lang/String;").asMethod<JString>()
    val ref = stringify(throwable)
    val text = ref.toKString() ?: "unknown"
    ref.deleteLocalRef()
    return text
}
