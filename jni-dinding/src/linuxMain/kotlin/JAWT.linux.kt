@file:Suppress("NOTHING_TO_INLINE")

package io.github.mimimishkin.jni

import kotlinx.cinterop.pointed
import kotlinx.cinterop.reinterpret
import io.github.mimimishkin.jni.raw.jawt_X11DrawingSurfaceInfo

public typealias X11DrawingSurfaceInfo = jawt_X11DrawingSurfaceInfo

public inline val DrawingSurfaceInfo.x11Info: X11DrawingSurfaceInfo?
    get() = platformInfo?.reinterpret<jawt_X11DrawingSurfaceInfo>()?.pointed

public inline val DrawingSurfaceInfo.drawable: Int?
    get() = x11Info?.drawable

public inline val DrawingSurface.drawable: Int?
    get() = useInfo { it.drawable }