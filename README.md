# Kotlin/Native JNI interop

[![Maven Central](https://img.shields.io/maven-central/v/io.github.mimimishkin/jni-binding.svg)](https://central.sonatype.org/artifact/io.github.mimimishkin/jni-binding)
![Kotlin](https://img.shields.io/badge/Kotlin-%E2%89%A52.4.20-7F52FF)

![Kotlin mingwX64](https://img.shields.io/badge/Kotlin-mingwX64-4287f5)
![Kotlin macosArm64](https://img.shields.io/badge/Kotlin-macosArm64-f5d042)
![Kotlin linuxX64](https://img.shields.io/badge/Kotlin-linuxX64-f54242)
![Kotlin linuxArm64](https://img.shields.io/badge/Kotlin-linuxArm64-f54242)

Write the **native** side of a JNI library in pure Kotlin/Native and the **JVM** side in plain Kotlin — the two halves
are matched, built, checked and packaged for you. No C, no handwritten JNI signatures, no cinterop setup.

- **Native side**: Kotlin functions marked with `@JniActual`.
- **JVM side**: `external` functions marked with `@JniExpect`.
- The **producer** plugin compiles the native module, links the binary and exports the list of implemented bindings
  together with their JVM signatures.
- The **consumer** plugin verifies that every declaration has a matching implementation, packages the binary into
  resources, and recompiles the JVM code whenever the native code changes.

## Contents

- [How it works](#how-it-works)
- [Quick start](#quick-start) — a complete native + JVM pair
- [Configuration reference](#configuration-reference) — every DSL option of both plugins
- [Annotations reference](#annotations-reference) — the ten annotations and what they do
- [Libraries](#libraries) — `jni-binding-raw`, `jni-binding`, `jawt-binding`
- [Samples](#samples) — runnable examples
- [Repository layout](#repository-layout)

## How it works

A JNI library is two halves that must agree on names and signatures. This project keeps them in sync by making the
*implementation* the source of truth:

```
 native module (Kotlin/Native)                     JVM module (JVM / Android JVM)
 @JniActual fun hello(): JString             ──►   @JniExpect external fun hello(): String
 @JniActual fun add(a: JInt, b: JInt): JInt        @JniExpect external fun add(a: Int, b: Int): Int
```

Step by step:

1. **Native functions are declared** with `@JniActual`. The producer plugin adds `jni-binding` and the annotations to
   the dependencies of the native module, which makes the JNI API (`JniEnv`, `JString`, `JObject`, ...) available.
2. **The producer plugin builds** `libnative.so` (or `.dll` / `.dylib`) and exports the list of
   bindings it implemented, together with their JVM signatures.
3. **The consumer plugin validates** the JVM side against that list while compiling. Bindings are matched by the
   **full JVM class name and the function name**; the JVM signature is derived from the Kotlin declaration. A
   declaration without an implementation, or a signature that does not match, is a **compile error** — not a
   `UnsatisfiedLinkError` at runtime.
4. **`copyToResources()`** puts the per-target binaries into the compilation's resources under
   `natives/<os>-<arch>/`, so that the JVM code can load the entry matching the machine it runs on. On Android the
   binaries are packaged among its **assets**. Without this call nothing is packaged, and the library has to be loaded
   explicitly.

Two additional considerations:

- **Producer and consumer are independent modules.** They can live in the same build or in different ones, and a JVM
  module can consume binaries built on another machine (see [prebuilt bindings](#consumer-side-jnilibrariescreate)).
- **Only the host target can be linked.** A build produces a shared library for the platform it runs on, so the
  usual setup builds the host target from source and takes the others from a prebuilt folder.
- **Both plugins are compatible with 
  [Isolated Projects](https://docs.gradle.org/current/userguide/isolated_projects.html) and the configuration cache.**

## Quick start

### 1. Native module — the producer

```kotlin
// native/build.gradle.kts

import io.github.mimimishkin.jni.binding.plugin.producer.JniExportMethod
import io.github.mimimishkin.jni.binding.plugin.producer.linkJvm
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    kotlin("multiplatform")
    id("io.github.mimimishkin.jni-binding-producer") version "1.0.2"
}

kotlin {
    jvmToolchain(17)

    // A binary can only be linked for the host, so all supported targets are declared
    // and only the current one is linked.
    listOf(
        mingwX64(),
        linuxX64(), 
        linuxArm64(), 
        macosArm64()
    ).forEach { target ->
        target.binaries {
            if (target.konanTarget == HostManager.host) {
                sharedLib("native") { // the shared library base name
                    linkJvm()         // link the JavaVM/JNI headers into the binary
                }
            }
        }
    }
}

jniLibraries {
    expectedJdkVersion = 17                         // JDK the native library is built against
    exportMethod = JniExportMethod.RegisterNatives  // or ExposeFunctions, see below
    allowSeveralHooks = false                       // allow several @JniOnLoad/@JniOnUnload in one library
}
```

```kotlin
// native/src/nativeMain/kotlin/Main.kt

// `context(env: JniEnv)` and an explicit JObject/JClass receiver are optional and
// are only required when the body needs them. A native allocation scope can be
// requested the same way — see the note below.
@JniActual(className = "org.sample.MainKt", methodName = "hello")
context(env: JniEnv, autofreeScope: AutofreeScope)
fun hello(): JString? = "Hello from Kotlin/Native".toJString()
```

Besides `JniEnv`, an actual may declare a second `context` parameter of a native-memory placement type: `AutofreeScope`,
`NativePlacement`, `ArenaBase`, or `MemScope`. The body can allocate through the placement — and use helpers such as
`String.toJString()`, which requires an `AutofreeScope` — without opening a scope itself.

### 2. JVM module — the consumer

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
            // The plugin activates as soon as `jniLibraries` is accessed here.
            jniLibraries.create("native") { // must match the sharedLib name in the producer
                // built on this machine
                linuxX64().fromProducer(project(":native"))
                // not buildable here — taken from a folder
                mingwX64().fromPrebuiltBinding(rootDir.resolve("jniBindings/windows-x86_64"))

                copyToResources()      // package the binaries into resources
                allowExtraActuals = false  // forbid @JniActual without an @JniExpect counterpart
                allowAbsentBindings = true // do not fail while some target is not built yet
            }
        }
    }
}
```

```kotlin
// app/src/main/kotlin/org/sample/Main.kt

// The implementation of this external function is provided from the native side.
@JniExpect
external fun hello(): String

@JniExpects
object Main {
    // Called automatically when the object is first accessed. A library inside a jar cannot be
    // loaded in place, so it is copied to a temp file first. `os` receives the OS family
    // ("windows", "linux", "macos", "android"), `arch` the normalized architecture ("x86_64", "aarch64", ...).
    @LoadMethod
    private fun load(os: String, arch: String) {
        val libPath = "/natives/$os-$arch/${System.mapLibraryName("native")}"
        val libStream = Main::class.java.getResourceAsStream(libPath)
            ?: error("No native library found at $libPath")
        val outputPath = Files.createTempFile(null, libPath.substringAfterLast('/'))
        libStream.use { input -> Files.copy(input, outputPath, StandardCopyOption.REPLACE_EXISTING) }
        outputPath.toFile().deleteOnExit()
        System.load(outputPath.absolutePathString())
    }
}
```

That is the whole contract. `@JniExpect external fun hello(): String` on the JVM side is implemented by the native
`@JniActual ... fun hello(): JString?` above, and both plugins verify it on every build.

### How names are matched

| Declared on the native side                                | Bound JVM name                                    |
|------------------------------------------------------------|---------------------------------------------------|
| top-level `fun` in `Main.kt`, package `org.sample`         | `org.sample.MainKt.hello` (file facade class)     |
| `@JniActuals(className = "org.sample.MainKt") object Main` | `org.sample.MainKt.hello`                         |
| nested class `Main.Nested`                                 | `org.sample.Main$Nested` (annotate it separately) |

A top-level function binds to the JVM **file facade class**, so `className` would otherwise repeat for every binding.
The functions can be grouped into an object annotated with `@JniActuals`, which declares it once:

```kotlin
@JniActuals(className = "org.sample.MainKt")
object Main {
    context(env: JniEnv)
    fun hello(): JString? = "Hello from Kotlin/Native".toJString()
}
```

Note that `@JniExpects` is not applied to nested classes: they are annotated separately and bound to
`"org.sample.Main$Nested"`.

## Configuration reference

### Producer: the `jniLibraries` extension

Configured on the native project:

| Option               | Type                        | Default           | Meaning                                                                                                      |
|----------------------|-----------------------------|-------------------|--------------------------------------------------------------------------------------------------------------|
| `expectedJdkVersion` | `Property<Int>`             | `1`               | JDK major version the native library is built against. A consumer compiled against an older JDK is rejected. |
| `exportMethod`       | `Property<JniExportMethod>` | `ExposeFunctions` | How `external` functions are linked: `RegisterNatives` or `ExposeFunctions`, see [below](#export-methods).   |
| `allowSeveralHooks`  | `Property<Boolean>`         | `false`           | Allow several `@JniOnLoad`/`@JniOnUnload` functions in one library.                                          |

### Producer: linker helpers

Called inside `binaries { sharedLib(...) { ... } }`:

| Function            | Adds                                                                                       |
|---------------------|--------------------------------------------------------------------------------------------|
| `linkJvm()`         | the JNI headers and `libjvm` of the JDK set by `expectedJdkVersion`                        |
| `linkJvm(javaHome)` | the `libjvm` of the JDK at that path, which is how a target that is not the host is linked |
| `linkJAwt()`        | the `libjawt` library; call after `linkJvm()`, which it reuses                             |
| `linkX11IfLinux()`  | the X11 libraries the AWT runtime needs on Linux                                           |

#### Cross-compiling

Gradle serves Java toolchains for the host only, so the toolchain above cannot link a target that
differs from it. `downloadCompatibleJdk` provides a JDK built for the target instead, working the
platform out from the target itself:

```kotlin
import io.github.mimimishkin.jni.binding.plugin.producer.downloadCompatibleJdk

binaries {
    sharedLib("native") {
        if (target.konanTarget == HostManager.host) {
            linkJvm()
        } else {
            linkJvm(downloadCompatibleJdk())                   // the Java version of expectedJdkVersion
            // linkJvm(downloadCompatibleJdk(21))              // or a named major version
            // linkJvm(downloadCompatibleJdk("17.0.13.11.1"))  // or an exact build
        }
    }
}
```

### Consumer side: `jniLibraries.create`

One `create` call per native library, on a JVM or Android JVM compilation:

| Option                     | Type                | Default             | Meaning                                                                                                                                                     |
|----------------------------|---------------------|---------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `copyToResources()`        | function            | not called          | Package the binaries of every configured target under `resourceDir` in the compilation's resources. On Android the binaries go into the APK assets.         |
| `copyToJniLibs()`          | function            | not called          | Android only: package the binaries into `jniLibs/<abi>/` of the APK or the AAR, so that `System.loadLibrary("...")` finds them. The default Android layout. |
| `resourceDir` (per target) | `Property<String>`  | `natives/$os-$arch` | Path the binary is placed at by `copyToResources()` - in the resources, or in the Android assets.                                                           |
| `allowExtraActuals`        | `Property<Boolean>` | `false`             | Forbid a native `@JniActual` that has no `@JniExpect` counterpart.                                                                                          |
| `allowAbsentBindings`      | `Property<Boolean>` | `false`             | Skip a target whose bindings are not available instead of failing.                                                                                          |

Per target, the source of the bindings is selected as follows:

| Function                           | Meaning                                                                                                          |
|------------------------------------|------------------------------------------------------------------------------------------------------------------|
| `fromProducer(project("native"))`  | Build that module on this machine; the native binary and its bindings are rebuilt automatically on every change. |
| `fromPrebuiltBinding(dir)`         | Take bindings and the binary from a folder, e.g. produced on another OS by another build.                        |

Target helpers: `mingwX64()`, `linuxX64()`, `linuxArm64()`, `macosArm64()`, `androidX86()`, `androidX64()`,
`androidArm32()`, `androidArm64()`. Each exposes `os`, `arch`, `abi`, `konanTarget` and `resourceDir`, so a
platform can be picked by family instead of hard-coded, as `samples/basic` does:

```kotlin
listOf(
    mingwX64(), 
    linuxX64(), 
    linuxArm64()
).forEach { target ->
    if (target.konanTarget?.family == HostManager.host.family) {
        target.fromProducer(project(":native"))               // buildable here
    } else {
        target.fromPrebuiltBinding(rootDir.resolve("jniBindings/${target.os}-${target.arch}"))
    }
    target.resourceDir = "natives/${target.os}-${target.arch}"
}
```

The consumer plugin recompiles the JVM side whenever a native module changes, checks that the JNI version matches
the JDK the JVM module is compiled against, and reports missing or mismatched bindings as compile errors.

### Generating the `@JniActual` stubs

Writing a binding starts on the JVM side: you declare the `@JniExpect` you want, and the build fails until the native
side implements it. The `generateJniActuals` task writes those missing `@JniActual` stubs into the producer for you:

```
./gradlew :consumer:compileKotlin        # fails, listing the @JniExpects that have no @JniActual yet
./gradlew :consumer:generateJniActuals   # writes the stubs into the producer
./gradlew :consumer:compileKotlin        # compiles; the stubs now have bodies to fill in
```

One file per JVM class is written into the producer's `src/nativeMain/kotlin`, each stub carrying the names and
signature that make it implement that exact JVM method:

```kotlin
@JniActual(className = "org.sample.MainKt", methodName = "hello")
@WithJvmSignature(
    parameterTypes = [],
    returnType = "java.lang.String",
)
context(env: JniEnv)
fun JClass.hello(): JString = TODO()
```

Filling in the bodies is the remaining work. Nothing that is already implemented or already declared is touched, so the 
task is safe to re-run.

### Export methods

`external` functions are linked to their native implementations in one of two ways, selected on the producer by
`jniLibraries.exportMethod`:

1. **`RegisterNatives`** (preferred, faster) — the functions are registered when the library is loaded, so a library
   serving several classes needs to be loaded only once. See
   [`RegisterNatives`](https://docs.oracle.com/en/java/javase/22/docs/specs/jni/functions.html#registering-native-methods)
   in the JNI specification.
2. **`ExposeFunctions`** (default) — the binary exports functions named `Java_some_package_ClassName_methodName`, which
   the JVM links by name; the library must then be loaded in each class that declares native methods. How names are
   derived and resolved is specified in
   [Resolving Native Method Names](https://docs.oracle.com/en/java/javase/22/docs/specs/jni/design.html#resolving-native-method-names).
   This is the only way to link JNI *critical native functions* on JDK — undocumented feature for faster JNI calls 
   deprecated in JDK 16 and removed in JDK 22. This does not apply to the same feature on Android—it is officially 
   supported there.

## Annotations reference

### JVM side (`jni-binding-annotations`, `jvmMain`)

| Annotation                    | Target                          | Meaning                                                                                                                                             |
|-------------------------------|---------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|
| `@JniExpect(vararg targets)`  | `external` functions/properties | Declares that the implementation is provided through JNI. Pass target names (`@JniExpect("mingwX64")`) to require the implementation only for them. |
| `@JniExpects(vararg targets)` | classes/objects                 | The same as `@JniExpect`, applied to the annotatee and every `external` member inside.                                                              |
| `@LoadMethod`                 | functions                       | Marks the function that loads the native library; called in `<clinit>` (companion) or `<init>` (object).                                            |

### Native side (`nativeMain`)

| Annotation                                      | Target                        | Meaning                                                                                                                         |
|-------------------------------------------------|-------------------------------|---------------------------------------------------------------------------------------------------------------------------------|
| `@JniActual(className, methodName)`             | top-level functions           | The native implementation of an `@JniExpect`; `methodName` defaults to the function name.                                       |
| `@JniActuals(className)`                        | top-level objects/classes     | The same for every public/internal function inside.                                                                             |
| `@JniOnLoad` / `@JniOnUnload`                   | top-level functions           | Called when the library is loaded and unloaded; no or a single `JavaVM` parameter. One of each unless `allowSeveralHooks`.      |
| `@WithJvmSignature(parameterTypes, returnType)` | functions                     | Overrides the whole JVM type signature.                                                                                         |
| `@WithJvmType(type)`                            | types                         | Declares the JVM type of the annotated type (`"java.lang.String"`, `"int"`, `"long[][]"`, ...).                                 |
| `@CriticalNative`                               | native `@JniActual` functions | Marks a *critical native* — no `JniEnv`/`JObject`, primitives and primitive arrays only. See [Export methods](#export-methods). |

See [`samples/basic`](samples/basic) for the exact shapes each annotation supports, and the code comments there for
`@WithJvmType`/`@WithJvmSignature` usage with typealiases.

## Libraries

### `jni-binding-raw`

The raw cinterop of the JDK headers (`jni.h`, `jawt.h`, `jvmti.h`, `jdwpTransport.h`, ...) without any additional
wrapper; all declarations live in the `jni` package. Note that the raw bindings require the JVM libraries to be added
to the linker path explicitly.
Full reference: [`jni-binding-raw/Module.md`](jni-binding-raw/Module.md).

### `jni-binding`

`implementation("io.github.mimimishkin:jni-binding:1.0.2")` — added to the dependencies of the native module by the
producer plugin automatically. It wraps JNI into idiomatic Kotlin without any cinterop configuration: JNI error codes
become Kotlin exceptions, types are null-safe, and there are helpers such as `String.modifiedUtf8` (see
[Modified UTF-8 Strings](https://docs.oracle.com/en/java/javase/22/docs/specs/jni/functions.html)).
Full reference: [`jni-binding/Module.md`](jni-binding/Module.md).

### `jawt-binding`

`implementation("io.github.mimimishkin:jawt-binding:1.0.2")` — the AWT native interface (`jawt.h`) as
`io.github.mimimishkin.jni.binding.awt`: `Awt` and `DrawingSurface`/`DrawingSurfaceInfo`, where the last one gains
platform-specific members — `hwnd`/`hdc`/`hbitmap` on Windows, `display`/`visualID`/`drawable` on Linux and
`layer`/`windowLayer` on macOS. Add the `libjawt` linker option with `linkJAwt()`.
Full reference: [`jawt-binding/Module.md`](jawt-binding/Module.md).

## Samples

- [`samples/basic`](samples/basic) — demonstrates all supported features: overloads, extension functions with
  `JObject`/`JClass` receivers, `@WithJvmType`/`@WithJvmSignature` overrides, `@JniOnLoad`/`@JniOnUnload` hooks, a 
  nested class, a non-ASCII class name, private field access, two native libraries in one build, per-target 
  implementations (`@JniExpect("mingwX64")` plus Kotlin `expect`/`actual`) and the producer-vs-prebuilt target setup.
- [`samples/android-basic`](samples/android-basic) — an Android app that walks the JNI boundary section by section:
  callbacks, native threads with a cached `JavaVM`, direct buffers and reference kinds, exceptions in both directions,
  calls into plain Java/Android objects.
- [`samples/windows-registry`](samples/windows-registry) — a real-world type-safe wrapper over the Windows Registry
  (Windows-only: it only builds when the host is `mingwX64`).

## Repository layout

```
jni-binding-raw/         raw cinterop bindings
jni-binding/             idiomatic Kotlin JNI wrapper
jawt-binding/            idiomatic Kotlin JAWT (AWT native interface) bindings
jni-binding-annotations/ JVM + native annotations
jni-binding-producer/    Kotlin compiler plugin (native side)
jni-binding-consumer/    Kotlin compiler plugin (JVM side)
jni-binding-plugins/     Gradle plugins with the jniLibraries DSL
samples/                 examples (basic, android-basic, windows-registry)
```

## License

[MIT](LICENSE.txt)