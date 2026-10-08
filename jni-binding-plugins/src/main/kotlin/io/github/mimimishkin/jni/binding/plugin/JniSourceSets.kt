package io.github.mimimishkin.jni.binding.plugin

import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

/**
 * The source sets a dependency shared by [compilations] should be added to.
 *
 * Several compilations of the same kind usually have different default source sets - `jvmMain` and `androidMain`,
 * `mingwX64Main` and `linuxX64Main` - and a dependency added to any one of them is not visible to the code shared by
 * the others. Both plugins therefore add theirs to a source set all of them have in common, so that code written in
 * it compiles against the dependency on every one of them.
 *
 * When they have nothing in common, which a `jvm` and an `android` target without a shared intermediate source set
 * are a case of, there is no source set that would work for the group: a dependency added to one of them is invisible
 * to the other. Each of their own source sets is returned then, one dependency per target, rather than picking one
 * target and leaving the rest without it.
 *
 * @param compilations the compilations sharing the dependency, already narrowed to one compilation kind.
 * @param common the shared source set to prefer, or `null` to compute the lowest common ancestor instead. Passing
 *   `null` is what keeps a platform-specific dependency out of a source set that other platforms also compile against.
 * @return the source sets to add the dependency to, empty if there are no compilations at all.
 */
internal fun dependencySourceSets(
    compilations: List<KotlinCompilation<*>>,
    common: KotlinSourceSet?,
): Set<KotlinSourceSet> {
    val sourceSets = compilations.map { it.defaultSourceSet }.distinct()
    if (sourceSets.size <= 1) return sourceSets.toSet()

    val shared = common
        ?: sourceSets
            .map { it.ancestors() }
            .reduce { acc, ancestors -> acc intersect ancestors }
            .maxByOrNull { it.ancestors().size }

    return shared?.let { setOf(it) } ?: sourceSets.toSet()
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
