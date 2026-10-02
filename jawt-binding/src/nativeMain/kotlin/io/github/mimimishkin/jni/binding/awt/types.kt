@file:Suppress("ClassName")

package io.github.mimimishkin.jni.binding.awt

import io.github.mimimishkin.jni.binding.JRef
import io.github.mimimishkin.jni.binding.JniEnv

import io.github.mimimishkin.jni.binding.annotation.WithJvmType
import io.github.mimimishkin.jni.binding._jobject
import kotlinx.cinterop.*

/**
 * The underlying type for [Awt].
 *
 * We need this type to be able to use [Awt] in consumer common code without cinterop commonization.
 */
public expect class Raw_Awt : CStructVar {
    @PublishedApi internal var version: Int
    internal var SynthesizeWindowActivation: CPointer<CFunction<(CPointer<jni.JNIEnvVar>?, CPointer<jni._jobject>?, UByte) -> Unit>>?
    internal var SetBounds: CPointer<CFunction<(CPointer<jni.JNIEnvVar>?, CPointer<jni._jobject>?, Int, Int, Int, Int) -> Unit>>?
    internal var FreeDrawingSurface: CPointer<CFunction<(CPointer<Raw_DrawingSurface>?) -> Unit>>?
    internal var Unlock: CPointer<CFunction<(CPointer<jni.JNIEnvVar>?) -> Unit>>?
    internal var Lock: CPointer<CFunction<(CPointer<jni.JNIEnvVar>?) -> Unit>>?
    internal var GetDrawingSurface: CPointer<CFunction<(CPointer<jni.JNIEnvVar>?, CPointer<jni._jobject>?) -> CPointer<Raw_DrawingSurface>?>>?
    internal var CreateEmbeddedFrame: CPointer<CFunction<(CPointer<jni.JNIEnvVar>?, CPointer<out CPointed>?) -> CPointer<jni._jobject>?>>?
    internal var GetComponent: CPointer<CFunction<(CPointer<jni.JNIEnvVar>?, CPointer<out CPointed>?) -> CPointer<jni._jobject>?>>?
}

/**
 * AWT native interface.
 *
 * The AWT native interface allows a native application as a means by which to access native structures in AWT. This is
 * to facilitate moving legacy C and C++ applications to Java and to target the needs of the developers who need to do
 * their own native rendering to canvases for performance or other reasons.
 *
 * Conversely, it also provides mechanisms for an application which already has a native window to provide that to AWT
 * for AWT rendering.
 *
 * Since every platform may be different in its native data structures and APIs for windowing systems, the application
 * must have necessarily provided a per-platform source and compile and deliver per-platform native code to use this
 * API.
 *
 * These interfaces are not part of the Java SE specification, and a VM is not required to implement this API.
 * However, it is strongly recommended that all implementations which support headful AWT also support these interfaces.
 */
public typealias Awt = CPointer<Raw_Awt>

/**
 * Java version that corresponds to the new JAWT API.
 */
public typealias AwtVersion = Int

/**
 * The underlying type for [DrawingSurface].
 *
 * We need this type to be able to use [DrawingSurface] in consumer common code without cinterop commonization.
 */
public expect class Raw_DrawingSurface : CStructVar {
    @PublishedApi internal var env: CPointer<jni.JNIEnvVar>?
    @PublishedApi internal var target: CPointer<jni._jobject>?
    internal var GetDrawingSurfaceInfo: CPointer<CFunction<(CPointer<Raw_DrawingSurface>?) -> CPointer<Raw_DrawingSurfaceInfo>?>>?
    internal var Unlock: CPointer<CFunction<(CPointer<Raw_DrawingSurface>?) -> Unit>>?
    internal var Lock: CPointer<CFunction<(CPointer<Raw_DrawingSurface>?) -> Int>>?
    internal var FreeDrawingSurfaceInfo: CPointer<CFunction<(CPointer<Raw_DrawingSurfaceInfo>?) -> Unit>>?
}

/**
 * Contains the underlying drawing information of a component.
 *
 * All operations on a [DrawingSurface] MUST be performed from the same thread as the call to [getDrawingSurface].
 */
public typealias DrawingSurface = CPointer<Raw_DrawingSurface>

/**
 * The underlying type for [DrawingSurfaceInfo].
 *
 * We need this type to be able to use [DrawingSurfaceInfo] in consumer common code without cinterop commonization.
 */
public expect class Raw_DrawingSurfaceInfo : CStructVar {
    @PublishedApi internal val bounds: AwtRectangle
    @PublishedApi internal var clip: CPointer<AwtRectangle>?
    @PublishedApi internal var clipSize: Int
    @PublishedApi internal var platformInfo: CPointer<out CPointed>?
    @PublishedApi internal var ds: CPointer<Raw_DrawingSurface>?
}

/**
 * Contains the underlying drawing information of a component.
 */
public typealias DrawingSurfaceInfo = CPointer<Raw_DrawingSurfaceInfo>

/**
 * Structure for a native rectangle.
 */
public expect class AwtRectangle : CStructVar {
    /**
     * X coordinate of an upper-left corner.
     */
    public var x: Int

    /**
     * Y coordinate of an upper-left corner.
     */
    public var y: Int

    /**
     * Width of a rectangle.
     */
    public var width: Int

    /**
     * Height of a rectangle.
     */
    public var height: Int
}

/**
 * The underlying `open` opaque type for [JAwtComponent].
 *
 * This type must not be used directly, it's only for type-safety of [JRef] usage.
 */
public open class _jawtComponent(rawPtr: NativePtr) : _jobject(rawPtr)

/**
 * Pointer to `java.awt.Component`.
 */
public typealias JAwtComponent = @WithJvmType("java.awt.Component") JRef<_jawtComponent>

/**
 * The underlying `open` opaque type for [JAwtFrame].
 *
 * This type must not be used directly, it's only for type-safety of [JRef] usage.
 */
public open class _jawtFrame(rawPtr: NativePtr) : _jawtComponent(rawPtr)

/**
 * Pointer to `java.awt.Frame`.
 */
public typealias JAwtFrame = @WithJvmType("java.awt.Frame") JRef<_jawtComponent>