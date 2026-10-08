package io.github.mimimishkin.jni.binding.plugin.consumer

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ExperimentalKotlinPoetApi
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.UNIT
import java.io.File

private const val TYPES_PACKAGE = "io.github.mimimishkin.jni.binding"
private const val ANNOTATIONS_PACKAGE = "$TYPES_PACKAGE.annotation"
private const val INTEROP_PACKAGE = "kotlinx.cinterop"
private const val IMPORT_PREFIX = "import "
private const val PACKAGE_PREFIX = "package "

private val JNI_ENV = ClassName(TYPES_PACKAGE, "JniEnv")
private val JNI_OBJECT = ClassName(TYPES_PACKAGE, "JObject")
private val JNI_CLASS = ClassName(TYPES_PACKAGE, "JClass")
private val JNI_ACTUAL = ClassName(ANNOTATIONS_PACKAGE, "JniActual")
private val JNI_CRITICAL_NATIVE = ClassName(ANNOTATIONS_PACKAGE, "CriticalNative")
private val WITH_JVM_SIGNATURE = ClassName(ANNOTATIONS_PACKAGE, "WithJvmSignature")
private val ARRAY_POINTER = ClassName(INTEROP_PACKAGE, "CArrayPointer")

/**
 * Writes `@JniActual` stubs for the expects no actual implements yet.
 */
internal class JniActualsStubWriter(private val sourceRoot: File) {

    /**
     * What a [write] call did, as the task reports it.
     *
     * The counts are kept apart because "nothing was generated" has three different reasons here, and telling them
     * apart is most of what the user needs to know about a run that did not do what they expected: the expect is
     * already implemented elsewhere, is already declared in the file the stub would have gone into, or has just been
     * written.
     */
    data class Result(
        /** Files that were written to, one per class that got something added. */
        val files: List<File>,
        /** Declarations written into them. */
        val written: Int,
        /** Missing declarations that the target file already declared, and that were therefore left alone. */
        val alreadyDeclared: Int,
    )

    /**
     * Writes stubs for [expects], grouped per JVM class.
     */
    fun write(expects: List<JniFunctionContract>): Result {
        val classes = expects.groupBy { it.className }.map { (className, classExpects) ->
            writeClass(className, classExpects)
        }
        return Result(
            files = classes.filter { it.written > 0 }.map { it.file },
            written = classes.sumOf { it.written },
            alreadyDeclared = classes.sumOf { it.alreadyDeclared },
        )
    }

    private fun writeClass(className: String, expects: List<JniFunctionContract>): ClassResult {
        val packageName = className.substringBeforeLast('.', "")
        // A nested class is `Outer$Nested` in the bytecode, and `$` is not usable in a Kotlin file name.
        val simpleName = className.substringAfterLast('.').replace('$', '_')
        val directory = packageName.takeIf(String::isNotEmpty)
            ?.let { sourceRoot.resolve(it.replace('.', '/')) }
            ?: sourceRoot
        val file = directory.resolve("$simpleName.kt")

        val existingText = file.takeIf(File::isFile)?.readText()
        val alreadyDeclared = existingText?.let(::declaredActuals).orEmpty()
        // Seeded with every function of the file, not just the actuals: a generated declaration colliding with one of
        // the user's own helpers would be a redeclaration error, while an actual matching a stale `actuals.json` is
        // already handled above by [alreadyDeclared].
        val usedNames = existingText?.let(::declaredFunctionNames)?.toMutableSet() ?: mutableSetOf()
        val own = expects.filterNot { (className to it.methodName) in alreadyDeclared }
        val stubs = own.mapNotNull { expect ->
            uniqueName(expect.methodName, usedNames)?.let { render(expect, it) }
        }
        val alreadyDeclaredCount = expects.size - own.size

        if (stubs.isNotEmpty()) {
            val spec = FileSpec.builder(packageName, simpleName)
                .indent(" ".repeat(4))
                .apply { stubs.forEach(::addFunction) }
                // A stub is declared in a Kotlin/Native source set, where `kotlin.Int` and the rest of the language
                // prelude is in scope without an import - and `java.lang`, which is not, is left out as well.
                .addKotlinDefaultImports(includeJvm = false, includeJs = false)
                .build()
            file.parentFile?.mkdirs()
            file.writeText(existingText?.let { appendTo(it, spec) } ?: spec.toString())
        }
        return ClassResult(file, stubs.size, alreadyDeclaredCount)
    }

    /**
     * What a single class's worth of expects added to its file.
     */
    private class ClassResult(val file: File, val written: Int, val alreadyDeclared: Int)

    /**
     * Adds what [spec] declares to [existingText], and changes nothing that is already there.
     *
     * A file the generator did not write belongs to the user - their comments, their helpers, their formatting - so it
     * is not rewritten from a spec: a [FileSpec] covers the stubs alone, and kotlinpoet has no API for emitting a
     * member on its own. Its own rendering is read instead, which also keeps the two halves of the answer consistent:
     * the `package` directive and the imports are the header to merge into what the file has, and everything from the
     * first declaration on is what to append at the end.
     */
    private fun appendTo(existingText: String, spec: FileSpec): String {
        val rendered = spec.toString().lines()
        val imports = rendered.imports()
        // kotlinpoet lays a file out as the package directive, a blank line, the imports, another blank line, and then
        // the declarations - so the header ends at the last import, or, for a spec that needs none, at the package
        // directive.
        val lastHeaderLine = when {
            imports.isNotEmpty() -> rendered.indexOfLast { it.startsWith(IMPORT_PREFIX) }
            spec.packageName.isEmpty() -> -1
            else -> rendered.indexOfFirst { it.startsWith(PACKAGE_PREFIX) }
        }
        val declarations = rendered.drop(lastHeaderLine + 1).dropWhile(String::isBlank).joinToString("\n")
        check(declarations.isNotEmpty()) {
            "kotlinpoet rendered no declaration for ${spec.packageName}.${spec.name}"
        }

        val lines = existingText.lines().toMutableList()
        val alreadyImported = lines.imports().mapTo(HashSet(), String::unescaped)
        val missingImports = imports.filterNot { it.unescaped() in alreadyImported }.map { "$IMPORT_PREFIX$it" }

        if (missingImports.isNotEmpty()) {
            val lastImport = lines.indexOfLast { it.startsWith(IMPORT_PREFIX) }
            if (lastImport >= 0) {
                lines.addAll(lastImport + 1, missingImports)
            } else {
                val packageIndex = lines.indexOfFirst { it.startsWith(PACKAGE_PREFIX) }
                val afterPackage = if (packageIndex >= 0) packageIndex + 1 else 0
                // Imports go below the blank line the file already separates the package directive with, if it has one,
                // and are separated from what follows them either way.
                val insertAt = if (lines.getOrNull(afterPackage)?.isBlank() == true) afterPackage + 1 else afterPackage
                lines.addAll(insertAt, missingImports)
                if (lines.getOrNull(insertAt + missingImports.size)?.isBlank() == false) {
                    lines.add(insertAt + missingImports.size, "")
                }
            }
        }

        while (lines.isNotEmpty() && lines.last().isBlank()) lines.removeAt(lines.size - 1)
        lines.add("")
        lines.add(declarations)
        return lines.joinToString("\n") + "\n"
    }

    /**
     * The stub of [expect], to be declared under [functionName].
     *
     * kotlinpoet orders what it emits as the annotations, then the context parameter, then the declaration - which is
     * the order the stub has to be read in: the two annotations say which JVM method this is, and the `JniEnv` is there
     * because a binding body cannot reach the JVM without one.
     */
    @OptIn(ExperimentalKotlinPoetApi::class)
    private fun render(expect: JniFunctionContract, functionName: String): FunSpec {
        val isCritical = expect.isCritical == true
        // The annotations in the order they are meant to be read: which JVM method this binds, what kind of native it
        // is, and the signature it is checked against.
        val annotations = listOfNotNull(
            binding(expect),
            AnnotationSpec.builder(JNI_CRITICAL_NATIVE).build().takeIf { isCritical },
            withJvmSignature(expect),
        )
        val builder = FunSpec.builder(functionName)
        annotations.forEach(builder::addAnnotation)

        if (isCritical) {
            return builder
                .addParameters(criticalParameters(expect))
                .returns(criticalScalarType(expect.returnTypeName))
                .addStatement("return TODO()")
                .build()
        }

        return builder
            .contextParameter("env", JNI_ENV)
            // A `JClass` receiver is what makes the producer see the actual as static, a `JObject` receiver as an
            // instance method; a top-level function would leave the stasis unstated and only "not contradict" the
            // expect.
            .receiver(if (expect.isStatic == true) JNI_CLASS else JNI_OBJECT)
            .addParameters(
                expect.parameterTypeNames.mapIndexed { index, jvmType ->
                    ParameterSpec.builder("p$index", kotlinTypeOf(jvmType)).build()
                }
            )
            .returns(kotlinTypeOf(expect.returnTypeName))
            .addStatement("return TODO()")
            .build()
    }

    /**
     * The annotation that binds the stub to one specific JVM method rather than to any function of its shape.
     */
    private fun binding(expect: JniFunctionContract): AnnotationSpec =
        AnnotationSpec.builder(JNI_ACTUAL)
            .addMember("className = %S", expect.className)
            .addMember("methodName = %S", expect.methodName)
            .build()

    /**
     * The JVM signature of the expect, spelled out rather than derived: it is what the stub is checked against, and a
     * `@JniActual` deriving it from the Kotlin types would take the `TODO()` body's `Nothing` for the return type,
     * which is not one of the native types the producer accepts.
     */
    private fun withJvmSignature(expect: JniFunctionContract): AnnotationSpec =
        AnnotationSpec.builder(WITH_JVM_SIGNATURE)
            .addMember("parameterTypes = %L", codeStringArray(expect.parameterTypeNames))
            .addMember("returnType = %S", expect.returnTypeName)
            .build()

    /**
     * The parameters of a `@CriticalNative` stub: a primitive array arrives as a `(length, pointer)` pair, everything
     * else as the primitive itself.
     */
    private fun criticalParameters(expect: JniFunctionContract): List<ParameterSpec> =
        expect.parameterTypeNames.flatMapIndexed { index, jvmType ->
            val name = "p$index"
            val varType = criticalVarType(jvmType)
            if (varType != null) {
                listOf(
                    ParameterSpec.builder("${name}Length", INT).build(),
                    ParameterSpec.builder(name, ARRAY_POINTER.parameterizedBy(varType)).build(),
                )
            } else {
                listOf(ParameterSpec.builder(name, criticalScalarType(jvmType)).build())
            }
        }

    companion object {
        /**
         * The Kotlin type a JVM type is expressed with in a generated actual.
         *
         * A primitive and its array have an exact native counterpart, `java.lang.String` and `java.lang.Object` have
         * well-known ones, and everything else - any other reference type, any array of a reference type, any
         * multi-dimensional array - is bound as a plain `JObject`/`JObjectArray`, which is all JNI needs to pass it
         * along. There is deliberately no nullable variant: a `JObject` is already a `CPointer` and therefore already
         * nullable, and a primitive cannot be null at all. `void` is the one type with no counterpart on the native
         * side, and a `Unit` return is what it becomes.
         */
        internal fun kotlinTypeOf(jvmType: String): ClassName = when (jvmType) {
            "void" -> UNIT
            "boolean" -> ClassName(TYPES_PACKAGE, "JBoolean")
            "byte" -> ClassName(TYPES_PACKAGE, "JByte")
            "char" -> ClassName(TYPES_PACKAGE, "JChar")
            "short" -> ClassName(TYPES_PACKAGE, "JShort")
            "int" -> ClassName(TYPES_PACKAGE, "JInt")
            "long" -> ClassName(TYPES_PACKAGE, "JLong")
            "float" -> ClassName(TYPES_PACKAGE, "JFloat")
            "double" -> ClassName(TYPES_PACKAGE, "JDouble")
            "java.lang.String" -> ClassName(TYPES_PACKAGE, "JString")
            "java.lang.Object" -> JNI_OBJECT
            "boolean[]" -> ClassName(TYPES_PACKAGE, "JBooleanArray")
            "byte[]" -> ClassName(TYPES_PACKAGE, "JByteArray")
            "char[]" -> ClassName(TYPES_PACKAGE, "JCharArray")
            "short[]" -> ClassName(TYPES_PACKAGE, "JShortArray")
            "int[]" -> ClassName(TYPES_PACKAGE, "JIntArray")
            "long[]" -> ClassName(TYPES_PACKAGE, "JLongArray")
            "float[]" -> ClassName(TYPES_PACKAGE, "JFloatArray")
            "double[]" -> ClassName(TYPES_PACKAGE, "JDoubleArray")
            "java.lang.Object[]" -> ClassName(TYPES_PACKAGE, "JObjectArray")
            // Any other array, including a multi-dimensional one: an array of arrays is an object rather than a
            // primitive array, so `JObjectArray` is the closest type able to carry it.
            else -> if (jvmType.endsWith("[]")) ClassName(TYPES_PACKAGE, "JObjectArray") else JNI_OBJECT
        }

        /**
         * The `kotlinx.cinterop` variable type a critical-native array parameter of JVM type
         * [jvmType] carries, or `null` when [jvmType] is not a primitive array. `boolean` is
         * `UByteVar`, matching the `jboolean` binary representation of its elements.
         */
        internal fun criticalVarType(jvmType: String): ClassName? = when (jvmType) {
            "boolean[]" -> ClassName(INTEROP_PACKAGE, "UByteVar")
            "byte[]" -> ClassName(INTEROP_PACKAGE, "ByteVar")
            "char[]" -> ClassName(INTEROP_PACKAGE, "UShortVar")
            "short[]" -> ClassName(INTEROP_PACKAGE, "ShortVar")
            "int[]" -> ClassName(INTEROP_PACKAGE, "IntVar")
            "long[]" -> ClassName(INTEROP_PACKAGE, "LongVar")
            "float[]" -> ClassName(INTEROP_PACKAGE, "FloatVar")
            "double[]" -> ClassName(INTEROP_PACKAGE, "DoubleVar")
            else -> null
        }

        /**
         * The Kotlin primitive a critical-native parameter or return of JVM type [jvmType] is
         * declared with. A critical native only ever deals in primitives and primitive arrays, so
         * anything else is a contract the producer rejects.
         */
        internal fun criticalScalarType(jvmType: String): ClassName = when (jvmType) {
            "void" -> UNIT
            "boolean" -> ClassName("kotlin", "Boolean")
            "byte" -> ClassName("kotlin", "Byte")
            "char" -> ClassName("kotlin", "Char")
            "short" -> ClassName("kotlin", "Short")
            "int" -> INT
            "long" -> ClassName("kotlin", "Long")
            "float" -> ClassName("kotlin", "Float")
            "double" -> ClassName("kotlin", "Double")
            else -> throw IllegalArgumentException(
                "A @CriticalNative only binds primitives and primitive arrays, not '$jvmType'"
            )
        }
    }
}

/**
 * `["int", "java.lang.String"]`, the way `@WithJvmSignature` takes a JVM signature.
 */
private fun codeStringArray(values: List<String>): CodeBlock =
    CodeBlock.builder().add("[").apply {
        values.forEachIndexed { index, value ->
            if (index > 0) add(", ")
            add("%S", value)
        }
    }.add("]").build()

/**
 * The qualified names the lines of a Kotlin file import.
 */
private fun List<String>.imports(): Set<String> =
    filter { it.startsWith(IMPORT_PREFIX) }
        .mapTo(linkedSetOf()) { it.removePrefix(IMPORT_PREFIX).trim() }

/**
 * The name with the backticks a keyword is written with taken off, so that two spellings of one name compare equal.
 */
private fun String.unescaped(): String = replace("`", "")

/**
 * A variant of [base] that is not in [used] and is marked as used, or `null` when every variant is taken.
 *
 * What is compared is the JVM name, because kotlinpoet escapes a name that is not an identifier rather than changing
 * it: a method called `get-value` is declared as `` `get-value` ``, which no other name can collide with.
 */
private fun uniqueName(base: String, used: MutableSet<String>): String? {
    if (used.add(base)) return base
    for (suffix in 2..MAX_NAME_VARIANTS) {
        if (used.add(base + suffix)) return base + suffix
    }
    return null
}

private const val MAX_NAME_VARIANTS = 100

private val actualAnnotation = Regex("""@JniActuals?\s*(\([^()]*\))?""")
private val classNameArgument = Regex("""\bclassName\s*=\s*"([^"]*)"""")
private val methodNameArgument = Regex("""\bmethodName\s*=\s*"([^"]*)"""")
private val functionDeclaration = Regex("""\bfun\s+(?:<[^>]*>\s*)?(?:[\w?.]+\.)?(`?[\w-]+`?)\s*\(""")

/**
 * The `(className, methodName)` pairs of the actuals already declared in [text].
 *
 * This is the precise check for "the user already wrote this one", and it is what makes the generator safe to re-run
 * against a tree it has already written into, or against a source file that predates the `actuals.json` it consults.
 */
private fun declaredActuals(text: String): Set<Pair<String, String>> = buildSet {
    for (annotation in actualAnnotation.findAll(text)) {
        val arguments = annotation.groupValues[1]
        val className = classNameArgument.find(arguments)?.groupValues?.get(1) ?: continue
        val methodName = methodNameArgument.find(arguments)?.groupValues?.get(1) ?: continue
        add(className to methodName)
    }
}

/**
 * The names of every function declared in [text], whatever it is an actual of.
 */
private fun declaredFunctionNames(text: String): Set<String> =
    functionDeclaration.findAll(text)
        .mapTo(linkedSetOf()) { it.groupValues[1].removeSurrounding("`") }