@file:Suppress("NOTHING_TO_INLINE")

package io.github.mimimishkin.jni.binding.awt

import io.github.mimimishkin.jni.binding.JniEnv
import kotlinx.cinterop.NativePlacement

/**
 * Receive [Awt] from [JniEnv] and executes [block] with it.
 *
 * Throw [IllegalStateException] if [getAwt] fails.
 */
context(env: JniEnv, placement: NativePlacement)
public inline fun <T> withAwt(version: AwtVersion, block: (Awt) -> T): T {
    val awt = getAwt(version)
    if (awt != null) {
        return block(awt)
    } else {
        throw IllegalStateException("AWT not found")
    }
}

/**
 * Locks the entire AWT, executes [block] and then release lock.
 *
 * @since 1.4
 */
context(env: JniEnv)
public inline fun <T> Awt.locking(block: () -> T): T {
    lock()
    try {
        return block()
    } finally {
        unlock()
    }
}

/**
 * Gets a [DrawingSurface] from a target [JAwtComponent] and executes [block] with it. Then safely release the surface.
 *
 * Throw [IllegalStateException] if [getDrawingSurface] fails.
 */
context(env: JniEnv)
public inline fun <T> Awt.useDrawingSurface(target: JAwtComponent, block: (DrawingSurface) -> T): T {
    val surface = checkNotNull(getDrawingSurface(target)) { "DrawingSurface not found" }

    try {
        return block(surface)
    } finally {
        freeDrawingSurface(surface)
    }
}

/**
 * Locks the surface and executes [block], then release lock.
 *
 * Throw [IllegalStateException] if [lock] fails.
 */
public inline fun <T> DrawingSurface.locking(block: context(JniEnv) () -> T): T {
    val res = lock()
    check(res and JAWT.LOCK_ERROR == 0) { "Error locking surface" }

    try {
        return block(env)
    } finally {
        unlock()
    }
}

/**
 * Gets a [DrawingSurfaceInfo] and executes [block], then release the surface info.
 *
 * Throw [IllegalStateException] if [getInfo] fails.
 */
public inline fun <T> DrawingSurface.useInfo(block: (DrawingSurfaceInfo) -> T): T {
    val info = checkNotNull(getInfo()) { "Error getting surface info" }

    try {
        return block(info)
    } finally {
        freeInfo(info)
    }
}

/**
 * Alias for stacking [Awt.useDrawingSurface], [DrawingSurface.locking] and [DrawingSurface.useInfo].
 */
context(env: JniEnv)
public inline fun <T> Awt.useDrawingSurfaceInfo(target: JAwtComponent, block: DrawingSurface.(DrawingSurfaceInfo) -> T): T {
    return useDrawingSurface(target) { surface ->
        surface.locking {
            surface.useInfo { info ->
                surface.block(info)
            }
        }
    }
}