@file:Suppress("NOTHING_TO_INLINE")

package io.github.mimimishkin.jni

import kotlinx.cinterop.pointed
import kotlinx.cinterop.reinterpret
import platform.windows.HWND
import io.github.mimimishkin.jni.raw.jawt_Win32DrawingSurfaceInfo

public typealias Win32DrawingSurfaceInfo = jawt_Win32DrawingSurfaceInfo

public inline val DrawingSurfaceInfo.win32Info: Win32DrawingSurfaceInfo?
    get() = platformInfo?.reinterpret<jawt_Win32DrawingSurfaceInfo>()?.pointed

public inline val DrawingSurfaceInfo.hwnd: HWND?
    get() = win32Info?.hwnd

public inline val DrawingSurface.hwnd: HWND?
    get() = useInfo { it.hwnd }