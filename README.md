# Kotlin Native JNI interop

[![Maven Central](https://img.shields.io/maven-central/v/io.github.mimimishkin/jni-binding.svg)](https://central.sonatype.org/artifact/io.github.mimimishkin/jni-binding)
![Kotlin](https://img.shields.io/badge/Kotlin-%E2%89%A52.4.20-7F52FF)

![Kotlin mingwX64](https://img.shields.io/badge/Kotlin-mingwX64-4287f5)
![Kotlin macosArm64](https://img.shields.io/badge/Kotlin-macosArm64-f5d042)
![Kotlin linuxX64](https://img.shields.io/badge/Kotlin-linuxX64-f54242)
![Kotlin linuxArm64](https://img.shields.io/badge/Kotlin-linuxArm64-f54242)

Write the native side of a JNI library in pure Kotlin/Native and keep the JVM side in sync automatically — without a
single line of C.

- the **producer** compiler plugin (`jni-binding-producer`) builds a Kotlin/Native module into a
  `.dll`/`.so`/`.dylib` and dumps the list of exported `@JniActual` bindings;
- the **consumer** compiler plugin (`jni-binding-consumer`) checks that every `@JniExpect` on the JVM side has its
  real implementation and packages the binary into resources;
- `jni-binding` / `jni-binding-raw` provide the JNI API for the native side, `jni-binding-annotations` the shared
  annotations, `jawt-binding` the JAWT (AWT native interface) bindings, and `jni-binding-plugins` the Gradle plugins
  (see [Repository layout](#repository-layout));
- [`samples/basic`](samples/basic) shows every feature in a very simple way, and
  [`samples/windows-registry`](samples/windows-registry) is a real-world type-safe wrapper over the Windows Registry.

## Get started

### 1. The native side (producer)

Apply the producer plugin to a Kotlin Multiplatform module and bind each implementation to its JVM counterpart:

```kotlin
// native/build.gradle.kts

plugins {
    kotlin("multiplatform")
    id("io.github.mimimishkin.jni-binding-producer") version "1.0.2"
}

kotlin {
    jvmToolchain(17)
    mingwX64().binaries {
        if (it.konanTarget == HostManager.host) sharedLib("native") { // shared binary base name
            linkJVM() // link the JavaVM headers into the binary
        }
    }
}

jniLibraries {
    jniVersion = 17              // JDK major version whose JNI version JNI_OnLoad will report
    exportMethod = JniExportMethod.RegisterNatives // or ExposeFunctions — see "Export methods" below
    allowSeveralHooks = false    // allow multiple @JniOnLoad / @JniOnUnload in one library
}
```

```kotlin
// native/src/nativeMain/kotlin/Main.kt

// context(env: JniEnv) and the JObject/JClass receiver are optional — add them only if the body uses them.
@JniActual(className = "org.sample.MainKt", methodName = "hello")
context(env: JniEnv)
fun hello(): JString? = "Hello from Kotlin/Native".toJString()
```

A top-level function binds to the JVM **file facade class** (`org.sample.MainKt` for `Main.kt` in `org.sample`), so
`className` would repeat for every binding. Grouping them with `@JniActuals` lifts the repetition away:

```kotlin
@JniActuals(className = "org.sample.MainKt")
object Main {
    context(env: JniEnv)
    fun hello(): JString? = "Hello from Kotlin/Native".toJString()
}
```

The plugin adds `jni-binding` and the annotations to your native dependencies, builds each target's shared library and
exports the bindings to the consumer. [`samples/basic/native`](samples/basic/native) exercises overloads,
`JObject`/`JClass` receivers, `@WithJvmType`/`@WithJvmSignature` overrides, `@JniOnLoad`/`@JniOnUnload` hooks and the
`jniLazy` caching helper.

### 2. The JVM side, wiring and loading (consumer)

Apply the consumer plugin to a JVM (or Android JVM) module, declare the same functions as `external`, and load the
binary:

```kotlin
// app/build.gradle.kts

plugins {
    kotlin("jvm")
    id("io.github.mimimishkin.jni-binding-consumer") version "1.0.2"
}

kotlin {
    jvmToolchain(17)
    target { // or jvm { ... } in multiplatform projects
        compilations.named("main") {
            // The plugin acts only after `jniLibraries` is accessed here (lazy activation).
            jniLibraries.create("native") { // must match the shared library name in producer
                mingwX64().fromProducer(project(":native"))                       // build the native module on the host
                linuxX64().fromPrebuiltBinding(rootDir.resolve("jniBindings/linux-x86_64")) // or bind prebuilt ones
                macosArm64().fromPrebuiltBinding(rootDir.resolve("jniBindings/macos-aarch64"))

                copyToResources()    // package the binaries into resources (see samples/basic for resourceDir layout)
                allowExtraActuals = false  // forbid @JniActual without an @JniExpect counterpart
                allowAbsentBindings = true // do not fail while some target's bindings are not built yet
            }
        }
    }
}
```

Bindings are matched by the **full JVM class name** and the **function name**:

```kotlin
// app/src/main/kotlin/org/sample/Main.kt

// The implementation of this external function is provided from the native side.
@JniExpect
external fun hello(): String

@JniExpects
object Main {
    // Called automatically when the object is first accessed. A library inside a jar cannot be loaded in place, so we
    // copy it to a temp file first. `os` receives the OS family ("windows", "linux", "macos"), `arch` the normalized
    // architecture ("x86_64", "aarch64", ...) and `vendor` is optional (java.vendor, unchanged).
    @LoadMethod
    private fun load(os: String, arch: String) {
        val libPath = "/natives/$os-$arch/${System.mapLibraryName("native")}"
        val libStream = Main::class.java.getResourceAsStream(libPath)
            ?: error("No native library found at $libPath")
        val outputPath = Files.createTempFile(null, libPath.substringAfterLast('/'))
        libStream.use { input ->
            Files.copy(input, outputPath, StandardCopyOption.REPLACE_EXISTING)
        }
        outputPath.toFile().deleteOnExit()
        System.load(outputPath.absolutePathString())
    }

    // @JniExpects is not applied to nested classes — annotate them separately and bind to "org.sample.Main$Nested".
    @JniExpects
    class Nested {
        external fun sayHello(printStream: PrintStream)
    }
}
```

The consumer plugin aggregates the bindings, validates the JNI version against your JDK, recompiles the JVM side
whenever a native module changes, and reports missing or mismatched bindings at compile time. Without
`copyToResources()` nothing is packaged, and you must load the library yourself. See [`samples/basic`](samples/basic)
for the complete wiring (producer on the host vs prebuilt fallback) and
[`samples/windows-registry`](samples/windows-registry) for a fixed-path variant.

## Export methods

`external` functions are linked to their native implementations in one of two ways, selected on the producer by
`jniLibraries.exportMethod`:

1. **`RegisterNatives`** (preferred, faster) — the functions are registered inside a generated `JNI_OnLoad`, so a
   library serving several classes is loaded once. See
   [`RegisterNatives`](https://docs.oracle.com/en/java/javase/22/docs/specs/jni/functions.html#registering-native-methods)
   in the JNI specification.
2. **`ExposeFunctions`** (default) — the binary exports functions named `Java_some_package_ClassName_methodName`, which
   the JVM links by name; the library must then be loaded in each class that declares native methods. How names are
   derived and resolved is specified in
   [Resolving Native Method Names](https://docs.oracle.com/en/java/javase/22/docs/specs/jni/design.html#resolving-native-method-names).
   This is the only way to link JNI *critical native functions* — deprecated in JDK 16 and removed in JDK 22.

## Annotations reference

### JVM side (`jni-binding-annotations`, `jvmMain`)

| Annotation    | Target                          | Meaning                                                                                                  |
|---------------|---------------------------------|----------------------------------------------------------------------------------------------------------|
| `@JniExpect`  | `external` functions/properties | Declares that the implementation is provided through JNI.                                                |
| `@JniExpects` | classes/objects                 | The same as `@JniExpect`, applied to the annotatee and every `external` member inside.                   |
| `@LoadMethod` | functions                       | Marks the function that loads the native library; called in `<clinit>` (companion) or `<init>` (object). |

### Native side (`nativeMain`)

| Annotation                                      | Target                        | Meaning                                                                                                                           |
|-------------------------------------------------|-------------------------------|-----------------------------------------------------------------------------------------------------------------------------------|
| `@JniActual(className, methodName)`             | top-level functions           | The native implementation of an `@JniExpect`.                                                                                     |
| `@JniActuals(className)`                        | top-level objects/classes     | The same for every public/internal function inside (or for a class created at `JNI_OnLoad`).                                      |
| `@JniOnLoad` / `@JniOnUnload`                   | top-level functions           | Called from the generated `JNI_OnLoad`/`JNI_OnUnload`; no or a single `JavaVM` parameter. One of each unless `allowSeveralHooks`. |
| `@WithJvmSignature(parameterTypes, returnType)` | functions                     | Overrides the whole JVM type signature.                                                                                           |
| `@WithJvmType(type)`                            | types                         | Declares the JVM type of the annotated type (`"java.lang.String"`, `"int"`, `"long[][]"`, ...). `@Repeatable`; the last wins.     |
| `@CriticalNative`                               | native `@JniActual` functions | Marks a *critical native* — no `JniEnv`/`JObject`, primitives and primitive arrays only. See [Export methods](#export-methods).   |

See [`samples/basic`](samples/basic) for the exact shapes each annotation supports, and the code comments there for
`@WithJvmType`/`@WithJvmSignature` usage with typealiases.

## The `jni-binding` library

The producer plugin adds it to your native dependencies automatically — or add
`implementation("io.github.mimimishkin:jni-binding:1.0.2")` manually. It wraps JNI into idiomatic Kotlin without any
cinterop configuration: JNI error codes become Kotlin exceptions, types are null-safe, and there are helpers such as
`String.modifiedUtf8` (see
[Modified UTF-8 Strings](https://docs.oracle.com/en/java/javase/22/docs/specs/jni/types.html#modified-utf-8-strings)),
`refFrame {}` and `jArgs {}`.

For direct low-level access there is **`jni-binding-raw`** — the raw cinterop of the JDK headers (`jni.h`, `jawt.h`,
...), all in the `jni` package.

The AWT native interface lives in a separate **`jawt-binding`** module:
`implementation("io.github.mimimishkin:jawt-binding:1.0.2")`. It wraps `jawt.h` into
`io.github.mimimishkin.jni.binding.awt` — [Awt], `DrawingSurface`/`DrawingSurfaceInfo` and the platform-specific
members (`hwnd`/`hdc`/... on Windows, X11 info on Linux, `CALayer` on macOS). Add the `libjawt` linker option with
`linkJAwt()` of the producer plugin.

## Requirements

- Kotlin ≥ 2.4.20.

## Repository layout

```
jni-binding-raw/         raw cinterop bindings
jni-binding/             idiomatic Kotlin JNI wrapper
jawt-binding/            idiomatic Kotlin JAWT (AWT native interface) bindings
jni-binding-annotations/ JVM + native annotations
jni-binding-producer/    Kotlin compiler plugin (native side)
jni-binding-consumer/    Kotlin compiler plugin (JVM side)
jni-binding-plugins/     Gradle plugins with the jniLibraries DSL
samples/                 examples (basic, windows-registry)
```