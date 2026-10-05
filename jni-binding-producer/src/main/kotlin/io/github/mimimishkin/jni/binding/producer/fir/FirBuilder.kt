package io.github.mimimishkin.jni.binding.producer.fir

import io.github.mimimishkin.jni.binding.producer.Symbols
import io.github.mimimishkin.jni.binding.producer.ident
import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.builtins.functions.FunctionTypeKind
import org.jetbrains.kotlin.descriptors.EffectiveVisibility
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirFunctionTarget
import org.jetbrains.kotlin.fir.FirImplementationDetail
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.FirTarget
import org.jetbrains.kotlin.fir.declarations.FirAnonymousFunction
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirReceiverParameter
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.declarations.FirValueParameterKind
import org.jetbrains.kotlin.fir.declarations.builder.buildAnonymousFunction
import org.jetbrains.kotlin.fir.declarations.builder.buildNamedFunction
import org.jetbrains.kotlin.fir.declarations.builder.buildProperty
import org.jetbrains.kotlin.fir.declarations.builder.buildReceiverParameter
import org.jetbrains.kotlin.fir.declarations.builder.buildValueParameter
import org.jetbrains.kotlin.fir.declarations.impl.FirResolvedDeclarationStatusImpl
import org.jetbrains.kotlin.fir.declarations.primaryConstructorIfAny
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirBlock
import org.jetbrains.kotlin.fir.expressions.FirEmptyArgumentList
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.buildResolvedArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotationArgumentMapping
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotationCall
import org.jetbrains.kotlin.fir.expressions.builder.buildAnonymousFunctionExpression
import org.jetbrains.kotlin.fir.expressions.builder.buildBlock
import org.jetbrains.kotlin.fir.expressions.builder.buildFunctionCall
import org.jetbrains.kotlin.fir.expressions.builder.buildLiteralExpression
import org.jetbrains.kotlin.fir.expressions.builder.buildPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.builder.buildReturnExpression
import org.jetbrains.kotlin.fir.expressions.builder.buildThisReceiverExpression
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.references.builder.buildImplicitThisReference
import org.jetbrains.kotlin.fir.references.builder.buildResolvedNamedReference
import org.jetbrains.kotlin.fir.resolve.providers.FirSymbolProvider
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirAnonymousFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirLocalPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirReceiverParameterSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.ConeAttributes
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.CompilerConeAttributes
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildTypeProjectionWithVariance
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.constructClassLikeType
import org.jetbrains.kotlin.fir.types.constructType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.impl.ConeClassLikeTypeImpl
import org.jetbrains.kotlin.fir.types.toLookupTag
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.types.ConstantValueKind
import org.jetbrains.kotlin.types.Variance

private object JniBindingStepKey : GeneratedDeclarationKey()

/**
 * Low-level FIR construction primitives and jni-binding DSL symbol lookups shared by the generator and the facade
 * assembler.
 */
@OptIn(FirImplementationDetail::class, SymbolInternals::class)
internal class FirBuilder(
    val session: FirSession,
) {

    private val provider: FirSymbolProvider by lazy { session.symbolProvider }

    /** The single plugin declaration origin reused by every generated declaration. */
    val pluginOrigin: FirDeclarationOrigin = FirDeclarationOrigin.Plugin(JniBindingStepKey)

    /** A FINAL, public effective status with the given declared [visibility]. */
    fun pluginStatus(visibility: Visibility): FirResolvedDeclarationStatusImpl =
        FirResolvedDeclarationStatusImpl(
            visibility = visibility,
            modality = Modality.FINAL,
            effectiveVisibility = EffectiveVisibility.Public,
        )

    /** A resolved type reference to [classId]'s default (non-nullable, no-argument) type. */
    fun resolvedTypeRef(classId: ClassId): FirResolvedTypeRef =
        buildResolvedTypeRef { coneType = classId.constructClassLikeType() }

    /** A value parameter of a generated function/lambda, bound to a fresh symbol (readable via [FirValueParameter.symbol]). */
    fun buildFnValueParameter(
        containingSymbol: FirCallableSymbol<*>,
        name: Name,
        coneType: ConeKotlinType,
        kind: FirValueParameterKind = FirValueParameterKind.Regular,
    ): FirValueParameter {
        val symbol = FirValueParameterSymbol()
        val parameter = buildValueParameter {
            moduleData = session.moduleData
            origin = pluginOrigin
            containingDeclarationSymbol = containingSymbol
            this.name = name
            returnTypeRef = buildResolvedTypeRef { this.coneType = coneType }
            this.symbol = symbol
            valueParameterKind = kind
        }
        symbol.bind(parameter)
        return parameter
    }

    /** A lambda receiver parameter bound to a fresh symbol (readable via [FirReceiverParameter.symbol]). */
    fun buildReceiverParameter(
        containingSymbol: FirCallableSymbol<*>,
        typeRef: FirResolvedTypeRef,
    ): FirReceiverParameter {
        val symbol = FirReceiverParameterSymbol()
        val parameter = buildReceiverParameter {
            moduleData = session.moduleData
            origin = pluginOrigin
            containingDeclarationSymbol = containingSymbol
            this.symbol = symbol
            this.typeRef = typeRef
        }
        symbol.bind(parameter)
        return parameter
    }

    /**
     * Builds a plugin-minted `FirNamedFunction`, binding both the function symbol and its return target. The [bodyBuilder]
     * receives the return target so its `buildReturnExpression { target = ... }` terminates the function body.
     * Both bindings register the declaration on the session; callers only ever read the [functionSymbol] afterwards.
     */
    fun buildGeneratedFunction(
        functionSymbol: FirNamedFunctionSymbol,
        visibility: Visibility,
        returnTypeRef: FirResolvedTypeRef,
        valueParameters: List<FirValueParameter>,
        annotations: List<FirAnnotation> = emptyList(),
        bodyBuilder: (returnTarget: FirTarget<FirFunction>) -> FirBlock,
    ) {
        val status = pluginStatus(visibility)
        val returnTarget: FirTarget<FirFunction> = FirFunctionTarget(null, false)
        val function = buildNamedFunction {
            moduleData = session.moduleData
            origin = pluginOrigin
            this.status = status
            isLocal = false
            this.returnTypeRef = returnTypeRef
            name = functionSymbol.callableId.callableName
            symbol = functionSymbol
            this.valueParameters.addAll(valueParameters)
            this.annotations.addAll(annotations)
            body = bodyBuilder(returnTarget)
        }
        returnTarget.bind(function)
        functionSymbol.bind(function)
    }

    /** Builds a plugin-minted `FirAnonymousFunction` lambda, binding both the lambda symbol and its return target. */
    fun buildLambda(
        lambdaSymbol: FirAnonymousFunctionSymbol,
        functionType: ConeKotlinType,
        returnTypeRef: FirResolvedTypeRef,
        valueParameters: List<FirValueParameter> = emptyList(),
        contextParameters: List<FirValueParameter> = emptyList(),
        receiverParameter: FirReceiverParameter? = null,
        bodyBuilder: (returnTarget: FirTarget<FirFunction>) -> FirBlock = { buildBlock { } },
    ): FirAnonymousFunction {
        val returnTarget: FirTarget<FirFunction> = FirFunctionTarget(null, false)
        val lambda = buildAnonymousFunction {
            moduleData = session.moduleData
            origin = pluginOrigin
            symbol = lambdaSymbol
            isLambda = true
            hasExplicitParameterList = false
            typeRef = buildResolvedTypeRef { coneType = functionType }
            this.returnTypeRef = returnTypeRef
            this.valueParameters.addAll(valueParameters)
            this.contextParameters.addAll(contextParameters)
            receiverParameter?.let { this.receiverParameter = it }
            body = bodyBuilder(returnTarget)
        }
        returnTarget.bind(lambda)
        lambdaSymbol.bind(lambda)
        return lambda
    }

    fun lambdaExpression(lambda: FirAnonymousFunction): FirExpression =
        buildAnonymousFunctionExpression { anonymousFunction = lambda }

    /**
     * Builds the `FunctionN` cone for a generated lambda's function type, mirroring
     * [org.jetbrains.kotlin.fir.resolve.FirTypeResolverImpl].
     */
    fun functionTypeCone(
        contextTypeRefs: List<ConeKotlinType> = emptyList(),
        receiverType: ConeKotlinType? = null,
        valueParameters: List<ConeKotlinType> = emptyList(),
        returnType: ConeKotlinType,
    ): ConeKotlinType {
        val parametersCount =
            valueParameters.size + contextTypeRefs.size + if (receiverType != null) 1 else 0
        val parameters =
            contextTypeRefs +
                listOfNotNull(receiverType) +
                valueParameters +
                listOf(returnType)
        val classId = FunctionTypeKind.Function.numberedClassId(parametersCount)
        val attributes = listOfNotNull(
            receiverType?.let { CompilerConeAttributes.ExtensionFunctionType },
            contextTypeRefs.takeIf { it.isNotEmpty() }
                ?.let { CompilerConeAttributes.ContextFunctionTypeParams(it.size) },
        )
        return ConeClassLikeTypeImpl(
            classId.toLookupTag(),
            parameters.toTypedArray(),
            isMarkedNullable = false,
            attributes = if (attributes.isEmpty()) ConeAttributes.Empty else ConeAttributes.create(attributes),
        )
    }

    /** The `Unit` cone type (the return type of hooks and `useEnv`/`RegisterNatives` blocks). */
    fun unitConeType(): ConeKotlinType =
        StandardClassIds.Unit.constructClassLikeType()

    /** The JVM-facing function-typed cone of a table entry: `JRef<CFunction<*>>`. */
    fun jRefCFunctionType(): ConeKotlinType =
        Symbols.JRef.constructClassLikeType(
            arrayOf(Symbols.CFunction.constructClassLikeType(arrayOf(StandardClassIds.Any.constructClassLikeType()))),
        )

    /**
     * An expression reading a value parameter as a property access on its symbol, e.g. `env` or `vm`
     * inside a generated body. [coneType] is the declared type of the parameter.
     */
    /** An expression reading a value parameter as a property access on its symbol, e.g. `env` or `vm`. */
    fun paramRead(paramSymbol: FirValueParameterSymbol, coneType: ConeKotlinType): FirPropertyAccessExpression =
        symbolRead(paramSymbol, paramSymbol.fir.name, coneType)

    /**
     * A local `var` of the generated body, bound to a fresh symbol and readable through [localVarRead].
     */
    fun buildLocalVar(name: Name, returnTypeRef: FirResolvedTypeRef): FirProperty =
        buildProperty {
            moduleData = session.moduleData
            origin = pluginOrigin
            status = FirResolvedDeclarationStatusImpl(
                visibility = Visibilities.Local,
                modality = Modality.FINAL,
                effectiveVisibility = EffectiveVisibility.Local,
            )
            isLocal = true
            isVar = true
            this.name = name
            this.returnTypeRef = returnTypeRef
            symbol = FirLocalPropertySymbol()
        }

    /**
     * An expression reading the local [var]; also its `lValue` in a [buildVariableAssignment].
     */
    fun localVarRead(varSymbol: FirPropertySymbol, coneType: ConeKotlinType): FirPropertyAccessExpression =
        symbolRead(varSymbol, varSymbol.fir.name, coneType)

    /**
     * The property access that reads a generated declaration by symbol, whatever kind of declaration it is.
     */
    private fun symbolRead(
        symbol: FirCallableSymbol<*>,
        name: Name,
        coneType: ConeKotlinType,
    ): FirPropertyAccessExpression =
        buildPropertyAccessExpression {
            coneTypeOrNull = coneType
            calleeReference = buildResolvedNamedReference {
                this.name = name
                resolvedSymbol = symbol
            }
        }

    /** The implicit `this` read of a lambda receiver parameter. */
    fun thisReceiverRead(receiverSymbol: FirReceiverParameterSymbol, coneType: ConeKotlinType): FirExpression =
        buildThisReceiverExpression {
            this.coneTypeOrNull = coneType
            calleeReference = buildImplicitThisReference {
                boundSymbol = receiverSymbol
            }
        }

    /**
     * The JNI env parameter type of a facade: the actual's `context(env)` parameter type, or
     * `CPointerVarOf<CPointer<Raw_JniNativeInterface>>` - the expansion of the `JniEnv` type alias - if absent.
     *
     * A native-placement `context` parameter (`AutofreeScope`/`NativePlacement`/`ArenaBase`/`MemScope`) is skipped:
     * an actual may declare it alone or before the env one, and the facade's env parameter must stay a `JniEnv`.
     */
    fun envTypeRef(actualFn: FirNamedFunction): FirResolvedTypeRef {
        val contextType = actualFn.contextParameters
            .map { it.returnTypeRef.coneType }
            .firstOrNull { it.isJniEnvType() }
        return if (contextType != null) {
            buildResolvedTypeRef { coneType = contextType }
        } else {
            buildResolvedTypeRef {
                coneType = Symbols.CPointerVarOf.constructClassLikeType(
                    arrayOf(
                        Symbols.CPointer.constructClassLikeType(
                            arrayOf(Symbols.JNINativeInterfaceWrapped.constructClassLikeType()),
                        ),
                    ),
                )
            }
        }
    }

    /** `receiver.modifiedUtf8`, a read of the `String.modifiedUtf8` extension property. */
    fun modifiedUtf8Read(receiver: FirExpression): FirExpression =
        buildPropertyAccessExpression {
            coneTypeOrNull = modifiedUtf8Property.resolvedReturnType
            extensionReceiver = receiver
            calleeReference = buildResolvedNamedReference {
                name = modifiedUtf8Property.name
                resolvedSymbol = modifiedUtf8Property
            }
        }

    /** A `String` literal of [className] with JVM internal slashes (`com/example/Native`), for `findClass`. */
    fun jvmClassNameLiteral(className: String): FirExpression =
        buildLiteralExpression(null, ConstantValueKind.String, className.replace('.', '/'), setType = true)

    /** The `@CName(...)` annotation of a generated entry point or exported facade. */
    fun cNameAnnotation(cNameValue: String): FirAnnotation {
        val cNameLiteral = buildLiteralExpression(null, ConstantValueKind.String, cNameValue, setType = true)
        return buildAnnotationCall {
            annotationTypeRef = buildResolvedTypeRef { coneType = cName.constructType() }
            calleeReference = buildResolvedNamedReference {
                name = cName.classId.shortClassName
                resolvedSymbol = cNameConstructor
            }
            containingDeclarationSymbol = cNameConstructor
            argumentList = buildResolvedArgumentList(
                original = null,
                linkedMapOf(cNameLiteral to externNameParameter.fir),
            )
            argumentMapping = buildAnnotationArgumentMapping {
                mapping[externNameParameter.name] = cNameLiteral
            }
        }
    }

    /** `expression.toKBoolean()`: converts a JNI `jboolean` (`UByte`) to `kotlin.Boolean`. */
    fun toKBoolean(expression: FirExpression): FirExpression =
        buildFunctionCall {
            calleeReference = buildResolvedNamedReference {
                name = "toKBoolean".ident()
                resolvedSymbol = extensionFunctionOn(Symbols.bindingPackage, "toKBoolean")
            }
            coneTypeOrNull = StandardClassIds.Boolean.constructClassLikeType()
            extensionReceiver = expression
            argumentList = FirEmptyArgumentList
        }

    /** `call.toJBoolean()`: converts a `kotlin.Boolean` result to the JNI `jboolean` (`UByte`) representation. */
    fun toJBoolean(call: FirExpression): FirExpression =
        buildFunctionCall {
            calleeReference = buildResolvedNamedReference {
                name = "toJBoolean".ident()
                resolvedSymbol = extensionFunctionOn(Symbols.bindingPackage, "toJBoolean")
            }
            coneTypeOrNull = StandardClassIds.UByte.constructClassLikeType()
            extensionReceiver = call
            argumentList = FirEmptyArgumentList
        }

    /**
     * The `staticCFunction { ... }` wrapper of a forwarding lambda, attributed with the exact `FunctionN` JNI type
     * arguments of the lambda (arity `N` ⇒ `staticCFunction<P1..PN, R>`).
     */
    fun resolvedStaticCFunction(lambda: FirAnonymousFunction): FirExpression {
        val staticCFunction = topLevelFunctionSymbol(Symbols.cinteropPackage, "staticCFunction") {
            it.valueParameterSymbols.size == 1 && it.typeParameterSymbols.size == lambda.valueParameters.size + 1
        }
        return buildFunctionCall {
            calleeReference = buildResolvedNamedReference {
                name = staticCFunction.name
                resolvedSymbol = staticCFunction
            }
            coneTypeOrNull = jRefCFunctionType()
            lambda.valueParameters.forEach { parameter ->
                typeArguments += buildTypeProjectionWithVariance {
                    typeRef = parameter.returnTypeRef
                    variance = Variance.INVARIANT
                }
            }
            typeArguments += buildTypeProjectionWithVariance {
                typeRef = lambda.returnTypeRef
                variance = Variance.INVARIANT
            }
            argumentList = buildResolvedArgumentList(
                original = null,
                linkedMapOf(lambdaExpression(lambda) to staticCFunction.valueParameterSymbols[0].fir),
            )
        }
    }

    /** The `kotlin.native.CName` annotation class symbol. */
    val cName: FirClassSymbol<*> by lazy {
        provider.getClassLikeSymbolByClassId(Symbols.CName) as FirClassSymbol<*>
    }
    /** The primary constructor of `@CName`. */
    val cNameConstructor: FirConstructorSymbol by lazy {
        cName.primaryConstructorIfAny(session)!!
    }
    /** The `externName` parameter of `@CName`. */
    val externNameParameter: FirValueParameterSymbol by lazy {
        cNameConstructor.valueParameterSymbols.first { it.name.asString() == "externName" }
    }

    /** The `jni-binding` `JavaVM` class symbol. */
    val javaVM: FirClassLikeSymbol<*> by lazy {
        provider.getClassLikeSymbolByClassId(Symbols.JavaVM)!!
    }

    /** The `kotlinx.cinterop.COpaquePointer` class symbol, used as the receiver type of uncontainerized functions. */
    val opaquePointer: FirClassLikeSymbol<*> by lazy {
        provider.getClassLikeSymbolByClassId(Symbols.COpaquePointer)!!
    }

    /**
     * The `JClass.registerNatives(count, block)` DSL function from `jni-binding`. `JClass` is a
     * typealias to `CPointer<out _jclass>`, so the `registerNatives` overloads are top-level
     * extensions rather than members; the DSL variant is picked by its `(Int, () -> Unit)` shape,
     * distinguishing it from the `(CValues, Int)` array-based overload.
     */
    val registerNativesFunction: FirFunctionSymbol<*> by lazy {
        topLevelFunctionSymbol(Symbols.bindingPackage, "registerNatives") {
            it.fir.receiverParameter != null &&
                it.valueParameterSymbols.size == 2 &&
                it.valueParameterSymbols[0].resolvedReturnType.classId == StandardClassIds.Int
        }
    }
    /** The `useEnv` context extension used to obtain a scoped `JniEnv` inside a `RegisterNatives` step. */
    val useEnvFunction: FirFunctionSymbol<*> by lazy {
        extensionFunctionOn(Symbols.bindingPackage, "useEnv")
    }
    /** The `context(MemScope, JniEnv) findClass(name)` function resolving a JVM class by its internal name. */
    val findClassFunction: FirFunctionSymbol<*> by lazy {
        contextTopLevelFunctionOn(Symbols.bindingPackage, "findClass")
    }
    /**
     * The `kotlinx.cinterop.memScoped` function (`inline fun <R> memScoped(block: MemScope.() -> R): R`),
     * used to provide a native allocation scope to actuals declaring a placement `context` parameter.
     */
    val memScopedFunction: FirFunctionSymbol<*> by lazy {
        topLevelFunctionSymbol(Symbols.cinteropPackage, "memScoped") {
            it.valueParameterSymbols.size == 1 && it.typeParameterSymbols.size == 1
        }
    }
    /**
     * The `JNINativeMethodRegistry.register(name, signature, functionPtr)` DSL function from
     * `jni-binding`, used inside the `registerNatives` block to bind a single native method.
     */
    val registerFunction: FirFunctionSymbol<*> by lazy {
        topLevelFunctionSymbol(Symbols.bindingPackage, "register") {
            it.fir.receiverParameter != null && it.valueParameterSymbols.size == 3
        }
    }
    /**
     * The `String.modifiedUtf8` extension property producing a JNI-modified UTF-8 `CValues`, used
     * inside `RegisterNatives` lambdas to pass class and method names to `findClass`/`register`.
     */
    val modifiedUtf8Property: FirPropertySymbol by lazy {
        provider.getTopLevelPropertySymbols(Symbols.bindingPackage, "modifiedUtf8".ident())
            .firstOrNull { it.fir.receiverParameter != null }
            ?: error("Missing the String.modifiedUtf8 extension property in the jni-binding library")
    }
    /** The `kotlin.error(message)` function, used to terminate a `RegisterNatives` step when `findClass` fails. */
    val errorFunction: FirFunctionSymbol<*> by lazy {
        topLevelFunctionSymbol(FqName("kotlin"), "error") { it.valueParameterSymbols.size == 1 }
    }

    // ------------------------------------------------------------------------------------------------
    // Critical natives: the `@CriticalNative` fallback facade turns each `jarray` into a `(length, pointer)` pair by
    // entering a critical region with `modifyCritical`.
    // ------------------------------------------------------------------------------------------------

    /**
     * The `JArray.length` extension property: the element count of a `jarray` as the fallback facade receives it,
     * which is the `length` half of the pair the actual declares.
     */
    val jArrayLength: FirPropertySymbol by lazy {
        provider.getTopLevelPropertySymbols(Symbols.bindingPackage, "length".ident())
            .firstOrNull { it.resolvedReceiverType?.isJArrayType() == true }
            ?: error("Missing the JArray.length extension property in the jni-binding library")
    }

    /**
     * `memScoped { placement -> body }`: wraps [body] in a `memScoped` call, providing a `MemScope` receiver.
     */
    fun memScopedCall(
        returnType: ConeKotlinType,
        body: (placementRead: FirExpression, lambdaSymbol: FirAnonymousFunctionSymbol) -> FirExpression,
    ): FirExpression {
        val lambdaSymbol = FirAnonymousFunctionSymbol()
        val memScopeType = Symbols.MemScope.constructClassLikeType()
        val receiverParameter = buildReceiverParameter(
            containingSymbol = lambdaSymbol,
            typeRef = buildResolvedTypeRef { coneType = memScopeType },
        )
        val placementRead = thisReceiverRead(receiverParameter.symbol, memScopeType)
        val bodyResult = body(placementRead, lambdaSymbol)
        val lambda = buildLambda(
            lambdaSymbol = lambdaSymbol,
            functionType = functionTypeCone(
                receiverType = memScopeType,
                returnType = returnType,
            ),
            returnTypeRef = buildResolvedTypeRef { coneType = returnType },
            receiverParameter = receiverParameter,
        ) { returnTarget ->
            buildBlock {
                statements += buildReturnExpression { target = returnTarget; result = bodyResult }
            }
        }
        return buildFunctionCall {
            calleeReference = buildResolvedNamedReference {
                name = memScopedFunction.name
                resolvedSymbol = memScopedFunction
            }
            coneTypeOrNull = returnType
            typeArguments += buildTypeProjectionWithVariance {
                typeRef = buildResolvedTypeRef { coneType = returnType }
                variance = Variance.INVARIANT
            }
            argumentList = buildResolvedArgumentList(
                original = null,
                linkedMapOf(lambdaExpression(lambda) to memScopedFunction.valueParameterSymbols[0].fir),
            )
        }
    }

    /**
     * The `JPrimitiveArray<T>.modifyCritical(onError, block)` extension: entering a critical region over a `jarray`.
     *
     * Its value parameters are the `onError` lambda and the `ModifyingArrayScope` block. The block returns `Unit`, so a
     * value cannot travel out of it as an expression - the facade's only job is to open and close the region.
     */
    val modifyCriticalFunction: FirFunctionSymbol<*> by lazy {
        provider.getTopLevelFunctionSymbols(Symbols.bindingAccessorsPackage, "modifyCritical".ident())
            .firstOrNull { symbol ->
                (symbol.fir as? FirFunction)?.receiverParameter != null &&
                    symbol.typeParameterSymbols.isNotEmpty()
            }
            ?: error("Missing the JPrimitiveArray.modifyCritical extension function in the jni-binding library")
    }

    /**
     * The `ModifyingArrayScope.finalize()` extension, releasing the critical region a `modifyCritical` block is
     * holding, with `ApplyChangesMode.FinalCommit`.
     *
     * The scope is an ordinary function type rather than a class, so this is an extension on it and not a member.
     */
    val finalizeFunction: FirFunctionSymbol<*> by lazy {
        extensionFunctionOn(Symbols.bindingAccessorsPackage, "finalize")
    }

    /**
     * The `ModifyingArrayScope` type, `((ApplyChangesMode) -> Unit)`, the receiver of a `modifyCritical`
     * block: the first type argument of the block's `Function3`, which is the block's whole
     * `ModifyingArrayScope.(CArrayPointer<*>, Boolean) -> Unit` type - not that type itself.
     */
    val modifyingArrayScopeType: ConeKotlinType by lazy {
        modifyingArrayScopeBlockType.typeArguments[0] as ConeKotlinType
    }

    /**
     * The `CArrayPointer<*>` parameter type of a `modifyCritical` block, its second type argument.
     *
     * A block passed to `modifyCritical` must carry exactly this type - the element type is a star
     * projection, whatever the region's own `CArrayPointer<T>` is - so a generated block conforms to
     * the parameter without a cast.
     */
    val modifyingArrayCarrayType: ConeKotlinType by lazy {
        modifyingArrayScopeBlockType.typeArguments[1] as ConeKotlinType
    }

    /** The whole `ModifyingArrayScope.(CArrayPointer<*>, Boolean) -> Unit` type of `modifyCritical`'s block parameter. */
    private val modifyingArrayScopeBlockType: ConeClassLikeType by lazy {
        (modifyCriticalFunction.fir as FirFunction).valueParameters[1].returnTypeRef.coneType as ConeClassLikeType
    }

    /** The single top-level extension function named [name] in [packageFqName]; fails if absent or ambiguous. */
    fun extensionFunctionOn(packageFqName: FqName, name: String): FirFunctionSymbol<*> =
        provider.getTopLevelFunctionSymbols(packageFqName, name.ident())
            .firstOrNull { symbol -> (symbol.fir as? FirFunction)?.receiverParameter != null }
            ?: error("Missing the '$name' extension function in the jni-binding library ($packageFqName)")

    /**
     * The single top-level function named [name] in [packageFqName] that satisfies [predicate];
     * fails with a diagnostic if absent.
     */
    fun topLevelFunctionSymbol(
        packageFqName: FqName,
        name: String,
        predicate: (FirFunctionSymbol<*>) -> Boolean,
    ): FirFunctionSymbol<*> =
        provider.getTopLevelFunctionSymbols(packageFqName, name.ident())
            .firstOrNull(predicate)
            ?: error("Missing the '$name' function in $packageFqName")

    /** A top-level function with a `context`-lambda parameter like `useEnv`/`findClass` (two context parameters, no receiver). */
    fun contextTopLevelFunctionOn(packageFqName: FqName, name: String): FirFunctionSymbol<*> =
        topLevelFunctionSymbol(packageFqName, name) {
            it.fir.receiverParameter == null && it.contextParameterSymbols.size == 2
        }
}