# Module jawt-binding

Kotlin/Native bindings for JAWT - the Java Abstract Window Toolkit native interface (`jawt.h`). The module wraps the
C API into idiomatic Kotlin on top of `jni-binding`, so a native application can render into a `java.awt.Component`
or hand a native window over to AWT without any manual cinterop configuration.

**Desktop only** (mingwX64, linuxX64, linuxArm64, macosArm64). Android has no AWT and is not a target of this module.

The module depends on `jni-binding` (and transitively on `jni-binding-raw` for the raw JDK headers), so everything from
the JNI core - [JniEnv], [JNI], the null-safe reference types - is available as usual. Link `libjawt` with
`linkJAwt()` of the producer plugin (call it after `linkJvm()`, which it reuses). On Linux also call
`linkX11IfLinux()` when the AWT runtime needs X11.

These interfaces are **not** part of the Java SE specification; a VM is not required to implement them. See the
[JAWT documentation](https://docs.oracle.com/en/java/javase/22/docs/technotes/guides/awt/AWT_Native_Interface.html).

# Package io.github.mimimishkin.jni.binding.awt

Bindings for JAWT - access to the native structures behind AWT (`java.awt`), used for native rendering onto a
component or for hand-off of a native window to AWT.

## Constants and versions

All constants live on the [JAWT] object:

- Versions: [JAWT.v3] (1.3), [JAWT.v4] (1.4), [JAWT.v7] (1.7), [JAWT.v9] (9). Pass one to [getAwt] /
  [withAwt]; newer versions unlock extra entry points (`Lock`/`Unlock`/`GetComponent` from 1.4,
  embedding from 9).
- Lock result flags returned by [DrawingSurface.lock]: [JAWT.LOCK_ERROR], [JAWT.LOCK_CLIP_CHANGED],
  [JAWT.LOCK_BOUNDS_CHANGED], [JAWT.LOCK_SURFACE_CHANGED].

## Typical rendering loop

Obtain the [Awt] interface, take a [DrawingSurface] for a `java.awt.Component` (usually a `Canvas` or `Window`),
lock it, and read the platform-specific [DrawingSurfaceInfo]. Session helpers acquire and release everything for you:

```kotlin
// context(env: JniEnv, memScope: MemScope)
withAwt(JAWT.v9) { awt ->
    awt.useDrawingSurface(component) { surface ->
        surface.locking {
            surface.useInfo { info ->
                // info.bounds, info.clipRects, ...
                // platform members: hwnd/hdc on Windows, display/drawable on Linux, layer on macOS
                // draw...
            }
        }
    }
}
```

Or in one call: [Awt.useDrawingSurfaceInfo].

All operations on a [DrawingSurface] **must** run on the same thread that obtained it via [getDrawingSurface].
When calling `lock` / `unlock` / `getInfo` / `freeInfo` from another thread, set [DrawingSurface.env] to that
thread's `JniEnv` first.

## Session helpers

| Helper                      | Role                                            |
|-----------------------------|-------------------------------------------------|
| [withAwt]                   | [getAwt] + throw if the VM does not expose JAWT |
| [Awt.locking]               | lock / unlock the entire AWT (since 1.4)        |
| [Awt.useDrawingSurface]     | [getDrawingSurface] + [freeDrawingSurface]      |
| [DrawingSurface.locking]    | [lock] + [unlock]; throws on [JAWT.LOCK_ERROR]  |
| [DrawingSurface.useInfo]    | [getInfo] + [freeInfo]                          |
| [Awt.useDrawingSurfaceInfo] | surface + lock + info stacked together          |

Low-level entry points (`getAwt`, `getDrawingSurface`, `lock`, `getInfo`, …) remain available when you need finer
control or want to cache the surface across frames.

## Embedding a native window into AWT

Since JAWT 9, a native container can host an AWT `Frame`:

- [Awt.createEmbeddedFrame] - create a `java.awt.Frame` inside a native parent (HWND / Drawable / NSWindow).
- [Awt.setBounds] - move/resize that embedded frame relative to the native parent (`Component.setBounds` alone
  keeps embedded frames at `(0, 0)` for compatibility).
- [Awt.synthesizeWindowActivation] - synthesize activate / deactivate for the embedded frame.

The reverse direction - resolve a `java.awt.Component` from a native handle - is [Awt.getComponent] (since 1.4).

Platform handles passed to embedding / `getComponent`:

| Platform | Handle         |
|----------|----------------|
| Windows  | `HWND`         |
| Linux    | X11 `Drawable` |
| macOS    | `NSWindow`     |

## Platform-specific [DrawingSurfaceInfo] members

Each desktop source set exposes the native structure members directly on [DrawingSurfaceInfo] - no
`platformInfo` cast:

**Windows** (`mingwMain`):

- [hwnd] - window handle (mutually exclusive with [hbitmap] / [pbits])
- [hbitmap] - DDB handle
- [pbits] - DIB bits pointer
- [hdc] - preferred device context (use instead of `BeginPaint` / `GetDC`)
- [hpalette]

**Linux** (`linuxMain`):

- [drawable], [display], [visualID], [colormapID], [depth]
- [GetAWTColor] - RGB → pixel for paletted modes (since 1.4)

**macOS** (`macosMain`):

- [layer] / [windowLayer] - `CALayer` of the surface / window

## Types

- [Awt] - the JAWT interface pointer returned by [getAwt].
- [DrawingSurface] - drawing surface of a [JAwtComponent]; must be freed with [freeDrawingSurface].
- [DrawingSurfaceInfo] - bounds, clip rectangles ([clipRects] / [clipSize]) and platform info; must be freed with
  [freeInfo].
- [AwtRectangle] - `{ x, y, width, height }` used by bounds and clip.
- [JAwtComponent] / [JAwtFrame] - typed [JObject] aliases for `java.awt.Component` / `java.awt.Frame`.
