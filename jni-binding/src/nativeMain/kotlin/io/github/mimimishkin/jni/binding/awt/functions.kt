package io.github.mimimishkin.jni.binding.awt

import io.github.mimimishkin.jni.binding.JniEnv
import io.github.mimimishkin.jni.binding.c
import io.github.mimimishkin.jni.binding.toJBoolean
import io.github.mimimishkin.jni.binding.toKBoolean
import io.github.mimimishkin.jni.binding.wrap
import jni.JAWT_GetAWT
import jni.JNIEnvVar
import kotlinx.cinterop.CArrayPointer
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.NativePlacement
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.invoke
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr

/**
 * Get the AWT native structure.
 */
context(env: JniEnv, placement: NativePlacement)
public fun getAwt(version: AwtVersion): Awt? {
    val awt = placement.alloc<jni.jawt> {
        this.version = version
    }
    @Suppress("UNCHECKED_CAST")
    val success = JAWT_GetAWT(env.ptr as CValuesRef<JNIEnvVar>?, awt.ptr).toKBoolean()
    @Suppress("UNCHECKED_CAST")
    return if (success) awt.ptr as Awt? else null
}

/**
 * Version of AWT.
 */
public inline val Awt.version: AwtVersion
    get() = pointed.version

/**
 * Return a drawing surface from a target [JAwtComponent].
 * This value may be cached.
 *
 * Returns `null` if an error has occurred.
 * Target must be a `java.awt.Component` (should be a Canvas or Window for native rendering).
 * [freeDrawingSurface] must be called when finished with the returned [DrawingSurface].
 */
context(env: JniEnv)
public fun Awt.getDrawingSurface(target: JAwtComponent): DrawingSurface? {
    return pointed.GetDrawingSurface!!(env.ptr, target.c)
}

/**
 * Free the drawing surface allocated in [getDrawingSurface].
 */
public fun Awt.freeDrawingSurface(surface: DrawingSurface) {
    pointed.FreeDrawingSurface!!(surface)
}

/**
 * Locks the entire AWT for synchronization purposes.
 *
 * @since 1.4
 */
context(env: JniEnv)
public fun Awt.lock() {
    pointed.Lock!!(env.ptr)
}

/**
 * Unlocks the entire AWT for synchronization purposes.
 *
 * @since 1.4
 */
context(env: JniEnv)
public fun Awt.unlock() {
    pointed.Unlock!!(env.ptr)
}

/**
 * Returns a reference to a `java.awt.Component` from a native platform handle:
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
public fun Awt.getComponent(platformInfo: COpaquePointer): JAwtComponent? {
    return pointed.GetComponent!!(env.ptr, platformInfo).wrap()
}

/**
 * Creates a `java.awt.Frame` placed in a native container. Container is referenced by the native platform handle:
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
public fun Awt.createEmbeddedFrame(platformInfo: COpaquePointer): JAwtFrame? {
    return pointed.CreateEmbeddedFrame!!(env.ptr, platformInfo).wrap()
}

/**
 * Moves and resizes the embedded frame. The new location of the top-left corner is specified by x and y parameters
 * relative to the native parent component. The new size is specified by width and height.
 *
 * The embedded frame should be created by [createEmbeddedFrame] method, or this function will not have any effect.
 *
 * `java.awt.Component.setLocation()` and `java.awt.Component.setBounds()` for EmbeddedFrame really don't move it within
 * the native parent. These methods always locate the embedded frame at (0, 0) for backward compatibility. To allow
 * moving embedded frames this method was introduced, and it works just the same way as `setLocation()` and
 * `setBounds()` for usual, non-embedded components.
 *
 * Using usual `get/setLocation()` and `get/setBounds()` together with this new method is not recommended.
 *
 * @since 9
 */
context(env: JniEnv)
public fun Awt.setBounds(embeddedFrame: JAwtFrame, x: Int, y: Int, w: Int, h: Int) {
    pointed.SetBounds!!(env.ptr, embeddedFrame.c, x, y, w, h)
}

/**
 * Synthesize a native message to activate or deactivate an EmbeddedFrame window. If [doActivate] is `true` activates
 * the window, otherwise, deactivates the window.
 *
 * The embedded frame should be created by [createEmbeddedFrame] method, or this function will not have any effect.
 *
 * @since 9
 */
context(env: JniEnv)
public fun Awt.synthesizeWindowActivation(embeddedFrame: JAwtFrame, doActivate: Boolean) {
    pointed.SynthesizeWindowActivation!!(env.ptr, embeddedFrame.c, doActivate.toJBoolean())
}

/**
 * Cached reference to the Java environment of the calling thread.
 * If [lock], [unlock], [getInfo] or [freeInfo] are called from a different thread, this
 * data member should be set before calling those functions.
 */
public inline var DrawingSurface.env: JniEnv
    get() = pointed.env!!.pointed
    set(value) { pointed.env = value.ptr }

/**
 * Cached reference to the target object.
 */
public inline val DrawingSurface.target: JAwtComponent
    get() = pointed.target.wrap()!!

/**
 * Cached pointer to the underlying drawing surface.
 */
public inline val DrawingSurfaceInfo.surface: DrawingSurface
    get() = pointed.ds!!

/**
 * Bounding rectangle of the drawing surface.
 */
public inline val DrawingSurfaceInfo.bounds: AwtRectangle
    get() = pointed.bounds

/**
 * Number of rectangles in the clip.
 */
public inline val DrawingSurfaceInfo.clipSize: Int
    get() = pointed.clipSize

/**
 * Clip rectangle C array.
 */
public inline val DrawingSurfaceInfo.clip: CArrayPointer<AwtRectangle>
    get() = pointed.clip!!

/**
 * Clip rectangle list.
 */
public inline val DrawingSurfaceInfo.clipRects: List<AwtRectangle>
    get() = List(clipSize) { i -> clip[i] }

/**
 * Lock the surface of the target component for native rendering. When finished drawing, the surface must be unlocked
 * with [unlock]).
 *
 * This function returns a bitmask with one or more of the following values:
 * [JAWT.LOCK_ERROR], [JAWT.LOCK_CLIP_CHANGED], [JAWT.LOCK_BOUNDS_CHANGED], [JAWT.LOCK_SURFACE_CHANGED].
 */
public fun DrawingSurface.lock(): Int {
    return pointed.Lock!!.invoke(this)
}

/**
 * Get the drawing surface info.
 * The value returned may be cached, but the values may change if additional calls to [lock] or [unlock] are made.
 *
 * [lock] must be called before this can return a valid value.
 * Returns `null` if an error has occurred.
 *
 * When finished with the returned value, [freeInfo] must be called.
 */
public fun DrawingSurface.getInfo(): DrawingSurfaceInfo? {
    return pointed.GetDrawingSurfaceInfo!!.invoke(this)
}

/**
 * Free the drawing surface info.
 */
public fun DrawingSurface.freeInfo(info: DrawingSurfaceInfo) {
    pointed.FreeDrawingSurfaceInfo!!.invoke(info)
}

/**
 * Unlock the drawing surface of the target component for native rendering.
 */
public fun DrawingSurface.unlock() {
    pointed.Unlock!!.invoke(this)
}