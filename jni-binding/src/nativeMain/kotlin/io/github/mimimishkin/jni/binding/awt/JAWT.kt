package io.github.mimimishkin.jni.binding.awt

import jni.JAWT_LOCK_BOUNDS_CHANGED
import jni.JAWT_LOCK_CLIP_CHANGED
import jni.JAWT_LOCK_ERROR
import jni.JAWT_LOCK_SURFACE_CHANGED
import jni.JAWT_VERSION_1_3
import jni.JAWT_VERSION_1_4
import jni.JAWT_VERSION_1_7
import jni.JAWT_VERSION_9

/**
 * Functions and utilities to work with JAWT (Java Abstract Window Toolkit).
 */
public object JAWT {
    /**
     * Java version *1.3*.
     */
    public inline val v3: AwtVersion get() = JAWT_VERSION_1_3

    /**
     * Java version *1.4*.
     */
    public inline val v4: AwtVersion get() = JAWT_VERSION_1_4

    /**
     * Java version *1.7*.
     */
    public inline val v7: AwtVersion get() = JAWT_VERSION_1_7

    /**
     * Java version *9*.
     */
    public inline val v9: AwtVersion get() = JAWT_VERSION_9

    /**
     * Returns by [DrawingSurface.lock] when an error has occurred, and the surface could not be locked.
     */
    public inline val LOCK_ERROR: Int get() = JAWT_LOCK_ERROR

    /**
     * Returns by [DrawingSurface.lock] when the clip region has changed.
     */
    public inline val LOCK_CLIP_CHANGED: Int get() = JAWT_LOCK_CLIP_CHANGED

    /**
     * Returns by [DrawingSurface.lock] when the bounds of the surface have changed.
     */
    public inline val LOCK_BOUNDS_CHANGED: Int get() = JAWT_LOCK_BOUNDS_CHANGED

    /**
     * Returns by [DrawingSurface.lock] when the surface itself has changed
     */
    public inline val LOCK_SURFACE_CHANGED: Int get() = JAWT_LOCK_SURFACE_CHANGED
}