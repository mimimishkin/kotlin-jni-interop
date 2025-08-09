package io.github.mimimishkin.jni.binding.plugin.producer

import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.symbol.FunctionKind.TOP_LEVEL
import com.google.devtools.ksp.symbol.Visibility.INTERNAL
import com.google.devtools.ksp.symbol.Visibility.PUBLIC
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.ksp.toTypeName
import io.github.mimimishkin.jni.binding.plugin.guess
import io.github.mimimishkin.jni.binding.plugin.producer.JniExportMethod.BindOnLoad
import io.github.mimimishkin.jni.binding.plugin.producer.JniExportMethod.ExposeFunctions

public class GenerateBindingProcessor(
    private val logger: KSPLogger,
    private val codeGenerator: CodeGenerator,
    private val jniVersion: Int,
    private val signatureTypes: SignatureTypes,
    private val generateHooks: Boolean,
    private val allowSeveralHooks: Boolean,
    private val exportMethod: JniExportMethod,
) : SymbolProcessor {
    public companion object Provider : SymbolProcessorProvider {
        override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
            return GenerateBindingProcessor(
                logger = environment.logger,
                codeGenerator = environment.codeGenerator,
                jniVersion = environment.options["jniVersion"]?.toInt() ?: 1,
                signatureTypes = environment.options["signatureTypes"]?.let { SignatureTypes.valueOf(it) }
                    ?: SignatureTypes.CorrectlyNamed,
                generateHooks = environment.options["generateHooks"]?.toBoolean() ?: true,
                allowSeveralHooks = environment.options["allowSeveralHooks"]?.toBoolean() ?: true,
                exportMethod = environment.options["exportMethod"]?.let { JniExportMethod.valueOf(it) }
                    ?: ExposeFunctions,
            )
        }
    }

    private inline fun <T> logging(symbol: KSNode, block: () -> T): T? {
        try {
            return block()
        } catch (e: Exception) {
            logger.error(e.message.orEmpty(), symbol)
            return null
        }
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        fun KSAnnotation.isJniActual(): Boolean {
            return shortName.asString() == "JniActual" && annotationType.resolve().declaration.qualifiedName?.asString() == "io.github.mimimishkin.jni.annotations.JniActual"
        }
        fun KSAnnotation.isJniActuals(): Boolean {
            return shortName.asString() == "JniActuals" && annotationType.resolve().declaration.qualifiedName?.asString() == "io.github.mimimishkin.jni.annotations.JniActuals"
        }
        fun KSAnnotation.isJvmOverloads(): Boolean {
            return shortName.asString() == "JvmOverloads" && annotationType.resolve().declaration.qualifiedName?.asString() == "kotlin.jvm.JvmOverloads"
        }

        fun KSFunctionDeclaration.describeActual(
            jvmClass: ClassName,
            jvmMethod: String,
            container: ClassName?,
        ): JniActualInfo? {
            val actualFun = this@describeActual
            val actualPackage = actualFun.packageName.asString()
            val actualName = actualFun.simpleName.asString()

            fun KSType.toParameterType(isReturnType: Boolean): JniActualInfo.Parameter? {
                return logging(actualFun) {
                    if (isReturnType) signatureTypes.makeReturnType(this) else signatureTypes.makeParameter(this)
                }
            }

            val visibility = actualFun.getVisibility()
            if (visibility !in listOf(PUBLIC, INTERNAL)) {
                logger.error("@JniActual function must be visible from generated code but is $visibility", actualFun)
                return null
            }

            if (actualFun.functionKind != TOP_LEVEL) {
                logger.error("@JniActual can only be used on top-level functions", actualFun)
                return null
            }

            if ((actualFun.parent as KSAnnotated).annotations.any(KSAnnotation::isJniActuals)) {
                logger.error("@JniActual cannot be used together with @JniActuals", actualFun)
                return null
            }

            if (actualFun.annotations.any(KSAnnotation::isJvmOverloads)) {
                logger.error("@JvmOverloads is not allowed for @JniActual functions. Prefer writing a non-external wrapper with this annotation", actualFun)
                return null
            }

            if (actualFun.typeParameters.isNotEmpty()) {
                logger.error("@JniActual functions cannot have type parameters", actualFun)
                return null
            }

            if (actualFun.isActual) {
                return null
            }

            val receiver = actualFun.extensionReceiver?.resolve()
            val isStatic = receiver?.let {
                logging(actualFun) { signatureTypes.checkReceiver(it) } ?: return null
            }

            val parameters = actualFun.parameters.map { parameter ->
                if (parameter.hasDefault) {
                    logger.error("Default parameters are not allowed in @JniActual functions", parameter)
                    return null
                }

                parameter.type.resolve().toParameterType(isReturnType = false) ?: return null
            }

            val returnType = actualFun.returnType?.resolve().let { returnType ->
                if (returnType == null) {
                    logger.error("Return type is required for @JniActual function", actualFun)
                    return null
                }

                returnType.toParameterType(isReturnType = true) ?: return null
            }

            return JniActualInfo(
                container = container,
                methodQualifier = if (container == null) "$actualPackage.$actualName" else actualName,
                parameters = parameters,
                returns = returnType,
                isStatic = isStatic,
                jvmClass = jvmClass,
                jvmMethod = jvmMethod,
                objOrClsType = receiver?.toTypeName(),
                envType = ClassName("io.github.mimimishkin.jni.binding", "JniEnv"), // TODO: fix it when ksp will support context parameters
            )
        }

        fun KSClassDeclaration.describeActuals(): Sequence<JniActualInfo> {
            val container = this@describeActuals
            if (container.classKind != ClassKind.OBJECT) {
                logger.error("@JniActuals can only be used on object classes", container)
                return emptySequence()
            }

            if (container.isActual) {
                return emptySequence()
            }

            val containerQualifier = ClassName(packageName.asString(), container.simpleName.asString())

            val jvmClass = container.annotations.single(KSAnnotation::isJniActuals).run {
                val className = arguments.first { it.name?.asString() == "className" }.value as String
                if (className.isNotEmpty()) ClassName.guess(className) else containerQualifier
            }

            return container.getAllFunctions().mapNotNull { function ->
                val actualName = function.simpleName.asString()
                function.describeActual(jvmClass, actualName, containerQualifier)
            }
        }

        val actualsInfo = mutableMapOf<KSFile, MutableList<JniActualInfo>>().withDefault { mutableListOf() }

        val actuals = resolver.getSymbolsWithAnnotation("io.github.mimimishkin.jni.annotations.JniActual").filterIsInstance<KSFunctionDeclaration>()
        val actualsContainers = resolver.getSymbolsWithAnnotation("io.github.mimimishkin.jni.annotations.JniActuals").filterIsInstance<KSClassDeclaration>()

        for (actualFun in actuals) {
            val (jvmClass, jvmMethod) = actualFun.annotations.single(KSAnnotation::isJniActual).run {
                val className = arguments.first { it.name?.asString() == "className" }.value as String
                val methodName = arguments.first { it.name?.asString() == "methodName" }.value as String
                ClassName.guess(className) to methodName.ifEmpty { actualFun.simpleName.asString() }
            }

            actualsInfo[actualFun.containingFile]!! += actualFun.describeActual(jvmClass, jvmMethod, container = null) ?: continue
        }

        for (actualsContainer in actualsContainers) {
            actualsInfo[actualsContainer.containingFile]!! += actualsContainer.describeActuals()
        }

        when (exportMethod) {
            ExposeFunctions -> {
                actualsInfo.forEach { (file, actualsInfo) ->
                    val writer = codeGenerator.createNewFile(
                        dependencies = Dependencies(aggregating = false, file),
                        packageName = file.packageName.asString(),
                        fileName = file.fileName.removeSuffix(".kt") + "Binding",
                    ).writer()

                    FileSpec
                        .builder(file.packageName.asString(), file.fileName)
                        .addFunctions(actualsInfo.map(BindingGenerator::jniFunction))
                        .build()
                        .writeTo(writer)
                }
            }
            BindOnLoad -> {
                TODO("Not yet implemented")
            }
        }

        return emptyList()
    }
}