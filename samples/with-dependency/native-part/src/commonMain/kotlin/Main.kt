import io.github.mimimishkin.jni.binding.*
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.utf8

// native functions can be implemented like these
@CName("Java_io_github_mimimishkin_samples_longcomputation_Main_nativeComputation")
fun nativeComputation(env: JniEnv, obj: JObject, count: JInt) {
    val array = ByteArray(count)
    for ((index, b) in array.withIndex()) {
        array[index] = (b + index).toByte()
    }
}

val jniVersion = JNI.v21

// or like this
@CName("JNI_OnLoad")
fun onLoad(vm: JavaVM, unused: COpaquePointer): JniVersion {
    memScoped {
        vm.withEnv(jniVersion) {
            val clazz = findClass("io/github/mimimishkin/samples/longcomputation/Main".utf8)

            if (clazz == null) {
                val exClass = findClass("java/lang/Exception".utf8)!!
                throwNew(exClass, "Could not find class".utf8)
            } else {
                clazz.registerNatives(1) {
                    register("nativeComputation".utf8, "(I)V".utf8, staticCFunction(::nativeComputation))
                }
            }
        }
    }

    return jniVersion
}