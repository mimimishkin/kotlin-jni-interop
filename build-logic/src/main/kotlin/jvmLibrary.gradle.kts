import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.powerAssert)
    alias(libs.plugins.buildConfig)
}

kotlin {
    explicitApiWarning()
}

buildConfig {
    packageName("io.github.mimimishkin.jni.binding")
    buildConfigField("VERSION", project.version.toString())
    buildConfigField("GROUP", project.group.toString())

    buildConfigField("PLUGINS_ARTIFACT", "jni-binding-plugins")
    buildConfigField("CONSUMER_ARTIFACT", "jni-binding-consumer")
    buildConfigField("PRODUCER_ARTIFACT", "jni-binding-producer")
    buildConfigField("CINTEROP_ARTIFACT", "jni-binding-raw")
    buildConfigField("WRAPPER_ARTIFACT", "jni-binding")
    buildConfigField("ANNOTATIONS_ARTIFACT", "jni-binding-annotations")

    buildConfigField<String>("CONSUMER_ID", expression($$"\"$GROUP:$CONSUMER_ARTIFACT:$VERSION\""))
    buildConfigField<String>("PRODUCER_ID", expression($$"\"$GROUP:$PRODUCER_ARTIFACT:$VERSION\""))
    buildConfigField<String>("CINTEROP_ID", expression($$"\"$GROUP:$CINTEROP_ARTIFACT:$VERSION\""))
    buildConfigField<String>("WRAPPER_ID", expression($$"\"$GROUP:$WRAPPER_ARTIFACT:$VERSION\""))
    buildConfigField<String>("ANNOTATIONS_ID", expression($$"\"$GROUP:$ANNOTATIONS_ARTIFACT:$VERSION\""))
}

dependencies {
    testImplementation(kotlin("test"))
}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
powerAssert {
    functions = listOf(
        "kotlin.assert",
        "kotlin.test.assertTrue",
        "kotlin.test.assertFalse",
        "kotlin.test.assertEquals",
        "kotlin.test.assertNotEquals",
    )
}