@file:Suppress("NOTHING_TO_INLINE")

package io.github.mimimishkin.jni.binding.accessors

import io.github.mimimishkin.jni.binding.ApplyChangesMode
import io.github.mimimishkin.jni.binding.JBooleanArray
import io.github.mimimishkin.jni.binding.JByteArray
import io.github.mimimishkin.jni.binding.JCharArray
import io.github.mimimishkin.jni.binding.JDoubleArray
import io.github.mimimishkin.jni.binding.JFloatArray
import io.github.mimimishkin.jni.binding.JIntArray
import io.github.mimimishkin.jni.binding.JLongArray
import io.github.mimimishkin.jni.binding.JPrimitiveArray
import io.github.mimimishkin.jni.binding.JShortArray
import io.github.mimimishkin.jni.binding.JString
import io.github.mimimishkin.jni.binding.JniEnv
import io.github.mimimishkin.jni.binding.getChars
import io.github.mimimishkin.jni.binding.getCharsCritical
import io.github.mimimishkin.jni.binding.getElements
import io.github.mimimishkin.jni.binding.getElementsCritical
import io.github.mimimishkin.jni.binding.getUTFChars
import io.github.mimimishkin.jni.binding.releaseChars
import io.github.mimimishkin.jni.binding.releaseCharsCritical
import io.github.mimimishkin.jni.binding.releaseElements
import io.github.mimimishkin.jni.binding.releaseElementsCritical
import io.github.mimimishkin.jni.binding.releaseUTFChars
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CArrayPointer
import kotlinx.cinterop.CPrimitiveVar
import kotlinx.cinterop.DoubleVar
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.NativePlacement
import kotlinx.cinterop.ShortVar
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.UShortVar

/**
 * Alias for safely retrieving a string's chars via [getChars] and releasing it with [releaseChars].
 *
 * @param onError code to execute if [getChars] fails.
 * @param block code to execute with the retrieved chars and copy marker.
 */
context(env: JniEnv, placement: NativePlacement)
public inline fun JString.modifyChars(onError: () -> Unit, block: (chars: CArrayPointer<UShortVar>, isCopy: Boolean) -> Unit) {
    val (chars, isCopy) = getChars() ?: return onError()
    try {
        return block(chars, isCopy)
    } finally {
        releaseChars(chars)
    }
}

/**
 * Alias for safely retrieving a string's bytes via [getUTFChars] and releasing it with [releaseUTFChars].
 *
 * @param onError code to execute if [getUTFChars] fails.
 * @param block code to execute with the retrieved bytes and copy marker.
 */
context(env: JniEnv, placement: NativePlacement)
public inline fun JString.modifyUTFChars(onError: () -> Unit, block: (bytes: CArrayPointer<ByteVar>, isCopy: Boolean) -> Unit) {
    val (bytes, isCopy) = getUTFChars() ?: return onError()
    try {
        return block(bytes, isCopy)
    } finally {
        releaseUTFChars(bytes)
    }
}

/**
 * Alias for safely retrieving a string's chars via [getCharsCritical] and [releaseCharsCritical].
 *
 * @param onError code to execute if [getCharsCritical] fails.
 * @param block code to execute with the retrieved chars and copy marker.
 *
 * @since JDK/JRE 1.2
 */
context(env: JniEnv, placement: NativePlacement)
public inline fun JString.modifyCritical(onError: () -> Unit, block: (chars: CArrayPointer<UShortVar>, isCopy: Boolean) -> Unit) {
    val (chars, isCopy) = getCharsCritical() ?: return onError()
    try {
        return block(chars, isCopy)
    } finally {
        releaseCharsCritical(chars)
    }
}

/**
 * Special type that exposes methods [commit], [finalize] and [abort] to simplify working with arrays.
 * Is used in [modify] functions.
 */
public typealias ModifyingArrayScope = (ApplyChangesMode) -> Unit

/**
 * Alias for releasing an array with [ApplyChangesMode.Commit].
 */
public inline fun ModifyingArrayScope.commit(): Unit = this(ApplyChangesMode.Commit)

/**
 * Alias for releasing an array with [ApplyChangesMode.Commit].
 */
public inline fun ModifyingArrayScope.finalize(): Unit = this(ApplyChangesMode.FinalCommit)

/**
 * Alias for releasing an array with [ApplyChangesMode.Commit].
 */
public inline fun ModifyingArrayScope.abort(): Unit = this(ApplyChangesMode.Abort)

/**
 * Alias for working with `boolean` array's elements via [getElements] and releasing it with
 * [releaseElements].
 *
 * You can access [commit], [finalize] and [abort] methods inside [block].
 *
 * @param onError code to execute if [getElements] fails.
 * @param block code to execute with the retrieved C array and copy marker.
 */
context(env: JniEnv, placement: NativePlacement)
public inline fun JBooleanArray.modify(onError: () -> Unit, block: ModifyingArrayScope.(carray: CArrayPointer<UByteVar>, isCopy: Boolean) -> Unit) {
    val (carray, isCopy) = getElements() ?: return onError()
    return block({ mode -> releaseElements(carray, mode) }, carray, isCopy)
}

/**
 * Alias for working with `byte` array's elements via [JByteArray.getElements] and releasing it with
 * [JByteArray.releaseElements].
 *
 * You can access [commit], [finalize] and [abort] methods inside [block].
 *
 * @param onError code to execute if [JByteArray.getElements] fails.
 * @param block code to execute with the retrieved C array and copy marker.
 */
context(env: JniEnv, placement: NativePlacement)
public inline fun JByteArray.modify(onError: () -> Unit, block: ModifyingArrayScope.(carray: CArrayPointer<ByteVar>, isCopy: Boolean) -> Unit) {
    val (carray, isCopy) = getElements() ?: return onError()
    return block({ mode -> releaseElements(carray, mode) }, carray, isCopy)
}

/**
 * Alias for working with `char` array's elements via [JCharArray.getElements] and releasing it with
 * [JCharArray.releaseElements].
 *
 * You can access [commit], [finalize] and [abort] methods inside [block].
 *
 * @param onError code to execute if [JCharArray.getElements] fails.
 * @param block code to execute with the retrieved C array and copy marker.
 */
context(env: JniEnv, placement: NativePlacement)
public inline fun JCharArray.modify(onError: () -> Unit, block: ModifyingArrayScope.(carray: CArrayPointer<UShortVar>, isCopy: Boolean) -> Unit) {
    val (carray, isCopy) = getElements() ?: return onError()
    return block({ mode -> releaseElements(carray, mode) }, carray, isCopy)
}

/**
 * Alias for working with  `short` array's elements via [JShortArray.getElements] and releasing it with
 * [JShortArray.releaseElements].
 *
 * You can access [commit], [finalize] and [abort] methods inside [block].
 *
 * @param onError code to execute if [JShortArray.getElements] fails.
 * @param block code to execute with the retrieved C array and copy marker.
 */
context(env: JniEnv, placement: NativePlacement)
public inline fun JShortArray.modify(onError: () -> Unit, block: ModifyingArrayScope.(carray: CArrayPointer<ShortVar>, isCopy: Boolean) -> Unit) {
    val (carray, isCopy) = getElements() ?: return onError()
    return block({ mode -> releaseElements(carray, mode) }, carray, isCopy)
}

/**
 * Alias for working with  `int` array's elements via [JIntArray.getElements] and releasing it with
 * [JIntArray.releaseElements].
 *
 * You can access [commit], [finalize] and [abort] methods inside [block].
 *
 * @param onError code to execute if [JIntArray.getElements] fails.
 * @param block code to execute with the retrieved C array and copy marker.
 */
context(env: JniEnv, placement: NativePlacement)
public inline fun JIntArray.modify(onError: () -> Unit, block: ModifyingArrayScope.(carray: CArrayPointer<IntVar>, isCopy: Boolean) -> Unit) {
    val (carray, isCopy) = getElements() ?: return onError()
    return block({ mode -> releaseElements(carray, mode) }, carray, isCopy)
}

/**
 * Alias for working with `long` array's elements via [JLongArray.getElements] and releasing it with
 * [JLongArray.releaseElements].
 *
 * You can access [commit], [finalize] and [abort] methods inside [block].
 *
 * @param onError code to execute if [JLongArray.getElements] fails.
 * @param block code to execute with the retrieved C array and copy marker.
 */
context(env: JniEnv, placement: NativePlacement)
public inline fun JLongArray.modify(onError: () -> Unit, block: ModifyingArrayScope.(carray: CArrayPointer<LongVar>, isCopy: Boolean) -> Unit) {
    val (carray, isCopy) = getElements() ?: return onError()
    return block({ mode -> releaseElements(carray, mode) }, carray, isCopy)
}

/**
 * Alias for working with `float` array's elements via [JFloatArray.getElements] and releasing it with
 * [JFloatArray.releaseElements].
 *
 * You can access [commit], [finalize] and [abort] methods inside [block].
 *
 * @param onError code to execute if [JFloatArray.getElements] fails.
 * @param block code to execute with the retrieved C array and copy marker.
 */
context(env: JniEnv, placement: NativePlacement)
public inline fun JFloatArray.modify(onError: () -> Unit, block: ModifyingArrayScope.(carray: CArrayPointer<FloatVar>, isCopy: Boolean) -> Unit) {
    val (carray, isCopy) = getElements() ?: return onError()
    return block({ mode -> releaseElements(carray, mode) }, carray, isCopy)
}

/**
 * Alias for working with `double` array's elements via [JDoubleArray.getElements] and releasing it with
 * [JDoubleArray.releaseElements].
 *
 * You can access [commit], [finalize] and [abort] methods inside [block].
 *
 * @param onError code to execute if [JDoubleArray.getElements] fails.
 * @param block code to execute with the retrieved C array and copy marker.
 */
context(env: JniEnv, placement: NativePlacement)
public inline fun JDoubleArray.modify(onError: () -> Unit, block: ModifyingArrayScope.(carray: CArrayPointer<DoubleVar>, isCopy: Boolean) -> Unit) {
    val (carray, isCopy) = getElements() ?: return onError()
    return block({ mode -> releaseElements(carray, mode) }, carray, isCopy)
}

/**
 * Alias for working with primitive array's elements via [JPrimitiveArray.getElementsCritical]. and releasing it with
 * [JPrimitiveArray.releaseElementsCritical].
 *
 * You can access [commit], [finalize] and [abort] methods inside [block].
 *
 * @param onError code to execute if [JPrimitiveArray.getElements] fails.
 * @param block code to execute with the retrieved C array and copy marker.
 */
context(env: JniEnv, placement: NativePlacement)
public inline fun <T : CPrimitiveVar> JPrimitiveArray<T>.modifyCritical(onError: () -> Unit, block: ModifyingArrayScope.(carray: CArrayPointer<*>, isCopy: Boolean) -> Unit) {
    val (carray, isCopy) = getElementsCritical() ?: return onError()
    return block({ mode -> releaseElementsCritical(carray, mode) }, carray, isCopy)
}