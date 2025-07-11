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

@PublishedApi
internal inline val DrawingSurfaceInfo.x11Info: jawt_X11DrawingSurfaceInfo
    get() = pointed.platformInfo!!.reinterpret<jawt_X11DrawingSurfaceInfo>().pointed

public inline val DrawingSurfaceInfo.drawable: Drawable
    get() = x11Info.drawable

public inline val DrawingSurfaceInfo.display: CPointer<Display>
    get() = x11Info.display!!

public inline val DrawingSurfaceInfo.visualID: VisualID
    get() = x11Info.visualID

public inline val DrawingSurfaceInfo.colormapID: Colormap
    get() = x11Info.colormapID

public inline val DrawingSurfaceInfo.depth: Int
    get() = x11Info.depth

/**
 * Returns a pixel value from a set of RGB values.
 * This is useful for paletted color (256 color) modes.
 *
 * @since 1.4
 */
public inline fun DrawingSurfaceInfo.GetAWTColor(r: Int, g: Int, b: Int): Int {
    return x11Info.GetAWTColor!!.invoke(surface, r, g, b)
}