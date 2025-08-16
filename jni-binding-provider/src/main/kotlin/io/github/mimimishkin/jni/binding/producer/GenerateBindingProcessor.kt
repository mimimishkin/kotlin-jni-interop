package io.github.mimimishkin.jni.binding.producer

import com.google.auto.service.AutoService
import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.symbol.FunctionKind.TOP_LEVEL
import com.google.devtools.ksp.symbol.Visibility.INTERNAL
import com.google.devtools.ksp.symbol.Visibility.PUBLIC
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ksp.kspDependencies
import com.squareup.kotlinpoet.ksp.writeTo
import io.github.mimimishkin.jni.binding.producer.GenerateBindingProcessor.Provider.Companion.ALLOW_SEVERAL_HOOKS_KEY
import io.github.mimimishkin.jni.binding.producer.JniActualInfo.JvmSignature
import kotlinx.serialization.json.Json

public class GenerateBindingProcessor(
    private val logger: KSPLogger,
    private val codeGenerator: CodeGenerator,
    private val jniVersion: JniVersion,
    private val allowSeveralHooks: Boolean,
    private val useOnLoad: Boolean,
    private val interopConfig: JniInteropConfig,
    private val expectations: Set<JniExpectInfo>,
    private val allowExtraActuals: Boolean,
) : SymbolProcessor {
    @AutoService(SymbolProcessorProvider::class)
    public class Provider : SymbolProcessorProvider {
        public companion object {
            public const val JNI_VERSION_KEY: String = "jniBinding.jniVersion"
            public const val ALLOW_SEVERAL_HOOKS_KEY: String = "jniBinding.allowSeveralHooks"
            public const val USE_ON_LOAD_KEY: String = "jniBinding.useOnLoad"
            public const val INTEROP_CONFIG_KEY: String = "jniBinding.interopConfig"
            public const val EXPECTATIONS_CONFIG_KEY: String = "jniBinding.expectations"
            public const val ALLOW_EXTRA_BINDINGS_CONFIG_KEY: String = "jniBinding.allowExtraActuals"
        }

        override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
            val jniVersion = JniVersion.fromMajor(environment.options[JNI_VERSION_KEY]?.toInt() ?: 1)
            val allowSeveralHooks = environment.options[ALLOW_SEVERAL_HOOKS_KEY].toBoolean()
            val useOnLoad = environment.options[USE_ON_LOAD_KEY].toBoolean()
            val interopConfig = environment.options[INTEROP_CONFIG_KEY]
                ?.let {
                    if (it == "FromJniBinding") JniInteropConfig.FromJniBinding
                    else Json.decodeFromString<JniInteropConfig>(it)
                }
                ?: error("$INTEROP_CONFIG_KEY option must be specified in ksp arguments")
            val expectations = environment.options[EXPECTATIONS_CONFIG_KEY]
                ?.let { Json.decodeFromString<Set<JniExpectInfo>>(it) }
                ?: emptySet()
            val allowExtraBindings = environment.options[ALLOW_EXTRA_BINDINGS_CONFIG_KEY]?.toBoolean()
                ?: expectations.isEmpty()

            return GenerateBindingProcessor(
                logger = environment.logger,
                codeGenerator = environment.codeGenerator,
                jniVersion = jniVersion,
                allowSeveralHooks = allowSeveralHooks,
                useOnLoad = useOnLoad,
                interopConfig = interopConfig,
                expectations = expectations,
                allowExtraActuals = allowExtraBindings,
            )
        }
    }
    
    public companion object {
        internal const val paket = "io.github.mimimishkin.jni.binding"
        internal const val annotationsPaket = "$paket.annotation"
    }

    private val allRoundsActuals = mutableListOf<JniActualInfo>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val actuals = describeAllActuals(resolver).toList().also { allRoundsActuals += it }
        val beforeLoad = whatsBeforeLoadOrUnload(resolver, load = true).toList()
        val beforeUnload = whatsBeforeLoadOrUnload(resolver, load = false).toList()

        val generator = BindingGenerator(interopConfig)
        if (!useOnLoad) {
            generateJniFunctions(generator, actuals)
            generateOnLoad(generator, beforeLoad, actuals = emptyList())
            generateOnUnload(generator, beforeUnload)
        } else {
            generateOnLoad(generator, beforeLoad, actuals)
            generateOnUnload(generator, beforeUnload)
        }

        return emptyList()
    }

    override fun finish() {
        val actuals = allRoundsActuals.toMutableSet()

        expectations.forEach { expect ->
            val actual = actuals.find { expect.matches(it) }
            if (actual != null) {
                actuals -= actual
            } else {
                logger.error("No @JniActual found for expect: $expect.")
            }
        }

        if (!allowExtraActuals && actuals.isNotEmpty()) {
            val methods = actuals.groupBy { it.source }.asIterable().joinToString("; ") { (file, actuals) ->
                actuals.joinToString { it.method.canonicalName } + " in $file"
            }
            logger.error("There are @JniActual functions without expect counterparts: $methods.")
        }
    }

    private fun describeAllActuals(resolver: Resolver): Sequence<JniActualInfo> = sequence {
        val annotated = resolver.getSymbolsWithAnnotation("$annotationsPaket.JniActual")
            .filterIsInstance<KSFunctionDeclaration>()
            .filter { !it.isActual }
        for (actualFun in annotated) logging(actualFun) {
            val (jvmClass, jvmMethod) = actualFun.annotations.single { it.isJniActual() }.run {
                val className = arguments.first { it.name?.asString() == "className" }.value as String
                val methodName = arguments.firstOrNull { it.name?.asString() == "methodName" }?.value as String?
                ClassName.guess(className) to methodName.orEmpty().ifEmpty { actualFun.simpleName.asString() }
            }

            require(actualFun.functionKind == TOP_LEVEL) { "@JniActual can only be used on top-level functions" }
            require((actualFun.parent as KSAnnotated).annotations.none { it.isJniActuals() }) {
                "@JniActual cannot be used together with @JniActuals"
            }

            yield(describeActual(actualFun, jvmClass, jvmMethod, container = null))
        }

        val actualsContainers = resolver.getSymbolsWithAnnotation("$annotationsPaket.JniActuals")
            .filterIsInstance<KSClassDeclaration>()
            .filter { !it.isActual }
        for (actualsContainer in actualsContainers) logging(actualsContainer) {
            yieldAll(describeActuals(actualsContainer))
        }
    }

    private fun describeActuals(container: KSClassDeclaration): Sequence<JniActualInfo> {
        require(container.classKind == ClassKind.OBJECT) { "@JniActuals can only be used on object classes" }
        require(container.parent is KSFile) { "@JniActuals can only be used on top-level objects" }

        val containerQualifier = ClassName(container.packageName.asString(), container.simpleName.asString())

        val jvmClass = container.annotations.single { it.isJniActuals() }.run {
            val className = arguments.find { it.name?.asString() == "className" }?.value as String?
            if (className != null && className.isNotEmpty()) ClassName.guess(className) else containerQualifier
        }

        return container.getAllFunctions().map { function ->
            val actualName = function.simpleName.asString()
            describeActual(function, jvmClass, actualName, containerQualifier)
        }
    }

    private fun describeActual(
        actualFun: KSFunctionDeclaration,
        jvmClass: ClassName,
        jvmMethod: String,
        container: ClassName?,
    ): JniActualInfo {
        val actualPackage = actualFun.packageName.asString()
        val actualName = actualFun.simpleName.asString()

        val visibility = actualFun.getVisibility()
        require(visibility in listOf(PUBLIC, INTERNAL)) {
            "@JniActual function must be visible from generated code but is $visibility"
        }

        /*
        TODO: move to JniExpect processor
        require(actualFun.annotations.none { it.isJvmOverloads() }) {
            "@JvmOverloads is not allowed for @JniActual functions. Prefer writing a non-external wrapper with this annotation"
        }
        */

        require(actualFun.typeParameters.isEmpty()) { "@JniActual functions cannot have type parameters" }

        val (receiver, isStatic) = interopConfig.checkObjOrCls(actualFun.extensionReceiver?.resolve())

        val parameterInfos = actualFun.parameters.map { parameter ->
            require(!parameter.hasDefault) { "Default parameters are not allowed in @JniActual functions" }
            require(!parameter.isVararg) { "Vararg parameters are not allowed in @JniActual functions" }
            interopConfig.makeType(parameter.type.resolve(), isReturnType = false)
        }

        val returnType = requireNotNull(actualFun.returnType) { "Error while resolving return type" }
        val returnInfo = interopConfig.makeType(returnType.resolve(), isReturnType = true)

        var jvmSignature = actualFun.annotations.singleOrNull { it.isWithJvmSignature() }?.let { annotation ->
            @Suppress("UNCHECKED_CAST")
            val parameterTypes = annotation.arguments.first { it.name?.asString() == "parameterTypes" }.value as Array<String>
            val returnType = annotation.arguments.first { it.name?.asString() == "returnType" }.value as String
            JvmSignature(parameterTypes.toList(), returnType)
        }

        if (jvmSignature == null) {
            val parameterTypes = actualFun.parameters.map {
                it.type.jvmType ?: error("Corresponding type on JVM side of parameter ${it.name?.asString()} is " +
                        "unknown. Specify it with @WithJvmType or @WithJvmSignature")
            }
            val returnType = returnType.jvmType ?: error("Corresponding type on JVM side of return type is unknown. " +
                    "Specify it with @WithJvmType or @WithJvmSignature")
            jvmSignature = JvmSignature(parameterTypes, returnType)
        }

        return JniActualInfo(
            source = actualFun.containingFile!!,
            method = container?.let { MemberName(it, actualName) } ?: MemberName(actualPackage, actualName),
            parameters = parameterInfos,
            returns = returnInfo,
            needEnv = true, // TODO: fix when ksp will support context parameters
            needObjOrCls = receiver != null,
            isStatic = isStatic,
            jvmClass = jvmClass,
            jvmMethod = jvmMethod,
            jvmSignature = jvmSignature,
        )
    }

    private fun whatsBeforeLoadOrUnload(resolver: Resolver, load: Boolean): Sequence<JniHookInfo> = sequence {
        val annotation = if (load) "JniOnLoad" else "JniOnUnload"
        val annotated = resolver.getSymbolsWithAnnotation("$annotationsPaket.$annotation")
            .filterIsInstance<KSFunctionDeclaration>()
            .filter { !it.isActual }
        for (hookFun in annotated) logging(hookFun) {
            val visibility = hookFun.getVisibility()
            require(visibility in listOf(PUBLIC, INTERNAL)) {
                "@$annotation function must be visible from generated code but is $visibility"
            }

            require(hookFun.typeParameters.isEmpty()) { "@$annotation functions cannot have type parameters" }
            require(hookFun.functionKind == TOP_LEVEL) { "@$annotation can only be used on top-level functions" }
            require(hookFun.extensionReceiver == null) { "@$annotation cannot be an extension function" }
            require(hookFun.returnType?.resolve() == resolver.builtIns.unitType) { "@$annotation cannot return a value" }
            require(hookFun.parameters.size <= 1) { "@$annotation cannot have more than one parameter" }

            hookFun.parameters.singleOrNull()?.let { parameter ->
                require(!parameter.hasDefault) { "@$annotation cannot have default parameters" }
                require(!parameter.isVararg) { "@$annotation cannot hava a vararg parameter" }
                interopConfig.checkVm(parameter.type.resolve())
            }

            val hook = JniHookInfo(
                source = hookFun.containingFile!!,
                method = MemberName(hookFun.packageName.asString(), hookFun.simpleName.asString()),
                needVm = hookFun.parameters.isNotEmpty(),
            )
            yield(hook)
        }
    }

    private fun generateJniFunctions(generator: BindingGenerator, actuals: Collection<JniActualInfo>) {
        actuals.groupBy { it.source }.forEach { (originatingFile, actuals) ->
            val fileName = originatingFile.fileName.removeSuffix(".kt") + "JniBinding"
            val packageName = generatedPackageName(originatingFile.packageName.asString())
            val file = FileSpec
                .builder(packageName, fileName)
                .addFunctions(actuals.map { generator.jniFunction(it) })
                .build()

            file.writeTo(codeGenerator, aggregating = false, listOf(originatingFile))
        }
    }

    private fun generateOnLoad(
        generator: BindingGenerator,
        hooks: Collection<JniHookInfo>,
        actuals: Collection<JniActualInfo>,
    ) = generateHook(generator, hooks, actuals, load = true)

    private fun generateOnUnload(
        generator: BindingGenerator,
        hooks: Collection<JniHookInfo>,
    ) = generateHook(generator, hooks, currentActuals = emptyList(), load = false)

    private var onLoadWasGenerated = false
    private val allRoundsOnLoad = mutableListOf<JniHookInfo>()
    private val allRoundsOnUnload = mutableListOf<JniHookInfo>()

    private fun generateHook(
        generator: BindingGenerator,
        currentHooks: Collection<JniHookInfo>,
        currentActuals: Collection<JniActualInfo>,
        load: Boolean
    ) {
        if (!load && currentHooks.isEmpty()) {
            return // skip generating an empty JNI_OnUnload function or unnecessary regeneration
        }
        if (load && onLoadWasGenerated && currentActuals.isEmpty() && currentHooks.isEmpty()) {
            return // skip unnecessary regeneration of a JNI_OnLoad function
        }

        val allRoundsHooks = if (load) {
            allRoundsOnLoad.also { it += currentHooks }
        } else {
            allRoundsOnUnload.also { it += currentHooks }
        }

        val actuals = if (useOnLoad) allRoundsActuals else emptyList()
        if (load && jniVersion == JniVersion.V1_1 && allRoundsHooks.isEmpty() && actuals.isEmpty()) {
            return // skip generating default JNI_OnLoad
        }

        check(allowSeveralHooks || allRoundsHooks.size <= 1) {
            val annotation = if (load) "@JniOnLoad" else "@JniOnUnload"
            "More than one $annotation function was detected. Please specify $ALLOW_SEVERAL_HOOKS_KEY option to " +
                    "true if you want to have several $annotation functions."
        }

        val file = FileSpec
            .builder(allRoundsHooks.mostCommonPackage(), if (load) "JniOnLoad" else "JniOnUnload")
            .addFunction(
                if (load) generator.onLoad(jniVersion, allRoundsHooks, actuals)
                else generator.onUnLoad(allRoundsHooks)
            )
            .build()

        // regenerate a hook if it already exists
        val output = try {
            codeGenerator.createNewFile(
                dependencies = file.kspDependencies(aggregating = true),
                packageName = file.packageName,
                fileName = file.name,
            )
        } catch (e: FileAlreadyExistsException) {
            e.file.outputStream()
        }

        output.writer().use { file.writeTo(it) }

        if (load) onLoadWasGenerated = true
    }

    private fun Iterable<WithSource>.mostCommonPackage(): String {
        val common = map { it.source.packageName.asString() }.reduceOrNull { a, b -> a.commonPrefixWith(b) }
        return generatedPackageName(common)
    }

    private fun generatedPackageName(packageName: String?) =
        if (packageName.isNullOrEmpty()) "generated" else "$packageName.generated"

    // simplifies error handling in the processor
    private inline fun logging(symbol: KSNode, block: () -> Unit) {
        try {
            return block()
        } catch (e: Exception) {
            logger.error("Problem in $symbol. " + e.message.orEmpty(), symbol)
        }
    }

    private fun KSAnnotation.isJniActual() = isAnnotation(annotationsPaket, "JniActual")
    private fun KSAnnotation.isJniActuals() = isAnnotation(annotationsPaket, "JniActuals")
    //    private fun KSAnnotation.isJvmOverloads() = isAnnotation("kotlin.jvm", "JvmOverloads")
    private fun KSAnnotation.isWithJvmSignature() = isAnnotation(annotationsPaket, "WithJvmSignature")
    private fun KSAnnotation.isWithJvmType() = isAnnotation(annotationsPaket, "WithJvmType")

    private val KSTypeReference.allAnnotations: Sequence<KSAnnotation> get() = sequence {
        yieldAll(annotations)

        var type = this@allAnnotations
        do {
            val alias = type.resolve().declaration as? KSTypeAlias ?: break
            yieldAll(alias.annotations)
            type = alias.type
        } while (true)
    }

    private val KSTypeReference.jvmType: String? get() {
        val annotation = allAnnotations.firstOrNull { it.isWithJvmType() }
        val jvmType = annotation?.let { it.arguments.single().value as String }
        if (jvmType != null) return jvmType

        return when (resolveActual().declaration.qualifiedName?.asString()) {
            "kotlin.Boolean" -> "boolean"
            "kotlin.UByte" -> "boolean"
            "kotlin.Byte" -> "byte"
            "kotlin.Char" -> "char"
            "kotlin.UShort" -> "char"
            "kotlin.Short" -> "short"
            "kotlin.Int" -> "int"
            "kotlin.Long" -> "long"
            "kotlin.Float" -> "float"
            "kotlin.Double" -> "double"
            "kotlin.Unit" -> "void"
            else -> null
        }
    }
}