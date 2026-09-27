# Module jawt-binding

Kotlin/Native bindings for JAWT — the Java Abstract Window Toolkit native interface. The module wraps the C
`jawt.h` API into idiomatic Kotlin on top of the `jni-binding` module, so a native application can render into a
`java.awt.Component` or hand a native window over to AWT without any manual cinterop configuration.

The module depends on `jni-binding` (and transitively on `jni-binding-raw`, which provides the raw `jni` cinterop of
the JDK headers), so everything from the JNI core — [JniEnv], [JNI], the null-safe reference types — is available as
usual. Add the `libjawt` linker option with `linkJAwt()` of the producer plugin.

## Requirements

- Kotlin ≥ 2.4.20.
- A JVM to run the library against.
- A JVM that implements the AWT native interface (it is not part of the Java SE specification).

# Package io.github.mimimishkin.jni.binding.awt

Bindings for JAWT — access to the native structures behind AWT (`java.awt`), used for native rendering onto a
component or for hand-off of a native window to AWT. All constants are grouped into the [JAWT] object (versions
[JAWT.v3], [JAWT.v4], [JAWT.v7], [JAWT.v9], lock result flags `LOCK_ERROR`, `LOCK_CLIP_CHANGED`,
`LOCK_BOUNDS_CHANGED`, `LOCK_SURFACE_CHANGED`).

Get the interface with [getAwt] (or the scoped [withAwt]), obtain the [DrawingSurface] of a component, lock it, and
read the platform-specific [DrawingSurfaceInfo] — everything is thread-safe through `Awt.locking` and
`DrawingSurface.locking`:

```kotlin
// context(env: JniEnv, memScope: MemScope)
withAwt(JNI.v21) { awt ->
    awt.useDrawingSurface(component) { surface ->
        surface.locking {
            surface.useInfo { info ->
                // info.bounds, info.clipRects, ...
                // platform-specific members (hwnd/hdc/hbitmap on Windows, ...) in the platform source sets
                // draw...
            }
        }
    }
}
```

- Session helpers: [withAwt], [Awt.locking], [Awt.useDrawingSurface], [Awt.useDrawingSurfaceInfo],
  [DrawingSurface.locking], [DrawingSurface.useInfo] — they acquire/release and lock/unlock for you.
- Embedding: [Awt.createEmbeddedFrame], [Awt.setBounds], [Awt.synthesizeWindowActivation].
- The [DrawingSurfaceInfo] in an `info.kt` platform source set (in `mingwMain`, `linuxMain`, `macosMain`) exposes the
  underlying structure members directly — e.g. `hwnd`, `hdc`, `hbitmap`, `pbits`, `hpalette` on Windows — instead of
  requiring a `platformInfo` cast.

These interfaces are not part of the Java SE specification and a VM is not required to implement them. See the
[JAWT documentation](https://docs.oracle.com/en/java/javase/22/docs/technotes/guides/awt/AWT_Native_Interface.html).
