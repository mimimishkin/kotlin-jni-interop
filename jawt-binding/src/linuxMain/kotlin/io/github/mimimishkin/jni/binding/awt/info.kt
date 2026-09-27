@file:Suppress("NOTHING_TO_INLINE", "FunctionName")

package io.github.mimimishkin.jni.binding.awt

import jni.Colormap
import jni.Display
import jni.Drawable
import jni.VisualID
import kotlinx.cinterop.pointed
import kotlinx.cinterop.reinterpret
import jni.jawt_X11DrawingSurfaceInfo
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.invoke

internal inline val DrawingSurfaceInfo.x11Info: jawt_X11DrawingSurfaceInfo
    get() = pointed.platformInfo!!.reinterpret<jawt_X11DrawingSurfaceInfo>().pointed

public val DrawingSurfaceInfo.drawable: Drawable
    get() = x11Info.drawable

public val DrawingSurfaceInfo.display: CPointer<Display>
    get() = x11Info.display!!

public val DrawingSurfaceInfo.visualID: VisualID
    get() = x11Info.visualID

public val DrawingSurfaceInfo.colormapID: Colormap
    get() = x11Info.colormapID

public val DrawingSurfaceInfo.depth: Int
    get() = x11Info.depth

/**
 * Returns a pixel value from a set of RGB values.
 * This is useful for paletted color (256 color) modes.
 *
 * @since 1.4
 */
public fun DrawingSurfaceInfo.GetAWTColor(r: Int, g: Int, b: Int): Int {
    return x11Info.GetAWTColor!!.invoke(surface, r, g, b)
}