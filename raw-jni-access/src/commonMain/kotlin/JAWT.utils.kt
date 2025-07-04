@file:Suppress("NOTHING_TO_INLINE")

package io.github.mimimishkin.jni

import kotlinx.cinterop.memScoped

context(env: JNIEnv)
public inline fun <T> withAwt(version: JAWT.Version = JAWT.Version.VERSION_9, block: (JAwt) -> T): T {
    return memScoped {
        val awt = env.GetAwt(version)
        if (awt != null) {
            block(awt)
        } else {
            throw IllegalStateException("AWT not found")
        }
    }
}

context(env: JNIEnv)
public inline fun <T> JAwt.withLock(block: () -> T): T {
    Lock(env)
    try {
        return block()
    } finally {
        Unlock(env)
    }
}

context(env: JNIEnv)
public inline fun <T> JAwt.useDrawingSurface(target: JObject, block: (DrawingSurface) -> T): T {
    val surface = GetDrawingSurface(env, target)
    if (surface == null)
        throw IllegalStateException("DrawingSurface not found")

    try {
        return block(surface)
    } finally {
        FreeDrawingSurface(surface)
    }
}

public inline fun <T> DrawingSurface.withLock(block: () -> T): T {
    val res = Lock()
    if (res and JAWT.LOCK_ERROR != 0)
        throw IllegalStateException("Error locking surface")

    try {
        return block()
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