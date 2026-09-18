package io.github.mimimishkin.jni.binding.plugin.consumer

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.domainObjectContainer
import org.jetbrains.kotlin.gradle.plugin.HasProject
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.androidJvm
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.jvm
import org.jetbrains.kotlin.tooling.core.Extras
import org.jetbrains.kotlin.tooling.core.extrasKeyOf
import org.jetbrains.kotlin.tooling.core.getOrPut

/**
 * Creates a backing container of [JniLibraryConfig]s for the given compilation.
 */
private fun jniLibraryConfigSet(compilation: KotlinCompilation<*>): NamedDomainObjectContainer<JniLibraryConfig> =
    compilation.project.objects.domainObjectContainer(JniLibraryConfig::class) { name ->
        JniLibraryConfig(name, compilation)
    }

/**
 * Container of JNI libraries configured for a single JVM/Android [compilation][KotlinCompilation].
 *
 * Each element is a [JniLibraryConfig] named after its native library.
 */
public class JniLibrariesContainer(
    /**
     * The compilation these JNI libraries are wired to.
     */
    public val compilation: KotlinCompilation<*>,
    backingContainer: NamedDomainObjectContainer<JniLibraryConfig> = jniLibraryConfigSet(compilation)
) : NamedDomainObjectContainer<JniLibraryConfig> by backingContainer, HasProject {
    override val project: Project = compilation.project

    public companion object {
        /**
         * Key used to store this container in compilation [Extras].
         */
        public val EXTRAS_KEY: Extras.Key<JniLibrariesContainer> = extrasKeyOf<JniLibrariesContainer>()
    }

    init {
        require(compilation.platformType in listOf(androidJvm, jvm)) {
            "Only jvm and android targets can be used with jniLibrary"
        }
    }
}

/**
 * Container of JNI libraries of this compilation. Created lazily on first access.
 */
public val KotlinCompilation<*>.jniLibraries: JniLibrariesContainer
    get() = extras.getOrPut(JniLibrariesContainer.EXTRAS_KEY) { JniLibrariesContainer(this) }

/**
 * Provider returning the container only if it has already been created, `null` otherwise.
 */
internal val KotlinCompilation<*>.jniLibrariesProvider: Provider<JniLibrariesContainer>
    get() = project.provider { extras[JniLibrariesContainer.EXTRAS_KEY] }

/**
 * Configures JNI libraries of this compilation with the given [action].
 */
public fun KotlinCompilation<*>.jniLibraries(action: Action<JniLibrariesContainer>) {
    action.execute(jniLibraries)
}
