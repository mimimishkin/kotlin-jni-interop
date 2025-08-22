@file:Suppress("NOTHING_TO_INLINE")

package io.github.mimimishkin.jni.binding.accessors

import io.github.mimimishkin.jni.binding.JClass
import io.github.mimimishkin.jni.binding.JFieldID
import io.github.mimimishkin.jni.binding.JObject
import io.github.mimimishkin.jni.binding.JRef
import io.github.mimimishkin.jni.binding.JString
import io.github.mimimishkin.jni.binding.JniEnv
import io.github.mimimishkin.jni.binding._jobject
import io.github.mimimishkin.jni.binding._jstring
import io.github.mimimishkin.jni.binding.c
import io.github.mimimishkin.jni.binding.deleteLocalRef
import io.github.mimimishkin.jni.binding.fieldId
import io.github.mimimishkin.jni.binding.getBooleanField
import io.github.mimimishkin.jni.binding.getByteField
import io.github.mimimishkin.jni.binding.getCharField
import io.github.mimimishkin.jni.binding.getDoubleField
import io.github.mimimishkin.jni.binding.getFloatField
import io.github.mimimishkin.jni.binding.getIntField
import io.github.mimimishkin.jni.binding.getLongField
import io.github.mimimishkin.jni.binding.getObjectField
import io.github.mimimishkin.jni.binding.getShortField
import io.github.mimimishkin.jni.binding.getStaticBooleanField
import io.github.mimimishkin.jni.binding.getStaticByteField
import io.github.mimimishkin.jni.binding.getStaticCharField
import io.github.mimimishkin.jni.binding.getStaticDoubleField
import io.github.mimimishkin.jni.binding.getStaticFloatField
import io.github.mimimishkin.jni.binding.getStaticIntField
import io.github.mimimishkin.jni.binding.getStaticLongField
import io.github.mimimishkin.jni.binding.getStaticObjectField
import io.github.mimimishkin.jni.binding.getStaticShortField
import io.github.mimimishkin.jni.binding.javaClass
import io.github.mimimishkin.jni.binding.setBooleanField
import io.github.mimimishkin.jni.binding.setByteField
import io.github.mimimishkin.jni.binding.setCharField
import io.github.mimimishkin.jni.binding.setDoubleField
import io.github.mimimishkin.jni.binding.setFloatField
import io.github.mimimishkin.jni.binding.setIntField
import io.github.mimimishkin.jni.binding.setLongField
import io.github.mimimishkin.jni.binding.setObjectField
import io.github.mimimishkin.jni.binding.setShortField
import io.github.mimimishkin.jni.binding.setStaticBooleanField
import io.github.mimimishkin.jni.binding.setStaticByteField
import io.github.mimimishkin.jni.binding.setStaticCharField
import io.github.mimimishkin.jni.binding.setStaticDoubleField
import io.github.mimimishkin.jni.binding.setStaticFloatField
import io.github.mimimishkin.jni.binding.setStaticIntField
import io.github.mimimishkin.jni.binding.setStaticLongField
import io.github.mimimishkin.jni.binding.setStaticObjectField
import io.github.mimimishkin.jni.binding.setStaticShortField
import io.github.mimimishkin.jni.binding.staticFieldId
import io.github.mimimishkin.jni.binding.toJString
import io.github.mimimishkin.jni.binding.toKString
import kotlinx.cinterop.AutofreeScope
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.utf8
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Returns a [ReadWriteProperty] for `boolean` field of the object with the specified [fieldId].
 */
context(env: JniEnv)
public fun JObject.booleanField(fieldId: JFieldID): ReadWriteProperty<Any?, Boolean> {
    return object : ReadWriteProperty<Any?, Boolean> {
        override fun getValue(
            thisRef: Any?,
            property: KProperty<*>
        ) = getBooleanField(fieldId)

        override fun setValue(
            thisRef: Any?,
            property: KProperty<*>,
            value: Boolean
        ) = setBooleanField(fieldId, value)
    }
}

/**
 * Returns a [ReadWriteProperty] for `boolean` field of the object with the specified [name].
 *
 * @param name the static field name in the null-terminated modified UTF-8. Use [io.github.mimimishkin.jni.binding.modifiedUtf8] to get it, or if
 * you are sure that your string doesn't have illegal characters you may use optimized [String.utf8].
 *
 * @return a delegatable property of [Boolean] type or `null` if the specified field was not found.
 *
 * @throws NoSuchFieldError if the specified field cannot be found.
 * @throws ExceptionInInitializerError if the class initializer fails due to an exception.
 * @throws OutOfMemoryError if the system runs out of memory.
 */
context(env: JniEnv, autofreeScope: AutofreeScope)
public inline fun JObject.booleanField(name: CValuesRef<ByteVar>): ReadWriteProperty<Any?, Boolean>? {
    return booleanField(javaClass.fieldId(name, "Z".utf8) ?: return null)
}

/**
 * Returns a [ReadWriteProperty] for `byte` field of the object with the specified [fieldId].
 */
context(env: JniEnv)
public fun JObject.byteField(fieldId: JFieldID): ReadWriteProperty<Any?, Byte> {
    return object : ReadWriteProperty<Any?, Byte> {
        override fun getValue(
            thisRef: Any?,
            property: KProperty<*>
        ) = getByteField(fieldId)

        override fun setValue(
            thisRef: Any?,
            property: KProperty<*>,
            value: Byte
        ) = setByteField(fieldId, value)
    }
}

/**
 * Returns a [ReadWriteProperty] for `byte` field of the object with the specified [name].
 *
 * @param name the static field name in the null-terminated modified UTF-8. Use [io.github.mimimishkin.jni.binding.modifiedUtf8] to get it, or if
 * you are sure that your string doesn't have illegal characters you may use optimized [String.utf8].
 *
 * @return a delegatable property of [Byte] type or `null` if the specified field was not found.
 *
 * @throws NoSuchFieldError if the specified field cannot be found.
 * @throws ExceptionInInitializerError if the class initializer fails due to an exception.
 * @throws OutOfMemoryError if the system runs out of memory.
 */
context(env: JniEnv, autofreeScope: AutofreeScope)
public inline fun JObject.byteField(name: CValuesRef<ByteVar>): ReadWriteProperty<Any?, Byte>? {
    return byteField(javaClass.fieldId(name, "B".utf8) ?: return null)
}

/**
 * Returns a [ReadWriteProperty] for `char` field of the object with the specified [fieldId].
 */
context(env: JniEnv)
public fun JObject.charField(fieldId: JFieldID): ReadWriteProperty<Any?, Char> {
    return object : ReadWriteProperty<Any?, Char> {
        override fun getValue(
            thisRef: Any?,
            property: KProperty<*>
        ) = getCharField(fieldId)

        override fun setValue(
            thisRef: Any?,
            property: KProperty<*>,
            value: Char
        ) = setCharField(fieldId, value)
    }
}

/**
 * Returns a [ReadWriteProperty] for `char` field of the object with the specified [name].
 *
 * @param name the static field name in the null-terminated modified UTF-8. Use [io.github.mimimishkin.jni.binding.modifiedUtf8] to get it, or if
 * you are sure that your string doesn't have illegal characters you may use optimized [String.utf8].
 *
 * @return a delegatable property of [Char] type or `null` if the specified field was not found.
 *
 * @throws NoSuchFieldError if the specified field cannot be found.
 * @throws ExceptionInInitializerError if the class initializer fails due to an exception.
 * @throws OutOfMemoryError if the system runs out of memory.
 */
context(env: JniEnv, autofreeScope: AutofreeScope)
public inline fun JObject.charField(name: CValuesRef<ByteVar>): ReadWriteProperty<Any?, Char>? {
    return charField(javaClass.fieldId(name, "C".utf8) ?: return null)
}

/**
 * Returns a [ReadWriteProperty] for `short` field of the object with the specified [fieldId].
 */
context(env: JniEnv)
public fun JObject.shortField(fieldId: JFieldID): ReadWriteProperty<Any?, Short> {
    return object : ReadWriteProperty<Any?, Short> {
        override fun getValue(
            thisRef: Any?,
            property: KProperty<*>
        ) = getShortField(fieldId)

        override fun setValue(
            thisRef: Any?,
            property: KProperty<*>,
            value: Short
        ) = setShortField(fieldId, value)
    }
}

/**
 * Returns a [ReadWriteProperty] for `short` field of the object with the specified [name].
 *
 * @param name the static field name in the null-terminated modified UTF-8. Use [io.github.mimimishkin.jni.binding.modifiedUtf8] to get it, or if
 * you are sure that your string doesn't have illegal characters you may use optimized [String.utf8].
 *
 * @return a delegatable property of [Short] type or `null` if the specified field was not found.
 *
 * @throws NoSuchFieldError if the specified field cannot be found.
 * @throws ExceptionInInitializerError if the class initializer fails due to an exception.
 * @throws OutOfMemoryError if the system runs out of memory.
 */
context(env: JniEnv, autofreeScope: AutofreeScope)
public inline fun JObject.shortField(name: CValuesRef<ByteVar>): ReadWriteProperty<Any?, Short>? {
    return shortField(javaClass.fieldId(name, "S".utf8) ?: return null)
}

/**
 * Returns a [ReadWriteProperty] for `int` field of the object with the specified [fieldId].
 */
context(env: JniEnv)
public fun JObject.intField(fieldId: JFieldID): ReadWriteProperty<Any?, Int> {
    return object : ReadWriteProperty<Any?, Int> {
        override fun getValue(
            thisRef: Any?,
            property: KProperty<*>
        ) = getIntField(fieldId)

        override fun setValue(
            thisRef: Any?,
            property: KProperty<*>,
            value: Int
        ) = setIntField(fieldId, value)
    }
}

/**
 * Returns a [ReadWriteProperty] for `int` field of the object with the specified [name].
 *
 * @param name the static field name in the null-terminated modified UTF-8. Use [io.github.mimimishkin.jni.binding.modifiedUtf8] to get it, or if
 * you are sure that your string doesn't have illegal characters you may use optimized [String.utf8].
 *
 * @return a delegatable property of [Int] type or `null` if the specified field was not found.
 *
 * @throws NoSuchFieldError if the specified field cannot be found.
 * @throws ExceptionInInitializerError if the class initializer fails due to an exception.
 * @throws OutOfMemoryError if the system runs out of memory.
 */
context(env: JniEnv, autofreeScope: AutofreeScope)
public inline fun JObject.intField(name: CValuesRef<ByteVar>): ReadWriteProperty<Any?, Int>? {
    return intField(javaClass.fieldId(name, "I".utf8) ?: return null)
}

/**
 * Returns a [ReadWriteProperty] for `long` field of the object with the specified [fieldId].
 */
context(env: JniEnv)
public fun JObject.longField(fieldId: JFieldID): ReadWriteProperty<Any?, Long> {
    return object : ReadWriteProperty<Any?, Long> {
        override fun getValue(
            thisRef: Any?,
            property: KProperty<*>
        ) = getLongField(fieldId)

        override fun setValue(
            thisRef: Any?,
            property: KProperty<*>,
            value: Long
        ) = setLongField(fieldId, value)
    }
}

/**
 * Returns a [ReadWriteProperty] for `long` field of the object with the specified [name].
 *
 * @param name the static field name in the null-terminated modified UTF-8. Use [io.github.mimimishkin.jni.binding.modifiedUtf8] to get it, or if
 * you are sure that your string doesn't have illegal characters you may use optimized [String.utf8].
 *
 * @return a delegatable property of [Long] type or `null` if the specified field was not found.
 *
 * @throws NoSuchFieldError if the specified field cannot be found.
 * @throws ExceptionInInitializerError if the class initializer fails due to an exception.
 * @throws OutOfMemoryError if the system runs out of memory.
 */
context(env: JniEnv, autofreeScope: AutofreeScope)
public inline fun JObject.longField(name: CValuesRef<ByteVar>): ReadWriteProperty<Any?, Long>? {
    return longField(javaClass.fieldId(name, "J".utf8) ?: return null)
}

/**
 * Returns a [ReadWriteProperty] for `float` field of the object with the specified [fieldId].
 */
context(env: JniEnv)
public fun JObject.floatField(fieldId: JFieldID): ReadWriteProperty<Any?, Float> {
    return object : ReadWriteProperty<Any?, Float> {
        override fun getValue(
            thisRef: Any?,
            property: KProperty<*>
        ) = getFloatField(fieldId)

        override fun setValue(
            thisRef: Any?,
            property: KProperty<*>,
            value: Float
        ) = setFloatField(fieldId, value)
    }
}

/**
 * Returns a [ReadWriteProperty] for `float` field of the object with the specified [name].
 *
 * @param name the static field name in the null-terminated modified UTF-8. Use [io.github.mimimishkin.jni.binding.modifiedUtf8] to get it, or if
 * you are sure that your string doesn't have illegal characters you may use optimized [String.utf8].
 *
 * @return a delegatable property of [Float] type or `null` if the specified field was not found.
 *
 * @throws NoSuchFieldError if the specified field cannot be found.
 * @throws ExceptionInInitializerError if the class initializer fails due to an exception.
 * @throws OutOfMemoryError if the system runs out of memory.
 */
context(env: JniEnv, autofreeScope: AutofreeScope)
public inline fun JObject.floatField(name: CValuesRef<ByteVar>): ReadWriteProperty<Any?, Float>? {
    return floatField(javaClass.fieldId(name, "F".utf8) ?: return null)
}

/**
 * Returns a [ReadWriteProperty] for `double` field of the object with the specified [fieldId].
 */
context(env: JniEnv)
public fun JObject.doubleField(fieldId: JFieldID): ReadWriteProperty<Any?, Double> {
    return object : ReadWriteProperty<Any?, Double> {
        override fun getValue(
            thisRef: Any?,
            property: KProperty<*>
        ) = getDoubleField(fieldId)

        override fun setValue(
            thisRef: Any?,
            property: KProperty<*>,
            value: Double
        ) = setDoubleField(fieldId, value)
    }
}

/**
 * Returns a [ReadWriteProperty] for `double` field of the object with the specified [name].
 *
 * @param name the static field name in the null-terminated modified UTF-8. Use [io.github.mimimishkin.jni.binding.modifiedUtf8] to get it, or if
 * you are sure that your string doesn't have illegal characters you may use optimized [String.utf8].
 *
 * @return a delegatable property of [Double] type or `null` if the specified field was not found.
 *
 * @throws NoSuchFieldError if the specified field cannot be found.
 * @throws ExceptionInInitializerError if the class initializer fails due to an exception.
 * @throws OutOfMemoryError if the system runs out of memory.
 */
context(env: JniEnv, autofreeScope: AutofreeScope)
public inline fun JObject.doubleField(name: CValuesRef<ByteVar>): ReadWriteProperty<Any?, Double>? {
    return doubleField(javaClass.fieldId(name, "D".utf8) ?: return null)
}

/**
 * Returns a [ReadWriteProperty] for `Object` field of the object with the specified [fieldId].
 */
context(env: JniEnv)
public fun JObject.objectField(fieldId: JFieldID): ReadWriteProperty<Any?, JObject?> {
    return object : ReadWriteProperty<Any?, JObject?> {
        override fun getValue(
            thisRef: Any?,
            property: KProperty<*>
        ) = getObjectField(fieldId)

        override fun setValue(
            thisRef: Any?,
            property: KProperty<*>,
            value: JObject?
        ) = setObjectField(fieldId, value)
    }
}

/**
 * Returns a [ReadWriteProperty] for `Object` field of the object with the specified [name] and [sig].
 *
 * @param name the static field name in the null-terminated modified UTF-8.
 * @param sig the field signature in the null-terminated modified UTF-8. Use [io.github.mimimishkin.jni.binding.modifiedUtf8] to get it, or if you
 * are sure that your string doesn't have illegal characters you may use optimized [String.utf8].
 *
 * @return a delegatable property of [JObject]`?` type or `null` if the specified field was not found.
 *
 * @throws NoSuchFieldError if the specified field cannot be found.
 * @throws ExceptionInInitializerError if the class initializer fails due to an exception.
 * @throws OutOfMemoryError if the system runs out of memory.
 */
context(env: JniEnv, autofreeScope: AutofreeScope)
public inline fun JObject.objectField(name: CValuesRef<ByteVar>, sig: CValuesRef<ByteVar>): ReadWriteProperty<Any?, JObject?>? {
    return objectField(javaClass.fieldId(name, sig) ?: return null)
}

/**
 * Casts to any [JObject] descendant.
 */
public inline fun <T : JRef<O>, O : _jobject> ReadWriteProperty<Any?, JObject?>.asType(): ReadWriteProperty<Any?, T?> {
    @Suppress("UNCHECKED_CAST")
    return this as ReadWriteProperty<Any?, T?>
}

/**
 * Alias for [objectField] with `sig = "Ljava/lang/String;"`.
 */
context(env: JniEnv, autofreeScope: AutofreeScope)
public inline fun JObject.stringField(name: CValuesRef<ByteVar>): ReadWriteProperty<Any?, JString?>? {
    return objectField(name, "Ljava/lang/String;".utf8)?.asType()
}

/**
 * Unifies usage of `*Field()` functions.
 *
 * Note: [T] must be not-null if it's a primitive type, otherwise it must be nullable.
 */
context(env: JniEnv)
public inline fun <reified T> JObject.field(fieldId: JFieldID): ReadWriteProperty<Any?, T> {
    @Suppress("UNCHECKED_CAST")
    return when (T::class) {
        Boolean::class -> booleanField(fieldId)
        Byte::class -> byteField(fieldId)
        Char::class -> charField(fieldId)
        Short::class -> shortField(fieldId)
        Int::class -> intField(fieldId)
        Long::class -> longField(fieldId)
        Float::class -> floatField(fieldId)
        Double::class -> doubleField(fieldId)
        else -> objectField(fieldId)
    } as ReadWriteProperty<Any?, T>
}

/**
 * Returns a [ReadWriteProperty] for static `boolean` field of the class with the specified [fieldId].
 */
context(env: JniEnv)
public fun JClass.staticBooleanField(fieldId: JFieldID): ReadWriteProperty<Any?, Boolean> {
    return object : ReadWriteProperty<Any?, Boolean> {
        override fun getValue(
            thisRef: Any?,
            property: KProperty<*>
        ) = getStaticBooleanField(fieldId)

        override fun setValue(
            thisRef: Any?,
            property: KProperty<*>,
            value: Boolean
        ) = setStaticBooleanField(fieldId, value)
    }
}

/**
 * Returns a [ReadWriteProperty] for static `boolean` field of the class with the specified [name].
 *
 * @param name the static field name in the null-terminated modified UTF-8. Use [io.github.mimimishkin.jni.binding.modifiedUtf8] to get it, or if
 * you are sure that your string doesn't have illegal characters you may use optimized [String.utf8].
 *
 * @return a delegatable property of [Boolean] type or `null` if the specified field was not found.
 *
 * @throws NoSuchFieldError if the specified field cannot be found.
 * @throws ExceptionInInitializerError if the class initializer fails due to an exception.
 * @throws OutOfMemoryError if the system runs out of memory.
 */
context(env: JniEnv, autofreeScope: AutofreeScope)
public inline fun JClass.staticBooleanField(name: CValuesRef<ByteVar>): ReadWriteProperty<Any?, Boolean>? {
    return staticBooleanField(this.staticFieldId(name, "Z".utf8) ?: return null)
}

/**
 * Returns a [ReadWriteProperty] for static `byte` field of the class with the specified [fieldId].
 */
context(env: JniEnv)
public fun JClass.staticByteField(fieldId: JFieldID): ReadWriteProperty<Any?, Byte> {
    return object : ReadWriteProperty<Any?, Byte> {
        override fun getValue(
            thisRef: Any?,
            property: KProperty<*>
        ) = getStaticByteField(fieldId)

        override fun setValue(
            thisRef: Any?,
            property: KProperty<*>,
            value: Byte
        ) = setStaticByteField(fieldId, value)
    }
}

/**
 * Returns a [ReadWriteProperty] for static `byte` field of the class with the specified [name].
 *
 * @param name the static field name in the null-terminated modified UTF-8. Use [io.github.mimimishkin.jni.binding.modifiedUtf8] to get it, or if
 * you are sure that your string doesn't have illegal characters you may use optimized [String.utf8].
 *
 * @return a delegatable property of [Byte] type or `null` if the specified field was not found.
 *
 * @throws NoSuchFieldError if the specified field cannot be found.
 * @throws ExceptionInInitializerError if the class initializer fails due to an exception.
 * @throws OutOfMemoryError if the system runs out of memory.
 */
context(env: JniEnv, autofreeScope: AutofreeScope)
public inline fun JClass.staticByteField(name: CValuesRef<ByteVar>): ReadWriteProperty<Any?, Byte>? {
    return staticByteField(this.staticFieldId(name, "B".utf8) ?: return null)
}

/**
 * Returns a [ReadWriteProperty] for static `char` field of the class with the specified [fieldId].
 */
context(env: JniEnv)
public fun JClass.staticCharField(fieldId: JFieldID): ReadWriteProperty<Any?, Char> {
    return object : ReadWriteProperty<Any?, Char> {
        override fun getValue(
            thisRef: Any?,
            property: KProperty<*>
        ) = getStaticCharField(fieldId)

        override fun setValue(
            thisRef: Any?,
            property: KProperty<*>,
            value: Char
        ) = setStaticCharField(fieldId, value)
    }
}

/**
 * Returns a [ReadWriteProperty] for static `char` field of the class with the specified [name].
 *
 * @param name the static field name in the null-terminated modified UTF-8. Use [io.github.mimimishkin.jni.binding.modifiedUtf8] to get it, or if
 * you are sure that your string doesn't have illegal characters you may use optimized [String.utf8].
 *
 * @return a delegatable property of [Char] type or `null` if the specified field was not found.
 *
 * @throws NoSuchFieldError if the specified field cannot be found.
 * @throws ExceptionInInitializerError if the class initializer fails due to an exception.
 * @throws OutOfMemoryError if the system runs out of memory.
 */
context(env: JniEnv, autofreeScope: AutofreeScope)
public inline fun JClass.staticCharField(name: CValuesRef<ByteVar>): ReadWriteProperty<Any?, Char>? {
    return staticCharField(this.staticFieldId(name, "C".utf8) ?: return null)
}

/**
 * Returns a [ReadWriteProperty] for static `short` field of the class with the specified [fieldId].
 */
context(env: JniEnv)
public fun JClass.staticShortField(fieldId: JFieldID): ReadWriteProperty<Any?, Short> {
    return object : ReadWriteProperty<Any?, Short> {
        override fun getValue(
            thisRef: Any?,
            property: KProperty<*>
        ) = getStaticShortField(fieldId)

        override fun setValue(
            thisRef: Any?,
            property: KProperty<*>,
            value: Short
        ) = setStaticShortField(fieldId, value)
    }
}

/**
 * Returns a [ReadWriteProperty] for static `short` field of the class with the specified [name].
 *
 * @param name the static field name in the null-terminated modified UTF-8. Use [io.github.mimimishkin.jni.binding.modifiedUtf8] to get it, or if
 * you are sure that your string doesn't have illegal characters you may use optimized [String.utf8].
 *
 * @return a delegatable property of [Short] type or `null` if the specified field was not found.
 *
 * @throws NoSuchFieldError if the specified field cannot be found.
 * @throws ExceptionInInitializerError if the class initializer fails due to an exception.
 * @throws OutOfMemoryError if the system runs out of memory.
 */
context(env: JniEnv, autofreeScope: AutofreeScope)
public inline fun JClass.staticShortField(name: CValuesRef<ByteVar>): ReadWriteProperty<Any?, Short>? {
    return staticShortField(this.staticFieldId(name, "S".utf8) ?: return null)
}

/**
 * Returns a [ReadWriteProperty] for static `int` field of the class with the specified [fieldId].
 */
context(env: JniEnv)
public fun JClass.staticIntField(fieldId: JFieldID): ReadWriteProperty<Any?, Int> {
    return object : ReadWriteProperty<Any?, Int> {
        override fun getValue(
            thisRef: Any?,
            property: KProperty<*>
        ) = getStaticIntField(fieldId)

        override fun setValue(
            thisRef: Any?,
            property: KProperty<*>,
            value: Int
        ) = setStaticIntField(fieldId, value)
    }
}

/**
 * Returns a [ReadWriteProperty] for static `int` field of the class with the specified [name].
 *
 * @param name the static field name in the null-terminated modified UTF-8. Use [io.github.mimimishkin.jni.binding.modifiedUtf8] to get it, or if
 * you are sure that your string doesn't have illegal characters you may use optimized [String.utf8].
 *
 * @return a delegatable property of [Int] type or `null` if the specified field was not found.
 *
 * @throws NoSuchFieldError if the specified field cannot be found.
 * @throws ExceptionInInitializerError if the class initializer fails due to an exception.
 * @throws OutOfMemoryError if the system runs out of memory.
 */
context(env: JniEnv, autofreeScope: AutofreeScope)
public inline fun JClass.staticIntField(name: CValuesRef<ByteVar>): ReadWriteProperty<Any?, Int>? {
    return staticIntField(this.staticFieldId(name, "I".utf8) ?: return null)
}

/**
 * Returns a [ReadWriteProperty] for static `long` field of the class with the specified [fieldId].
 */
context(env: JniEnv)
public fun JClass.staticLongField(fieldId: JFieldID): ReadWriteProperty<Any?, Long> {
    return object : ReadWriteProperty<Any?, Long> {
        override fun getValue(
            thisRef: Any?,
            property: KProperty<*>
        ) = getStaticLongField(fieldId)

        override fun setValue(
            thisRef: Any?,
            property: KProperty<*>,
            value: Long
        ) = setStaticLongField(fieldId, value)
    }
}

/**
 * Returns a [ReadWriteProperty] for static `long` field of the class with the specified [name].
 *
 * @param name the static field name in the null-terminated modified UTF-8. Use [io.github.mimimishkin.jni.binding.modifiedUtf8] to get it, or if
 * you are sure that your string doesn't have illegal characters you may use optimized [String.utf8].
 *
 * @return a delegatable property of [Long] type or `null` if the specified field was not found.
 *
 * @throws NoSuchFieldError if the specified field cannot be found.
 * @throws ExceptionInInitializerError if the class initializer fails due to an exception.
 * @throws OutOfMemoryError if the system runs out of memory.
 */
context(env: JniEnv, autofreeScope: AutofreeScope)
public inline fun JClass.staticLongField(name: CValuesRef<ByteVar>): ReadWriteProperty<Any?, Long>? {
    return staticLongField(this.staticFieldId(name, "J".utf8) ?: return null)
}

/**
 * Returns a [ReadWriteProperty] for static `float` field of the class with the specified [fieldId].
 */
context(env: JniEnv)
public fun JClass.staticFloatField(fieldId: JFieldID): ReadWriteProperty<Any?, Float> {
    return object : ReadWriteProperty<Any?, Float> {
        override fun getValue(
            thisRef: Any?,
            property: KProperty<*>
        ) = getStaticFloatField(fieldId)

        override fun setValue(
            thisRef: Any?,
            property: KProperty<*>,
            value: Float
        ) = setStaticFloatField(fieldId, value)
    }
}

/**
 * Returns a [ReadWriteProperty] for static `float` field of the class with the specified [name].
 *
 * @param name the static field name in the null-terminated modified UTF-8. Use [io.github.mimimishkin.jni.binding.modifiedUtf8] to get it, or if
 * you are sure that your string doesn't have illegal characters you may use optimized [String.utf8].
 *
 * @return a delegatable property of [Float] type or `null` if the specified field was not found.
 *
 * @throws NoSuchFieldError if the specified field cannot be found.
 * @throws ExceptionInInitializerError if the class initializer fails due to an exception.
 * @throws OutOfMemoryError if the system runs out of memory.
 */
context(env: JniEnv, autofreeScope: AutofreeScope)
public inline fun JClass.staticFloatField(name: CValuesRef<ByteVar>): ReadWriteProperty<Any?, Float>? {
    return staticFloatField(this.staticFieldId(name, "F".utf8) ?: return null)
}

/**
 * Returns a [ReadWriteProperty] for static `double` field of the class with the specified [fieldId].
 */
context(env: JniEnv)
public fun JClass.staticDoubleField(fieldId: JFieldID): ReadWriteProperty<Any?, Double> {
    return object : ReadWriteProperty<Any?, Double> {
        override fun getValue(
            thisRef: Any?,
            property: KProperty<*>
        ) = getStaticDoubleField(fieldId)

        override fun setValue(
            thisRef: Any?,
            property: KProperty<*>,
            value: Double
        ) = setStaticDoubleField(fieldId, value)
    }
}

/**
 * Returns a [ReadWriteProperty] for static `double` field of the class with the specified [name].
 *
 * @param name the static field name in the null-terminated modified UTF-8. Use [io.github.mimimishkin.jni.binding.modifiedUtf8] to get it, or if
 * you are sure that your string doesn't have illegal characters you may use optimized [String.utf8].
 *
 * @return a delegatable property of [Double] type or `null` if the specified field was not found.
 *
 * @throws NoSuchFieldError if the specified field cannot be found.
 * @throws ExceptionInInitializerError if the class initializer fails due to an exception.
 * @throws OutOfMemoryError if the system runs out of memory.
 */
context(env: JniEnv, autofreeScope: AutofreeScope)
public inline fun JClass.staticDoubleField(name: CValuesRef<ByteVar>): ReadWriteProperty<Any?, Double>? {
    return staticDoubleField(this.staticFieldId(name, "D".utf8) ?: return null)
}

/**
 * Returns a [ReadWriteProperty] for static `Object` field of the class with the specified [fieldId].
 */
context(env: JniEnv)
public fun JClass.staticObjectField(fieldId: JFieldID): ReadWriteProperty<Any?, JObject?> {
    return object : ReadWriteProperty<Any?, JObject?> {
        override fun getValue(
            thisRef: Any?,
            property: KProperty<*>
        ) = getStaticObjectField(fieldId)

        override fun setValue(
            thisRef: Any?,
            property: KProperty<*>,
            value: JObject?
        ) = setStaticObjectField(fieldId, value)
    }
}

/**
 * Returns a [ReadWriteProperty] for static `Object` field of the class with the specified [name] and [sig].
 *
 * @param name the static field name in the null-terminated modified UTF-8.
 * @param sig the field signature in the null-terminated modified UTF-8. Use [io.github.mimimishkin.jni.binding.modifiedUtf8] to get it, or if you
 * are sure that your string doesn't have illegal characters you may use optimized [String.utf8].
 *
 * @return a delegatable property of [JObject]`?` type or `null` if the specified field was not found.
 *
 * @throws NoSuchFieldError if the specified field cannot be found.
 * @throws ExceptionInInitializerError if the class initializer fails due to an exception.
 * @throws OutOfMemoryError if the system runs out of memory.
 */
context(env: JniEnv, autofreeScope: AutofreeScope)
public inline fun JClass.staticObjectField(name: CValuesRef<ByteVar>, sig: CValuesRef<ByteVar>): ReadWriteProperty<Any?, JObject?>? {
    return staticObjectField(this.staticFieldId(name, sig) ?: return null)
}

/**
 * Alias for [staticObjectField] with `sig = "Ljava/lang/String;"`.
 */
context(env: JniEnv, autofreeScope: AutofreeScope)
public inline fun JClass.staticStringField(name: CValuesRef<ByteVar>): ReadWriteProperty<Any?, JString?>? {
    return staticObjectField(name, "Ljava/lang/String;".utf8)?.asType()
}

/**
 * Simplifies usage of [stringField] and [staticStringField] allowing to pass Kotlin strings directly.
 *
 * Note: conversion may fail due to [OutOfMemoryError].
 */
context(env: JniEnv, autofreeScope: AutofreeScope)
public fun ReadWriteProperty<Any?, JString?>.asKotlin(): ReadWriteProperty<Any?, String?> {
    return object : ReadWriteProperty<Any?, String?> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): String? {
            val jString = this@asKotlin.getValue(thisRef, property)
            return jString?.toKString()?.also { jString.deleteLocalRef() }
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String?) {
            val jString = value?.toJString()
            this@asKotlin.setValue(thisRef, property, jString)
            jString?.deleteLocalRef()
        }
    }
}

/**
 * Unifies usage of `static*Field()` functions.
 *
 * Note: [T] must be not-null if it's a primitive type, otherwise it must be nullable.
 */
context(env: JniEnv)
public inline fun <reified T> JClass.staticField(fieldId: JFieldID): ReadWriteProperty<Any?, T> {
    @Suppress("UNCHECKED_CAST")
    return when (T::class) {
        Boolean::class -> staticBooleanField(fieldId)
        Byte::class -> staticByteField(fieldId)
        Char::class -> staticCharField(fieldId)
        Short::class -> staticShortField(fieldId)
        Int::class -> staticIntField(fieldId)
        Long::class -> staticLongField(fieldId)
        Float::class -> staticFloatField(fieldId)
        Double::class -> staticDoubleField(fieldId)
        else -> staticObjectField(fieldId)
    } as ReadWriteProperty<Any?, T>
}