# `samples/android-basic`

An Android application using `jni-binding` end to end: one Kotlin/Native library built for four ABIs and packaged
into the APK with no `CMakeLists.txt`, `Android.mk` or manual `jniLibs` copying.

## What it shows

`MainActivity` has one button per topic:

- callbacks into Java, including from native threads through a global reference;
- the `JavaVM` cached in `JNI_OnLoad` and worker threads that attach to it;
- direct buffers, local vs global references and local frames;
- exceptions thrown from native and caught from Java;
- calls into plain Java/Android types (`StringBuilder`, `HashMap`, `ArrayList`, `android.util.Log`) the plugin never
  bound;
- the ABIs the device supports.