plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    explicitApiWarning()
}

dependencies {
    testImplementation(kotlin("test"))
}