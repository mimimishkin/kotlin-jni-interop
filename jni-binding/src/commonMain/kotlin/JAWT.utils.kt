@file:Suppress("NOTHING_TO_INLINE")

package io.github.mimimishkin.jni

import kotlinx.cinterop.memScoped

context(env: JniEnv)
public inline fun <T> withAwt(version: AwtVersion = JAWT.lastVersion, block: (Awt) -> T): T {
    return memScoped {
        val awt = env.GetAwt(version)
        if (awt != null) {
            block(awt)
        } else {
            throw IllegalStateException("AWT not found")
        }
    }
}

context(env: JniEnv)
public inline fun <T> Awt.withLock(block: () -> T): T {
    Lock()
    try {
        return block()
    } finally {
        Unlock()
    }
}

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