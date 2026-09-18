package io.github.mimimishkin.jni.binding.plugin

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.findByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBinary

/**
 * JVM language version of this project's Java toolchain, or `null` if the Java plugin is not applied.
 */
internal val Project.targetJVM: Provider<JavaLanguageVersion> get() =
    project.extensions.findByType<JavaPluginExtension>()
        ?.toolchain
        ?.languageVersion
        ?: provider { null }

/**
 * Joins [parts] into a single camelCase name, skipping `null` and empty parts.
 */
internal fun camelCase(vararg parts: String?): String {
    val parts = parts.mapNotNull { it?.takeIf(String::isNotEmpty) }
    val pascalCase = parts.joinToString("") { part -> part.replaceFirstChar { it.uppercase() } }
    return pascalCase.replaceFirstChar { it.lowercase() }
}

/**
 * Builds a configuration/task name unique for this compilation, prefixed with the target
 * and compilation names when applicable.
 */
internal fun KotlinCompilation<*>.disambiguateName(vararg simpleNames: String): String {
    return camelCase(
        target.disambiguationClassifier,
        compilationName.takeIf { it != KotlinCompilation.MAIN_COMPILATION_NAME },
        *simpleNames
    )
}

/**
 * Final file name of this native binary, including platform-specific prefix and suffix
 * (e.g. `libnative.so`, `native.dll`).
 */
internal val NativeBinary.finalName: String
    get() {
        val kind = outputKind.compilerOutputKind
        val target = target.konanTarget
        return kind.prefix(target) + baseName + kind.suffix(target)
    }