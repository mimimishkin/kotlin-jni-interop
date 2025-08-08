@file:Suppress("NOTHING_TO_INLINE")

package io.github.mimimishkin.jni.awt.ext

import io.github.mimimishkin.jni.JObject
import io.github.mimimishkin.jni.JniEnv
import io.github.mimimishkin.jni.awt.*
import kotlinx.cinterop.memScoped

/**
 * Receive [Awt] from [JniEnv] and executes [block] with it.
 *
 * Throw [IllegalStateException] if [GetAwt] fails.
 */
public inline fun <T> JniEnv.withAwt(version: AwtVersion = JAWT.lastVersion, block: (Awt) -> T): T {
    return memScoped {
        val awt = GetAwt(version)
        if (awt != null) {
            block(awt)
        } else {
            throw IllegalStateException("AWT not found")
        }
    }
}

/**
 * Locks the entire AWT, executes [block] and then release lock.
 *
 * @since 1.4
 */
context(env: JniEnv)
public inline fun <T> Awt.withLock(block: () -> T): T {
    Lock()
    try {
        return block()
    } finally {
        Unlock()
    }
}

/**
 * Gets a [DrawingSurface] from a target [JObject] and executes [block] with it. Then safely release the surface.
 *
 * Throw [IllegalStateException] if [GetDrawingSurface] fails.
 */
context(env: JniEnv)
public inline fun <T> Awt.useDrawingSurface(target: JObject, block: (DrawingSurface) -> T): T {
    val surface = GetDrawingSurface(target)
    if (surface == null)
        throw IllegalStateException("DrawingSurface not found")

    try {
        return block(surface)
    } finally {
        FreeDrawingSurface(surface)
    }
}

/**
 * Locks the surface and executes [block], then release lock.
 *
 * Throw [IllegalStateException] if [Lock] fails.
 */
public inline fun <T> DrawingSurface.withLock(block: context(JniEnv) () -> T): T {
    val res = Lock()
    if (res and JAWT.LOCK_ERROR != 0)
        throw IllegalStateException("Error locking surface")

    try {
        return block(env)
    } finally {
        Unlock()
    }
}

/**
 * Gets a [DrawingSurfaceInfo] and executes [block], then release the surface info.
 *
 * Throw [IllegalStateException] if [GetDrawingSurfaceInfo] fails.
 */
public inline fun <T> DrawingSurface.useInfo(block: (DrawingSurfaceInfo) -> T): T {
    val info = GetDrawingSurfaceInfo()
    if (info == null)
        throw IllegalStateException("Error getting surface info")

    try {
        return block(info)
    } finally {
        FreeDrawingSurfaceInfo(info)
    }
}

/**
 * Alias for stacking [Awt.useDrawingSurface], [DrawingSurface.withLock] and [DrawingSurface.useInfo].
 */
context(env: JniEnv)
public inline fun <T> Awt.useDrawingSurfaceInfo(target: JObject, block: DrawingSurface.(DrawingSurfaceInfo) -> T): T {
    return useDrawingSurface(target) { surface ->
        surface.withLock {
            surface.useInfo { info ->
                surface.block(info)
            }
        }
    }
}