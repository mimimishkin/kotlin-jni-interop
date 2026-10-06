# Kotlin/Native JNI interop

[![Maven Central](https://img.shields.io/maven-central/v/io.github.mimimishkin/jni-binding.svg)](https://central.sonatype.org/artifact/io.github.mimimishkin/jni-binding)
![Kotlin](https://img.shields.io/badge/Kotlin-%E2%89%A52.4.20-7F52FF)

![Kotlin mingwX64](https://img.shields.io/badge/Kotlin-mingwX64-4287f5)
![Kotlin macosArm64](https://img.shields.io/badge/Kotlin-macosArm64-f5d042)
![Kotlin linuxX64](https://img.shields.io/badge/Kotlin-linuxX64-f54242)
![Kotlin linuxArm64](https://img.shields.io/badge/Kotlin-linuxArm64-f54242)

**Write JNI libraries in Kotlin on both sides.** The native part is ordinary Kotlin/Native, the JVM and Android part is
ordinary Kotlin. No C, no handwritten JNI signatures, no `cinterop.def` for the boundary — and the compiler checks
that the two sides match, on every build, for every platform you ship.

```
  Kotlin/Native                               JVM / Android
  @JniActual fun hello(): JString    ←———→    @JniExpect external fun hello(): String
```

- `@JniActual` marks a native implementation, `@JniExpect` the JVM `external` declaration that it implements.
- The **producer** plugin builds the native library. The **consumer** plugin validates the JVM side against it,
  packages the binaries per platform, and rebuilds everything when the native code changes.
- A mismatch between the two sides is a **compile error**, not an `UnsatisfiedLinkError` in production.

Reach for it when part of your app has to be native — an image or audio codec, a crypto or compression library, a
physics kernel, a port of existing C code, one hot loop — and you would rather not own a C toolchain and a pile of
unchecked JNI glue to get there.

Everything this project supports is shown end to end in [`samples/basic`](samples/basic). Use this README as a map and
that sample as the full reference.

## Contents

- [Why](#why)
- [What you write instead](#what-you-write-instead)
- [What you get](#what-you-get)
- [Quick start](#quick-start)
- [Configuration](#configuration)
- [Annotations](#annotations)
- [Critical natives](#critical-natives)
- [Libraries](#libraries)
- [Samples](#samples)

## Why

JNI is a low-level interface, and low-level interfaces do not scale to a real codebase. Three things go wrong, every
time.

**1. The signature is a string that nobody checks.**

```c
JNIEXPORT jstring JNICALL Java_org_sample_Hello_hello(JNIEnv *env, jobject self) {
    return (*env)->NewStringUTF(env, "Hello");
}
```

The class name, the function name and the descriptor `()Ljava/lang/String;` are three places to keep in sync and
zero places the compiler looks at. Rename a Kotlin function and the C file still compiles, the jar still builds, and
the failure arrives as `UnsatisfiedLinkError: No implementation found for ...` on a user's machine — for one OS, or
one ABI, or one device.

**2. The native side is C, so you write your program twice.**

`JNIEnv*`, `jobject`, `jstring`, UTF-8 versus UTF-16, local reference frames, `ReleaseStringUTFChars`.
The algorithm is fine; everything around it is bookkeeping in a language your JVM side cannot see.

**3. The artifacts are hand-assembled.**

One binary per OS and per ABI, each copied into the right place inside the jar or the APK, plus the ABI list, plus the
startup code that works out which binary this machine needs and unpacks it.

This project removes all three.

## What you write instead

The native side, in Kotlin/Native:

```kotlin
@JniActuals(className = "org.sample.Hello")
object Hello {
    context(env: JniEnv)
    fun hello(): JString? = "Hello from Kotlin/Native".toJString()
}
```

The JVM side, in Kotlin:

```kotlin
@JniExpect
external fun hello(): String
```

That is the whole contract. The JNI symbol name and the descriptor are derived from the Kotlin declarations on both
sides, checked against each other while the consumer compiles, and packaged into the artifact automatically.

|                               | Hand-written JNI                  | Here                                                            |
|-------------------------------|-----------------------------------|-----------------------------------------------------------------|
| Native code                   | C / C++                           | Kotlin/Native                                                   |
| Symbol names, descriptors     | written by hand                   | derived from the Kotlin declarations                            |
| Contract mismatch             | `UnsatisfiedLinkError` at runtime | compile error                                                   |
| Per-OS / per-ABI binaries     | copied by hand                    | packaged per target by the plugin                               |
| Rebuild after a native change | your build script                 | automatic, and the JVM side is revalidated                      |
| Runtime loading               | `System.load(...)` per module     | your `@LoadMethod`, called from the class initializer by plugin |

## What you get

**The contract is checked, per platform.** Bindings are matched by full JVM class name and function name, and the
signature is derived from the Kotlin types — overloads, extensions, `infix`, nested classes, nullable parameters,
`Array<Float>` → `java.lang.Float[]` all work. When platforms differ, say so per target
(`@JniExpect("linuxX64")`), and every target is validated against its own set of bindings.

**The native side is Kotlin.** Your native code is Kotlin/Native: collections, data classes, exceptions,
coroutines — and `jni-binding` wraps the JNI API into null-safe Kotlin around it: `String.toJString()`,
`JString.toKString()`, typed arrays, allocation scopes requested as extra `context` parameters, field and method IDs, 
DSL. You still use cinterop when you call third-party C APIs — that is ordinary Kotlin/Native work — but the JNI
boundary itself is two annotations.

**You can start from either side.** Write the JVM API first and run `./gradlew :app:generateJniActuals`: the stubs for
the missing `@JniActual`s are generated into the producer.

**Performance when you need it.** `@CriticalNative` binds a function to JNI's critical path — the fastest way to call
native code on Android/ART. See [Critical natives](#critical-natives) for what it costs on desktop JVMs.

## Quick start

Two modules: a native **producer** and a JVM **consumer**.

### Native module

```kotlin
// native/build.gradle.kts

plugins {
    kotlin("multiplatform")
    id("io.github.mimimishkin.jni-binding-producer") version "2.0.0"
}

kotlin {
    jvmToolchain(17)
    mingwX64().binaries.sharedLib("native") {   // or linuxX64() / macosArm64() / ...
        linkJvm()                               // desktop only; Android needs no linkJvm()
    }
}

jniLibraries {
    expectedJdkVersion = 17
}
```

```kotlin
// native/src/nativeMain/kotlin/Hello.kt

@JniActual(className = "org.sample.HelloKt")
context(env: JniEnv, autofreeScope: AutofreeScope)
fun hello(): JString? = "Hello from Kotlin/Native".toJString()
```

`context(env: JniEnv)` (and an allocation scope such as `AutofreeScope`) are optional — add them only when the
body needs them.

### JVM module

```kotlin
// app/build.gradle.kts

plugins {
    kotlin("jvm")
    id("io.github.mimimishkin.jni-binding-consumer") version "2.0.0"
}

kotlin {
    jvmToolchain(17)

    target { // or jvm { ... } in a multiplatform project
        compilations.named("main") {
            jniLibraries.create("native") {     // same name as sharedLib("native")
                mingwX64 {
                    fromProducer(project(":native")) // sync natives
                    copyToResources()                // puts the binary into jar resources
                }
            }
        }
    }
}
```

```kotlin
// app/src/main/kotlin/org/sample/Hello.kt

@JniExpect
external fun hello(): String

@LoadMethod
private fun load(os: String, arch: String) {
    // `os` receives the OS family ("windows", "linux", "macos", "android"),
    // `arch` the normalized architecture ("x86_64", "aarch64", ...).

    // After copyToResources() the binary is at /natives/$os-$arch/...
    // Copy it out of the jar and System.load(...). Full example: samples/basic.
}
```

That is the whole contract: `@JniExpect` on the JVM side is implemented by `@JniActual` on the native side.

Tip: declare `@JniExpect` first, then run `./gradlew :app:generateJniActuals` to stub the missing `@JniActual`s
into the producer.

### Naming

A top-level function in `Hello.kt`, package `org.sample`, binds to the JVM file facade `org.sample.HelloKt`.
Group several functions with `@JniActuals` so you write the class name once:

```kotlin
@JniActuals(className = "org.sample.HelloKt")
object Hello {
    context(env: JniEnv, autofreeScope: AutofreeScope)
    fun hello(): JString? = "Hello".toJString()
}
```

## Configuration

### Producer (`jniLibraries` on the native project)

```kotlin
jniLibraries {
    expectedJdkVersion = 17                     // JDK this library is built for (ignored on Android)
    exportMethod = JniExportMethod.RegisterNatives  // preferred; default is ExposeFunctions
    allowSeveralHooks = false                   // several @JniOnLoad / @JniOnUnload?
}
```

On **desktop**, call `linkJvm()` inside `sharedLib { ... }`. On **Android**, do not — linking is automatic:

```kotlin
androidNativeArm64().binaries.sharedLib("native")
```

Need AWT? Call `linkJAwt()` after `linkJvm()` (and `linkX11IfLinux()` on Linux). For a non-host desktop target,
pass a target JDK with `linkJvm(downloadCompatibleJdk())` — see [`samples/basic`](samples/basic).

### Consumer (`jniLibraries.create` on the JVM / Android compilation)

```kotlin
jniLibraries.create("native") {
    mingwX64 {
        fromProducer(project("native"))     // build here
        copyToResources("natives/$os-$arch")       // copy lib into jar resources / Android assets
    }
    androidArm32 {
        fromPrebuiltBinding(prebuiltDir)     // or take a binary built elsewhere
        copyToJniLibs()      // Android only: copy to jniLibs/<abi>/ for System.loadLibrary
    }

    allowExtraActuals = false
    allowAbsentBindings = false
}
```

Targets: `mingwX64()`, `linuxX64()`, `linuxArm64()`, `macosArm64()`, `androidX86()`, `androidX64()`,
`androidArm32()`, `androidArm64()`.

A build can only **link** the host OS, so the usual pattern is: build the host from the producer, take other
platforms from a prebuilt folder (as in [`samples/basic`](samples/basic)).

### Dependencies

Both plugins add their artifacts to the default source sets. With a custom source set hierarchy you may need to
add them by hand:

```kotlin
// producer
implementation("io.github.mimimishkin:jni-binding:2.0.0")
implementation("io.github.mimimishkin:jni-binding-annotations:2.0.0")
// consumer
implementation("io.github.mimimishkin:jni-binding-annotations:2.0.0")
```

### Export methods

How `external` methods find their native implementations:

- **`RegisterNatives`** (recommended) — register on library load; one load serves every class.
- **`ExposeFunctions`** (default) — export `Java_...` symbols by name; each class that declares natives must load the
  library. Required for *critical natives* on the JDK — see [Critical natives](#critical-natives).

## Annotations

**JVM**

- `@JniExpect` / `@JniExpects` — this `external` member (or whole class) is implemented via JNI.
  Optional target filter: `@JniExpect("mingwX64")`.
- `@LoadMethod` — function that loads the native library (runs from the object's / companion's initializer).
- `@CriticalNative` — critical native (for static non-synchronized functions; primitives and primitive arrays only).

**Native**

- `@JniActual` / `@JniActuals` — native implementation of an `@JniExpect`.
- `@JniOnLoad` / `@JniOnUnload` — library load / unload hooks.
- `@WithJvmType` / `@WithJvmSignature` — override the derived JVM type or full signature when needed.
- `@CriticalNative` — critical native (no `JniEnv` / `JObject`; primitives and primitive arrays only).

See [`samples/basic`](samples/basic) for every supported shape.

## Critical natives

This is optimization for faster JNI function calls. A `@CriticalNative` is called **without a `JniEnv` and without a
class/object reference**. Only primitives / primitive arrays (On JDK only - each array arrives as a `(length, pointer)`
pair), `static` and non-`synchronized` on the JVM side. Also, the method must never call back into the JVM.

```kotlin
// native
@CriticalNative
@JniActual
fun sum(size: Int, values: CArrayPointer<IntVar>): Long   // (length, pointer)

// JVM
@CriticalNative
@JniExpect
external fun sum(values: IntArray): Long    // int[]
```

**Desktop (HotSpot).** Undocumented and unsupported: deprecated in JDK 16, removed in JDK 22.
The `JavaCritical_` path needs JDK 21 or older, `-XX:+CriticalJNINatives` option, and a JIT-compiled call site
(128 calls on my machine); until then — and on newer JDKs — the ordinary `Java_` facade runs.
Works only with [`ExposeFunctions`](#export-methods) export method. Prefer Project Panama for new code.

**Android (ART).** First-class support — see
[`CriticalNative`](https://developer.android.com/reference/dalvik/annotation/optimization/CriticalNative) and
[JNI tips](https://developer.android.com/ndk/guides/jni-tips#faster-native-calls-with-fastnative-and-criticalnative).
Both `RegisterNatives` and `ExposeFunctions` work (`minSdk` 26+).

See `basic` and `android-basic` samples for reference.

## Libraries

| Artifact                                       | Role                                                             |
|------------------------------------------------|------------------------------------------------------------------|
| [`jni-binding`](jni-binding/Module.md)         | Idiomatic JNI API for Kotlin/Native .                            |
| [`jawt-binding`](jawt-binding/Module.md)       | JAWT / AWT native interface; link with `linkJAwt()`.             |
| [`jni-binding-raw`](jni-binding-raw/Module.md) | Raw cinterop of the JDK headers (`jni` package), if you need it. |

## Samples

- [`samples/basic`](samples/basic) — full feature tour and multi-platform producer / prebuilt setup.
- [`samples/android-basic`](samples/android-basic) — Android app across the JNI boundary: calling the Java API from
  native, exceptions, memory and threads, critical natives.
- [`samples/windows-registry`](samples/windows-registry) — Windows Registry wrapper (`mingwX64` only): a typed
  `Registry` API over the win32 `Reg*` functions, ~300 lines of Kotlin per side, no C and no JNI signatures anywhere.

## License

[MIT](LICENSE.txt)