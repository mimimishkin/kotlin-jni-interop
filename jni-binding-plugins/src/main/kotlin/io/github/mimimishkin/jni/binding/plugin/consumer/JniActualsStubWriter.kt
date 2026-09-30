package io.github.mimimishkin.jni.binding.plugin.consumer

import java.io.File

private const val TYPES_PACKAGE = "io.github.mimimishkin.jni.binding"
private const val ANNOTATIONS_PACKAGE = "$TYPES_PACKAGE.annotation"
private const val ENV_TYPE = "JniEnv"

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
        val stubs = own
            .mapNotNull { expect -> uniqueName(kotlinFunctionName(expect.methodName), usedNames)?.let { render(expect, it) } }
        val alreadyDeclaredCount = expects.size - own.size

        if (stubs.isNotEmpty()) {
            val imports = stubs.flatMapTo(linkedSetOf()) { it.imports }.toList()
            val body = stubs.joinToString(separator = "\n\n") { it.code }

            if (existingText == null) {
                file.parentFile?.mkdirs()
                file.writeText(newFile(packageName, imports, body))
            } else {
                file.writeText(appendTo(existingText, imports, body))
            }
        }
        return ClassResult(file, stubs.size, alreadyDeclaredCount)
    }

    /**
     * What a single class's worth of expects added to its file.
     */
    private class ClassResult(val file: File, val written: Int, val alreadyDeclared: Int)

    private fun newFile(packageName: String, imports: List<String>, body: String): String = buildString {
        if (packageName.isNotEmpty()) appendLine("package $packageName")
        appendLine()
        imports.forEach { appendLine("import $it") }
        appendLine()
        appendLine(body)
    }

    /**
     * Adds [imports] to an existing file's imports and [body] to its end, changing nothing else.
     *
     * The missing imports go into the import block where one already exists; a file with no imports at all (its
     * declarations using qualified names, say) gets them right after the package directive.
     */
    private fun appendTo(existingText: String, imports: List<String>, body: String): String {
        val lines = existingText.lines().toMutableList()
        val alreadyImported = lines.asSequence()
            .filter { it.startsWith("import ") }
            .map { it.removePrefix("import ").trim() }
            .toSet()
        val missingImports = imports
            .filterNot { it in alreadyImported }
            .map { "import $it" }

        if (missingImports.isNotEmpty()) {
            val lastImport = lines.indexOfLast { it.startsWith("import ") }
            if (lastImport >= 0) {
                lines.addAll(lastImport + 1, missingImports)
            } else {
                val packageIndex = lines.indexOfFirst { it.startsWith("package ") }
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
        lines.add(body)
        return lines.joinToString("\n") + "\n"
    }

    private fun render(expect: JniFunctionContract, functionName: String): Stub {
        // A `JClass` receiver is what makes the producer see the actual as static, a `JObject` receiver as an instance
        // method; a top-level function would leave the stasis unstated and only "not contradict" the expect.
        val receiver = if (expect.isStatic == true) "JClass" else "JObject"
        val parameterTypes = expect.parameterTypeNames.map(::kotlinTypeOf)
        val returnType = kotlinTypeOf(expect.returnTypeName)

        val imports = (parameterTypes + returnType + receiver + ENV_TYPE)
            .filterNot { it == "Unit" } // kotlin.Unit needs no import
            .mapTo(linkedSetOf()) { "$TYPES_PACKAGE.$it" }
            .apply {
                add("$ANNOTATIONS_PACKAGE.JniActual")
                add("$ANNOTATIONS_PACKAGE.WithJvmSignature")
            }

        val signature = expect.parameterTypeNames.joinToString(", ") { "\"$it\"" }
        val parameters = parameterTypes.mapIndexed { index, type -> "p$index: $type" }.joinToString(", ")

        val code = buildString {
            // These two annotations carry the names, and are what makes the stub implement this specific JVM method
            // rather than just any function that happens to share its shape.
            appendLine("@JniActual(className = ${expect.className.quoted()}, methodName = ${expect.methodName.quoted()})")
            appendLine("@WithJvmSignature(")
            appendLine("    parameterTypes = [$signature],")
            appendLine("    returnType = ${expect.returnTypeName.quoted()},")
            appendLine(")")
            // `JniEnv` is what a binding body needs to reach the JVM at all: building the `JString` behind a
            // `java.lang.String` return value, for one. The producer accepts either no context parameter or a single
            // one of this type, so the stub asks for it and the user drops the line for a function that turns out not
            // to need it.
            appendLine("context(env: $ENV_TYPE)")
            // The return type is written out because `TODO()` infers `Nothing` without it, which is not one of the
            // native types the producer accepts as a bound signature.
            append("fun $receiver.$functionName($parameters): $returnType = TODO()")
        }
        return Stub(functionName, code, imports.toList())
    }

    private class Stub(val functionName: String, val code: String, val imports: List<String>)

    companion object {
        /**
         * The Kotlin type a JVM type is expressed with in a generated actual.
         *
         * A primitive and its array have an exact native counterpart, `java.lang.String` and `java.lang.Object` have
         * well-known ones, and everything else - any other reference type, any array of a reference type, any
         * multi-dimensional array - is bound as a plain `JObject`/`JObjectArray`, which is all JNI needs to pass it
         * along. There is deliberately no nullable variant: a `JObject` is already a `CPointer` and therefore already
         * nullable, and a primitive cannot be null at all.
         */
        internal fun kotlinTypeOf(jvmType: String): String = when (jvmType) {
            "void" -> "Unit"
            "boolean" -> "JBoolean"
            "byte" -> "JByte"
            "char" -> "JChar"
            "short" -> "JShort"
            "int" -> "JInt"
            "long" -> "JLong"
            "float" -> "JFloat"
            "double" -> "JDouble"
            "java.lang.String" -> "JString"
            "java.lang.Object" -> "JObject"
            "boolean[]" -> "JBooleanArray"
            "byte[]" -> "JByteArray"
            "char[]" -> "JCharArray"
            "short[]" -> "JShortArray"
            "int[]" -> "JIntArray"
            "long[]" -> "JLongArray"
            "float[]" -> "JFloatArray"
            "double[]" -> "JDoubleArray"
            "java.lang.Object[]" -> "JObjectArray"
            // Any other array, including a multi-dimensional one: an array of arrays is an object rather than a
            // primitive array, so `JObjectArray` is the closest type able to carry it.
            else -> if (jvmType.endsWith("[]")) "JObjectArray" else "JObject"
        }

        /**
         * The Kotlin function name to declare a stub of [methodName] under.
         *
         * A JVM name may contain characters a Kotlin identifier cannot, and may be a hard keyword; since the real name
         * travels in `@JniActual(methodName = ...)`, the declaration's own name is only ever a label and can be mangled
         * freely.
         */
        internal fun kotlinFunctionName(methodName: String): String {
            val mangled = methodName.map { if (it.isLetterOrDigit() || it == '_') it else '_' }.joinToString("")
            val identifier = mangled.ifEmpty { "_" }.let { if (it.first().isDigit()) "_$it" else it }
            return if (identifier in KOTLIN_HARD_KEYWORDS) "`$identifier`" else identifier
        }

        private val KOTLIN_HARD_KEYWORDS = setOf(
            "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface", "is",
            "null", "object", "package", "return", "super", "this", "throw", "true", "try", "typealias", "typeof",
            "val", "var", "when", "while",
        )
    }
}

/**
 * A variant of [base] that is not in [used] and is marked as used, or `null` when every variant is taken.
 */
private fun uniqueName(base: String, used: MutableSet<String>): String? {
    if (used.add(base)) return base
    for (suffix in 2..MAX_NAME_VARIANTS) {
        if (used.add(base + suffix)) return base + suffix
    }
    return null
}

private const val MAX_NAME_VARIANTS = 100

private val actualAnnotation = Regex("""@JniActuals?\s*(\((?:[^()])*\))?""")
private val classNameArgument = Regex("""\bclassName\s*=\s*"([^"]*)"""")
private val methodNameArgument = Regex("""\bmethodName\s*=\s*"([^"]*)"""")
private val functionDeclaration = Regex("""\bfun\s+(?:<[^>]*>\s*)?(?:[\w?.]+\.)?(\w+)\s*\(""")

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
    functionDeclaration.findAll(text).mapTo(linkedSetOf()) { it.groupValues[1] }

private fun String.quoted(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
