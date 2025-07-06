@file:Suppress("NOTHING_TO_INLINE", "FunctionName")

package io.github.mimimishkin.jni

import io.github.mimimishkin.jni.internal.raw.Colormap
import io.github.mimimishkin.jni.internal.raw.Display
import io.github.mimimishkin.jni.internal.raw.Drawable
import io.github.mimimishkin.jni.internal.raw.VisualID
import kotlinx.cinterop.pointed
import kotlinx.cinterop.reinterpret
import io.github.mimimishkin.jni.internal.raw.jawt_X11DrawingSurfaceInfo
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.invoke

/**
 * X11-specific declarations for AWT native interface.
 */
public typealias X11DrawingSurfaceInfo = CPointer<jawt_X11DrawingSurfaceInfo>

public inline val DrawingSurfaceInfo.x11Info: X11DrawingSurfaceInfo
    get() = pointed.platformInfo!!.reinterpret()

public inline val DrawingSurfaceInfo.drawable: Drawable
    get() = x11Info.pointed.drawable

public inline val DrawingSurfaceInfo.drawable: Drawable
    get() = x11Info.pointed.drawable

public inline val DrawingSurfaceInfo.display: CPointer<Display>
    get() = x11Info.pointed.display!!

public inline val DrawingSurfaceInfo.visualID: VisualID
    get() = x11Info.pointed.visualID

public inline val DrawingSurfaceInfo.colormapID: Colormap
    get() = x11Info.pointed.colormapID

public inline val DrawingSurfaceInfo.depth: Int
    get() = x11Info.pointed.depth

/**
 * Returns a pixel value from a set of RGB values.
 * This is useful for paletted color (256 color) modes.
 *
 * @since 1.4
 */
public inline fun DrawingSurfaceInfo.GetAWTColor(r: Int, g: Int, b: Int): Int {
    return x11Info.pointed.GetAWTColor!!.invoke(surface, r, g, b)
}