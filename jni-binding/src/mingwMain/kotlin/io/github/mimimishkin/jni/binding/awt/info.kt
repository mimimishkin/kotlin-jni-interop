@file:Suppress("NOTHING_TO_INLINE")

package io.github.mimimishkin.jni.binding.awt

import jni.jawt_Win32DrawingSurfaceInfo
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.pointed
import kotlinx.cinterop.reinterpret
import platform.windows.HBITMAP
import platform.windows.HDC
import platform.windows.HPALETTE
import platform.windows.HWND

internal inline val DrawingSurfaceInfo.win32Info: jawt_Win32DrawingSurfaceInfo
    get() = pointed.platformInfo!!.reinterpret<jawt_Win32DrawingSurfaceInfo>().pointed

/**
 * Native window handle.
 *
 * Either this or [hbitmap] or [pbits] is valid.
 */
public val DrawingSurfaceInfo.hwnd: HWND
    get() = win32Info.hwnd!!

/**
 * DDB handle.
 *
 * Either this or [hwnd] or [pbits] is valid.
 */
public val DrawingSurfaceInfo.hbitmap: HBITMAP
    get() = win32Info.hbitmap!!

/**
 * DIB handle.
 *
 * Either this or [hwnd] or [hbitmap] is valid.
 */
public val DrawingSurfaceInfo.pbits: COpaquePointer
    get() = win32Info.pbits!!

/**
 * This HDC should always be used instead of the HDC returned from `BeginPaint()` or any calls to `GetDC()`.
 */
public val DrawingSurfaceInfo.hdc: HDC
    get() = win32Info.hdc!!

public val DrawingSurfaceInfo.hpalette: HPALETTE
    get() = win32Info.hpalette!!
