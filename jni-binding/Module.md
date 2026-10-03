# Module jni-binding

Kotlin/Native bindings for the Java Native Interface (JNI). The module wraps the C JNI API into idiomatic Kotlin -
null-safe reference types, `context(JniEnv)` parameters, DSLs and small helpers - so the native side of a JNI library
can be written without C and without any manual cinterop setup.

It targets both **desktop** (mingwX64, linuxX64, linuxArm64, macosArm64) and **Android Native**
(androidNativeArm32/64, androidNativeX86/X64). Desktop builds sit on `jni-binding-raw`; Android builds use the platform
NDK headers.

The producer Gradle plugin adds this artifact to native source sets automatically. Combine it with
`jni-binding-annotations` and the producer/consumer plugins to keep the JVM and native sides of your bindings in sync -
see the repository [README](https://github.com/mimimishkin/kotlin-jni-interop#readme). Every function mirrors its JNI
counterpart one-to-one; the
[JNI Specification](https://docs.oracle.com/en/java/javase/22/docs/specs/jni/functions.html) is the authoritative
behaviour reference.

# Package io.github.mimimishkin.jni.binding

Core bindings: all JNI types, the [JNI] object with version constants, the Invocation API (`createJavaVM`,
`attachCurrentThread`, …), every function of the JNI `JniEnv` interface, and Kotlin utilities around them.

## Getting a JNI environment

JNI functions require a `JniEnv` in context (`context(env: JniEnv)`).

- **Exported native methods** (e.g. an `@JniActual`) - the JVM already provides the environment. Declaring
  `context(env: JniEnv)` is optional; the producer injects it when you ask for it. A native allocation scope
  (`AutofreeScope`, `MemScope`, …) can be requested the same way.
- **Worker threads** - keep a `JavaVM` from `JNI_OnLoad` (or [JNI.javaVMs]) and call
  [JavaVM.attachCurrentThread] / [JavaVM.withEnvAttaching] / [JavaVM.useEnvAttaching].
- **Embedding a JVM** into a native process - [JNI.createJavaVM] with [buildJavaVMInitArgs], then
  `env.use { … }` (see [JniEnv.use]).

Version constants live on the [JNI] object (`JNI.v1` … `JNI.v24`). Pass the version you need to `getEnv` /
`attachCurrentThread` / `createJavaVM`.

## Types

JNI types are null-safe and grouped by purpose:

- **Primitives**: [JBoolean], [JByte], [JChar], [JShort], [JInt], [JLong], [JFloat], [JDouble], [JVoid].
  A JNI `boolean` is an unsigned `UByte`. Pass Kotlin primitives as-is in most APIs (`Boolean`, `Char`, …);
  use the JNI object types (`JBoolean`, `JChar`) only where the raw representation matters. Crossing the boundary:
  `String.toJString()` / `JString.toKString()`, `ByteArray.toJArray()` / `JByteArray.toKArray()`, and the analogous
  helpers for the other primitive arrays.

- **Object references**: [JObject], [JClass], [JThrowable], [JString], [JArray] and the typed arrays
  [JBooleanArray]…[JDoubleArray], [JObjectArray], [JWeak] (not counted by GC), [JByteBuffer] (direct buffers).
  They behave like typed pointers - compare with `isSame`, check type with `instanceOf`, read `refType`
  ([JObjectRefType]), and convert with `unsafeCast()` only when you know the type.

- **IDs**: [JFieldID], [JMethodID] from [fieldId] / [staticFieldId] / [methodId] / [staticMethodId].

- **Arguments**: [JValue] is the union for Java method arguments. Build a [JArguments] array with [jArgs]:

  ```kotlin
  val args = jArgs(3) { int(42); ref(jString); ref(null) }
  ```

- **VM types**: [JavaVM], [JniEnv], [JavaVMInitArgs], [JavaVMOption], [JniVersion]. Build init args with
  `buildJavaVMInitArgs(JNI.v21, n) { option(...) }`.

- **Native method registration**: [JniNativeMethod] and the [registerNatives] DSL:
  `clazz.registerNatives(1) { register("impl".utf8, "(Ljava/lang/String;)I".utf8, staticCFunction { … }) }`.

## Functions

Usage is close to the C++ style: `env->FindClass(className.c_str())` becomes `findClass(className.utf8)`. The
differences:

1. **Type safety** - no `jobject` casts; null-safety throughout.
2. **Errors as exceptions** - functions that return a JNI error code throw a Kotlin exception instead
   ([JniThreadDetachedException], [JniVersionException], [JniOutOfMemoryException], [JniVmAlreadyExistsException]).
3. **`context(JniEnv)`** - the environment is never passed as an ordinary parameter.
4. **Modified UTF-8** - strings that cross into JNI must be null-terminated modified UTF-8. Prefer
   [String.modifiedUtf8], or the Kotlin `String` overloads below. Standard `String.utf8` differs only for `'\u0000'`
   and non-BMP characters, so it is safe when the string has neither.

The full `JniEnv` surface is covered: class operations (`findClass`, `defineClass`, `superclass`,
`isAssignableFrom`, `registerNatives`/`unregisterNatives`, and on desktop `module` since JDK 9), exceptions
(`throwEx`, `throwNew`, `pendingException`, `handleJvmException`, `printStackTrace`, `clearException`,
`isExceptionThrown`, `fatalError`), local references (`deleteLocalRef`, `newLocalRef`, `pushLocalFrame`,
`popLocalFrame`, `refFrame`/`fromRefFrame`), monitors (`monitorEnter`, `monitorExit`), strings (`newString`,
`length`, `getChars`, `getUTFChars`, `getRegion`, `getUTFRegion`, `utfLength`, and on desktop `utfLengthLong`
since JDK 24, `getCharsCritical`), arrays (`newXArray`, `getRegion`, `setRegion`, `getElements`, `releaseElements`
with an [ApplyChangesMode], `getElementsCritical`), object fields and methods (see
[accessors](#package-iogithubmimimishkinjnibindingaccessors) for the typed variants), reflection
(`fromReflectedMethod`, `toReflectedMethod`, …), direct buffers (`newDirectByteBuffer`, `address`, `capacity`) and,
on desktop, `isVirtualThread` since JDK 21.

### Kotlin `String` overloads

Most lookup and message APIs have overloads that take a Kotlin `String` instead of a
`CValuesRef<ByteVar>`. They encode with [String.modifiedUtf8] and **throw** when the lookup fails, instead of
returning `null`:

```kotlin
context(env: JniEnv, autofreeScope: AutofreeScope)
fun example() {
    val clazz = findClass("java/lang/String")
    val mid = clazz.methodId("length", "()I")
    throwNew(clazz, "something went wrong")
}
```

Available for `findClass`, `defineClass`, `methodId` / `staticMethodId`, `fieldId` / `staticFieldId`,
`throwNew`, `fatalError`, `attachCurrentThread` / `attachCurrentThreadAsDaemon`, `withEnvAttaching`, and
`registerNatives { register(name, sig, fn) }`.

## Utilities

- [String.modifiedUtf8] - correct modified UTF-8 encoding (Kotlin `'\u0000'` → `0xC0 0x80`, non-BMP as
  surrogate pairs). Required by
  [Modified UTF-8 Strings](https://docs.oracle.com/en/java/javase/22/docs/specs/jni/functions.html#modified-utf-8-strings)
  in the JNI specification.
- [JniEnv.use] / [JavaVM.useEnv] / [JavaVM.useEnvAttaching] - scope an environment together with a native
  `MemScope` so allocations inside (UTF-8 values, [jArgs] arrays, …) are freed automatically.
- [JavaVM.withEnv] / [JavaVM.withEnvAttaching] - obtain an environment for the current thread (and detach when
  done, for the attaching variants).
- [jniLazy] / [JniLazy] - lazily initialized values whose `context(JniEnv, MemScope)` initializer runs once, in an
  attached environment, with synchronized initialization. Useful for caching `JClass` / `JMethodID` across calls.
- [localIntoWeakRef] / [localIntoGlobalRef] - elevate a local reference and delete the local one in one step.

# Package io.github.mimimishkin.jni.binding.accessors

High-level typed access to Java methods, fields and arrays - Kotlin lambdas, `ReadWriteProperty` delegates and scoped
DSLs. Everything here stays `context(JniEnv, MemScope)` / `NativePlacement`-friendly.

## Methods

Fetch a method ID once, then call it as an ordinary function with reified types - no `jArgs`, no
`call<Type>Method`:

```kotlin
// requires JniEnv and a NativePlacement / AutofreeScope
val add = clazz.methodId("add", "(II)I").asMethod<Int, Int, Int>()
val result = obj.add(2, 3) // 5
```

- [asMethod] - instance methods (0 to 10 typed parameters, any return type).
- [asStaticMethod] - static methods; the receiver is the class.
- [asConstructor] - constructors; the receiver is the class, returns `JObject?`.
- [asNonvirtualMethod] - non-virtual dispatch; the first parameter is the `JClass` to dispatch on.

Primitives in the type arguments must be non-null; reference types must be nullable.

## Fields

Get or set a field through a Kotlin `ReadWriteProperty` - bind it once and use it like a property:

```kotlin
// requires JniEnv and an AutofreeScope
val jName: JString? by obj.stringField("name")
var name: String? by obj.stringField("name").asKotlin()
```

Instance: `objectField`, `stringField`, `booleanField`…`doubleField`, and generic `field`. Static:
`staticObjectField`, `staticStringField`, `staticIntField`… and `staticField`.

Each has two forms:

- **Kotlin `String` name** - encodes with [String.modifiedUtf8] and throws if the field is missing.
- **`CValuesRef<ByteVar>` name** - returns `null` when the field is not found; pass `name.utf8` or
  `name.modifiedUtf8`.

Convert an object delegate to a specific type with [asType], and to a Kotlin `String` (reading/writing `JString?`)
with [asKotlin]. Fields can also be bound by a ready [JFieldID].

## Arrays and strings, in place

Instead of `getElements` → mutate → `releaseElements(mode)` pairs, mutate the body in a scope and choose how
changes are applied:

```kotlin
val javaArray: JIntArray = …
javaArray.modify({ /* onError */ }) { carray, isCopy ->
    // this: ModifyingArrayScope
    carray[0] = 42
    commit()    // copy back, keep the buffer
    // finalize() - copy back and free
    // abort()    - discard changes
}
```

- [modify] on every `J<Primitive>Array` (with `commit()` / `finalize()` / `abort()` on [ModifyingArrayScope]),
  plus [modifyCritical] for pinning.
- [modifyChars] / [modifyUTFChars] / [modifyCritical] on a `JString`.

# Package io.github.mimimishkin.jni.binding.annotation

Annotations shared by the producer (`@JniActual`, `@JniActuals`, `@JniOnLoad`, `@JniOnUnload`, `@WithJvmType`,
`@WithJvmSignature`, `@CriticalNative`) and the consumer (`@JniExpect`, `@JniExpects`, `@LoadMethod`). They are
declared in the `jni-binding-annotations` module and are re-exported here for convenience; see the
[annotations reference](https://github.com/mimimishkin/kotlin-jni-interop#annotations-reference) in the repository
README for when and how to use each one.
