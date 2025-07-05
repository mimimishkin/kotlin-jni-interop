@file:Suppress("NOTHING_TO_INLINE")

package io.github.mimimishkin.jni

import io.github.mimimishkin.jni.raw.*
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.alloc
import kotlinx.cinterop.invoke
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr

public typealias JAwt = jawt

public object JAWT {
    public enum class Version(@PublishedApi internal val nativeCode: Int) {
        VERSION_1_3(JAWT_VERSION_1_3),
        VERSION_1_4(JAWT_VERSION_1_4),
        VERSION_1_7(JAWT_VERSION_1_7),
        VERSION_9(JAWT_VERSION_9),
    }

    /**
     * Returns by [DrawingSurface.Lock] when an error has occurred, and the surface could not be locked.
     */
    public inline val LOCK_ERROR: Int get() = JAWT_LOCK_ERROR

    /**
     * Returns by [DrawingSurface.Lock] when the clip region has changed.
     */
    public inline val LOCK_CLIP_CHANGED: Int get() = JAWT_LOCK_CLIP_CHANGED

    /**
     * Returns by [DrawingSurface.Lock] when the bounds of the surface have changed.
     */
    public inline val LOCK_BOUNDS_CHANGED: Int get() = JAWT_LOCK_BOUNDS_CHANGED

    /**
     * Returns by [DrawingSurface.Lock] when the surface itself has changed
     */
    public inline val LOCK_SURFACE_CHANGED: Int get() = JAWT_LOCK_SURFACE_CHANGED
}

context(memScope: MemScope)
public fun JNIEnv.GetAwt(version: JAWT.Version): JAwt? {
    val awt = memScope.alloc<JAwt> {
        this.version = version.nativeCode
    }
    val success = JAWT_GetAWT(ptr, awt.ptr).toKBoolean()
    return if (success) awt else null
}

public typealias DrawingSurface = jawt_DrawingSurface
public typealias DrawingSurfaceInfo = jawt_DrawingSurfaceInfo

/**
 * Return a drawing surface from a target [JObject].
 * This value may be cached.
 *
 * Returns `null` if an error has occurred.
 * Target must be a java.awt.Component (should be a Canvas or Window for native rendering).
 * [FreeDrawingSurface] must be called when finished with the returned [DrawingSurface].
 */
public inline fun JAwt.GetDrawingSurface(env: JNIEnv, target: JObject): DrawingSurface? {
    return GetDrawingSurface!!.invoke(env.ptr, target)?.pointed
}

/**
 * Free the drawing surface allocated in [GetDrawingSurface].
 */
public inline fun JAwt.FreeDrawingSurface(surface: DrawingSurface) {
    FreeDrawingSurface!!.invoke(surface.ptr)
}

/**
 * Locks the entire AWT for synchronization purposes.
 *
 * @since 1.4
 */
public inline fun JAwt.Unlock(env: JNIEnv) {
    Unlock!!.invoke(env.ptr)
}

/**
 * Unlocks the entire AWT for synchronization purposes.
 *
 * @since 1.4
 */
public inline fun JAwt.Lock(env: JNIEnv) {
    Lock!!.invoke(env.ptr)
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
public inline fun JAwt.GetComponent(env: JNIEnv, platformInfo: COpaquePointer): JObject? {
    return GetComponent!!.invoke(env.ptr, platformInfo)
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
public inline fun JAwt.CreateEmbeddedFrame(env: JNIEnv, platformInfo: COpaquePointer): JObject? {
    return CreateEmbeddedFrame!!.invoke(env.ptr, platformInfo)
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
public inline fun JAwt.SetBounds(env: JNIEnv, embeddedFrame: JObject, x: Int, y: Int, w: Int, h: Int) {
    SetBounds!!.invoke(env.ptr, embeddedFrame, x, y, w, h)
}

/**
 * Synthesize a native message to activate or deactivate an EmbeddedFrame window. If [doActivate] is `true` activates
 * the window, otherwise, deactivates the window.
 *
 * The embedded frame should be created by [CreateEmbeddedFrame] method, or this function will not have any effect.
 *
 * @since 9
 */
public inline fun JAwt.SynthesizeWindowActivation(env: JNIEnv, embeddedFrame: JObject, doActivate: Boolean) {
    SynthesizeWindowActivation!!.invoke(env.ptr, embeddedFrame, doActivate.toJBoolean())
}

/**
 * Lock the surface of the target component for native rendering. When finished drawing, the surface must be unlocked
 * with [Unlock]).
 *
 * This function returns a bitmask with one or more of the following values:
 * [JAWT.LOCK_ERROR], [JAWT.LOCK_CLIP_CHANGED], [JAWT.LOCK_BOUNDS_CHANGED], [JAWT.LOCK_SURFACE_CHANGED].
 */
public inline fun DrawingSurface.Lock(): Int {
    return Lock!!.invoke(ptr)
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
    return GetDrawingSurfaceInfo!!.invoke(ptr)?.pointed
}

/**
 * Free the drawing surface info.
 */
public inline fun DrawingSurface.FreeDrawingSurfaceInfo(info: DrawingSurfaceInfo) {
    FreeDrawingSurfaceInfo!!.invoke(info.ptr)
}

/**
 * Unlock the drawing surface of the target component for native rendering.
 */
public inline fun DrawingSurface.Unlock() {
    Unlock!!.invoke(ptr)
}
