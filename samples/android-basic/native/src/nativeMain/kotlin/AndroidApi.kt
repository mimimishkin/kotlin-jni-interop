import io.github.mimimishkin.jni.binding.JObject
import io.github.mimimishkin.jni.binding.JString
import io.github.mimimishkin.jni.binding.JniEnv
import io.github.mimimishkin.jni.binding.annotation.JniActuals
import io.github.mimimishkin.jni.binding.annotation.WithJvmType
import io.github.mimimishkin.jni.binding.accessors.asMethod
import io.github.mimimishkin.jni.binding.callBooleanMethod
import io.github.mimimishkin.jni.binding.callIntMethod
import io.github.mimimishkin.jni.binding.callObjectMethod
import io.github.mimimishkin.jni.binding.callStaticIntMethod
import io.github.mimimishkin.jni.binding.callStaticObjectMethod
import io.github.mimimishkin.jni.binding.deleteLocalRef
import io.github.mimimishkin.jni.binding.findClass
import io.github.mimimishkin.jni.binding.int
import io.github.mimimishkin.jni.binding.jArgs
import io.github.mimimishkin.jni.binding.ref
import io.github.mimimishkin.jni.binding.refFrame
import io.github.mimimishkin.jni.binding.staticMethodId
import io.github.mimimishkin.jni.binding.toJString
import io.github.mimimishkin.jni.binding.toKString
import kotlinx.cinterop.AutofreeScope

private typealias JStringBuilder = @WithJvmType("java.lang.StringBuilder") JObject
private typealias JHashMap = @WithJvmType("java.util.HashMap") JObject
private typealias JArrayList = @WithJvmType("java.util.ArrayList") JObject

@JniActuals(className = "org.sample.AndroidApi")
object AndroidApi {

    /**
     * Calls the `StringBuilder.append(String)` overload `times` times.
     */
    context(env: JniEnv, autofreeScope: AutofreeScope)
    fun appendTo(builder: JStringBuilder?, value: JString?, times: Int): JString? {
        val target = builder ?: return "null builder".toJString()
        val append = target
            .objectMethodId("append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;")
            .asMethod<JString?, JObject?>()
        val toString = target.objectMethodId("toString", "()Ljava/lang/String;")
            .asMethod<JString?>()
        repeat(times) {
            append(target, value)?.deleteLocalRef()
        }
        return toString(target)
    }

    /**
     * Iterates `HashMap.entrySet` and adds [delta] to every value, through
     * `intValue()`/`Integer.valueOf`.
     */
    context(env: JniEnv, autofreeScope: AutofreeScope)
    fun bump(map: JHashMap?, delta: Int): Int {
        val target = map ?: return 0
        return refFrame(32) {
            val integerClass = findClass("java/lang/Integer")
            val valueOf = checkNotNull(
                integerClass.staticMethodId("valueOf", "(I)Ljava/lang/Integer;")
            ) { "Cannot find Integer.valueOf" }
            val entries = target.callObjectMethod(
                target.objectMethodId("entrySet", "()Ljava/util/Set;"), jArgs(0) {}
            ) ?: return@refFrame 0
            val iterator = entries.callObjectMethod(
                entries.objectMethodId("iterator", "()Ljava/util/Iterator;"), jArgs(0) {}
            ) ?: return@refFrame 0
            val hasNext = iterator.objectMethodId("hasNext", "()Z")
            val next = iterator.objectMethodId("next", "()Ljava/lang/Object;")
            var sum = 0
            while (iterator.callBooleanMethod(hasNext, jArgs(0) {})) {
                val entry = iterator.callObjectMethod(next, jArgs(0) {}) ?: continue
                val boxed = entry.callObjectMethod(
                    entry.objectMethodId("getValue", "()Ljava/lang/Object;"), jArgs(0) {}
                )
                val current =
                    boxed?.callIntMethod(boxed.objectMethodId("intValue", "()I"), jArgs(0) {}) ?: 0
                val updated = current + delta
                val newBoxed =
                    integerClass.callStaticObjectMethod(valueOf, jArgs(1) { int(updated) })
                entry.callObjectMethod(
                    entry.objectMethodId("setValue", "(Ljava/lang/Object;)Ljava/lang/Object;"),
                    jArgs(1) { ref(newBoxed) },
                )
                sum += updated
            }
            sum
        }
    }

    /**
     * Reads `ArrayList.size`/`get` and joins the elements.
     */
    context(env: JniEnv, autofreeScope: AutofreeScope)
    fun join(parts: JArrayList?, separator: JString?): JString? {
        val target = parts ?: return "null list".toJString()
        val sizeId = target.objectMethodId("size", "()I")
        val get = target.objectMethodId("get", "(I)Ljava/lang/Object;").asMethod<Int, JString?>()
        val sep = separator?.toKString() ?: ""
        val builder = StringBuilder()
        val count = target.callIntMethod(sizeId, jArgs(0) {})
        for (i in 0 until count) {
            if (i > 0) builder.append(sep)
            val element = get(target, i)
            builder.append(element?.toKString() ?: "")
            element?.deleteLocalRef()
        }
        return builder.toString().toJString()
    }

    /**
     * Calls the static `android.util.Log.i`.
     */
    context(env: JniEnv, autofreeScope: AutofreeScope)
    fun logcat(tag: JString?, message: JString?): Int {
        val log = findClass("android/util/Log")
        val i = log.staticMethodId("i", "(Ljava/lang/String;Ljava/lang/String;)I")
        val result = log.callStaticIntMethod(i, jArgs(2) { ref(tag); ref(message) })
        log.deleteLocalRef()
        return result
    }
}
