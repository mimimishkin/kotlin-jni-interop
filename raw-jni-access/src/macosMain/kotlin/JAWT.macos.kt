@file:Suppress("NOTHING_TO_INLINE")

package io.github.mimimishkin.jni

import kotlinx.cinterop.pointed
import kotlinx.cinterop.reinterpret
import platform.AppKit.NSWindow

public inline val DrawingSurfaceInfo.nswindow: NSWindow?
    get() = platformInfo?.reinterpret<NSWindow>()?.pointed

public inline val DrawingSurface.nswindow: NSWindow?
    get() = useInfo { it.nswindow }