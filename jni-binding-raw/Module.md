# Module jni-binding-raw

Library `jni-binding-raw` contains a raw output of Cinterop on all headers from `jdk/include`. They are
`jni.h`, `jawt.h`, `classfile_constants.h`, `jdwpTransport.h`, `jvmti.h`, `jvmticmlr.h`.  
Also, much more convenient to use `jni-binding` library is available.

*Raw* here means that it's not wrapped somehow. Just preprocessed cinterop for all desktop native targets:
mingwX64, linuxX64, linuxArm64, macosArm64.

All declarations can be found in `jni` package.

## Configuration notes

While configuring cinterop for jni by your own, you can find out several issues: 

- Cinterop doesn't support opaque structures. This is reflected in the types like `cnames.structs.jobject` which cannot
  be resolved and cause warnings during compilation and IDE errors.  
  To avoid this, the following lines were added to the end of a `.def` file:
  ```
  typedef struct _jobject {} *jobject;
  typedef struct _jfieldID {} *jfieldID;
  typedef struct _jmethodID {} *jmethodID;
  ```

- In original `jni.h` file some functions require JNI environment and Java VM as an opaque pointer instead of `JNIEnv` 
  and `JavaVM`. The header file was modified for a better type safety experience. 