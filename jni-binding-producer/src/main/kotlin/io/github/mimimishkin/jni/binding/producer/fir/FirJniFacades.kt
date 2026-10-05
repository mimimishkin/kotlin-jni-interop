package io.github.mimimishkin.jni.binding.producer.fir

import io.github.mimimishkin.jni.binding.producer.Symbols
import io.github.mimimishkin.jni.binding.producer.ident
import io.github.mimimishkin.jni.binding.producer.jniCName
import io.github.mimimishkin.jni.binding.producer.jniCriticalCName
import io.github.mimimishkin.jni.binding.producer.model.ActualParameterType
import io.github.mimimishkin.jni.binding.producer.model.JniVersion
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.Visibilities.Internal
import org.jetbrains.kotlin.descriptors.Visibilities.Public
import org.jetbrains.kotlin.fir.FirImplementationDetail
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.builder.buildProperty
import org.jetbrains.kotlin.fir.declarations.impl.FirDefaultPropertyBackingField
import org.jetbrains.kotlin.fir.declarations.impl.FirDefaultPropertyGetter
import org.jetbrains.kotlin.fir.declarations.impl.FirDefaultPropertySetter
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.fir.expressions.FirEmptyArgumentList
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.expressions.buildResolvedArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.*
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.references.builder.buildResolvedNamedReference
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.resolve.getContainingClass
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirAnonymousFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildTypeProjectionWithVariance
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.types.ConstantValueKind
import org.jetbrains.kotlin.types.Variance

/**
 * Assembles the FIR bodies of facades (the `@CName`-exported functions), `RegisterNatives` bridge lambdas, and
 * late-init container instance properties.
 */
@OptIn(FirImplementationDetail::class, SymbolInternals::class)
internal class FirJniFacades(
    private val builder: FirBuilder,
    private val session: FirSession,
    private val jniVersion: JniVersion,
) {

    private val jvmSignatureProvider: JvmSignatureProvider get() = session.jvmSignatureProvider

    // ------------------------------------------------------------------
    // Facade function memoisation
    // ------------------------------------------------------------------

    /** Memoization of built facades by their callable id, so a callable id is never generated twice. */
    private val facadeFunctions = mutableMapOf<CallableId, FirNamedFunctionSymbol>()

    /**
     * The exported `@CName("Java_..._method")` forwarding facade. Only generated in expose-function mode:
     * in register natives mode the forwarding body is inlined into the `staticCFunction` lambda instead (see
     * [buildForwardingLambda]) and no facade is emitted.
     */
    fun facade(
        callableId: CallableId,
        actualLocation: FunLocation,
        includeSignature: Boolean,
    ): FirNamedFunctionSymbol {
        facadeFunctions[callableId]?.let { return it }
        val (jvmClass, jvmMethod) = jniActualArguments(actualLocation.fn)
            ?: error("Missing JNI target for $callableId")

        val functionSymbol = FirNamedFunctionSymbol(callableId)
        val actualFn = actualLocation.fn.fir as FirNamedFunction
        val receiverTypeRef = actualLocation.fn.resolvedReceiverTypeRef
            ?: builder.resolvedTypeRef(Symbols.COpaquePointer)
        val parts = buildFacadeParts(
            containingSymbol = functionSymbol,
            actualFn = actualFn,
            actualSymbol = actualLocation.fn,
            container = actualLocation.container,
            receiverName = when (facadeReceiverStasis(receiverTypeRef)) {
                true -> "class"
                false -> "object"
                null -> "classOrObject"
            }.ident(),
            receiverType = receiverTypeRef,
        )

        builder.buildGeneratedFunction(
            functionSymbol = functionSymbol,
            visibility = Public,
            returnTypeRef = facadeReturnType(actualFn),
            valueParameters = listOf(parts.envParam, parts.receiverParam) + parts.regularParams,
            annotations = listOf(builder.cNameAnnotation(jniCName(
                jvmClass,
                jvmMethod,
                if (includeSignature) jvmSignatureProvider.signatureInfo(actualFn).parameterTypes.filterNotNull() else emptyList(),
            ))),
        ) { returnTarget ->
            buildBlock {
                statements += buildReturnExpression {
                    target = returnTarget
                    result = parts.forwardingResult
                }
            }
        }

        facadeFunctions[callableId] = functionSymbol
        return functionSymbol
    }

    // ------------------------------------------------------------------
    // Critical natives
    // ------------------------------------------------------------------

    /**
     * The critical entry point of a `@CriticalNative` actual: a function with the actual's own parameter list, because
     * that is the calling convention - every array argument arrives as a `(length: Int, CArrayPointer<T>)` pair, and
     * neither a `JniEnv` nor a class/object reference is passed. An array is therefore forwarded unchanged, and the
     * only conversion left is a `kotlin.Boolean`, which crosses the C boundary as the JNI `jboolean` (`UByte`).
     *
     * [underJavaCriticalName] picks the symbol it is exported as, which is where the two platforms differ: a desktop
     * JVM resolves `JavaCritical_<class>_<method>`, Android resolves the same function under `Java_<class>_<method>`.
     */
    fun criticalFacade(
        callableId: CallableId,
        actualLocation: FunLocation,
        layout: CriticalLayout,
        includeSignature: Boolean,
        underJavaCriticalName: Boolean = true,
    ): FirNamedFunctionSymbol {
        facadeFunctions[callableId]?.let { return it }
        val (jvmClass, jvmMethod) = jniActualArguments(actualLocation.fn)
            ?: error("Missing JNI target for $callableId")
        val functionSymbol = FirNamedFunctionSymbol(callableId)
        val forwarding = criticalForwarding(functionSymbol, actualLocation, layout)
        val overloads = layout.signatureParameters(includeSignature)
        val cName = if (underJavaCriticalName) {
            jniCriticalCName(jvmClass, jvmMethod, overloads)
        } else {
            jniCName(jvmClass, jvmMethod, overloads)
        }

        builder.buildGeneratedFunction(
            functionSymbol = functionSymbol,
            visibility = Public,
            returnTypeRef = builder.resolvedTypeRef(layout.nativeReturnClassId()),
            valueParameters = forwarding.params,
            annotations = listOf(builder.cNameAnnotation(cName)),
        ) { returnTarget ->
            buildBlock {
                statements += buildReturnExpression { target = returnTarget; result = forwarding.returned }
            }
        }


        facadeFunctions[callableId] = functionSymbol
        return functionSymbol
    }

    /**
     * The ordinary `Java_<class>_<method>` entry point of a `@CriticalNative` actual, which has to work whether or not
     * the critical symbol is ever taken.
     *
     * Here the parameters are the *JVM* ones, so every array arrives as a `jarray`. Each becomes the
     * `(length, CArrayPointer<TVar>)` pair the actual declares by entering a critical region with `modifyCritical`,
     * released again with `finalize()` from a `finally`, so an actual that throws cannot leave the caller's array
     * pinned. The result cannot travel out through the region call, so it is assigned to a local and returned after.
     */
    fun criticalFallbackFacade(
        callableId: CallableId,
        actualLocation: FunLocation,
        layout: CriticalLayout,
        includeSignature: Boolean,
    ): FirNamedFunctionSymbol {
        facadeFunctions[callableId]?.let { return it }
        val (jvmClass, jvmMethod) = jniActualArguments(actualLocation.fn)
            ?: error("Missing JNI target for $callableId")
        val actualFn = actualLocation.fn.fir as FirNamedFunction
        val functionSymbol = FirNamedFunctionSymbol(callableId)

        val envTypeRef = builder.envTypeRef(actualFn)
        val envParam = builder.buildFnValueParameter(functionSymbol, "env".ident(), envTypeRef.coneType)
        val receiverParam = builder.buildFnValueParameter(
            functionSymbol,
            "classOrObject".ident(),
            builder.resolvedTypeRef(Symbols.COpaquePointer).coneType,
        )

        // One facade parameter per collapsed JVM parameter: a scalar as its primitive, an array pair as the jarray.
        val slotParams = layout.slots.map { slot ->
            val param = actualFn.valueParameters[slot.nativeLengthIndex]
            val coneType = when (slot) {
                is CriticalSlot.Scalar ->
                    if (slot.type == ActualParameterType.BooleanIn) {
                        StandardClassIds.UByte.constructClassLikeType()
                    } else {
                        param.returnTypeRef.coneType
                    }
                // The jarray arrives as a raw pointer, i.e. the `J<element>Array` alias
                // `CPointer<out _j<element>Array>` - not the `_j<element>Array` class it is named
                // after: that one is a `CPointed`, and the `JArray.length`/`.c` accessors the facade
                // reads the array through cast their receiver to a `CPointer`, which a `CPointed` is
                // not. The alias' `CPointer` form is, so the parameter is wrapped in `CArrayPointer`
                // (the alias of `CPointer`) exactly like the regular facade's array parameters.
                is CriticalSlot.Array -> Symbols.CArrayPointer.constructClassLikeType(
                    arrayOf(slot.element.arrayClassId.constructClassLikeType()),
                )
            }
            builder.buildFnValueParameter(functionSymbol, param.name, coneType)
        }

        // Pre-create the `carray` read of every region, so the actual's call can be built before the regions
        // themselves: the pointer of a region is only in scope inside that region's block.
        val regions = layout.slots.mapIndexedNotNull { slotIndex, slot ->
            val element = (slot as? CriticalSlot.Array)?.element ?: return@mapIndexedNotNull null
            val lambdaSymbol = FirAnonymousFunctionSymbol()
            val scopeType = builder.modifyingArrayScopeType
            val scopeReceiver = builder.buildReceiverParameter(
                containingSymbol = lambdaSymbol,
                typeRef = buildResolvedTypeRef { coneType = scopeType },
            )
            val carrayParam = builder.buildFnValueParameter(
                lambdaSymbol,
                "carray".ident(),
                // `modifyCritical` declares its pointer as `CArrayPointer<*>`, but nothing about the *runtime* pointer
                // changes: it is the very same address. The region therefore declares it with the element type the
                // actual wants, which is what a `reinterpret` cast would have produced - only without a reified call,
                // which the backend cannot lower for a plugin-generated symbol.
                elementPointerType(element),
            )
            val isCopyParam = builder.buildFnValueParameter(
                lambdaSymbol,
                "isCopy".ident(),
                StandardClassIds.Boolean.constructClassLikeType(),
            )
            CriticalRegion(
                slotIndex = slotIndex,
                element = element,
                lambdaSymbol = lambdaSymbol,
                scopeReceiver = scopeReceiver,
                carrayParam = carrayParam,
                isCopyParam = isCopyParam,
                carrayRead = builder.paramRead(carrayParam.symbol, carrayParam.returnTypeRef.coneType),
            )
        }
        fun regionOf(slotIndex: Int): CriticalRegion = regions.single { it.slotIndex == slotIndex }

        val slotRead = { slotIndex: Int ->
            val param = slotParams[slotIndex]
            builder.paramRead(param.symbol, param.returnTypeRef.coneType)
        }
        val envRead = builder.paramRead(envParam.symbol, envParam.returnTypeRef.coneType)
        val argOf = { index: Int ->
            val slot = layout.slotAt(index)!!
            val slotIndex = layout.slots.indexOf(slot)
            when (slot) {
                is CriticalSlot.Scalar -> {
                    val scalarParam = slotParams[slotIndex]
                    val read = builder.paramRead(scalarParam.symbol, scalarParam.returnTypeRef.coneType)
                    if (slot.type == ActualParameterType.BooleanIn) builder.toKBoolean(read) else read
                }
                is CriticalSlot.Array -> {
                    if (index == slot.lengthIndex) {
                        buildPropertyAccessExpression {
                            coneTypeOrNull = builder.jArrayLength.resolvedReturnType
                            // `JArray.length` is a `context(env: JniEnv)` extension property. The env has to be passed
                            // explicitly, and first: the backend fills the accessor's value parameters positionally and
                            // does not treat a context receiver specially, so leaving the env out makes the array
                            // stand in for it and leaves `<this>` unfilled - which no earlier phase reports.
                            contextArguments += envRead
                            // The receiver belongs in `extensionReceiver` and not in `explicitReceiver`: only the
                            // former is recognized as the accessor's receiver when the expected receiver type is
                            // worked out, and the latter would be converted as a qualifier of its own.
                            extensionReceiver = slotRead(slotIndex)
                            calleeReference = buildResolvedNamedReference {
                                name = builder.jArrayLength.name
                                resolvedSymbol = builder.jArrayLength
                            }
                        }
                    } else {
                        regionOf(slotIndex).carrayRead
                    }
                }
            }
        }

        val returnType = layout.nativeReturnClassId()
        val returnConeType = returnType.constructClassLikeType()

        // `modifyCritical`'s block is `Unit`-returning, so the value the actual computes cannot travel out through the
        // surrounding `memScoped` call. It is parked in a local that outlives the regions, and read back after them.
        val resultVar = builder.buildLocalVar("result".ident(), builder.resolvedTypeRef(returnType))
        val resultRead = { builder.localVarRead(resultVar.symbol, returnConeType) }

        val scoped = builder.memScopedCall(builder.unitConeType()) { placementRead, _ ->
            buildBlock {
                coneTypeOrNull = builder.unitConeType()
                var computed: FirExpression = actualCall(
                    actualFn = actualFn,
                    actualSymbol = actualLocation.fn,
                    container = actualLocation.container,
                    arguments = actualFn.valueParameters.indices.map { argOf(it) },
                )
                if (layout.isBooleanReturn) computed = builder.toJBoolean(computed)
                // Assigned from inside the innermost region: assigning the region call itself would store the `Unit` it
                // returns. A statement, so the region receives it wrapped in a block.
                val assignment = buildVariableAssignment {
                    lValue = resultRead()
                    rValue = computed
                }
                if (regions.isEmpty()) {
                    statements += assignment
                } else {
                    var body: FirExpression = buildBlock {
                        coneTypeOrNull = returnConeType
                        statements += assignment
                    }
                    for (region in regions.asReversed()) {
                        val nested = body
                        body = criticalRegion(
                            arrayRead = slotRead(region.slotIndex),
                            region = region,
                            body = nested,
                            envRead = envRead,
                            placementRead = placementRead,
                        )
                    }
                    statements += body
                }
            }
        }

        builder.buildGeneratedFunction(
            functionSymbol = functionSymbol,
            visibility = Public,
            returnTypeRef = builder.resolvedTypeRef(returnType),
            valueParameters = listOf(envParam, receiverParam) + slotParams,
            annotations = listOf(builder.cNameAnnotation(
                jniCName(jvmClass, jvmMethod, layout.signatureParameters(includeSignature)),
            )),
        ) { returnTarget ->
            buildBlock {
                statements += resultVar
                statements += scoped
                statements += buildReturnExpression { target = returnTarget; result = resultRead() }
            }
        }

        facadeFunctions[callableId] = functionSymbol
        return functionSymbol
    }

    /**
     * The pre-built half of one `modifyCritical` region: the scope receiver, the `carray`/`isCopy` value parameters and
     * the read of `carray`.
     *
     * Everything the region needs is built before its block, because the actual's call - which reads the pointer - has
     * to be complete before the block that releases the region can be built around it. Crucially all of it is bound to
     * one [lambdaSymbol]: a value parameter whose `containingDeclarationSymbol` is a throwaway symbol would leave
     * FIR2IR unable to resolve it.
     */
    private class CriticalRegion(
        val slotIndex: Int,
        val element: CriticalElement,
        val lambdaSymbol: FirAnonymousFunctionSymbol,
        val scopeReceiver: FirReceiverParameter,
        val carrayParam: FirValueParameter,
        val isCopyParam: FirValueParameter,
        val carrayRead: FirExpression,
    )
    /**
     * `array.modifyCritical({ error(...) }) { carray, _ -> try { body } finally { finalize() } }`.
     */
    private fun criticalRegion(
        arrayRead: FirExpression,
        region: CriticalRegion,
        body: FirExpression,
        envRead: FirExpression,
        placementRead: FirExpression,
    ): FirExpression {
        val scopeType = builder.modifyingArrayScopeType

        val tryExpression = buildTryExpression {
            // A generated try/finally is a statement, so it has no type of its own; FIR2IR needs it stated explicitly.
            coneTypeOrNull = builder.unitConeType()
            tryBlock = buildBlock {
                coneTypeOrNull = builder.unitConeType()
                statements += body
            }
            finallyBlock = buildBlock {
                coneTypeOrNull = builder.unitConeType()
                statements += buildFunctionCall {
                    calleeReference = buildResolvedNamedReference {
                        name = builder.finalizeFunction.name
                        resolvedSymbol = builder.finalizeFunction
                    }
                    coneTypeOrNull = builder.unitConeType()
                    extensionReceiver = builder.thisReceiverRead(region.scopeReceiver.symbol, scopeType)
                    argumentList = FirEmptyArgumentList
                }
            }
        }
        val blockLambda = builder.buildLambda(
            lambdaSymbol = region.lambdaSymbol,
            functionType = builder.functionTypeCone(
                receiverType = scopeType,
                valueParameters = listOf(
                    // The block carries `modifyCritical`'s own `CArrayPointer<*>` parameter type,
                    // not the region's element-typed `CArrayPointer<T>`: the two differ only in the
                    // projection, and the element type is applied where the region's `carray` is
                    // declared, so the actual's call needs no cast.
                    builder.modifyingArrayCarrayType,
                    region.isCopyParam.returnTypeRef.coneType,
                ),
                returnType = builder.unitConeType(),
            ),
            returnTypeRef = builder.resolvedTypeRef(StandardClassIds.Unit),
            valueParameters = listOf(region.carrayParam, region.isCopyParam),
            receiverParameter = region.scopeReceiver,
        ) { _ -> buildBlock { statements += tryExpression } }

        return buildFunctionCall {
            calleeReference = buildResolvedNamedReference {
                name = builder.modifyCriticalFunction.name
                resolvedSymbol = builder.modifyCriticalFunction
            }
            coneTypeOrNull = builder.unitConeType()
            extensionReceiver = arrayRead
            contextArguments += envRead
            contextArguments += placementRead
            argumentList = buildResolvedArgumentList(
                original = null,
                linkedMapOf(
                    builder.lambdaExpression(onErrorLambda()) to
                        builder.modifyCriticalFunction.valueParameterSymbols[0].fir,
                    builder.lambdaExpression(blockLambda) to
                        builder.modifyCriticalFunction.valueParameterSymbols[1].fir,
                ),
            )
        }
    }

    /**
     * The `onError` argument of `modifyCritical`: a `() -> Unit` that aborts, since a critical region is mandatory.
     */
    private fun onErrorLambda(): FirAnonymousFunction {
        val symbol = FirAnonymousFunctionSymbol()
        return builder.buildLambda(
            lambdaSymbol = symbol,
            functionType = builder.functionTypeCone(returnType = builder.unitConeType()),
            returnTypeRef = builder.resolvedTypeRef(StandardClassIds.Unit),
        ) { _ ->
            buildBlock {
                statements += buildFunctionCall {
                    calleeReference = buildResolvedNamedReference {
                        name = builder.errorFunction.name
                        resolvedSymbol = builder.errorFunction
                    }
                    coneTypeOrNull = builder.unitConeType()
                    argumentList = buildResolvedArgumentList(
                        original = null,
                        linkedMapOf(
                            buildLiteralExpression(
                                null,
                                ConstantValueKind.String,
                                "Cannot obtain a critical pointer for a @CriticalNative array argument",
                                setType = true,
                            ) to builder.errorFunction.valueParameterSymbols[0].fir,
                        ),
                    )
                }
            }
        }
    }

    /**
     * A call to [actualSymbol] forwarding [arguments], dispatching on the container instance / object qualifier.
     */
    private fun actualCall(
        actualFn: FirNamedFunction,
        actualSymbol: FirCallableSymbol<*>,
        container: FirRegularPropertySymbol?,
        arguments: List<FirExpression>,
    ): FirExpression {
        val mapping = LinkedHashMap<FirExpression, FirValueParameter>()
        actualFn.valueParameters.forEachIndexed { index, param -> mapping[arguments[index]] = param }
        val dispatch = dispatchReceiver(actualFn, container)
        return buildFunctionCall {
            calleeReference = buildResolvedNamedReference {
                name = actualFn.name
                resolvedSymbol = actualSymbol
            }
            coneTypeOrNull = actualSymbol.resolvedReturnType
            argumentList = buildResolvedArgumentList(null, mapping)
            if (dispatch != null) {
                this.dispatchReceiver = dispatch
            }
        }
    }


    /**
     * `CArrayPointer<TVar>`: the typed form of the pointer a critical region of [element] yields.
     */
    private fun elementPointerType(element: CriticalElement): ConeKotlinType =
        Symbols.CArrayPointer.constructClassLikeType(arrayOf(element.varClassId.constructClassLikeType()))

    // ------------------------------------------------------------------
    // Container instance properties
    // ------------------------------------------------------------------

    fun containerInstance(
        clazz: FirRegularClassSymbol,
        instanceSymbol: FirRegularPropertySymbol,
    ): List<FirPropertySymbol> {
        val moduleData = builder.session.moduleData
        val origin = builder.pluginOrigin
        val returnTypeRef = buildResolvedTypeRef { coneType = clazz.defaultType() }
        val status = builder.pluginStatus(Internal).apply { isLateInit = true }

        val property = buildProperty {
            resolvePhase = FirResolvePhase.BODY_RESOLVE
            this.moduleData = moduleData
            this.origin = origin
            symbol = instanceSymbol
            name = instanceSymbol.callableId.callableName
            this.status = status
            isLocal = false
            isVar = true
            this.returnTypeRef = returnTypeRef
            getter = FirDefaultPropertyGetter(
                source = null,
                moduleData = moduleData,
                origin = origin,
                propertyTypeRef = returnTypeRef,
                visibility = status.visibility,
                propertySymbol = instanceSymbol,
                modality = status.modality,
                effectiveVisibility = status.effectiveVisibility,
                resolvePhase = FirResolvePhase.BODY_RESOLVE,
            )
            setter = FirDefaultPropertySetter(
                source = null,
                moduleData = moduleData,
                origin = origin,
                propertyTypeRef = returnTypeRef,
                visibility = status.visibility,
                propertySymbol = instanceSymbol,
                modality = status.modality,
                effectiveVisibility = status.effectiveVisibility,
                resolvePhase = FirResolvePhase.BODY_RESOLVE,
            )
            backingField = FirDefaultPropertyBackingField(
                moduleData,
                origin,
                source = null,
                mutableListOf(),
                returnTypeRef,
                isVar = true,
                instanceSymbol,
                status,
                resolvePhase = FirResolvePhase.BODY_RESOLVE,
            )
            bodyResolveState = FirPropertyBodyResolveState.ALL_BODIES_RESOLVED
        }
        instanceSymbol.bind(property)
        return listOf(instanceSymbol)
    }

    /**
     * The step initializing the generated [instance] of a constructable container once per library
     * load: via the `JavaVM` constructor when the class declares one, otherwise via the no-arg constructor.
     */
    fun containerInitStep(
        clazz: FirRegularClassSymbol,
        instance: FirRegularPropertySymbol,
    ): (FirValueParameterSymbol, ConeKotlinType) -> FirStatement = { vm, vmType ->
        if (vmConstructor(clazz) != null) {
            containerInitialization(clazz, instance, vm, vmType)
        } else {
            containerInitializationNoArg(clazz, instance)
        }
    }

    // ------------------------------------------------------------------
    // Hook call / RegisterNatives bridges
    // ------------------------------------------------------------------

    /**
     * A call to a `@JniOnLoad`/`@JniOnUnload` hook function. The optional `JavaVM` parameter is bound
     * to the entry point's `vm`; container hooks additionally receive their dispatch receiver so they
     * can access the initialized container instance.
     */
    fun hookCall(
        location: FunLocation,
        vmParameterSymbol: FirValueParameterSymbol,
        vmType: ConeKotlinType,
    ): FirExpression {
        val fn = location.fn.fir as FirNamedFunction
        val valueArgument = if (fn.valueParameters.size == 1) {
            builder.paramRead(vmParameterSymbol, vmType)
        } else {
            null
        }
        val dispatch = dispatchReceiver(fn, location.container)
        return buildFunctionCall {
            calleeReference = buildResolvedNamedReference {
                name = fn.name
                resolvedSymbol = location.fn
            }
            coneTypeOrNull = fn.returnTypeRef.coneType
            argumentList = valueArgument?.let {
                buildResolvedArgumentList(null, linkedMapOf(it to fn.valueParameters.single()))
            } ?: FirEmptyArgumentList
            if (dispatch != null) {
                this.dispatchReceiver = dispatch
            }
        }
    }

    /**
     * The `vm.useEnv { findClass(...).registerNatives(...) }` statement registering every actual of one
     * JVM class: resolves the class by its internal name (aborting with `error` on failure), then calls
     * the DSL `registerNatives(count) { ... }` with one `register` entry per method.
     */
    fun registerNativesStep(
        className: String,
        actuals: List<FunLocation>,
        vmParameterSymbol: FirValueParameterSymbol,
        vmType: ConeKotlinType,
    ): FirExpression = buildFunctionCall {
        calleeReference = buildResolvedNamedReference {
            name = builder.useEnvFunction.name
            resolvedSymbol = builder.useEnvFunction
        }
        coneTypeOrNull = builder.unitConeType()
        extensionReceiver = builder.paramRead(vmParameterSymbol, vmType)
        argumentList = buildResolvedArgumentList(
            original = null,
            linkedMapOf(
                buildLiteralExpression(null, ConstantValueKind.Int, jniVersion.native, setType = true) to
                        builder.useEnvFunction.valueParameterSymbols[0].fir,
                buildRegisterNativesLambda(className, actuals) to
                        builder.useEnvFunction.valueParameterSymbols[1].fir,
            ),
        )
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    /** The FIR type used by the facade parameter at [index]; avoids fabrication on invalid inputs. */
    private fun facadeParamType(actualFn: FirNamedFunction, index: Int): FirResolvedTypeRef {
        val regularParams = actualFn.valueParameters
        val param = regularParams[index]
        val paramType = param.returnTypeRef
        return if (paramType.coneType.classId == Symbols.CPointer) {
            paramType as FirResolvedTypeRef
        } else {
            val actualParamType = jvmSignatureProvider.signatureInfo(actualFn).parameterTypes.getOrNull(index)
            val nativeClassId = actualParamType?.nativeClassId
            if (nativeClassId != null) builder.resolvedTypeRef(nativeClassId) else paramType as FirResolvedTypeRef
        }
    }

    /** The FIR type used by the facade return; avoids fabrication on invalid inputs. */
    private fun facadeReturnType(actualFn: FirNamedFunction): FirResolvedTypeRef {
        val returnTypeRef = actualFn.returnTypeRef
        return if (returnTypeRef.coneType.classId == Symbols.CPointer) {
            returnTypeRef as FirResolvedTypeRef
        } else {
            val actualReturnType = jvmSignatureProvider.signatureInfo(actualFn).returnType
            val nativeClassId = actualReturnType?.nativeClassId
            if (nativeClassId != null) builder.resolvedTypeRef(nativeClassId) else returnTypeRef as FirResolvedTypeRef
        }
    }

    /** The JNI signature string of a `FunLocation` in `(params)return` form; only called on mappable actuals. */
    private fun jvmSignature(actual: FunLocation): String {
        val fn = actual.fn.fir as FirNamedFunction
        val info = jvmSignatureProvider.signatureInfo(fn)
        if (info.parameterTypes.any { it == null }) {
            error("Cannot compute the JNI signature of ${actual.fn.name}: some parameter types have no JVM binding (see the compiler diagnostics)")
        }
        val returnType = info.returnType
            ?: error("Cannot compute the JNI signature of ${actual.fn.name}: the return type has no JVM binding (see the compiler diagnostics)")
        return ActualParameterType.mapSignature(info.parameterTypes.filterNotNull(), returnType)
    }

    /** The regular (non-env, non-receiver) parameters of a facade. */
    private fun facadeValueParameters(
        containingSymbol: FirCallableSymbol<*>,
        actualFn: FirNamedFunction,
    ): List<FirValueParameter> =
        actualFn.valueParameters.indices.map { index ->
            builder.buildFnValueParameter(
                containingSymbol = containingSymbol,
                name = actualFn.valueParameters[index].name,
                coneType = facadeParamType(actualFn, index).coneType,
            )
        }

    /** The parameters and body of the shared forwarding of a facade or a `staticCFunction` lambda. */
    private class FacadeParts(
        val envParam: FirValueParameter,
        val receiverParam: FirValueParameter,
        val regularParams: List<FirValueParameter>,
        val forwardingResult: FirExpression,
    )

    /**
     * Builds the env/receiver/regular parameters of a facade or `staticCFunction` lambda and the call to the
     * actual, converting a `kotlin.Boolean` result to the JNI `jboolean` (`UByte`) representation. The bodies
     * built on top of these parts (a `@CName` facade vs an inlined lambda) then only differ in their shell.
     */
    private fun buildFacadeParts(
        containingSymbol: FirCallableSymbol<*>,
        actualFn: FirNamedFunction,
        actualSymbol: FirCallableSymbol<*>,
        container: FirRegularPropertySymbol?,
        receiverName: Name,
        receiverType: FirResolvedTypeRef,
    ): FacadeParts {
        val envType = builder.envTypeRef(actualFn).coneType
        val envParam = builder.buildFnValueParameter(containingSymbol, "env".ident(), envType)
        val receiverParam = builder.buildFnValueParameter(containingSymbol, receiverName, receiverType.coneType)
        val regularParams = facadeValueParameters(containingSymbol, actualFn)

        val forwardingCall = buildForwardingExpression(
            actualFn = actualFn,
            actualSymbol = actualSymbol,
            container = container,
            envParamSymbol = envParam.symbol,
            receiverParamSymbol = receiverParam.symbol,
            receiverType = receiverType.coneType,
            envType = envType,
            facadeParams = regularParams,
        )
        val forwardingResult =
            if (jvmSignatureProvider.signatureInfo(actualFn).returnType == ActualParameterType.BooleanOut) {
                builder.toJBoolean(forwardingCall)
            } else {
                forwardingCall
            }
        return FacadeParts(envParam, receiverParam, regularParams, forwardingResult)
    }

    /**
     * The forwarding expression of a facade or `staticCFunction` lambda: a call to the actual. When the actual
     * declares a native-placement `context` parameter (`AutofreeScope`/`NativePlacement`/`ArenaBase`/`MemScope`),
     * the call is wrapped in `kotlinx.cinterop.memScoped { ... }` and the scope is forwarded as that context
     * argument, while the `JniEnv` context parameter still reads the facade's `env`.
     */
    private fun buildForwardingExpression(
        actualFn: FirNamedFunction,
        actualSymbol: FirCallableSymbol<*>,
        container: FirRegularPropertySymbol?,
        envParamSymbol: FirValueParameterSymbol,
        receiverParamSymbol: FirValueParameterSymbol,
        receiverType: ConeKotlinType,
        envType: ConeKotlinType,
        facadeParams: List<FirValueParameter>,
    ): FirExpression {
        if (actualFn.contextParameters.none { it.returnTypeRef.coneType.isNativePlacementType() }) {
            return buildForwardingCall(
                actualFn = actualFn,
                actualSymbol = actualSymbol,
                container = container,
                envParamSymbol = envParamSymbol,
                receiverParamSymbol = receiverParamSymbol,
                receiverType = receiverType,
                envType = envType,
                facadeParams = facadeParams,
                placementRead = null,
            )
        }

        val lambdaSymbol = FirAnonymousFunctionSymbol()
        val memScopeType = Symbols.MemScope.constructClassLikeType()
        val receiverParameter = builder.buildReceiverParameter(
            containingSymbol = lambdaSymbol,
            typeRef = buildResolvedTypeRef { coneType = memScopeType },
        )
        val placementRead = builder.thisReceiverRead(receiverParameter.symbol, memScopeType)
        val call = buildForwardingCall(
            actualFn = actualFn,
            actualSymbol = actualSymbol,
            container = container,
            envParamSymbol = envParamSymbol,
            receiverParamSymbol = receiverParamSymbol,
            receiverType = receiverType,
            envType = envType,
            facadeParams = facadeParams,
            placementRead = placementRead,
        )
        val lambda = builder.buildLambda(
            lambdaSymbol = lambdaSymbol,
            functionType = builder.functionTypeCone(
                receiverType = memScopeType,
                returnType = actualSymbol.resolvedReturnType,
            ),
            returnTypeRef = buildResolvedTypeRef { coneType = actualSymbol.resolvedReturnType },
            receiverParameter = receiverParameter,
        ) { returnTarget ->
            buildBlock {
                statements += buildReturnExpression {
                    target = returnTarget
                    result = call
                }
            }
        }
        return buildFunctionCall {
            calleeReference = buildResolvedNamedReference {
                name = builder.memScopedFunction.name
                resolvedSymbol = builder.memScopedFunction
            }
            coneTypeOrNull = actualSymbol.resolvedReturnType
            typeArguments += buildTypeProjectionWithVariance {
                typeRef = buildResolvedTypeRef { coneType = actualSymbol.resolvedReturnType }
                variance = Variance.INVARIANT
            }
            argumentList = buildResolvedArgumentList(
                original = null,
                linkedMapOf(
                    builder.lambdaExpression(lambda) to builder.memScopedFunction.valueParameterSymbols[0].fir,
                ),
            )
        }
    }

    /**
     * The call body shared by facades and `staticCFunction` lambdas: reads the facade parameters,
     * converts `kotlin.Boolean` inputs to the JNI `jboolean` representation, forwards the actual's
     * `context` parameters to `env`, and dispatches on the container instance / object qualifier /
     * nothing. A native-placement context parameter is forwarded from [placementRead] instead of `env`.
     */
    private fun buildForwardingCall(
        actualFn: FirNamedFunction,
        actualSymbol: FirCallableSymbol<*>,
        container: FirRegularPropertySymbol?,
        envParamSymbol: FirValueParameterSymbol,
        receiverParamSymbol: FirValueParameterSymbol,
        receiverType: ConeKotlinType,
        envType: ConeKotlinType,
        facadeParams: List<FirValueParameter>,
        placementRead: FirExpression?,
    ): FirExpression {
        val actualParams = actualFn.valueParameters
        val regularArguments = List(actualParams.size) { index ->
            val arg = builder.paramRead(facadeParams[index].symbol, facadeParamType(actualFn, index).coneType)
            if (jvmSignatureProvider.signatureInfo(actualFn).parameterTypes.getOrNull(index) == ActualParameterType.BooleanIn) {
                builder.toKBoolean(arg)
            } else {
                arg
            }
        }
        val regularMapping = LinkedHashMap<FirExpression, FirValueParameter>()
        actualParams.forEachIndexed { index, param ->
            regularMapping[regularArguments[index]] = param
        }

        val contextArgumentsValue = actualFn.contextParameters.map { contextParam ->
            if (placementRead != null && contextParam.returnTypeRef.coneType.isNativePlacementType()) {
                placementRead
            } else {
                builder.paramRead(envParamSymbol, envType)
            }
        }
        val extensionReceiver = actualFn.receiverParameter?.let {
            builder.paramRead(receiverParamSymbol, receiverType)
        }
        val dispatch = dispatchReceiver(actualFn, container)

        return buildFunctionCall {
            calleeReference = buildResolvedNamedReference {
                name = actualFn.name
                resolvedSymbol = actualSymbol
            }
            coneTypeOrNull = actualSymbol.resolvedReturnType
            argumentList = buildResolvedArgumentList(null, regularMapping)
            contextArguments += contextArgumentsValue
            if (extensionReceiver != null) {
                this.extensionReceiver = extensionReceiver
            }
            if (dispatch != null) {
                this.dispatchReceiver = dispatch
            }
        }
    }

    /**
     * The dispatch receiver of a call to [actualFn]: the generated lateinit instance for a constructable container
     * member, the object qualifier for an object member, `null` for a top-level function.
     */
    private fun dispatchReceiver(
        actualFn: FirNamedFunction,
        container: FirRegularPropertySymbol?,
    ): FirExpression? {
        val containingClass = actualFn.getContainingClass() ?: return null
        val clazz = containingClass.symbol
        return if (container != null) {
            instancePropertyAccess(container, clazz)
        } else if (containingClass.classKind == ClassKind.OBJECT) {
            buildResolvedQualifier {
                packageFqName = clazz.classId.packageFqName
                relativeClassFqName = clazz.classId.relativeClassName
                resolvedToCompanionObject = false
                coneTypeOrNull = clazz.defaultType()
                accessedObjectSymbol = clazz
            }
        } else {
            null
        }
    }

    /** An expression reading the generated container instance property. */
    private fun instancePropertyAccess(instance: FirRegularPropertySymbol, clazz: FirRegularClassSymbol): FirExpression =
        buildPropertyAccessExpression {
            coneTypeOrNull = clazz.defaultType()
            calleeReference = buildResolvedNamedReference {
                name = instance.callableId.callableName
                resolvedSymbol = instance
            }
        }

    /** The visible no-arg constructor of a constructable container, or `null`. */
    private fun noArgConstructor(clazz: FirRegularClassSymbol): FirConstructorSymbol? =
        clazz.constructors(builder.session).filter { it.visibility in listOf(Public, Internal) }.find {
            it.valueParameterSymbols.isEmpty()
        }

    /** The visible `JavaVM`-accepting constructor of a constructable container, or `null`. */
    private fun vmConstructor(clazz: FirRegularClassSymbol): FirConstructorSymbol? =
        clazz.constructors(builder.session).filter { it.visibility in listOf(Public, Internal) }.find {
            it.valueParameterSymbols.singleOrNull()?.resolvedReturnType?.isJavaVmType() == true
        }

    /** `instance = Container()`: initializes a container that declares no `JavaVM` constructor. */
    private fun containerInitializationNoArg(
        clazz: FirRegularClassSymbol,
        instance: FirRegularPropertySymbol,
    ): FirStatement {
        val constructor = noArgConstructor(clazz) ?: error("Missing no-arg constructor in @JniActuals class ${clazz.classId}")
        return buildVariableAssignment {
            lValue = instancePropertyAccess(instance, clazz)
            rValue = buildFunctionCall {
                calleeReference = buildResolvedNamedReference {
                    name = constructor.callableId.callableName
                    resolvedSymbol = constructor
                }
                coneTypeOrNull = clazz.defaultType()
                argumentList = FirEmptyArgumentList
            }
        }
    }

    /** `instance = Container(vm)`: initializes a container whose constructor takes the `JavaVM`. */
    private fun containerInitialization(
        clazz: FirRegularClassSymbol,
        instance: FirRegularPropertySymbol,
        vmParameterSymbol: FirValueParameterSymbol,
        vmType: ConeKotlinType,
    ): FirStatement {
        val constructor = vmConstructor(clazz) ?: error("Missing vm constructor in @JniActuals class ${clazz.classId}")
        val vmRead = builder.paramRead(vmParameterSymbol, vmType)
        return buildVariableAssignment {
            lValue = instancePropertyAccess(instance, clazz)
            rValue = buildFunctionCall {
                calleeReference = buildResolvedNamedReference {
                    name = constructor.callableId.callableName
                    resolvedSymbol = constructor
                }
                coneTypeOrNull = clazz.defaultType()
                argumentList = buildResolvedArgumentList(
                    null,
                    linkedMapOf(vmRead to constructor.valueParameterSymbols.single().fir),
                )
            }
        }
    }

    /**
     * The `context(MemScope, JniEnv) () -> Unit` lambda passed to [FirBuilder.useEnvFunction]: finds the class and registers all
     * its natives, i.e. `(findClass("com/example/Native".modifiedUtf8) ?: error("...")).registerNatives(count) { ... }`.
     */
    private fun buildRegisterNativesLambda(className: String, actuals: List<FunLocation>): FirExpression {
        val lambdaSymbol = FirAnonymousFunctionSymbol()

        val memScopeType = Symbols.MemScope.constructClassLikeType()
        val envType = Symbols.JniEnv.constructClassLikeType()

        val memScopeParam = builder.buildFnValueParameter(
            containingSymbol = lambdaSymbol,
            name = "memScope".ident(),
            coneType = memScopeType,
            kind = FirValueParameterKind.ContextParameter,
        )
        val envContextParam = builder.buildFnValueParameter(
            containingSymbol = lambdaSymbol,
            name = "env".ident(),
            coneType = envType,
            kind = FirValueParameterKind.ContextParameter,
        )

        val envContextRead = builder.paramRead(envContextParam.symbol, envType)
        val memScopeContextRead = builder.paramRead(memScopeParam.symbol, memScopeType)

        val findClassCall = buildFunctionCall {
            calleeReference = buildResolvedNamedReference {
                name = builder.findClassFunction.name
                resolvedSymbol = builder.findClassFunction
            }
            coneTypeOrNull = builder.findClassFunction.resolvedReturnType
            contextArguments += envContextRead
            contextArguments += memScopeContextRead
            argumentList = buildResolvedArgumentList(
                original = null,
                linkedMapOf(
                    builder.modifiedUtf8Read(builder.jvmClassNameLiteral(className)) to builder.findClassFunction.valueParameterSymbols[0].fir,
                ),
            )
        }

        val errorCall = buildFunctionCall {
            calleeReference = buildResolvedNamedReference {
                name = builder.errorFunction.name
                resolvedSymbol = builder.errorFunction
            }
            coneTypeOrNull = StandardClassIds.Nothing.constructClassLikeType()
            argumentList = buildResolvedArgumentList(
                original = null,
                linkedMapOf(
                    buildLiteralExpression(
                        null,
                        ConstantValueKind.String,
                        "Registering native methods failed for JVM class $className",
                        setType = true,
                    ) to builder.errorFunction.valueParameterSymbols[0].fir,
                ),
            )
        }

        val jClassRead = buildElvisExpression {
            lhs = findClassCall
            rhs = errorCall
            coneTypeOrNull = builder.registerNativesFunction.fir.receiverParameter!!.typeRef.coneType
        }

        val registerBlockLambda = buildRegistryLambda(actuals, memScopeContextRead)

        val registerNativesCall = buildFunctionCall {
            calleeReference = buildResolvedNamedReference {
                name = builder.registerNativesFunction.name
                resolvedSymbol = builder.registerNativesFunction
            }
            coneTypeOrNull = builder.unitConeType()
            extensionReceiver = jClassRead
            contextArguments += envContextRead
            contextArguments += memScopeContextRead
            argumentList = buildResolvedArgumentList(
                original = null,
                linkedMapOf(
                    buildLiteralExpression(null, ConstantValueKind.Int, actuals.size, setType = true)
                        to builder.registerNativesFunction.valueParameterSymbols[0].fir,
                    registerBlockLambda to builder.registerNativesFunction.valueParameterSymbols[1].fir,
                ),
            )
        }

        val lambda = builder.buildLambda(
            lambdaSymbol = lambdaSymbol,
            functionType = builder.functionTypeCone(
                contextTypeRefs = listOf(memScopeType, envType),
                returnType = builder.unitConeType(),
            ),
            returnTypeRef = builder.resolvedTypeRef(StandardClassIds.Unit),
            contextParameters = listOf(memScopeParam, envContextParam),
        ) { _ ->
            buildBlock { statements += registerNativesCall }
        }
        return builder.lambdaExpression(lambda)
    }

    /**
     * The `JNINativeMethodRegistry.() -> Unit` receiver lambda passed to [FirBuilder.registerNativesFunction]: registers every
     * method of the class via `register("name".modifiedUtf8, "(...)...".modifiedUtf8, staticCFunction { ... })`.
     */
    private fun buildRegistryLambda(
        actuals: List<FunLocation>,
        memScopeContextRead: FirExpression,
    ): FirExpression {
        val lambdaSymbol = FirAnonymousFunctionSymbol()

        val registryReceiverType = builder.registerFunction.fir.receiverParameter!!.typeRef.coneType
        val registryReceiverParameter = builder.buildReceiverParameter(
            containingSymbol = lambdaSymbol,
            typeRef = buildResolvedTypeRef { coneType = registryReceiverType },
        )

        val lambda = builder.buildLambda(
            lambdaSymbol = lambdaSymbol,
            functionType = builder.functionTypeCone(
                receiverType = registryReceiverType,
                returnType = builder.unitConeType(),
            ),
            returnTypeRef = builder.resolvedTypeRef(StandardClassIds.Unit),
            receiverParameter = registryReceiverParameter,
        ) { _ ->
            buildBlock {
                actuals.forEach { actual ->
                    statements += buildRegisterCall(actual, registryReceiverParameter, memScopeContextRead)
                }
            }
        }
        return builder.lambdaExpression(lambda)
    }

    /**
     * `registry.register("<method>".modifiedUtf8, "(<params>)<return>".modifiedUtf8, staticCFunction { ... })`:
     * the single-method entry of a `RegisterNatives` block, forwarding to the actual via [buildForwardingLambda].
     */
    private fun buildRegisterCall(
        actual: FunLocation,
        registryReceiverParameter: FirReceiverParameter,
        memScopeContextRead: FirExpression,
    ): FirExpression {
        val methodName = jniActualArguments(actual.fn)!!.methodName
        val methodNameLiteral = buildLiteralExpression(null, ConstantValueKind.String, methodName, setType = true)
        val signatureLiteral = buildLiteralExpression(null, ConstantValueKind.String, jvmSignature(actual), setType = true)

        val registryReceiverRead = builder.thisReceiverRead(registryReceiverParameter.symbol, registryReceiverParameter.typeRef.coneType)

        val fnPtrLambda = buildForwardingLambda(actual)

        return buildFunctionCall {
            calleeReference = buildResolvedNamedReference {
                name = builder.registerFunction.name
                resolvedSymbol = builder.registerFunction
            }
            coneTypeOrNull = builder.unitConeType()
            extensionReceiver = registryReceiverRead
            contextArguments += memScopeContextRead
            argumentList = buildResolvedArgumentList(
                original = null,
                linkedMapOf(
                    builder.modifiedUtf8Read(methodNameLiteral) to builder.registerFunction.valueParameterSymbols[0].fir,
                    builder.modifiedUtf8Read(signatureLiteral) to builder.registerFunction.valueParameterSymbols[1].fir,
                    fnPtrLambda to builder.registerFunction.valueParameterSymbols[2].fir,
                ),
            )
        }
    }

    /**
     * The `staticCFunction` block forwarded to the actual: `(env, objectOrClass, args...) -> JVM-return` for
     * a regular actual. A critical native is different: the function pointer a `RegisterNatives` block
     * registers is called with the *critical* ABI - no `JniEnv`, no class/object reference, every array as
     * the `(length, CArrayPointer)` pair the actual declares - so its lambda takes the `JniActual`'s own
     * parameter list and forwards to it directly (see [criticalForwardingLambda]).
     */
    private fun buildForwardingLambda(actual: FunLocation): FirExpression {
        val fn = actual.fn.fir as FirNamedFunction

        val layout = session.jvmSignatureProvider.criticalLayout(fn)
        if (layout != null) {
            check(layout.isValid) {
                "@CriticalNative actual ${actual.fn.name.asString()} has an invalid parameter layout; " +
                        "see the compiler diagnostics"
            }
            return criticalForwardingLambda(actual, layout)
        }

        val lambdaSymbol = FirAnonymousFunctionSymbol()

        val receiverTypeRef = fn.receiverParameter?.typeRef as? FirResolvedTypeRef
            ?: builder.resolvedTypeRef(Symbols.COpaquePointer)
        val parts = buildFacadeParts(
            containingSymbol = lambdaSymbol,
            actualFn = fn,
            actualSymbol = actual.fn,
            container = actual.container,
            receiverName = "receiver".ident(),
            receiverType = receiverTypeRef,
        )

        val facadeReturnTypeRef = facadeReturnType(fn)
        val lambda = builder.buildLambda(
            lambdaSymbol = lambdaSymbol,
            functionType = builder.functionTypeCone(
                valueParameters = listOf(parts.envParam.returnTypeRef.coneType, parts.receiverParam.returnTypeRef.coneType) +
                    parts.regularParams.map { it.returnTypeRef.coneType },
                returnType = facadeReturnTypeRef.coneType,
            ),
            returnTypeRef = facadeReturnTypeRef,
            valueParameters = listOf(parts.envParam, parts.receiverParam) + parts.regularParams,
        ) { returnTarget ->
            buildBlock {
                statements += buildReturnExpression {
                    target = returnTarget
                    result = parts.forwardingResult
                }
            }
        }
        return builder.resolvedStaticCFunction(lambda)
    }

    /**
     * The `staticCFunction` block of a critical native registered with `RegisterNatives`: the registered pointer is
     * called with the critical convention, so the lambda takes the actual's own parameters and forwards to it.
     */
    private fun criticalForwardingLambda(actual: FunLocation, layout: CriticalLayout): FirExpression {
        val lambdaSymbol = FirAnonymousFunctionSymbol()
        val forwarding = criticalForwarding(lambdaSymbol, actual, layout)

        val lambda = builder.buildLambda(
            lambdaSymbol = lambdaSymbol,
            functionType = builder.functionTypeCone(
                valueParameters = forwarding.params.map { it.returnTypeRef.coneType },
                returnType = layout.nativeReturnClassId().constructClassLikeType(),
            ),
            returnTypeRef = builder.resolvedTypeRef(layout.nativeReturnClassId()),
            valueParameters = forwarding.params,
        ) { returnTarget ->
            buildBlock {
                statements += buildReturnExpression { target = returnTarget; result = forwarding.returned }
            }
        }
        return builder.resolvedStaticCFunction(lambda)
    }

    /**
     * The parameters of a critical entry point plus the call to the actual that reads them back, with the result
     * narrowed to the native return type.
     */
    private fun criticalForwarding(
        containingSymbol: FirCallableSymbol<*>,
        actual: FunLocation,
        layout: CriticalLayout,
    ): CriticalForwarding {
        val fn = actual.fn.fir as FirNamedFunction
        val params = fn.valueParameters.indices.map { index ->
            val param = fn.valueParameters[index]
            val coneType = if (layout.scalarTypeAt(index) == ActualParameterType.BooleanIn) {
                StandardClassIds.UByte.constructClassLikeType()
            } else {
                param.returnTypeRef.coneType
            }
            builder.buildFnValueParameter(containingSymbol, param.name, coneType)
        }
        val call = actualCall(
            actualFn = fn,
            actualSymbol = actual.fn,
            container = actual.container,
            arguments = fn.valueParameters.indices.map { index ->
                val read = builder.paramRead(params[index].symbol, params[index].returnTypeRef.coneType)
                if (layout.scalarTypeAt(index) == ActualParameterType.BooleanIn) builder.toKBoolean(read) else read
            },
        )
        return CriticalForwarding(
            params = params,
            returned = if (layout.isBooleanReturn) builder.toJBoolean(call) else call,
        )
    }

    /** The parameter list of a critical entry point and the expression its body returns. */
    private class CriticalForwarding(val params: List<FirValueParameter>, val returned: FirExpression)

    /** Whether [this] is a native-memory placement type an actual may take as an additional `context` parameter. */
    private fun ConeKotlinType.isNativePlacementType(): Boolean {
        val id = classId ?: return false
        return id in NATIVE_PLACEMENT_CLASS_IDS
    }
}

/** The native-memory placement types accepted as an additional `context` parameter of an actual. */
private val NATIVE_PLACEMENT_CLASS_IDS = setOf(
    Symbols.NativePlacement,
    Symbols.AutofreeScope,
    Symbols.ArenaBase,
    Symbols.MemScope,
)
