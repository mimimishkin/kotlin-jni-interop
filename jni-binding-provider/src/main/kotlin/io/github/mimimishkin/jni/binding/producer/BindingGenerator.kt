package io.github.mimimishkin.jni.binding.producer

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.KModifier.PUBLIC
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import io.github.mimimishkin.jni.binding.producer.GenerateBindingProcessor.Companion.annotationsPaket
import io.github.mimimishkin.jni.binding.producer.JniActualInfo.JvmSignature

internal class BindingGenerator(private val interopConfig: JniInteropConfig) {
    private val CName = ClassName("kotlin.native", "CName")

    private fun CName(externName: String): AnnotationSpec =
        AnnotationSpec.builder(CName).addMember("%S", externName).build()

    private val COpaquePointerType = ClassName("kotlinx.cinterop", "COpaquePointer")

    private val memScoped = MemberName("kotlinx.cinterop", "memScoped")

    private val staticCFunction = MemberName("kotlinx.cinterop", "staticCFunction")

    private val toCValues = MemberName("kotlinx.cinterop", "toCValues", isExtension = true)

    private val ptr = MemberName("kotlinx.cinterop", "ptr", isExtension = true)

    private val allocArray = MemberName("kotlinx.cinterop", "allocArray", isExtension = true)

    private val get = MemberName("kotlinx.cinterop", "get", isExtension = true)

    private val hexFormat = HexFormat {
        upperCase = true
        number {
            minLength = 2
            removeLeadingZeros = true
            prefix = "0x"
            suffix = ".toByte()"
        }
    }

    fun jniFunction(actual: JniActualInfo): FunSpec {
        val funName = actual.method.simpleName + "JniBinding"
        return FunSpec.builder(funName).apply {
            addOriginatingKSFile(actual.source)

            addAnnotation(CName(externName = actual.jvmClass.mapNativeFunName(actual.jvmMethod)))
            addModifiers(PUBLIC)

            val envName = "env"
            addParameter(envName, interopConfig.envType)

            val (objOrClsName, objOrClsType) = objOrCls(actual)
            addParameter(objOrClsName, objOrClsType)

            val parameters = actual.parameters.mapParameters { name -> addParameter(name, type) }
            val returnsCastExpression = actual.returns.also { returns(it.type) }.castExpression

            addStatement("return %L", invokeExpression(actual, envName, objOrClsName, parameters, returnsCastExpression))
        }.build()
    }

    fun onLoad(
        jniVersion: JniVersion,
        beforeLoad: Collection<JniHookInfo>,
        actuals: Collection<JniActualInfo>
    ): FunSpec = FunSpec.builder("onLoadJniBinding").apply {
        val vmName = hook(load = true, hooks = beforeLoad, actuals = actuals)

        if (actuals.isNotEmpty()) {
            val onLoad = checkNotNull(interopConfig.forOnLoad) { "forOnLoad must be specified if BindOnLoad mode was selected" }

            controlFlow("%M", memScoped) {
                // get JNIEnv
                val getEnv = CodeBlock.of(
                    format = onLoad.expressionGetEnv,
                    namedArgs = mapOf(
                        "vm" to vmName,
                        "jniVersion" to jniVersion.native,
                        "getEnv" to onLoad.getEnv
                    )
                )
                val envName = "vmEnv"
                addStatement("val %N = %L", envName, getEnv)

                // register functions for all classes
                controlFlow("context(%N)", envName) {
                    val classesInfo = actuals.groupBy { it.jvmClass }.toList()
                    classesInfo.forEachIndexed { i, (clazz, infos) ->
                        // create a val with a class name
                        val classNameName = "className$i"
                        addStatement("val %N = %L", classNameName, cValuesWithModifiedUtf8(clazz.mapNativeClassName()))

                        // search class by name and fail application if class not found
                        val findClass = CodeBlock.of(
                            format = onLoad.expressionFindClass,
                            namedArgs = mapOf(
                                "env" to envName,
                                "className" to classNameName,
                                "findClass" to onLoad.findClass
                            )
                        )
                        val className = "class$i"
                        addStatement("val %N = %L ?: error(%S)", className, findClass, "Can't find class ${clazz.canonicalName}")

                        // create and fulfill an array of JNINativeMethod
                        val nativesName = "natives$i"
                        addStatement("val %N = %M<%T>(%L)", nativesName, allocArray, onLoad.nativeMethodType, infos.size)
                        for ((i, info) in infos.withIndex()) {
                            // fulfill JNINativeMethod struct
                            controlFlow("%N.%M(%L).run", nativesName, get, i) {
                                // set name
                                addStatement("name = %L", cValuesWithModifiedUtf8(info.jvmMethod))
                                // set signature
                                addStatement("signature = %L", cValuesWithModifiedUtf8(info.jvmSignature.toString()))

                                // prepare function pointer: prepare parameters
                                val parametersSpec = mutableListOf<CodeBlock>()

                                // prepare env parameter
                                val envName = "env"
                                parametersSpec += CodeBlock.of("%N: %T", envName, interopConfig.envType)

                                // prepare objOrCls parameter
                                val (objOrClsName, objOrClsType) = objOrCls(info)
                                parametersSpec += CodeBlock.of("%N: %T", objOrClsName, objOrClsType)

                                // prepare other parameters
                                val parameters = info.parameters.mapParameters { name ->
                                    parametersSpec += CodeBlock.of("%N: %T", name, type)
                                }

                                // prepare returns cast expression
                                val returnsCastExpression = info.returns.castExpression

                                // finally, set the function pointer
                                controlFlow("fnPtr = %M { %L ->", staticCFunction, parametersSpec.joinToCode()) {
                                    addCode(invokeExpression(info, envName, objOrClsName, parameters, returnsCastExpression))
                                }
                            }
                        }

                        // finally, register natives
                        addStatement(
                            format = onLoad.expressionRegisterNatives,
                            namedArgs = mapOf(
                                "env" to envName,
                                "class" to className,
                                "natives" to nativesName,
                                "nativesCount" to infos.size,
                                "registerNatives" to onLoad.registerNatives
                            )
                        )

                        // add a new line for better readability
                        if (i != classesInfo.lastIndex) {
                            addCode("\n")
                        }
                    }
                }
            }
        }

        addStatement("return %L", jniVersion.native)
    }.build()

    fun onUnLoad(beforeUnload: Collection<JniHookInfo>) = FunSpec.builder("onUnloadJniBinding").apply {
        hook(load = false, hooks = beforeUnload, actuals = emptyList())
    }.build()

    private fun Iterable<JniActualInfo.Parameter>.mapParameters(sideAction: JniActualInfo.Parameter.(name: String) -> Unit): List<Pair<String, String>> {
        return mapIndexed { i, parameter ->
            val parameterName = "param$i"
            sideAction(parameter, parameterName)
            parameterName to parameter.castExpression
        }
    }

    fun ClassName.mapNativeFunName(method: String): String {
        fun String.escape(): String = asIterable().joinToString("") { c ->
            when {
                c == '_' -> "_1"
                c == ';' -> "_2"
                c == '[' -> "_3"
                c.code > 127 -> "_0${c.code.toString(16).padStart(4, '0').lowercase()}"
                else -> c.toString()
            }
        }

        val paket = packageName.escape().replace('.', '_')
        val clazz = simpleNames.joinToString("$").escape()
        val method = method.escape()
        return "Java_" + paket + "_" + clazz + "_" + method
    }

    private fun ClassName.mapNativeClassName(): String {
        return reflectionName().replace('.', '/')
    }

    private fun invokeExpression(
        actual: JniActualInfo,
        envName: String,
        objOrClsName: String,
        parameters: List<Pair<String, String>>,
        returnsCastExpression: String,
    ): CodeBlock = buildCodeBlock {
        if (actual.method.enclosingClassName != null) {
            beginControlFlow("with(%T)", actual.method.enclosingClassName)
        }

        if (actual.needEnv) {
            beginControlFlow("context(%N)", envName)
        }

        add("«")
        if (actual.needObjOrCls) add("%N.", objOrClsName)
        add("%M(%L)%L", actual.method, parameters.joinToString { (name, cast) -> name + cast }, returnsCastExpression)
        add("\n»")

        if (actual.needEnv) {
            endControlFlow()
        }

        if (actual.method.enclosingClassName != null) {
            endControlFlow()
        }
    }

    private fun cValuesWithModifiedUtf8(string: String): CodeBlock {
        return CodeBlock.of("%L.%M().%M", byteArrayWithModifiedUtf8(string), toCValues, ptr)
    }

    private fun byteArrayWithModifiedUtf8(string: String): CodeBlock {
        val declaration = buildString {
            fun appendEscaping(byte: Int) {
                append(byte.toHexString(hexFormat))
                append(", ")
            }

            append("byteArrayOf(")

            for (ch in string) {
                when (ch) {
                    '\u0000' -> {
                        // Null char is encoded as 0xC0 0x80
                        appendEscaping(0xC0)
                        appendEscaping(0x80)
                    }

                    in '\u0001'..'\u007F' -> {
                        // 1-byte encoding
                        appendEscaping(ch.code)
                    }

                    in '\u0080'..'\u07FF' -> {
                        // 2-byte encoding
                        appendEscaping(0xC0 or (ch.code shr 6))
                        appendEscaping(0x80 or (ch.code and 0x3F))
                    }

                    else -> {
                        // 3-byte encoding (including surrogates)
                        appendEscaping(0xE0 or (ch.code shr 12))
                        appendEscaping(0x80 or ((ch.code shr 6) and 0x3F))
                        appendEscaping(0x80 or (ch.code and 0x3F))
                    }
                }
            }

            append("0)")
        }

        return CodeBlock.of("/* from %S */ %L", string, declaration)
    }

    private fun FunSpec.Builder.hook(load: Boolean, hooks: Collection<JniHookInfo>, actuals: Collection<JniActualInfo>): String {
        hooks.forEach { addOriginatingKSFile(it.source) }
        actuals.forEach { addOriginatingKSFile(it.source) }

        addAnnotation(CName(externName = if (load) "JNI_OnLoad" else "JNI_OnUnload"))
        addModifiers(PUBLIC)
        returns(if (load) Int::class else Unit::class)

        val vmName = "vm"
        addParameter(vmName, interopConfig.vmType)
        addParameter("reserved", COpaquePointerType)

        for (hook in hooks) {
            if (hook.needVm) addStatement("%M(%N)", hook.method, vmName)
            else addStatement("%M()", hook.method)
        }

        return vmName
    }

    private fun objOrCls(actual: JniActualInfo): Pair<String, TypeName> {
        val objOrClsName = when (actual.isStatic) {
            true -> "cls"
            false -> "obj"
            null -> "clsOrObj"
        }
        val objOrClsType = if (actual.isStatic == true) interopConfig.clsType else interopConfig.objType

        return objOrClsName to objOrClsType
    }
}