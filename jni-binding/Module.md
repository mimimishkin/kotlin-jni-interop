# Module jni-binding

Kotlin Native bindings for the Java Native Interface (JNI) to simplify the interaction between Kotlin Native and JVM.

## Overview

The `jni-binding` module offers a zero-cost, convenient Kotlin interface to JNI without requiring manual cinterop 
configuration. It wraps the C-based JNI API with idiomatic Kotlin code, leveraging Kotlin's type system, null safety,
and context parameters to make JNI programming safer and more ergonomic.

## Features

- **Zero-cost abstractions**: Most functions are inline and won't impact binary size or performance
- **Kotlin-friendly API**: Uses null-safety, DSLs, and context parameters for a more idiomatic experience
- **Comprehensive coverage**: Provides access to all JNI functions and features
- **Cross-platform support**: Works on Windows, macOS, Linux, and Android Native
- **Kotlin side utilities**: Simplifies working with JNI by providing Kotlin-specific utilities.

# Package io.github.mimimishkin.jni

Contains Kotlin bindings to JNI types and functions.

## Types overview

#### Java VM

The `JavaVM` type represents the Java VM instance and provides methods for thread attachment and VM management.
This is also called Invocation API.

There are also `JavaVMInitArgs` and `JavaVMOption` which is need to create new Java VM.

#### JNI Environment

The `JniEnv` type provides access to JNI functions for interacting with the Java VM.

#### JVM types

They are:

- **Primitives**: `JBoolean`, `JByte`, `JChar`, `JShort`, `JInt`, `JLong`, `JFloat`, `JDouble`.

- **Object references**: `JObject`, `JClass`, `JThrowable`, `JString`, `JArray`, `JBooleanArray`, `JByteArray`,
  `JCharArray`, `JShortArray`, `JIntArray`, `JLongArray`, `JFloatArray`, `JDoubleArray`, `JObjectArray`, `JWeak`.  
  **Note:** they all are typealiases to `JObject`.

- **Field and method ID**: `JFieldID`, `JMethodID`.

- **Types that is used to pass arguments to JVM functions**: `JValue`, `JArguments`.

- **Type to register native methods**: `JNINativeMethod`.

## Functions overview

Using most of the functions is similar to their usage in C++. For example, the Kotlin equivalent of
`env->FindClass(className.c_str())` then in Kotlin its equivalent is `env.FindClass(className.utf8)`.

The only difference is:
1. Type safety.
2. Functions that return a JNI error code will instead throw a Kotlin exception when the error occurs.
3. Functions starting with JNI_ are grouped into a `JNI` object.

## Constants overview

All constants are grouped into `JNI` object.

# Package io.github.mimimishkin.jni.awt

Contains Kotlin bindings to JNI types and functions specific to Java Abstract Window Toolkit (AWT).

## Types overview

#### Java AWT

The `Awt` type provides access to the Java AWT (Abstract Window Toolkit) for native GUI operations.

## Functions overview

Using most of the functions is similar to their usage in C++.

The only difference is:
1. Type safety.
2. Functions starting with JAWT_ are grouped into a `JAWT` object.
3. Instead of reinterpretation of `platformInfo`, you may use its members directly from `DrawingSurfaceInfo` in 
   platform-specific source sets. 

## Constants overview

All constants are grouped into `JAWT` object.

# Package io.github.mimimishkin.jni.ext

Contains utilities that greatly simplify JNI code.

See details on concrete functions/properties.

# Package io.github.mimimishkin.jni.internal.raw

Contains the intact output of cinterop. All other packages are wrappers over this.

If you find a bug in the wrapper code or need it for any other reason, you can use this package for low-level
type-insecure access to JNI.