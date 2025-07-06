@file:Suppress("NOTHING_TO_INLINE")

package io.github.mimimishkin.jni

import io.github.mimimishkin.jni.internal.raw.jawt_Win32DrawingSurfaceInfo
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.pointed
import kotlinx.cinterop.reinterpret
import platform.windows.HBITMAP
import platform.windows.HDC
import platform.windows.HPALETTE
import platform.windows.HWND

/**
 * Microsoft Windows specific declarations for AWT native interface.
 */
public typealias Win32DrawingSurfaceInfo = CPointer<jawt_Win32DrawingSurfaceInfo>

public inline val DrawingSurfaceInfo.win32Info: Win32DrawingSurfaceInfo
    get() = pointed.platformInfo!!.reinterpret()

/**
 * Native window handle.
 *
 * Either this or [hbitmap] or [pbits] is valid.
 */
public inline val DrawingSurfaceInfo.hwnd: HWND
    get() = win32Info.pointed.hwnd!!

/**
 * DDB handle.
 *
 * Either this or [hwnd] or [pbits] is valid.
 */
public inline val DrawingSurfaceInfo.hbitmap: HBITMAP
    get() = win32Info.pointed.hbitmap!!

/**
 * DIB handle.
 *
 * Either this or [hwnd] or [hbitmap] is valid.
 */
public inline val DrawingSurfaceInfo.pbits: COpaquePointer
    get() = win32Info.pointed.pbits!!

/**
 * This HDC should always be used instead of the HDC returned from `BeginPaint()` or any calls to `GetDC()`.
 */
public inline val DrawingSurfaceInfo.hdc: HDC
    get() = win32Info.pointed.hdc!!

public inline val DrawingSurfaceInfo.hpalette: HPALETTE
    get() = win32Info.pointed.hpalette!!
