# Module jni-binding

Kotlin/Native bindings for the Java Native Interface (JNI). The module wraps the C JNI API into idiomatic Kotlin —
null-safe types, `context(JniEnv)` parameters, DSLs and small helpers — so the native side of a JNI library can be
written without a single line of C and without any manual cinterop configuration.

Combine it with the `jni-binding-annotations` module and the producer/consumer Gradle plugins to keep the JVM side and
the native side of your bindings in sync automatically, as shown in the repository
[README](https://github.com/mimimishkin/kotlin-jni-interop#readme). Every function in this module mirrors its JNI
counterpart one-to-one; see the
[JNI Specification](https://docs.oracle.com/en/java/javase/22/docs/specs/jni/functions.html) for the details.

## Requirements

- Kotlin ≥ 2.4.20.
- A JVM to run the library against.

# Package io.github.mimimishkin.jni.binding

Core bindings: all JNI types, the `JNI` object with version constants, the Invocation API (`createJavaVM`,
`attachCurrentThread`, ...), every function of the JNI `JniEnv` interface and small Kotlin utilities around them.

## Getting a JNI environment

JNI functions are extension functions that require a `JniEnv` in context (`context(env: JniEnv)`). 
Use [JNI.createJavaVM] to embed a JVM into a native application, or [JavaVM.getEnv] / [JavaVM.attachCurrentThread] to
obtain an environment for an already running VM.

The JVM provides the environment for methods exported to Java, e.g. inside an `@JniActual` function
(`context(env: JniEnv)` is optional and is injected automatically if you declare it). When the library is embedded
natively via the Invocation API, a `JavaVM` is obtained from [JNI.javaVMs], then
`vm.attachCurrentThread(JNI.v21).use { ... }` scopes the environment (see [JniEnv.use]).

All constants of a given JNI version live in the [JNI] object.

## Types

JNI types are null-safe and grouped by purpose:

- **Primitives**: [JBoolean], [JByte], [JChar], [JShort], [JInt], [JLong], [JFloat], [JDouble], [JVoid]. A `boolean`
  is an unsigned `UByte` — pass Kotlin primitives as-is in most APIs (`Boolean`, `Char`, ...), JNI object types
  (`JBoolean`, `JChar`) only where the raw JNI representation is meaningful. Conversion helpers exist where the value
  crosses the boundary: `String.toJString()` / `JString.toKString()`, `JByteArray.toKArray()` /
  `ByteArray.toJArray()`, etc.

- **Object references**: [JObject], [JClass], [JThrowable], [JString], [JArray] and the typed arrays
  [JBooleanArray]..[JDoubleArray], [JObjectArray], [JWeak] (not counted by GC), [JByteBuffer] (direct buffers).
  They behave like typed pointers — compare with `isSame`, check type with `instanceOf`, read `refType`
  ([JObjectRefType]), and convert freely with `unsafeCast()` only when you know the type.

- **IDs**: [JFieldID], [JMethodID] obtained from [fieldId] / [staticFieldId] / [methodId] / [staticMethodId].

- **Arguments**: [JValue] is the union for passing arguments to a Java method. Use [jArgs] to build a [JArguments]
  array:

  ```kotlin
  val args = jArgs(3) { int(42); ref(jString); ref(null) }
  ```

- **VM types**: [JavaVM], [JniEnv], [JavaVMInitArgs], [JavaVMOption], [JniVersion]. Build init args with
  `buildJavaVMInitArgs(JNI.v21, n) { option(...) }`.

- **Native method registration**: [JniNativeMethod] and the [registerNatives] DSL:
  `clazz.registerNatives(1) { register("impl".utf8, "(Ljava/lang/String;)I".utf8, staticCFunction { ... }) }`.

## Functions

Using most of the functions is similar to their usage in C++: `env->FindClass(className.c_str())` becomes
`findClass(className.utf8)`. The differences are:

1. Type safety — no `jobject` casts, null-safety throughout.
2. Functions that return a JNI error code throw a Kotlin exception instead of returning the code.
3. Everything is a top-level extension function requiring `context(env: JniEnv)`, so the environment is never passed
   explicitly.
4. Strings crossing into JNI must be null-terminated modified UTF-8 — the extension [String.modifiedUtf8] encodes
   them correctly (a Kotlin `'\u0000'` becomes the two-byte `0xC0 0x80` sequence and non-BMP characters are encoded as
   surrogate pairs). Standard `String.utf8` differs from modified UTF-8 only there, so it is safe when the string
   contains no `'\u0000'` and no characters beyond the Basic Multilingual Plane.

The full `JniEnv` interface is covered: class operations (`findClass`, `defineClass`, `superclass`,
`isAssignableFrom`, `registerNatives`/`unregisterNatives`, `module` since JDK 9), exceptions (`throwEx`, `throwNew`,
`pendingException`, `handleJvmException`, `printStackTrace`, `clearException`, `isExceptionThrown`, `fatalError`),
local references (`deleteLocalRef`, `newLocalRef`, `pushLocalFrame`, `popLocalFrame`, `refFrame`/`fromRefFrame`),
monitors (`monitorEnter`, `monitorExit`), strings (`newString`, `length`, `getChars`, `getUTFChars`, `getRegion`,
`getUTFRegion`, `utfLength`, `utfLengthLong` since JDK 24, `getCharsCritical`), arrays (`newXArray`, `getRegion`,
`setRegion`, `getElements`, `releaseElements` with an [ApplyChangesMode], `getElementsCritical`), object fields and
methods (see [accessors](#package-iogithubmimimishkinjnibindingaccessors) for the typed variants), reflection
(`fromReflectedMethod`, `toReflectedMethod`, ...), direct buffers (`newDirectByteBuffer`, `address`, `capacity`) and
`isVirtualThread` since JDK 21.

## Utilities

- `String.modifiedUtf8` — correct modified UTF-8 encoding described above.
- [JniEnv.use] / [JavaVM.useEnv] — scope an environment together with a native `MemScope` so allocations inside
  (JDK `utf8` values, [jArgs] arrays, ...) are freed automatically.
- [JavaVM.withEnv] / [JavaVM.withEnvAttaching] — obtain an environment for the current thread and detach when done.
- [jniLazy] / [JniLazy] — lazily initialized values whose `context(JniEnv, MemScope)` initializer runs once, in an
  attached environment, with thread-safe (synchronized) initialization and a global value.
- [localIntoWeakRef] / [localIntoGlobalRef] — elevate a local reference without manual `newGlobalRef` bookkeeping.

Error codes from the Invocation API and `registerNatives`/monitors are wrapped into appropriate Kotlin exceptions by
`JNI.safeCall` (internal).

# Package io.github.mimimishkin.jni.binding.accessors

High-level typed access to Java methods, fields and arrays — the `JniEnv` counterpart in the form of Kotlin lambdas,
`ReadWriteProperty` delegates and scoped DSLs. Everything here stays `context(JniEnv, MemScope)`-friendly.

## Methods

Fetch a method ID once, then call it as an ordinary function with reified types — no `jArgs`, no `call<Type>Method`:

```kotlin
// requires JniEnv access (and a NativePlacement scope)
val add = clazz.methodId("add".utf8, "(II)I".utf8)!!.asMethod<Int, Int, Int>()
val result = obj.add(2, 3) // 5
```

- [asMethod] — instance methods (0 to 10 typed parameters, any return type).
- [asStaticMethod] — the same for static methods, receiver is the class.
- [asConstructor] — constructors, receiver is the class, returns `JObject?`.
- [asNonvirtualMethod] — non-virtual dispatch: the first parameter is the `JClass` to dispatch on.

## Fields

Get or set a field through a Kotlin `ReadWriteProperty` delegate — bind it once and use it like a property:

```kotlin
// requires JniEnv access (and an allocation scope for the utf8 name)
val jName by obj.stringField("name".utf8)!!                 // delegate for the JString? "name" field
var name: String? by obj.stringField("name".utf8)!!.asKotlin() // the same, as Kotlin String?
```

Instance: `objectField`, `stringField`, `booleanField`...`doubleField`, and generic `field`. Static:
`staticObjectField`, `staticStringField`, `staticIntField`... and `staticField`. Convert an object delegate to a
specific type with [asType], and to a Kotlin `String` (reading/writing `JString?`) with [asKotlin]. Fields can be
bound by `JFieldID` or, more conveniently, by `name` (+ signature for object fields).

## Arrays and strings, in place

Instead of `getElements` → mutate → `releaseElements(mode)` pairs, mutate the body in a scope and choose how changes
are applied:

```kotlin
val javaArray: JIntArray = ...
javaArray.modify({ /* onError */ }) { carray: CArrayPointer<ByteVar>, isCopy: Boolean ->
    // this: ModifyingArrayScope
    carray[0] = 42
    commit()   // copy back, keep buffer  | finalize() — copy back and free | abort() — discard
}
```

- [modify] on every `J<Primitive>Array` (with `commit()` / `finalize()` / `abort()` on [ModifyingArrayScope]),
  plus [modifyCritical] for pinning.
- [modifyChars] / [modifyUTFChars] / [modifyCritical] on a `JString`.

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

# Package io.github.mimimishkin.jni.binding.annotation

Annotations shared by the producer (`@JniActual`, `@JniActuals`, `@JniOnLoad`, `@JniOnUnload`, `@WithJvmType`,
`@WithJvmSignature`, `@CriticalNative`) and the consumer (`@JniExpect`, `@JniExpects`, `@LoadMethod`). They are
declared in the `jni-binding-annotations` module and are re-exported here for convenience; see the
[annotations reference](https://github.com/mimimishkin/kotlin-jni-interop#annotations-reference) in the repository
README for when and how to use each one.