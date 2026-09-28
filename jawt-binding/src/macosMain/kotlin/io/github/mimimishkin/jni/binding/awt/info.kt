@file:Suppress("NOTHING_TO_INLINE")

package io.github.mimimishkin.jni.binding.awt

import jni.JAWT_SurfaceLayersProtocol
import kotlinx.cinterop.interpretObjCPointer
import kotlinx.cinterop.pointed
import platform.QuartzCore.CALayer

internal inline val DrawingSurfaceInfo.surfaceLayers: JAWT_SurfaceLayersProtocol
    get() = interpretObjCPointer(pointed.platformInfo!!.rawValue)

public var DrawingSurfaceInfo.layer: CALayer
    get() = surfaceLayers.layer!!
    set(value) { surfaceLayers.layer = value }

public val DrawingSurfaceInfo.windowLayer: CALayer
    get() = surfaceLayers.windowLayer!!