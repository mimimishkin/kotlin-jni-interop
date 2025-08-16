plugins {
    id("convention.jvm-library")
    alias(libs.plugins.serialization)
    kotlin("kapt")
    id("convention.publish")
}

dependencies {
    implementation(libs.serialization.json)
    implementation(libs.ksp.api)
    implementation(libs.bundles.kotlinPoet)
    compileOnly(libs.autoService.annotations)
    kapt(libs.autoService.processor)
}