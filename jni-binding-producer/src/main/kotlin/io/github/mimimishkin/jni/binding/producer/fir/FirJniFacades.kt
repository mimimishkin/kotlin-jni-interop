package io.github.mimimishkin.jni.binding.producer.fir

import io.github.mimimishkin.jni.binding.producer.Symbols
import io.github.mimimishkin.jni.binding.producer.ident
import io.github.mimimishkin.jni.binding.producer.jniCName
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
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.types.ConstantValueKind

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

        val forwardingCall = buildForwardingCall(
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
     * The call body shared by facades and `staticCFunction` lambdas: reads the facade parameters,
     * converts `kotlin.Boolean` inputs to the JNI `jboolean` representation, forwards the actual's
     * `context` parameters to `env`, and dispatches on the container instance / object qualifier /
     * nothing.
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

        val contextArgumentsValue = actualFn.contextParameters.map {
            builder.paramRead(envParamSymbol, envType)
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
     * The `staticCFunction` block forwarded to the actual: `(env, objectOrClass, args...) -> JVM-return`. Unlike the
     * expose-function mode (which emits a `@CName` facade), here the whole dispatch - parameter conversion, the
     * object/instance resolution and the Boolean wrap - is inlined into the lambda body, so no facade is emitted.
     */
    private fun buildForwardingLambda(actual: FunLocation): FirExpression {
        val lambdaSymbol = FirAnonymousFunctionSymbol()
        val fn = actual.fn.fir as FirNamedFunction

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
}