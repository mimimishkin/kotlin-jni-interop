import io.github.mimimishkin.jni.*
import io.github.mimimishkin.jni.JClass
import io.github.mimimishkin.jni.JniEnv
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.utf8

@CName("Java_io_github_mimimishkin_samples_longcomputation_Main_nativeComputationAutoFound")
fun nativeComputation(env: JniEnv, clazz: JClass, count: JInt) {
    val array = ByteArray(count)
    for ((index, b) in array.withIndex()) {
        array[index] = (b + index).toByte()
    }
}

@CName("JNI_OnLoad")
fun onLoad(vm: JavaVM, unused: COpaquePointer): JniVersion {
    val version = JNI.lastVersion
    memScoped {
        vm.withEnv(version) {
            val clazz = FindClass("io/github/mimimishkin/samples/longcomputation/Main".utf8)

            if (clazz == null) {
                val exClass = FindClass("java/lang/Exception".utf8)!!
                ThrowNew(exClass, "Could not find class".utf8)
            } else {
                registerNativesFor(clazz, 1) {
                    register("nativeComputation".utf8, "(I)V".utf8, staticCFunction { env: JniEnv, clazz: JClass, count: JInt ->
                        val array = ByteArray(count)
                        for ((index, b) in array.withIndex()) {
                            array[index] = (b + index).toByte()
                        }
                    })
                }
            }
        }
    }

    return version
}

