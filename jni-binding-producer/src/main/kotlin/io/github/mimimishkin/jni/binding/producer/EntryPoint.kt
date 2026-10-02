package io.github.mimimishkin.jni.binding.producer

import com.google.auto.service.AutoService
import io.github.mimimishkin.jni.binding.BuildConfig
import io.github.mimimishkin.jni.binding.producer.fir.FirJniBindingGenerator
import io.github.mimimishkin.jni.binding.producer.fir.FirJniBindingUseChecker
import io.github.mimimishkin.jni.binding.producer.fir.JvmSignatureProvider
import io.github.mimimishkin.jni.binding.producer.ir.JniHookStepsMerger
import io.github.mimimishkin.jni.binding.producer.model.JniVersion
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.compiler.plugin.registerExtension
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.config.targetPlatform
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.platform.konan.NativePlatformWithTarget
import java.io.File

internal object Keys {
    val ENABLED = CompilerConfigurationKey<Boolean>("Enable plugin")
    val EXPECTED_JDK_VERSION = CompilerConfigurationKey<Int>("JDK major version whose JNI version will be used")
    val ALLOW_SEVERAL_HOOKS = CompilerConfigurationKey<Boolean>("Allow several hooks of the same type")
    val USE_REGISTER_NATIVES = CompilerConfigurationKey<Boolean>("Generate bindings using RegisterNatives")
    val ACTUALS_FILE = CompilerConfigurationKey<File>("JSON file to write the actuals into")
}

/**
 * Registers the plugin's command-line options:
 * - `enabled` - master switch; `false` skips all generation and checking,
 * - `expectedJdkVersion` - JDK major version whose JNI version will be reported by the generated `JNI_OnLoad`,
 * - `allowSeveralHooks` - relaxes the single-`@JniOnLoad` constraint,
 * - `useRegisterNatives` - generates `RegisterNatives` bindings instead of exported `@CName` facades,
 * - `actualsFile` - the JSON file the `actuals.json` contract is written into (required).
 */
@OptIn(ExperimentalCompilerApi::class)
@AutoService(CommandLineProcessor::class)
public class JniBindingProducerCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = BuildConfig.PRODUCER_ARTIFACT

    private val enabledOption = CliOption(
        optionName = "enabled",
        valueDescription = "<true|false>",
        description = "Whether to enable the plugin or not.",
        required = false
    )
    private val expectedJdkVersionOption = CliOption(
        optionName = "expectedJdkVersion",
        valueDescription = "<java major version>",
        description = "JDK major version whose JNI version will be used.",
        required = false
    )
    private val allowSeveralHookOption = CliOption(
        optionName = "allowSeveralHooks",
        valueDescription = "<true|false>",
        description = "Whether to allow several hooks of the same type to be exposed.",
        required = false
    )
    private val useRegisterNativesOption = CliOption(
        optionName = "useRegisterNatives",
        valueDescription = "<true|false>",
        description = "Whether to generate bindings using RegisterNatives.",
        required = false
    )
    private val actualsFileOption = CliOption(
        optionName = "actualsFile",
        valueDescription = "<path>",
        description = "JSON file to write the actuals into.",
        required = true
    )
    override val pluginOptions: Collection<AbstractCliOption> = listOf(
        enabledOption,
        expectedJdkVersionOption,
        allowSeveralHookOption,
        useRegisterNativesOption,
        actualsFileOption
    )

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration
    ) {
        when (option) {
            enabledOption -> configuration.put(Keys.ENABLED, value.toBoolean())
            expectedJdkVersionOption -> configuration.put(Keys.EXPECTED_JDK_VERSION, value.toInt())
            allowSeveralHookOption -> configuration.put(Keys.ALLOW_SEVERAL_HOOKS, value.toBoolean())
            useRegisterNativesOption -> configuration.put(Keys.USE_REGISTER_NATIVES, value.toBoolean())
            actualsFileOption -> configuration.put(Keys.ACTUALS_FILE, File(value))
        }
    }
}

/**
 * Wires the producer into a compilation: reads the plugin options, resets the `actuals.json` contract file
 * once per process, then registers the FIR checker/generator extensions and the IR hook-steps merger.
 */
@OptIn(ExperimentalCompilerApi::class)
@AutoService(CompilerPluginRegistrar::class)
public class JniBindingProducerRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = "io.github.mimimishkin.jni-binding-producer"

    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        if (configuration[Keys.ENABLED] == false) return

        val actualsFile = configuration[Keys.ACTUALS_FILE]
            ?: error("jni-binding-producer: the 'actualsFile' option is required")
        val jniVersion = when {
            configuration.isAndroidTarget() -> JniVersion.V1_6
            else -> JniVersion.fromMajor(configuration[Keys.EXPECTED_JDK_VERSION, 1])
        }
        val useRegisterNatives = configuration.getBoolean(Keys.USE_REGISTER_NATIVES)
        val allowSeveralHooks = configuration.getBoolean(Keys.ALLOW_SEVERAL_HOOKS)

        // Reset the file once per compilation process. `configurePlugin` (and therefore every fragment session) must
        // not reset it: a Kotlin/Native module compiles into several fragments in one process, each appending its own
        // actuals to the same file, and a per-fragment reset would clobber what the other fragments already wrote.
        actualsFile.parentFile?.mkdirs()
        actualsFile.writeText("[]")

        FirExtensionRegistrar.registerExtension(
            FirRegistrar(
                jniVersion = jniVersion,
                useRegisterNatives = useRegisterNatives,
                allowSeveralHooks = allowSeveralHooks,
                actualsFile = actualsFile,
            )
        )
        IrGenerationExtension.registerExtension(
            JniHookStepsMerger()
        )
    }

    private fun CompilerConfiguration.isAndroidTarget(): Boolean =
        this.targetPlatform?.componentPlatforms.orEmpty()
            .filterIsInstance<NativePlatformWithTarget>()
            .any { it.target.family == Family.ANDROID }

    private class FirRegistrar(
        val jniVersion: JniVersion,
        val useRegisterNatives: Boolean,
        val allowSeveralHooks: Boolean,
        val actualsFile: File,
    ) : FirExtensionRegistrar() {
        override fun ExtensionRegistrarContext.configurePlugin() {
            +{ session: FirSession -> JvmSignatureProvider(session) }
            +{ session: FirSession -> FirJniBindingUseChecker(session, allowSeveralHooks) }
            +{ session: FirSession -> FirJniBindingGenerator(session, jniVersion, useRegisterNatives, actualsFile) }
        }
    }
}