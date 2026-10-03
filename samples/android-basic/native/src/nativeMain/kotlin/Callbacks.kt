import io.github.mimimishkin.jni.binding.JByteArray
import io.github.mimimishkin.jni.binding.JNI
import io.github.mimimishkin.jni.binding.JObject
import io.github.mimimishkin.jni.binding.JString
import io.github.mimimishkin.jni.binding.JniEnv
import io.github.mimimishkin.jni.binding.annotation.JniActuals
import io.github.mimimishkin.jni.binding.annotation.WithJvmType
import io.github.mimimishkin.jni.binding.accessors.asMethod
import io.github.mimimishkin.jni.binding.callStaticObjectMethod
import io.github.mimimishkin.jni.binding.callVoidMethod
import io.github.mimimishkin.jni.binding.deleteGlobalRef
import io.github.mimimishkin.jni.binding.deleteLocalRef
import io.github.mimimishkin.jni.binding.findClass
import io.github.mimimishkin.jni.binding.int
import io.github.mimimishkin.jni.binding.jArgs
import io.github.mimimishkin.jni.binding.methodId
import io.github.mimimishkin.jni.binding.newByteArray
import io.github.mimimishkin.jni.binding.newGlobalRef
import io.github.mimimishkin.jni.binding.ref
import io.github.mimimishkin.jni.binding.refFrame
import io.github.mimimishkin.jni.binding.setRegion
import io.github.mimimishkin.jni.binding.staticMethodId
import io.github.mimimishkin.jni.binding.toJString
import io.github.mimimishkin.jni.binding.toKString
import io.github.mimimishkin.jni.binding.withEnvAttaching
import kotlinx.cinterop.AutofreeScope
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import kotlin.concurrent.atomics.AtomicLong

@JniActuals(className = "org.sample.Callbacks")
object Callbacks {

    context(env: JniEnv, autofreeScope: AutofreeScope)
    fun ping(): JString? = "pong".toJString()

    /**
     * Converts the argument to a Kotlin `String` and back to a `JString`.
     */
    context(env: JniEnv, autofreeScope: AutofreeScope)
    fun echo(message: JString?): JString? = (message?.toKString() ?: "null").toJString()

    context(env: JniEnv)
    fun sum(a: Int, b: Int, c: Int): Int = a + b + c

    /**
     * Allocates and fills a `JByteArray`.
     */
    context(env: JniEnv)
    fun fill(size: Int, seed: Int): JByteArray? {
        val array = newByteArray(size) ?: return null
        var state = seed
        val bytes = ByteArray(size) {
            state = state xor (state shl 13)
            state = state xor (state ushr 7)
            state = state xor (state shl 17)
            state.toByte()
        }
        bytes.usePinned { pinned ->
            array.setRegion(0, size, pinned.addressOf(0))
        }
        return array
    }

    /**
     * Calls `Replies.onInt`/`onText`/`onBytes` and returns `value`, or -1 if `listener` is null.
     */
    context(env: JniEnv, autofreeScope: AutofreeScope)
    fun roundTrip(listener: @WithJvmType("org.sample.Replies") JObject?, value: Int): Int {
        val target = listener ?: return -1
        target.callVoidMethod(repliesIds.onInt, jArgs(1) { int(value) })
        val text = (value * 2).toString().toJString() ?: return -1
        target.callVoidMethod(repliesIds.onText, jArgs(1) { ref(text) })
        val bytes = newByteArray(4) ?: return -1
        ByteArray(4) { i -> (value shr (i * 8)).toByte() }.usePinned { pinned ->
            bytes.setRegion(0, 4, pinned.addressOf(0))
        }
        target.callVoidMethod(repliesIds.onBytes, jArgs(1) { ref(bytes) })
        bytes.deleteLocalRef()
        text.deleteLocalRef()
        return value
    }

    /**
     * Promotes `listener` to a global reference for the workers and releases it afterward.
     */
    context(env: JniEnv)
    fun fromNativeThreads(listener: @WithJvmType("org.sample.Replies") JObject?, threads: Int, iterations: Int): Long {
        val global = listener?.newGlobalRef() ?: return 0L
        val delivered = refFrame(16) { reportFromWorkers(global, threads, iterations) }
        global.deleteGlobalRef()
        return delivered
    }
}

/**
 * Runs [threads] workers that each attach to the VM and report [iterations] times.
 *
 * Declared without a `JniEnv` context parameter so each worker uses its own attached environment.
 */
@OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
private fun reportFromWorkers(listener: JObject, threads: Int, iterations: Int): Long {
    val delivered = AtomicLong(0L)
    runOnWorkers(threads) {
        val vm = javaVM
        memScoped {
            vm.withEnvAttaching(JNI.v6, "example-worker") {
                repeat(iterations) { iteration ->
                    val text = "${workerName()} #$iteration".toJString() ?: return@repeat
                    listener.callVoidMethod(repliesIds.onText, jArgs(1) { ref(text) })
                    delivered.addAndFetch(1L)
                }
            }
        }
    }
    return delivered.load()
}

/**
 * `Thread.currentThread().getName()` of the thread that just attached.
 */
context(env: JniEnv, autofreeScope: AutofreeScope)
private fun workerName(): String =
    refFrame(8) {
        val threadClass = findClass("java/lang/Thread")
        val currentThreadId = threadClass.staticMethodId("currentThread", "()Ljava/lang/Thread;")
        val thread = checkNotNull(threadClass.callStaticObjectMethod(currentThreadId, jArgs(0) {}))
        val getName = threadClass.methodId("getName", "()Ljava/lang/String;").asMethod<JString?>()
        (thread.getName())?.toKString() ?: "unknown"
    }
