# Kotlin/Native JNI interop

[![Maven Central](https://img.shields.io/maven-central/v/io.github.mimimishkin/jni-binding.svg)](https://central.sonatype.org/artifact/io.github.mimimishkin/jni-binding)
![Kotlin](https://img.shields.io/badge/Kotlin-%E2%89%A52.4.20-7F52FF)

![Kotlin mingwX64](https://img.shields.io/badge/Kotlin-mingwX64-4287f5)
![Kotlin macosArm64](https://img.shields.io/badge/Kotlin-macosArm64-f5d042)
![Kotlin linuxX64](https://img.shields.io/badge/Kotlin-linuxX64-f54242)
![Kotlin linuxArm64](https://img.shields.io/badge/Kotlin-linuxArm64-f54242)

Write JNI libraries in **Kotlin on both sides** — native code in Kotlin/Native, JVM code in ordinary Kotlin.
No C, no handwritten JNI signatures, no cinterop setup. The build checks that the two sides match.

Absolutely everything this project supports is shown end to end in [`samples/basic`](samples/basic).
Use this README as a map and that sample as the full reference.

```
 Native (Kotlin/Native)                         JVM
 @JniActual fun hello(): JString     ←──→       @JniExpect external fun hello(): String
```

- Mark native implementations with `@JniActual`.
- Mark JVM `external` declarations with `@JniExpect`.
- Apply the **producer** plugin to the native module and the **consumer** plugin to the JVM (or Android) module.
  Mismatches are **compile errors**, not runtime `UnsatisfiedLinkError`s.

## Contents

- [Quick start](#quick-start)
- [Configuration](#configuration)
- [Annotations](#annotations)
- [Libraries](#libraries)
- [Samples](#samples)

## Quick start

Two modules: a native **producer** and a JVM **consumer**.

### Native module

```kotlin
// native/build.gradle.kts

plugins {
    kotlin("multiplatform")
    id("io.github.mimimishkin.jni-binding-producer") version "1.0.2"
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
    id("io.github.mimimishkin.jni-binding-consumer") version "1.0.2"
}

kotlin {
    jvmToolchain(17)

    target { // or jvm { ... } in a multiplatform project
        compilations.named("main") {
            jniLibraries.create("native") {     // same name as sharedLib("native")
                mingwX64().fromProducer(project(":native"))
                copyToResources()               // puts the binary into jar resources
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
    mingwX64().fromProducer(project(":native"))           // build here
    // linuxX64().fromPrebuiltBinding(prebuiltDir)        // or take a binary built elsewhere

    copyToResources()       // into jar resources / Android assets
    // copyToJniLibs()      // Android: jniLibs/<abi>/ for System.loadLibrary

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
implementation("io.github.mimimishkin:jni-binding:1.0.2")
implementation("io.github.mimimishkin:jni-binding-annotations:1.0.2")
// consumer
implementation("io.github.mimimishkin:jni-binding-annotations:1.0.2")
```

### Export methods

How `external` methods find their native implementations:

- **`RegisterNatives`** (recommended) — register on library load; one load serves every class.
- **`ExposeFunctions`** (default) — export `Java_...` symbols by name; each class that declares natives must load the
  library. Required for *critical natives* on the JDK (removed in JDK 22; fully supported on Android).

## Annotations

**JVM**

- `@JniExpect` / `@JniExpects` — this `external` member (or whole class) is implemented via JNI.
  Optional target filter: `@JniExpect("mingwX64")`.
- `@LoadMethod` — function that loads the native library (runs from the object's / companion's initializer).
- `@CriticalNative` — critical native (for static functions; primitives and primitive arrays only).

**Native**

- `@JniActual` / `@JniActuals` — native implementation of an `@JniExpect`.
- `@JniOnLoad` / `@JniOnUnload` — library load / unload hooks.
- `@WithJvmType` / `@WithJvmSignature` — override the derived JVM type or full signature when needed.
- `@CriticalNative` — critical native (no `JniEnv` / `JObject`; primitives and primitive arrays only).

See [`samples/basic`](samples/basic) for every supported shape.

## Libraries

| Artifact                                       | Role                                                             |
|------------------------------------------------|------------------------------------------------------------------|
| [`jni-binding`](jni-binding/Module.md)         | Idiomatic JNI API for Kotlin/Native .                            |
| [`jawt-binding`](jawt-binding/Module.md)       | JAWT / AWT native interface; link with `linkJAwt()`.             |
| [`jni-binding-raw`](jni-binding-raw/Module.md) | Raw cinterop of the JDK headers (`jni` package), if you need it. |

## Samples

- [`samples/basic`](samples/basic) — full feature tour and multi-platform producer / prebuilt setup.
- [`samples/android-basic`](samples/android-basic) — Android app across the JNI boundary.
- [`samples/windows-registry`](samples/windows-registry) — Windows Registry wrapper (`mingwX64` only).

## License

[MIT](LICENSE.txt)
