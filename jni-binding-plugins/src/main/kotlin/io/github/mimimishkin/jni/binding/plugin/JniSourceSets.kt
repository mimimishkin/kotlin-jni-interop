package io.github.mimimishkin.jni.binding.plugin

import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

/**
 * The source set that dependencies shared by [compilations] should be added to.
 *
 * Several compilations of the same kind usually have different default source sets - `jvmMain` and `androidMain`,
 * `mingwX64Main` and `linuxX64Main` - and a dependency added to any one of them is not visible to the code shared by
 * the others. Both plugins therefore add theirs once, to the source set all of them have in common.
 *
 * @param compilations the compilations sharing the dependency, already narrowed to one compilation kind.
 * @param common the shared source set to prefer, or `null` to compute the lowest common ancestor instead. Passing
 *   `null` is what keeps a platform-specific dependency out of a source set that other platforms also compile against.
 */
internal fun mostCommonSourceSet(
    compilations: List<KotlinCompilation<*>>,
    common: KotlinSourceSet?,
): KotlinSourceSet? {
    val sourceSets = compilations.map { it.defaultSourceSet }.distinct()

    if (sourceSets.isEmpty()) return null
    if (sourceSets.size == 1) return sourceSets.single()

    return common
        ?: sourceSets
            .map { it.ancestors() }
            .reduce { acc, ancestors -> acc intersect ancestors }
            .maxByOrNull { it.ancestors().size }
}

/**
 * `commonMain` or `commonTest`, whichever matches [compilationName], or `null` in a project that has no common source
 * set for it.
 */
internal fun commonSourceSet(kotlin: KotlinMultiplatformExtension, compilationName: String): KotlinSourceSet? =
    kotlin.sourceSets.findByName(
        if (compilationName == KotlinCompilation.MAIN_COMPILATION_NAME) {
            KotlinSourceSet.COMMON_MAIN_SOURCE_SET_NAME
        } else {
            KotlinSourceSet.COMMON_TEST_SOURCE_SET_NAME
        }
    )

/**
 * This source set and every source set it depends on, itself included.
 */
private fun KotlinSourceSet.ancestors(): Set<KotlinSourceSet> {
    val ancestors = linkedSetOf<KotlinSourceSet>()

    fun visit(current: KotlinSourceSet) {
        if (!ancestors.add(current)) return
        current.dependsOn.forEach { visit(it) }
    }

    visit(this)
    return ancestors
}
