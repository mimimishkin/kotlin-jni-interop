# Module jni-binding-plugins

Producer and consumer plugins for Kotlin/Native interop with JNI.

Producer plugin exports every function annotated with `@JniActual` to JNI, then the consumer plugin checks that all 
functions annotated with `@JniExpect` have a corresponding `@JniActual` function in the producer module.