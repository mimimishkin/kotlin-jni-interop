plugins {
    id("convention.native64bit-library")
    id("convention.publish")
}

description = "Annotations for kotlin-jni-interop project"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_1_1
}

kotlin {
    jvm()
}