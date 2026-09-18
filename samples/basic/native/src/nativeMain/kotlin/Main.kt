import Main.mainClass
import io.github.mimimishkin.jni.binding.*
import io.github.mimimishkin.jni.binding.accessors.asKotlin
import io.github.mimimishkin.jni.binding.accessors.asMethod
import io.github.mimimishkin.jni.binding.accessors.asType
import io.github.mimimishkin.jni.binding.accessors.staticObjectField
import io.github.mimimishkin.jni.binding.annotation.*
import kotlinx.cinterop.AutofreeScope
import kotlinx.cinterop.NativePlacement
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.utf8
import kotlin.properties.ReadWriteProperty

// JniActual should have no one or a single context parameter of type [JniEnv] and have a receiver of type [JObject] or
// [JClass] or don't have any receiver. In this case we only need env to use [String.toJString] in implementation.
@JniActual(className = "org.sample.MainKt", methodName = "outerFun")
context(env: JniEnv)
expect fun outerFun(): JString?

// Instead of repeating `className` parameter in every @JniActual we can use @JniActuals.
// `className` will be mapped to the right form, no matter what characters are used in it.
// You can omit it if the qualified name of the annotated object is the same as the JVM class name.
@JniActuals(className = "org.sample.Главный")
object Main {
    // If jni function doesn't need JniEnv or JObject/JClass, you can simply omit them
    fun add(receiver: Int, second: Int): Int {
        return receiver + second
    }

    // We can define JniActuals with the same name, overload will be handled.
    fun add(receiver: Float, second: Float): Float {
        return receiver + second
    }

    // The two next functions only need JniEnv.

    // You should expect that a parameter can be null because the corresponding `@JniExpect` can be called from an
    // environment that does not check for nullability (e.g., from Java code or reflection).
    context(env: JniEnv)
    fun sumArray(receiver: JByteArray?): Long {
        return receiver?.toKArray()?.sumOf { it.toLong() } ?: 0L
    }

    // Despite the thesis above, [receiver] is not nullable here.
    // Nullability checks is supported and if you are sure that corresponding @JniExpect will never pass null here,
    // you can declare it as non-nullable.
    //
    // Also take a look into parameter types:
    // In previous function we used [JByteArray]. This type is exposed by `jni-binding` library and has
    // `@WithJvmType("byte[]")` annotation.
    // Here we use [JFloatRefArray] type alias that has `@WithJvmType("java.lang.Float[]")` annotation.
    //
    // This annotation is required to
    // 1. ensure that the JNI function has the correct signature
    // 2. make code more readable
    // 3. generate bindings if exposing via JNI_OnLoad was chosen.
    context(env: JniEnv)
    fun sumArray2(receiver: JFloatRefArray): Long {
        memScoped {
            var sum = 0.0
            for (floatRef in receiver) {
                sum += floatRef?.floatValue() ?: 0f
            }
            return sum.toLong()
        }
    }

    // This property will internally cache methodId.
    // This is safe, as
    // - JniEnv won't be cached (you can't cache it because it's bind to the thread it was created in)
    // - methodId won't be changed between JNI calls. It still can be changed if its class was reloaded, but in our
    //   case it also means that this JNI lib will also be reloaded, so it's not a problem.
    private var _floatValue: (context(JniEnv, NativePlacement) JFloatRef.() -> Float)? = null

    context(env: JniEnv, autofreeScope: AutofreeScope)
    private val floatValue: context(JniEnv, NativePlacement) JFloatRef.() -> Float
        get() {
            if (_floatValue == null) {
                val floatClass: JClass = findClass("java/lang/Float".utf8)!!
                val methodId = floatClass.methodId("floatValue".utf8, "()F".utf8)!!
                _floatValue = methodId.asMethod<Float>()
                floatClass.deleteLocalRef() // remember to delete local references
            }

            return _floatValue!!
        }

    // this function needs both environment and `Главный` object instance
    context(env: JniEnv)
    fun JObject.editPrivateFinalField(newValue: JString) {
        memScoped {
            // Reading and writing to this value will be reflected in the `privateFinalField` variable on the JVM side.
            // Note that this property cannot be cached, as it internally uses an object local reference and the
            // [JniEnv], but `fieldId` is safe to cache.
            var privateFinalField: String? by mainClass.value
                .staticObjectField(privateFinalFieldId) // returns ReadWriteProperty<Any?, JObject?>
                .asType<JString, _>() // returns ReadWriteProperty<Any?, JString?>
                .asKotlin() // turns `JString?` property into `String?` property

            // Using ReadWriteProperty here is actually overkill, it is for sample purposes only.
            // You better want to use `set*Field` functions directly.
            val newValue = (newValue.toKString() + " (from Native)").toJString()
            mainClass.value.setStaticObjectField(privateFinalFieldId, newValue)
            newValue?.deleteLocalRef()
        }
    }

    // It's boring to write boilerplate code for caching some values that requires [JniEnv] for initialization, like for
    // `floatValue` above so here is a helper function `jniLazy` for it.
    // `mainClass.value` will be initialized on the first access with the provided [JniEnv] as a context parameter.
    // [JniLazy] also exposes [maybeValue] and [isInitialized] properties accessible without [JniEnv].
    // Note that it's safe to cache `JClass` as a weak global ref, because it won't prevent the class from being
    // unloaded.
    // We can guarantee that this reference will point to nonnull value during the lifetime of this JNI library, because
    // if the class is unloaded, it means that all classes and JNI libraries in its class loader is unloaded too.
    // Classes loaded by the Bootstrap, Extension and System class loaders cannot be unloaded, so caching them is also
    // safe, but you still need to create a global reference to access them later.
    // In other cases, caching any JVM references is potentially unsafe.
    val mainClass: JniLazy<JClass> = jniLazy {
        // `modifiedUtf8` can be used to ensure that the string is valid Modified UTF-8.
        // Here, however, it's only for sample purposes.
        val clazz = findClass("org/sample/Главный".modifiedUtf8)
        if (clazz == null) {
            handleJvmException { _: JThrowable ->
                // this error will override the one thrown by `findClass`
                val illegalStateException = findClass("java/lang/IllegalStateException".utf8)!!
                throwNew(illegalStateException, "Cannot find class org.sample.Главный".modifiedUtf8)
                illegalStateException.deleteLocalRef()
            }

            error("Cannot find class org.sample.Главный")
        }

        clazz.localIntoWeakRef()!! // Let's just pretend that it successfully returned a weak ref
    }
}

// This function will be called when the library is loaded.
// Instead of using [jniLazy], you also can initialize JVM things from here.
// Note: vm parameter is optional. You can omit it if you don't need it.
@JniOnLoad
fun onLoad(vm: JavaVM) {
    vm.useEnv(version = JNI.v10) {
        val id = mainClass.value.staticFieldId("privateFinalField".utf8, "Ljava/lang/String;".utf8)
            ?: error("Cannot find property privateFinalField of String type")

        privateFinalFieldId = id
    }
}

lateinit var privateFinalFieldId: JFieldID

// This type alias (at least `@WithJvmType` usage) is required.
// Unlike the previous function, the corresponding `@JniExpect` for this function has a receiver of the `Float[]`
// type.
// We must provide this information to ensure that the JNI and JVM parts have the same parameters/return types.
typealias JFloatRefArray = @WithJvmType("java.lang.Float[]") JObjectArray

// This type alias is not required as it's not used in any method signature,
// but you may want to use it to make the code more readable.
typealias JFloatRef = @WithJvmType("java.lang.Float") JObject

// To provide a nested class, you need to create a separate top-level @JniActuals.
//
// Note that @JniActuals can be applied not only to objects.
// It also can be applied to classes with a constructor which has:
// 1. a single parameter of a type [JavaVM], or
// 2. no parameters at all.
//
// In this case a new instance of the annotated class will be created at `JNI_OnLoad`.
// It doesn't make any practical sense, it's just for a different code organization.
@JniActuals(className = $$"org.sample.Главный$Nested")
class Nested(vm: JavaVM) {
    private lateinit var println: context(JniEnv, NativePlacement) JObject.(JString?) -> Unit

    // use [vm] to initialize JVM things
    init {
        vm.useEnv(JNI.v10) {
            // We use [refFrame] to ensure that all local references created in it are not leaked instead of manual
            // [deleteLocalRef] calls.
            refFrame(10) {
                val printStreamClass = findClass("java/io/PrintStream".utf8)!!
                val printlnId = printStreamClass.methodId("println".utf8, "(Ljava/lang/Object;)V".utf8)!!
                println = printlnId.asMethod<JString?, Unit>()
            }
        }
    }

    // private and protected functions are ignored by @JniActuals and won't be exposed as actuals.
    context(env: JniEnv)
    private fun JObject.println(message: String) {
        memScoped {
            val jstr = message.toJString()
            println(jstr)
            jstr?.deleteLocalRef()
        }
    }

    // Instead of annotation each @JniActual parameter and return type with @WithJvmType, you also can use
    // @WithJvmSignature once on the whole @JniActual.
    @WithJvmSignature(parameterTypes = ["java.io.PrintStream"], returnType = "void")
    context(env: JniEnv)
    fun sayHello(printStream: JObject) {
        printStream.println("Hello from Kotlin/Native!")
    }

    // Assume that we have some resource that we want to release when the library is unloaded.
    private var potentiallyLeakedResource: AutoCloseable = AutoCloseable {
        // some clean up action
    }

    // Similarly to @JniOnLoad, this function will be called when the library is unloaded.
    // This function also may have a [JavaVM] parameter, but it is generally not safe to get a [JniEnv] here, because
    // this function is called in an unknown context - often on a thread that is not attached to the JVM.
    // Attaching the current thread will also fail, yielding `JNI_EDETACHED`.
    //
    // Note that this function is in @JniActuals. Functions annotated with @JniOnLoad and @JniOnUnload inside
    // @JniActuals won't be exposed as actuals.
    @JniOnUnload
    fun cleanup() {
        // Release our resource.
        potentiallyLeakedResource.close()
    }
}