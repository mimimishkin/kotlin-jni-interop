# Kotlin Native JNI interop

[![Maven Central](https://img.shields.io/maven-central/v/io.github.mimimishkin/jni-binding.svg)](https://central.sonatype.org/artifact/io.github.mimimishkin/jni-binding)
![Kotlin](https://img.shields.io/badge/Kotlin-%E2%89%A52.2.0-7F52FF)

![Kotlin mingwX64](https://img.shields.io/badge/Kotlin-mingwX64-4287f5)
![Kotlin macosX64](https://img.shields.io/badge/Kotlin-macosX64-f5d042)
![Kotlin macosArm64](https://img.shields.io/badge/Kotlin-macosArm64-f5d042)
![Kotlin linuxX64](https://img.shields.io/badge/Kotlin-linuxX64-f54242)
![Kotlin linuxArm64](https://img.shields.io/badge/Kotlin-linuxArm64-f54242)

Ready-to-use K/Native binding to JNI for all platforms.

No longer need to configure cinterop yourself and suffer with inconvenient C pointers and function calls.
Just add 
```kotlin
implementation("io.github.mimimishkin:jni-binding:1.0.2")
```
to project dependencies.

## Advantages over cinterop

First and foremost, you don't need to configure cinterop, which would be a pain in a multiplatform project. Also, you 
don't need to wait until cinterop will be generated.

Secondly, this library contains only four non-inline declarations — a function to check what error the JNI function 
returned, `String.modifiedUtf8` to pass valid strings to JNI, and two empty objects for convenience (which the compiler 
is likely to reduce, since they are not used anywhere). 
All other methods and properties are inline and will not take up space in the already huge Kotlin binaries and will not 
affect performance.

Thirdly, these bindings are cooked specifically for Kotlin using all its possibilities: null-safety, DSL, functions 
with receiver and context parameters. This makes JNI easy to use, even for a child.

And of coerce, documentation.

## Functions naming rules and parameters

Most of the JNI functions have the same name as the original ones. For example, if in C you write 
`(*env)->FindClass(env, "com/example/Main")` then in Kotlin its equivalent is `FindClass("com/example/Main".utf8)`.

Some functions and constants have `JNI_` prefix. They have been grouped into a `JNI` object. The same if true for
declarations with `JAWT_` prefix.

There are also some names in camelCase. They are Kotlin wrappers that allow writing more readable and Kotlin-styled
code. For example `refFrame {}` which executes a block of code inside a new reference frame and `jArgs {}` which creates 
arguments to pass to JVM functions.

## You need to know

As you may have noticed, almost everything in Kotlin is in experimental stage. This library uses cinterop, cinterop
commonization and context parameters. You need to accept that a new version of Kotlin can make this library obsolete.

Kotlin currently doesn't support publishing commonized cinterop. So you cannot build for more than one target at one
time 😭. IDEA hints will also not work if more than one target is configured.

`freeCompilerArgs.add("-Xcontext-parameters")` is required to use most of the API.

If you produce native binaries, you also need to link your native binary with java shared libraries:
```kotlin
target.binaries {
    sharedLib {
        if (isWindows) {
            linkerOpts("-L$javaHome/lib", "-ljvm") // add "-ljawt" to use JAWT
        } else {
            linkerOpts("-L$javaHome/lib/server", "-ljvm") // add "-L$javaHome/lib", "-ljawt" to use JAWT
        }
    }
}
```

## Example

```kotlin
@CName("JNI_OnLoad")
fun onLoad(vm: JavaVM, unused: COpaquePointer): JniVersion {
    val version = JNI.lastVersion
    memScoped {
        vm.withEnv(version) {
            val clazz = FindClass("io/github/mimimishkin/samples/longcomputation/Main".utf8)

            if (clazz == null) {
                val exClass = FindClass("java/lang/Exception".utf8)!!
                ThrowNew(exClass, "Could not find class".utf8)
            } else {
                registerNativesFor(clazz, 1) {
                    register("nativeComputation".utf8, "(I)V".utf8, staticCFunction { env: JniEnv, clazz: JClass, count: JInt ->
                        val array = ByteArray(count)
                        for ((index, b) in array.withIndex()) {
                            array[index] = (b + index).toByte()
                        }
                    })
                }
            }
        }
    }

    return version
}
```