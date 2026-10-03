import io.github.mimimishkin.jni.binding.JClass
import io.github.mimimishkin.jni.binding.JMethodID
import io.github.mimimishkin.jni.binding.JNI
import io.github.mimimishkin.jni.binding.JObject
import io.github.mimimishkin.jni.binding.JavaVM
import io.github.mimimishkin.jni.binding.JniEnv
import io.github.mimimishkin.jni.binding.deleteLocalRef
import io.github.mimimishkin.jni.binding.findClass
import io.github.mimimishkin.jni.binding.javaClass
import io.github.mimimishkin.jni.binding.localIntoGlobalRef
import io.github.mimimishkin.jni.binding.methodId
import io.github.mimimishkin.jni.binding.staticMethodId
import io.github.mimimishkin.jni.binding.annotation.JniOnLoad
import io.github.mimimishkin.jni.binding.useEnv
import kotlinx.cinterop.AutofreeScope

/**
 * The process `JavaVM`.
 */
internal lateinit var javaVM: JavaVM

/**
 * Method ids of `org.sample.Replies`.
 */
internal class RepliesIds(
    val onInt: JMethodID,
    val onText: JMethodID,
    val onBytes: JMethodID,
)

internal lateinit var repliesIds: RepliesIds
internal lateinit var progressClass: JClass
internal lateinit var progressReportId: JMethodID

/**
 * The library's entry point, where the `jclass`es that outlive a thread are resolved.
 *
 * Top-level because a `JavaVM` is not a type an `@JniActuals` function can map to an `external`.
 */
@JniOnLoad
fun onLoad(vm: JavaVM) {
    javaVM = vm
    vm.useEnv(JNI.v6) {
        val replies = findClass("org/sample/Replies")
        repliesIds = run {
            RepliesIds(
                onInt = replies.methodId("onInt", "(I)V"),
                onText = replies.methodId("onText", "(Ljava/lang/String;)V"),
                onBytes = replies.methodId("onBytes", "([B)V"),
            )
        }
        replies.deleteLocalRef()

        val progress = findClass("org/sample/Progress")
        progressReportId = progress.staticMethodId("report", "(I)V")
        progressClass = progress.localIntoGlobalRef()!!
    }
}

/**
 * Looks up an instance method on the runtime class of this object.
 */
context(env: JniEnv, autofreeScope: AutofreeScope)
internal fun JObject.objectMethodId(name: String, signature: String): JMethodID {
    val clazz = javaClass
    return try {
        clazz.methodId(name, signature)
    } finally {
        clazz.deleteLocalRef()
    }
}
