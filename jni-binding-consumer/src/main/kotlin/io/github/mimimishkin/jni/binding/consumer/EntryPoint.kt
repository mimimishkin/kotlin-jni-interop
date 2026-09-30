package io.github.mimimishkin.jni.binding.consumer

import com.google.auto.service.AutoService
import io.github.mimimishkin.jni.binding.BuildConfig
import io.github.mimimishkin.jni.binding.consumer.fir.JniBindingUseChecker
import io.github.mimimishkin.jni.binding.consumer.ir.ProcessJniExpectsTransformer
import io.github.mimimishkin.jni.binding.consumer.jvm.JniExpectMatcher
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.jvm.extensions.ClassGeneratorExtension
import org.jetbrains.kotlin.cli.report
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.compiler.plugin.registerExtension
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import java.io.File

internal object Keys {
    val ENABLED = CompilerConfigurationKey<Boolean>("Enable plugin")
    val ACTUALS = CompilerConfigurationKey<Map<String, List<File>>>("actuals.json files, one entry per target")
    val ALLOW_EXTRA_ACTUALS = CompilerConfigurationKey<Boolean>("Allow extra @JniActuals")
    val EXPECTS_FILE = CompilerConfigurationKey<File>("JSON file to write the expects into")
}

@OptIn(ExperimentalCompilerApi::class)
@AutoService(CommandLineProcessor::class)
public class JniBindingConsumerCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = BuildConfig.CONSUMER_ARTIFACT

    private val enabledOption = CliOption(
        optionName = "enabled",
        valueDescription = "<true|false>",
        description = "Whether to enable the plugin or not.",
        required = false
    )
    private val actualsFileOption = CliOption(
        optionName = "actualsFile",
        valueDescription = "<target>:<path>",
        description = "Path to the actuals.json file of a specified target.",
        required = false,
        allowMultipleOccurrences = true
    )
    private val allowExtraActualsOption = CliOption(
        optionName = "allowExtraActuals",
        valueDescription = "<true|false>",
        description = "Whether to allow extra @JniActuals without corresponding @JniExpect.",
        required = false,
    )
    private val expectsFileOption = CliOption(
        optionName = "expectsFile",
        valueDescription = "<path>",
        description = "JSON file to write the expects into, read by the generateJniActuals task.",
        required = true,
    )
    override val pluginOptions: Collection<AbstractCliOption> = listOf(
        enabledOption,
        actualsFileOption,
        allowExtraActualsOption,
        expectsFileOption,
    )

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration
    ) {
        when (option) {
            enabledOption -> configuration.put(Keys.ENABLED, value.toBoolean())
            actualsFileOption -> {
                val (target, path) = value.split(':', limit = 2)
                // Several libraries can provide bindings for the same target, so accumulate the files instead of
                // replacing the previous provider of this target.
                configuration.put(
                    Keys.ACTUALS,
                    target,
                    configuration.getMap(Keys.ACTUALS)[target].orEmpty() + File(path)
                )
            }
            allowExtraActualsOption -> configuration.put(Keys.ALLOW_EXTRA_ACTUALS, value.toBoolean())
            expectsFileOption -> configuration.put(Keys.EXPECTS_FILE, File(value))
        }
    }
}

@OptIn(ExperimentalCompilerApi::class)
@AutoService(CompilerPluginRegistrar::class)
public class JniBindingConsumerRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = "io.github.mimimishkin.jni-binding-consumer"

    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        if (configuration[Keys.ENABLED] == false) return

        val actualFiles = configuration.getMap(Keys.ACTUALS)
        val allowExtraActuals = configuration.getBoolean(Keys.ALLOW_EXTRA_ACTUALS)
        val state = ConsumerState(
            actualFiles = actualFiles,
            expectsFile = checkNotNull(configuration[Keys.EXPECTS_FILE]) {
                "The 'expectsFile' plugin option is not set, but the plugin is enabled. The jni-binding consumer " +
                    "plugin always has to be given one; pass 'enabled=false' when there is nothing to do."
            },
            allowExtraActuals = allowExtraActuals,
            reportMessage = { factory, message -> configuration.report(factory, message) }
        )

        FirExtensionRegistrar.registerExtension(
            FirRegistrar
        )
        IrGenerationExtension.registerExtension(
            ProcessJniExpectsExtension(state)
        )
        ClassGeneratorExtension.registerExtension(
            JniExpectMatcher(state)
        )
    }

    internal class ProcessJniExpectsExtension(
        private val state: ConsumerState,
    ) : IrGenerationExtension {
        override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
            val transformer = ProcessJniExpectsTransformer(
                pluginContext = pluginContext,
                state = state,
            )
            moduleFragment.transform(transformer = transformer, data = null)
        }
    }

    private object FirRegistrar : FirExtensionRegistrar() {
        override fun ExtensionRegistrarContext.configurePlugin() {
            +::JniBindingUseChecker
        }
    }
}
