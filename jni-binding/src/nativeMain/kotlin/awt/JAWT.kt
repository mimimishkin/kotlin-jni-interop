@file:Suppress("NOTHING_TO_INLINE", "FunctionName")

package io.github.mimimishkin.jni.awt

import io.github.mimimishkin.jni.JObject
import io.github.mimimishkin.jni.JniEnv
import io.github.mimimishkin.jni.internal.raw.*
import io.github.mimimishkin.jni.toJBoolean
import io.github.mimimishkin.jni.toKBoolean
import kotlinx.cinterop.CArrayPointer
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.NativePlacement
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.invoke
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr

/**
 * AWT native interface.
 *
 * The AWT native interface allows a native application as a means by which to access native structures in AWT. This is
 * to facilitate moving legacy C and C++ applications to Java and to target the needs of the developers who need to do
 * their own native rendering to canvases for performance or other reasons.
 *
 * Conversely, it also provides mechanisms for an application which already has a native window to provide that to AWT
 * for AWT rendering.
 *
 * Since every platform may be different in its native data structures and APIs for windowing systems, the application
 * must have necessarily provided a per-platform source and compile and deliver per-platform native code to use this
 * API.
 *
 * These interfaces are not part of the Java SE specification, and a VM is not required to implement this API.
 * However, it is strongly recommended that all implementations which support headful AWT also support these interfaces.
 */
public typealias Awt = CPointer<jawt>

/**
 * Functions and utilities to work with JAWT (Java Abstract Window Toolkit).
 */
public object JAWT

/**
 * Java version that corresponds to the new JAWT API.
 */
public typealias AwtVersion = Int

/**
 * Java version *1.3*.
 */
public inline val JAWT.v3: AwtVersion get() = JAWT_VERSION_1_3

/**
 * Java version *1.4*.
 */
public inline val JAWT.v4: AwtVersion get() = JAWT_VERSION_1_4

/**
 * Java version *1.7*.
 */
public inline val JAWT.v7: AwtVersion get() = JAWT_VERSION_1_7

/**
 * Java version *9*.
 */
public inline val JAWT.v9: AwtVersion get() = JAWT_VERSION_9

/**
 * Latest version of the JAWT API.
 */
public inline val JAWT.lastVersion: AwtVersion get() = JAWT.v9

/**
 * Returns by [DrawingSurface.Lock] when an error has occurred, and the surface could not be locked.
 */
public inline val JAWT.LOCK_ERROR: Int get() = JAWT_LOCK_ERROR

/**
 * Returns by [DrawingSurface.Lock] when the clip region has changed.
 */
public inline val JAWT.LOCK_CLIP_CHANGED: Int get() = JAWT_LOCK_CLIP_CHANGED

/**
 * Returns by [DrawingSurface.Lock] when the bounds of the surface have changed.
 */
public inline val JAWT.LOCK_BOUNDS_CHANGED: Int get() = JAWT_LOCK_BOUNDS_CHANGED

/**
 * Returns by [DrawingSurface.Lock] when the surface itself has changed
 */
public inline val JAWT.LOCK_SURFACE_CHANGED: Int get() = JAWT_LOCK_SURFACE_CHANGED

/**
 * Get the AWT native structure.
 */
context(memScope: NativePlacement)
public fun JniEnv.GetAwt(version: AwtVersion = JAWT.lastVersion): Awt? {
    val awt = memScope.alloc<jawt> {
        this.version = version
    }
    val success = JAWT_GetAWT(ptr, awt.ptr).toKBoolean()
    return if (success) awt.ptr else null
}

/**
 * Contains the underlying drawing information of a component.
 *
 * All operations on a [DrawingSurface] MUST be performed from the same thread as the call to [GetDrawingSurface].
 */
public typealias DrawingSurface = CPointer<jawt_DrawingSurface>

/**
 * Cached reference to the Java environment of the calling thread.
 * If [Lock], [Unlock], [GetDrawingSurfaceInfo] or [FreeDrawingSurfaceInfo] are called from a different thread, this
 * data member should be set before calling those functions.
 */
public inline var DrawingSurface.env: JniEnv
    get() = pointed.env!!.pointed
    set(value) { pointed.env = value.ptr }

/**
 *  Cached reference to the target object.
 */
public inline val DrawingSurface.target: JObject
    get() = pointed.target!!

/**
 * Contains the underlying drawing information of a component.
 */
public typealias DrawingSurfaceInfo = CPointer<jawt_DrawingSurfaceInfo>

/**
 * Structure for a native rectangle.
 */
public typealias JAwtRectangle = jawt_Rectangle

/**
 * Cached pointer to the underlying drawing surface.
 */
public inline val DrawingSurfaceInfo.surface: DrawingSurface
    get() = pointed.ds!!

/**
 * Bounding rectangle of the drawing surface.
 */
public inline val DrawingSurfaceInfo.bounds: JAwtRectangle
    get() = pointed.bounds

/**
 * Number of rectangles in the clip.
 */
public inline val DrawingSurfaceInfo.clipSize: Int
    get() = pointed.clipSize

/**
 * Clip rectangle C array.
 */
public inline val DrawingSurfaceInfo.clip: CArrayPointer<JAwtRectangle>
    get() = pointed.clip!!

/**
 * Clip rectangle list.
 */
public inline val DrawingSurfaceInfo.clipRects: List<JAwtRectangle>
    get() = List(clipSize) { i -> clip[i] }

/**
 * Return a drawing surface from a target [JObject].
 * This value may be cached.
 *
 * Returns `null` if an error has occurred.
 * Target must be a java.awt.Component (should be a Canvas or Window for native rendering).
 * [FreeDrawingSurface] must be called when finished with the returned [DrawingSurface].
 */
context(env: JniEnv)
public inline fun Awt.GetDrawingSurface(target: JObject): DrawingSurface? {
    return pointed.GetDrawingSurface!!(env.ptr, target)
}

/**
 * Free the drawing surface allocated in [GetDrawingSurface].
 */
public inline fun Awt.FreeDrawingSurface(surface: DrawingSurface) {
    pointed.FreeDrawingSurface!!(surface)
}

/**
 * Locks the entire AWT for synchronization purposes.
 *
 * @since 1.4
 */
context(env: JniEnv)
public inline fun Awt.Lock() {
    pointed.Lock!!(env.ptr)
}

/**
 * Unlocks the entire AWT for synchronization purposes.
 *
 * @since 1.4
 */
context(env: JniEnv)
public inline fun Awt.Unlock() {
    pointed.Unlock!!(env.ptr)
}

/**
 * Returns a reference to a java.awt.Component from a native platform handle:
 * - on Windows - HWND
 * - on Linux - Drawable
 * - on macOS - NSWindow.
 *
 * The reference returned by this function is a local reference that is only valid in this environment.
 * This function returns `null` if no component could be found with matching platform information.
 *
 * @since 1.4
 */
context(env: JniEnv)
public inline fun Awt.GetComponent(platformInfo: COpaquePointer): JObject? {
    return pointed.GetComponent!!(env.ptr, platformInfo)
}

/**
 * Creates a java.awt.Frame placed in a native container. Container is referenced by the native platform handle:
 * - on Windows - HWND
 * - on Linux - Drawable
 * - on macOS - NSWindow.
 *
 * The reference returned by this function is a local reference that is only valid in this environment.
 * This function returns `null` if no frame could be created with matching platform information.
 *
 * @since 9
 */
context(env: JniEnv)
public inline fun Awt.CreateEmbeddedFrame(platformInfo: COpaquePointer): JObject? {
    return pointed.CreateEmbeddedFrame!!(env.ptr, platformInfo)
}

/**
 * Moves and resizes the embedded frame. The new location of the top-left corner is specified by x and y parameters
 * relative to the native parent component. The new size is specified by width and height.
 *
 * The embedded frame should be created by [CreateEmbeddedFrame] method, or this function will not have any effect.
 *
 * java.awt.Component.setLocation() and java.awt.Component.setBounds() for EmbeddedFrame really don't move it within the
 * native parent. These methods always locate the embedded frame at (0, 0) for backward compatibility. To allow moving
 * embedded frames this method was introduced, and it works just the same way as setLocation() and setBounds() for
 * usual, non-embedded components.
 *
 * Using usual get/setLocation() and get/setBounds() together with this new method is not recommended.
 *
 * @since 9
 */
context(env: JniEnv)
public inline fun Awt.SetBounds(embeddedFrame: JObject, x: Int, y: Int, w: Int, h: Int) {
    pointed.SetBounds!!(env.ptr, embeddedFrame, x, y, w, h)
}

/**
 * Synthesize a native message to activate or deactivate an EmbeddedFrame window. If [doActivate] is `true` activates
 * the window, otherwise, deactivates the window.
 *
 * The embedded frame should be created by [CreateEmbeddedFrame] method, or this function will not have any effect.
 *
 * @since 9
 */
context(env: JniEnv)
public inline fun Awt.SynthesizeWindowActivation(embeddedFrame: JObject, doActivate: Boolean) {
    pointed.SynthesizeWindowActivation!!(env.ptr, embeddedFrame, doActivate.toJBoolean())
}

/**
 * Lock the surface of the target component for native rendering. When finished drawing, the surface must be unlocked
 * with [Unlock]).
 *
 * This function returns a bitmask with one or more of the following values:
 * [JAWT.LOCK_ERROR], [JAWT.LOCK_CLIP_CHANGED], [JAWT.LOCK_BOUNDS_CHANGED], [JAWT.LOCK_SURFACE_CHANGED].
 */
public inline fun DrawingSurface.Lock(): Int {
    return pointed.Lock!!.invoke(this)
}

/**
 * Get the drawing surface info.
 * The value returned may be cached, but the values may change if additional calls to [Lock] or [Unlock] are made.
 *
 * [Lock] must be called before this can return a valid value.
 * Returns `null` if an error has occurred.
 *
 * When finished with the returned value, [FreeDrawingSurfaceInfo] must be called.
 */
public inline fun DrawingSurface.GetDrawingSurfaceInfo(): DrawingSurfaceInfo? {
    return pointed.GetDrawingSurfaceInfo!!.invoke(this)
}

/**
 * Free the drawing surface info.
 */
public inline fun DrawingSurface.FreeDrawingSurfaceInfo(info: DrawingSurfaceInfo) {
    pointed.FreeDrawingSurfaceInfo!!.invoke(info)
}

/**
 * Unlock the drawing surface of the target component for native rendering.
 */
public inline fun DrawingSurface.Unlock() {
    pointed.Unlock!!.invoke(this)
}
