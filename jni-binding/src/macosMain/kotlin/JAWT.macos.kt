@file:Suppress("NOTHING_TO_INLINE")

package io.github.mimimishkin.jni

import io.github.mimimishkin.jni.internal.raw.JAWT_SurfaceLayersProtocol
import kotlinx.cinterop.interpretObjCPointer
import kotlinx.cinterop.pointed
import platform.QuartzCore.CALayer

public typealias SurfaceLayersProtocol = JAWT_SurfaceLayersProtocol

public inline val DrawingSurfaceInfo.surfaceLayers: SurfaceLayersProtocol
    get() = interpretObjCPointer(pointed.platformInfo!!.rawValue)

public inline var DrawingSurfaceInfo.layer: CALayer
    get() = surfaceLayers.layer!!
    set(value) { surfaceLayers.layer = value }

public inline val DrawingSurfaceInfo.windowLayer: CALayer
    get() = surfaceLayers.windowLayer!!