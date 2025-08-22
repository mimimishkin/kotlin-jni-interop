package io.github.mimimishkin.jni.binding.awt

import jni.JNINativeInterface_
import jni._jobject
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVarOf
import kotlinx.cinterop.CStructVar

@Suppress("ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT")
public actual typealias _Awt = jni.jawt

@Suppress("ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT")
public actual typealias _DrawingSurface = jni.jawt_DrawingSurface

@Suppress("ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT")
public actual typealias _DrawingSurfaceInfo = jni.jawt_DrawingSurfaceInfo

public actual typealias AwtRectangle = jni.jawt_Rectangle