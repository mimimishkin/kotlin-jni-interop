import io.github.mimimishkin.jni.binding.JNI
import io.github.mimimishkin.jni.binding.JString
import io.github.mimimishkin.jni.binding.JniEnv
import io.github.mimimishkin.jni.binding.JniThreadDetachedException
import io.github.mimimishkin.jni.binding.annotation.JniActuals
import io.github.mimimishkin.jni.binding.callStaticObjectMethod
import io.github.mimimishkin.jni.binding.callStaticVoidMethod
import io.github.mimimishkin.jni.binding.deleteLocalRef
import io.github.mimimishkin.jni.binding.getEnv
import io.github.mimimishkin.jni.binding.int
import io.github.mimimishkin.jni.binding.jArgs
import io.github.mimimishkin.jni.binding.staticMethodId
import io.github.mimimishkin.jni.binding.toJString
import io.github.mimimishkin.jni.binding.toKString
import io.github.mimimishkin.jni.binding.withEnvAttaching
import io.github.mimimishkin.jni.binding.accessors.asMethod
import io.github.mimimishkin.jni.binding.findClass
import io.github.mimimishkin.jni.binding.methodId
import kotlinx.cinterop.AutofreeScope
import kotlinx.cinterop.memScoped
import kotlin.concurrent.atomics.AtomicLong

@JniActuals(className = "org.sample.Threads")
object Threads {

    /**
     * Whether the calling thread is attached to the VM.
     */
    context(autofreeScope: AutofreeScope)
    fun isAttached(): Boolean {
        return try {
            javaVM.getEnv(JNI.v6)
            true
        } catch (_: JniThreadDetachedException) {
            false
        }
    }

    /**
     * `Thread.currentThread().getName()` of the calling thread.
     */
    context(env: JniEnv, autofreeScope: AutofreeScope)
    fun currentThreadName(): JString? {
        val threadClass = findClass("java/lang/Thread")
        val currentThreadId = threadClass.staticMethodId("currentThread", "()Ljava/lang/Thread;")
        val thread = checkNotNull(threadClass.callStaticObjectMethod(currentThreadId, jArgs(0) {}))
        val getName = threadClass.methodId("getName", "()Ljava/lang/String;").asMethod<JString?>()
        val nameRef = getName(thread)
        val name = nameRef?.toKString() ?: "unknown"
        nameRef?.deleteLocalRef()
        thread.deleteLocalRef()
        threadClass.deleteLocalRef()
        return name.toJString()
    }

    /**
     * Starts [threads] workers that attach to the VM and call the static `Progress.report`
     * [iterations] times each.
     * */
    fun daemonThreadReports(threads: Int, iterations: Int): Long {
        val delivered = AtomicLong(0L)
        runOnWorkers(threads) {
            val vm = javaVM
            memScoped {
                vm.withEnvAttaching(JNI.v6, "example-worker") {
                    repeat(iterations) { value ->
                        progressClass.callStaticVoidMethod(progressReportId, jArgs(1) { int(value) })
                        delivered.addAndFetch(1L)
                    }
                }
            }
        }
        return delivered.load()
    }
}
