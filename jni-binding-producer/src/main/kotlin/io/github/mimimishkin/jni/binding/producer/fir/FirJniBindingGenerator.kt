package io.github.mimimishkin.jni.binding.producer.fir

import io.github.mimimishkin.jni.binding.producer.*
import io.github.mimimishkin.jni.binding.producer.model.JniActualInfo
import io.github.mimimishkin.jni.binding.producer.model.JniVersion
import kotlinx.serialization.json.Json
import org.jetbrains.kotlin.KtLightSourceElement
import org.jetbrains.kotlin.KtPsiSourceElement
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.Visibilities.Inherited
import org.jetbrains.kotlin.descriptors.Visibilities.Internal
import org.jetbrains.kotlin.descriptors.Visibilities.Public
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.declaredFunctions
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.utils.isClass
import org.jetbrains.kotlin.fir.declarations.utils.isExpect
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.expressions.builder.buildBlock
import org.jetbrains.kotlin.fir.expressions.builder.buildLiteralExpression
import org.jetbrains.kotlin.fir.expressions.builder.buildReturnExpression
import org.jetbrains.kotlin.fir.extensions.*
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.packageFqName
import org.jetbrains.kotlin.fir.resolve.providers.firProvider
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.constructType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.types.ConstantValueKind
import java.io.File

/**
 * Coordinates `@JniActual`/`@JniActuals`/`@JniOnLoad`/`@JniOnUnload` FIR declaration generation.
 *
 * The FIR *construction* lives in [FirBuilder], JVM type resolution in [JvmSignatureProvider], and the
 * facade/container/`RegisterNatives` bodies in [FirJniFacades].
 */
@OptIn(ExperimentalTopLevelDeclarationsGenerationApi::class, SymbolInternals::class)
internal class FirJniBindingGenerator(
    session: FirSession,
    private val jniVersion: JniVersion,
    private val useRegisterNatives: Boolean,
    private val isAndroid: Boolean,
    private val actualsFile: File,
) : FirDeclarationGenerationExtension(session) {

    private val provider = session.predicateBasedProvider
    private val builder = FirBuilder(session)
    private val facades = FirJniFacades(builder, session, jniVersion)

    private val actualPredicate = LookupPredicate.create { annotated(Symbols.JniActual.asSingleFqName()) }
    private val containerPredicate = LookupPredicate.create { annotated(Symbols.JniActuals.asSingleFqName()) }
    private val entryPointPredicate = LookupPredicate.create { annotated(Symbols.JniOnLoad.asSingleFqName()) }
    private val exitPointPredicate = LookupPredicate.create { annotated(Symbols.JniOnUnload.asSingleFqName()) }

    /** Registers the four annotation predicates so the compiler can resolve annotated symbols lazily. */
    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(actualPredicate, containerPredicate, entryPointPredicate, exitPointPredicate)
    }

    /** Whether the symbol may be bound from generated code: `public`, `internal`, or inherited visibility. */
    private val isVisibleFilter: (FirCallableSymbol<*>) -> Boolean = {
        it.visibility in listOf(Public, Internal, Inherited)
    }

    private val topLevelActuals: List<FirNamedFunctionSymbol> by lazy {
        provider.getSymbolsByPredicate(actualPredicate)
            .filterIsInstance<FirNamedFunctionSymbol>()
            .filter(isVisibleFilter)
            .filterNot { it.isExpect }
    }
    private val allContainers: List<FirRegularClassSymbol> by lazy {
        provider.getSymbolsByPredicate(containerPredicate).filterIsInstance<FirRegularClassSymbol>()
    }
    private val objectContainers: List<FirRegularClassSymbol> by lazy {
        allContainers.filter { it.classKind == ClassKind.OBJECT }
    }
    private val constructableContainers: List<FirRegularClassSymbol> by lazy {
        allContainers.filter { it.isClass }
    }
    /**
     * The generated `internal lateinit var` instance per [constructableContainers] entry.
     */
    private val containerInstances: List<FirRegularPropertySymbol> by lazy {
        constructableContainers.map { clazz ->
            val containerPath = clazz.packageFqName().classId(clazz.name).asFqNameString().replace('.', '_')
            FirRegularPropertySymbol(Symbols.generatedPackage.callableId(containerPath + "JniBinding"))
        }
    }

    /** Whether a container member counts as an actual. */
    private val actualFilter: (FirNamedFunctionSymbol) -> Boolean = { fn ->
        val isHook = fn.hasAnnotation(Symbols.JniOnLoad, session) || fn.hasAnnotation(Symbols.JniOnUnload, session)
        !isHook && !fn.isExpect && isVisibleFilter(fn)
    }
    private fun containerFunctions(filter: (FirNamedFunctionSymbol) -> Boolean): List<FunLocation> {
        val objectFns = objectContainers.flatMap { obj ->
            obj.declaredFunctions(session).filter(filter).map { FunLocation(it) }
        }
        val instanceFns = constructableContainers.zip(containerInstances).flatMap { (clazz, instance) ->
            clazz.declaredFunctions(session).filter(filter).map { FunLocation(it, instance) }
        }
        return objectFns + instanceFns
    }
    /** Every actual of the fragment (top-level plus in-container), as [FunLocation]s. */
    private val allActuals: List<FunLocation> by lazy {
        topLevelActuals.map { FunLocation(it, null) } + containerFunctions(actualFilter)
    }

    /** Whether a function is an on-load hook: annotated `@JniOnLoad`. */
    private val onLoadActionFilter: (FirNamedFunctionSymbol) -> Boolean = { fn ->
        fn.hasAnnotation(Symbols.JniOnLoad, session) && !fn.isExpect && isVisibleFilter(fn)
    }
    /** Whether a function is an on-unload hook: annotated `@JniOnUnload`. */
    private val onUnloadActionFilter: (FirNamedFunctionSymbol) -> Boolean = { fn ->
        fn.hasAnnotation(Symbols.JniOnUnload, session) && !fn.isExpect && isVisibleFilter(fn)
    }
    private val topLevelOnLoadActions: List<FirNamedFunctionSymbol> by lazy {
        provider.getSymbolsByPredicate(entryPointPredicate)
            .filterIsInstance<FirNamedFunctionSymbol>()
            .filter(onLoadActionFilter)
    }
    private val topLevelOnUnloadActions: List<FirNamedFunctionSymbol> by lazy {
        provider.getSymbolsByPredicate(exitPointPredicate)
            .filterIsInstance<FirNamedFunctionSymbol>()
            .filter(onUnloadActionFilter)
    }

    /** The `@JniOnLoad` hooks declared inside `@JniActuals` containers, as [FunLocation]s. */
    private val staticOnLoadActions: List<FunLocation> by lazy {
        containerFunctions(onLoadActionFilter)
    }
    /** The `@JniOnUnload` hooks declared inside `@JniActuals` containers, as [FunLocation]s. */
    private val staticOnUnloadActions: List<FunLocation> by lazy {
        containerFunctions(onUnloadActionFilter)
    }

    /**
     * The actuals who's every parameter and return type has a resolvable JVM binding.
     */
    private val mappableActuals: List<FunLocation> by lazy {
        allActuals.filter { location ->
            val fn = location.fn.fir as? FirNamedFunction ?: return@filter false
            val signatureInfo = session.jvmSignatureProvider.signatureInfo(fn)
            jniActualArguments(location.fn) != null &&
                signatureInfo.parameterTypes.none { it == null } &&
                signatureInfo.returnType != null
        }
    }

    /** The mangled JVM parameter signature distinguishing overloaded actuals in generated names and C names. */
    private fun FunLocation.overloadSignature(): String =
        session.jvmSignatureProvider.signatureInfo(fn.fir as FirNamedFunction)
            .parameterTypes
            .filterNotNull()
            .joinToString("") { it.signatureType.escapeSignature() }

    /**
     * The JVM targets (`className`, `methodName`) that are bound by more than one actual, i.e. the overloads.
     * Overloaded methods need their C name and generated facade name to carry the `__<signature>` disambiguation,
     * while non-overloaded methods keep the plain `Java_<class>_<method>` name the JVM also resolves by.
     */
    private val overloadedJniTargets: Set<JniTarget> by lazy {
        mappableActuals
            .mapNotNull { jniActualArguments(it.fn) }
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
    }

    /** Whether this actual shares its JVM target with another actual, requiring a `__<signature>` name. */
    private fun FunLocation.isOverloaded(): Boolean {
        val args = jniActualArguments(fn) ?: return false
        return args in overloadedJniTargets
    }

    /**
     * The facades to generate: every mappable actual in expose-function mode, none in register natives mode (the
     * dispatch is inlined into the `staticCFunction` lambdas of the `RegisterNatives` block instead). The facade
     * callable id of an overloaded actual carries the mangled parameter signature, so its overloads generate one
     * facade each.
     *
     * A `@CriticalNative` actual's facades depend on where the runtime looks for the critical convention. A desktop
     * JVM exports both `Java_<class>_<method>` - which has to work on its own, so it wraps each array in a critical
     * region itself - and `JavaCritical_<class>_<method>`. Android resolves the method under the plain `Java_` name and
     * calls it with the critical convention, so that is the only symbol it gets.
     */
    private val facadeMapping: Map<CallableId, FacadeRequest> by lazy {
        if (useRegisterNatives) emptyMap()
        else buildMap {
            mappableActuals.forEach { location ->
                val includeSignature = location.isOverloaded()
                val id = if (includeSignature) {
                    location.facadeCallableId(location.overloadSignature())
                } else {
                    location.facadeCallableId()
                }
                val critical = (location.fn.fir as FirNamedFunction).isCriticalNative(session)
                when {
                    !critical -> put(id, FacadeRequest(location, FacadeKind.NORMAL, includeSignature))
                    isAndroid -> put(id, FacadeRequest(location, FacadeKind.CRITICAL, includeSignature))
                    else -> {
                        put(id, FacadeRequest(location, FacadeKind.CRITICAL_FALLBACK, includeSignature))
                        put(
                            location.criticalFacadeCallableId(
                                if (includeSignature) location.overloadSignature() else "",
                            ),
                            FacadeRequest(location, FacadeKind.CRITICAL, includeSignature),
                        )
                    }
                }
            }
        }
    }

    /**
     * Which of the entry points of one actual a generated facade is.
     */
    private enum class FacadeKind {
        /**
         * A regular `Java_<class>_<method>` facade.
         */
        NORMAL,

        /**
         * The critical calling convention, exported as `JavaCritical_` on a desktop JVM and as `Java_` on Android.
         */
        CRITICAL,

        /**
         * The `Java_<class>_<method>` entry point of a `@CriticalNative` actual: it wraps the arrays in critical
         * regions.
         */
        CRITICAL_FALLBACK,
    }

    /**
     * One facade to generate: the actual it forwards to and which kind of facade it is.
     */
    private class FacadeRequest(
        val location: FunLocation,
        val kind: FacadeKind,
        val includeSignature: Boolean,
    )

    /**
     * Builds the facade described by [request], memoized inside [FirJniFacades].
     */
    private fun buildFacade(callableId: CallableId, request: FacadeRequest): FirNamedFunctionSymbol {
        val location = request.location
        if (request.kind == FacadeKind.NORMAL) {
            return facades.facade(callableId, location, request.includeSignature)
        }
        val fn = location.fn.fir as FirNamedFunction
        val layout = session.jvmSignatureProvider.criticalLayout(fn)
        check(layout != null && layout.isValid) {
            "@CriticalNative actual ${location.fn.name.asString()} has an invalid parameter layout; " +
                    "see the compiler diagnostics"
        }
        return if (request.kind == FacadeKind.CRITICAL) {
            facades.criticalFacade(callableId, location, layout, request.includeSignature, !isAndroid)
        } else {
            facades.criticalFallbackFacade(callableId, location, layout, request.includeSignature)
        }
    }
    /** Callable id → generated container instance property. */
    private val containerMapping: Map<CallableId, FirRegularPropertySymbol> by lazy {
        containerInstances.associateBy { it.callableId }
    }
    /** Generated container instance property → its constructable container class. */
    private val containerClassByInstance: Map<FirRegularPropertySymbol, FirRegularClassSymbol> by lazy {
        containerInstances.zip(constructableContainers).toMap()
    }

    /**
     * Each constant is a *phase* that is encoded into the step name, so the IR
     * merger sorts steps from all fragments by this global rank, then by the per-fragment sequence.
     *
     * The order guarantees the load-time invariants across fragment boundaries: a constructable
     * container is initialized (phase 1) before the `@JniOnLoad` hooks inside it run (phase 2), and
     * `RegisterNatives` (phase 3) completes before `JNI_OnLoad` returns.
     */
    private object StepPhase {
        const val TOP_LEVEL_LOAD_HOOKS = 0
        const val CONTAINER_INIT = 1
        const val IN_CONTAINER_LOAD_HOOKS = 2
        const val REGISTER_NATIVES = 3
        const val TOP_LEVEL_UNLOAD_HOOKS = 4
        const val IN_CONTAINER_UNLOAD_HOOKS = 5
    }

    /** Callable id → hook step, used by [generateFunctions] to dispatch generation. */
    private val stepMapping: Map<CallableId, HookStep> by lazy {
        hookSteps.associateBy { it.callableId }
    }

    /**
     * The hook steps this fragment contributes, in the canonical phase order (see [StepPhase]): the
     * load hooks outside containers, one initialization per constructable container, the load hooks
     * inside containers, the `RegisterNatives` steps, then the unload hooks. Each step is tagged with
     * its phase, so when the fragments of a module are merged the global invariants (container init
     * before in-container hooks, load before unload) survive the merge.
     */
    private val hookSteps: List<HookStep> by lazy {
        fun stepCallableId(phase: Int, index: Int, isLoad: Boolean): CallableId =
            Symbols.generatedPackage.callableId("entryPointStep${phase}_$index${if (isLoad) "Load" else "Unload"}")

        var index = 0
        fun step(
            phase: Int,
            isLoad: Boolean,
            body: (vmParameterSymbol: FirValueParameterSymbol, vmType: ConeKotlinType) -> FirStatement,
        ): HookStep = HookStep(stepCallableId(phase, index++, isLoad), isLoad, body)

        // The hook-call steps of one phase: the top-level functions first, then the in-container ones.
        fun hookCallSteps(
            phase: Int,
            isLoad: Boolean,
            topLevel: List<FirNamedFunctionSymbol>,
            inContainers: List<FunLocation>,
        ): List<HookStep> = buildList {
            topLevel.forEach { symbol ->
                add(step(phase, isLoad) { vm, vmType ->
                    facades.hookCall(FunLocation(symbol), vm, vmType)
                })
            }
            inContainers.forEach { location ->
                add(step(phase, isLoad) { vm, vmType ->
                    facades.hookCall(location, vm, vmType)
                })
            }
        }

        buildList {
            // Load hooks declared outside containers.
            addAll(hookCallSteps(StepPhase.TOP_LEVEL_LOAD_HOOKS, isLoad = true, topLevelOnLoadActions, emptyList()))
            // Create one instance of each constructable container.
            constructableContainers.zip(containerInstances).forEach { (clazz, instance) ->
                add(step(StepPhase.CONTAINER_INIT, true, facades.containerInitStep(clazz, instance)))
            }
            // Load hooks declared inside containers.
            addAll(hookCallSteps(StepPhase.IN_CONTAINER_LOAD_HOOKS, isLoad = true, emptyList(), staticOnLoadActions))

            // Register the native methods of every JVM class when `useRegisterNatives == true`.
            if (useRegisterNatives) {
                mappableActuals.groupBy { jniActualArguments(it.fn)?.className }.forEach { (className, actuals) ->
                    if (className == null) return@forEach
                    add(step(StepPhase.REGISTER_NATIVES, true) { vm, vmType ->
                        facades.registerNativesStep(className, actuals, vm, vmType)
                    })
                }
            }

            // Unload hooks declared outside containers, then inside containers.
            addAll(hookCallSteps(StepPhase.TOP_LEVEL_UNLOAD_HOOKS, isLoad = false, topLevelOnUnloadActions, emptyList()))
            addAll(hookCallSteps(StepPhase.IN_CONTAINER_UNLOAD_HOOKS, isLoad = false, emptyList(), staticOnUnloadActions))
        }
    }

    /**
     * The callable ids this generator owns: the entry and exit point bindings (only in non-common
     * fragments, so exactly one `JNI_OnLoad`/`JNI_OnUnload` reaches the C exporter per compilation),
     * the container instances, the facades and the hook steps. Called by the compiler before body
     * resolution; that is also the single place where the `.actuals` file is written.
     */
    override fun getTopLevelCallableIds(): Set<CallableId> {
        writeActuals()

        return buildSet {
            val isCommon = session.moduleData.isCommon
            if (!isCommon) { // generate JNI_OnLoad and JNI_OnUnload only in platform fragment to avoid redeclaration
                add(Symbols.entryPointBinding)
                add(Symbols.exitPointBinding)
            }

            addAll(containerMapping.keys)
            addAll(facadeMapping.keys)
            addAll(stepMapping.keys)
        }
    }

    /** Only the generated package is a symbol-provider target for this extension. */
    override fun hasPackage(packageFqName: FqName): Boolean = packageFqName == Symbols.generatedPackage

    /** Materializes the bodies of entry points, hook steps and facades for the requested callable id. */
    override fun generateFunctions(callableId: CallableId, context: MemberGenerationContext?): List<FirNamedFunctionSymbol> {
        val isEntryPoint = callableId == Symbols.entryPointBinding
        val isExitPoint = callableId == Symbols.exitPointBinding

        return when {
            isEntryPoint || isExitPoint -> {
                generateEntryPoint(
                    callableId = callableId,
                    isOnLoad = isEntryPoint,
                )
            }
            callableId in stepMapping -> {
                generateHookStep(stepMapping[callableId]!!)
            }
            callableId in facadeMapping -> {
                listOf(buildFacade(callableId, facadeMapping[callableId]!!))
            }
            else -> emptyList()
        }
    }

    /** Materializes the lateinit container instance properties for the requested callable id. */
    override fun generateProperties(callableId: CallableId, context: MemberGenerationContext?): List<FirPropertySymbol> {
        return when {
            callableId in containerMapping -> {
                val instance = containerMapping[callableId]!!
                val clazz = containerClassByInstance[instance]
                    ?: error("No @JniActuals container for generated instance $callableId")
                facades.containerInstance(clazz, instance)
            }
            else -> emptyList()
        }
    }

    /**
     * The empty `@CName("JNI_OnLoad")`/`@CName("JNI_OnUnload")` shell of this fragment. The on-load
     * shell returns the required JNI version (as [JniVersion.native]); the on-unload shell is bodyless.
     * The bodies are filled by [io.github.mimimishkin.jni.binding.producer.ir.JniHookStepsMerger] at the
     * IR phase: on-load steps are inserted before the `return`, on-unload steps appended at the end.
     */
    private fun generateEntryPoint(
        callableId: CallableId,
        isOnLoad: Boolean,
    ): List<FirNamedFunctionSymbol> {
        val functionSymbol = FirNamedFunctionSymbol(callableId)
        val vmParameter = builder.buildFnValueParameter(functionSymbol, "vm".ident(), builder.javaVM.constructType())
        val reservedParameter = builder.buildFnValueParameter(functionSymbol, "reserved".ident(), builder.opaquePointer.constructType())

        builder.buildGeneratedFunction(
            functionSymbol = functionSymbol,
            visibility = Public,
            returnTypeRef = if (isOnLoad) builder.resolvedTypeRef(StandardClassIds.Int)
            else builder.resolvedTypeRef(StandardClassIds.Unit),
            valueParameters = listOf(vmParameter, reservedParameter),
            annotations = listOf(builder.cNameAnnotation(if (isOnLoad) "JNI_OnLoad" else "JNI_OnUnload")),
        ) { returnTarget ->
            if (isOnLoad) {
                // The hook steps generated for this fragment are merged into this empty body by
                // JniHookStepsMerger (IR), in step order (phase, then fragment index), before the return.
                buildBlock {
                    statements += buildReturnExpression {
                        target = returnTarget
                        result = buildLiteralExpression(null, ConstantValueKind.Int, jniVersion.native, setType = true)
                    }
                }
            } else {
                buildBlock { }
            }
        }

        return listOf(functionSymbol)
    }

    /** A single private top-level function packaging one hook/container-init/`RegisterNatives` statement of this fragment. */
    private fun generateHookStep(step: HookStep): List<FirNamedFunctionSymbol> {
        val functionSymbol = FirNamedFunctionSymbol(step.callableId)
        val vmParameter = builder.buildFnValueParameter(functionSymbol, "vm".ident(), builder.javaVM.constructType())

        builder.buildGeneratedFunction(
            functionSymbol = functionSymbol,
            visibility = Visibilities.Private,
            returnTypeRef = builder.resolvedTypeRef(StandardClassIds.Unit),
            valueParameters = listOf(vmParameter),
        ) { _ ->
            buildBlock {
                statements += step.buildBody(vmParameter.symbol, builder.javaVM.constructType())
            }
        }
        return listOf(functionSymbol)
    }

    // ------------------------------------------------------------------
    // `actuals` JSON contract
    // ------------------------------------------------------------------

    private val prettyJson = Json { prettyPrint = true }

    private var actualsWritten: Boolean = false

    private fun writeActuals() {
        if (actualsWritten) return
        val infos = allActuals.mapNotNull { location ->
            val (className, methodName) = jniActualArguments(location.fn) ?: return@mapNotNull null
            val fn = location.fn.fir as FirNamedFunction

            val needEnv = fn.contextParameters.any { it.returnTypeRef.coneType.isJniEnvType() }

            val isStatic = facadeReceiverStasis(location.fn.resolvedReceiverTypeRef, session)

            val signatureInfo = session.jvmSignatureProvider.signatureInfo(fn)
            val jvmParameterTypes = signatureInfo.jsonParameterTypes
            if (jvmParameterTypes.any { it == null }) return@mapNotNull null
            val jvmReturnType = signatureInfo.jsonReturnType ?: return@mapNotNull null

            val source = sourcePath(location.fn)

            JniActualInfo(
                needEnv = needEnv,
                isStatic = isStatic,
                isCritical = fn.isCriticalNative(session),
                className = className,
                methodName = methodName,
                parameterTypes = jvmParameterTypes.mapNotNull { it },
                returnType = jvmReturnType,
                source = source,
            )
        }
        actualsWritten = true
        if (infos.isEmpty()) return

        val existing: List<JniActualInfo> = if (actualsFile.exists()) {
            runCatching { prettyJson.decodeFromString<List<JniActualInfo>>(actualsFile.readText()) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        val merged = existing + infos
        actualsFile.writeText(prettyJson.encodeToString(merged))
    }

    /**
     * The source file path of a `@JniActual` declaration. Preferred route: the FIR provider knows the containing
     * `FirFile`, whose `KtSourceFile` carries the on-disk path - this works for both JVM and Kotlin/Native
     * (incl. the light-tree backends where PSI is unavailable). Falls back to PSI materialization.
     */
    private fun sourcePath(fn: FirCallableSymbol<*>): String {
        try {
            val file = session.firProvider.getFirCallableContainerFile(fn)
            file?.sourceFile?.path?.let { return it }
        } catch (_: Throwable) {
        }
        val source = fn.fir.source ?: return "unknown"
        val psi = when (source) {
            is KtPsiSourceElement -> source.psi
            is KtLightSourceElement -> try {
                source.unwrapToKtPsiSourceElement()?.psi
            } catch (_: Throwable) {
                null
            }
        }
        return psi?.containingFile?.virtualFile?.path ?: "unknown"
    }
}

