package io.github.mimimishkin.jni.binding.plugin.consumer

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ExperimentalKotlinPoetApi
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeAliasSpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.annotated
import java.io.File

private const val TYPES_PACKAGE = "io.github.mimimishkin.jni.binding"
private const val ANNOTATIONS_PACKAGE = "$TYPES_PACKAGE.annotation"
private const val GENERATED_TYPES_PACKAGE = "$TYPES_PACKAGE.generated"
private const val GENERATED_TYPES_FILE = "JniTypes"
private const val INTEROP_PACKAGE = "kotlinx.cinterop"
private const val IMPORT_PREFIX = "import "
private const val PACKAGE_PREFIX = "package "

private val JNI_ENV = ClassName(TYPES_PACKAGE, "JniEnv")
private val JNI_CLASS = ClassName(TYPES_PACKAGE, "JClass")
private val JNI_ACTUAL = ClassName(ANNOTATIONS_PACKAGE, "JniActual")
private val JNI_CRITICAL_NATIVE = ClassName(ANNOTATIONS_PACKAGE, "CriticalNative")
private val WITH_JVM_TYPE = ClassName(ANNOTATIONS_PACKAGE, "WithJvmType")
private val JREF = ClassName(TYPES_PACKAGE, "JRef")
private val JOBJECT_OPAQUE = ClassName(TYPES_PACKAGE, "_jobject")
private val JOBJECT_ARRAY = ClassName(TYPES_PACKAGE, "JObjectArray")
private val ARRAY_POINTER = ClassName(INTEROP_PACKAGE, "CArrayPointer")
private val NATIVE_PTR = ClassName(INTEROP_PACKAGE, "NativePtr")

/**
 * A JVM reference type `jni-binding` already names, and the opaque type its `JRef` points at. Such a type is used
 * as-is rather than through a generated typealias, and an opaque type descending from one stops there.
 */
private class WellKnownType(val carrier: ClassName, val opaque: ClassName)

private val WELL_KNOWN_TYPES: Map<String, WellKnownType> = mapOf(
    "java.lang.Object" to WellKnownType(ClassName(TYPES_PACKAGE, "JObject"), JOBJECT_OPAQUE),
    "java.lang.String" to WellKnownType(ClassName(TYPES_PACKAGE, "JString"), ClassName(TYPES_PACKAGE, "_jstring")),
    "java.lang.Class" to WellKnownType(ClassName(TYPES_PACKAGE, "JClass"), ClassName(TYPES_PACKAGE, "_jclass")),
    "java.lang.Throwable" to WellKnownType(ClassName(TYPES_PACKAGE, "JThrowable"), ClassName(TYPES_PACKAGE, "_jthrowable")),
)

private val PRIMITIVE_JVM_TYPES = setOf("void", "boolean", "byte", "char", "short", "int", "long", "float", "double")
private val PRIMITIVE_ARRAY_JVM_TYPES =
    setOf("boolean[]", "byte[]", "char[]", "short[]", "int[]", "long[]", "float[]", "double[]")

/** Arrays `jni-binding` already names: their typealias would be a no-op re-spelling of `JObjectArray`. */
private val KNOWN_ARRAY_JVM_TYPES: Set<String> = PRIMITIVE_ARRAY_JVM_TYPES + "java.lang.Object[]"

/**
 * Package prefixes left out of a generated name, so `java.util.List` becomes `JList` and `kotlin.uuid.Uuid` becomes
 * `JUuid` rather than spelling the package out.
 */
private val WELL_KNOWN_PACKAGES = listOf("java.", "kotlin.")

/** The boxed classes of the JVM primitives, whose arrays a generated name marks with a `Ref`. */
private val PRIMITIVE_WRAPPER_JVM_TYPES = setOf(
    "java.lang.Boolean",
    "java.lang.Byte",
    "java.lang.Character",
    "java.lang.Short",
    "java.lang.Integer",
    "java.lang.Long",
    "java.lang.Float",
    "java.lang.Double",
)

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
        /** Files that were written to, one per class that got something added, plus the shared type file. */
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
        val registry = TypeRegistry.of(expects)
        val typesFile = writeTypes(registry)
        val classes = expects.groupBy { it.className }.map { (className, classExpects) ->
            writeClass(className, classExpects, registry)
        }
        return Result(
            files = buildList {
                addAll(classes.filter { it.written > 0 }.map { it.file })
                typesFile?.let(::add)
            },
            written = classes.sumOf { it.written },
            alreadyDeclared = classes.sumOf { it.alreadyDeclared },
        )
    }

    /**
     * Writes the shared file of generated reference types, or `null` when every declaration it would hold is
     * already there.
     */
    private fun writeTypes(registry: TypeRegistry): File? {
        val directory = sourceRoot.resolve(GENERATED_TYPES_PACKAGE.replace('.', '/'))
        val file = directory.resolve("$GENERATED_TYPES_FILE.kt")
        val existingText = file.takeIf(File::isFile)?.readText()
        val already = existingText?.let(::declaredTypeNames).orEmpty()

        val specs = buildList {
            for (jvmName in registry.classAliases.keys.sorted()) {
                val alias = registry.classAliases.getValue(jvmName)
                if (alias.simpleName !in already) add(classTypeAlias(jvmName, alias, registry.classOpaques.getValue(jvmName)))
                val opaque = registry.classOpaques.getValue(jvmName)
                if (opaque.simpleName !in already) add(classOpaque(jvmName, opaque, registry.parentOpaque(jvmName)))
            }
            for (jvmName in registry.arrayAliases.keys.sorted()) {
                val alias = registry.arrayAliases.getValue(jvmName)
                if (alias.simpleName !in already) add(arrayTypeAlias(jvmName, alias))
            }
        }
        if (specs.isEmpty()) return null

        val builder = FileSpec.builder(GENERATED_TYPES_PACKAGE, GENERATED_TYPES_FILE)
            .indent(" ".repeat(4))
            .addKotlinDefaultImports(includeJvm = false, includeJs = false)
        for (spec in specs) when (spec) {
            is TypeAliasSpec -> builder.addTypeAlias(spec)
            is TypeSpec -> builder.addType(spec)
        }
        val rendered = builder.build()
        directory.mkdirs()
        file.writeText(existingText?.let { appendTo(it, rendered) } ?: rendered.toString())
        return file
    }

    private fun classTypeAlias(jvmName: String, alias: ClassName, opaque: ClassName): TypeAliasSpec =
        TypeAliasSpec.builder(
            alias.simpleName,
            JREF.parameterizedBy(opaque).annotated(withJvmType(jvmName)),
        ).build()

    private fun classOpaque(jvmName: String, opaque: ClassName, parent: ClassName): TypeSpec =
        TypeSpec.classBuilder(opaque.simpleName)
            // `open` because a typealias' opaque may in turn be extended by the opaque of a subclass.
            .addModifiers(KModifier.OPEN)
            .primaryConstructor(FunSpec.constructorBuilder().addParameter("rawPtr", NATIVE_PTR).build())
            .superclass(parent)
            .addSuperclassConstructorParameter("rawPtr")
            .build()

    private fun arrayTypeAlias(jvmName: String, alias: ClassName): TypeAliasSpec =
        TypeAliasSpec.builder(alias.simpleName, JOBJECT_ARRAY.annotated(withJvmType(jvmName))).build()

    private fun withJvmType(jvmName: String): AnnotationSpec =
        AnnotationSpec.builder(WITH_JVM_TYPE).addMember("%S", jvmName).build()

    private fun writeClass(
        className: String,
        expects: List<JniFunctionContract>,
        registry: TypeRegistry,
    ): ClassResult {
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
            uniqueName(expect.methodName, usedNames)?.let { render(expect, it, registry) }
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
     * the order the stub has to be read in: the annotation says which JVM method this is, and the `JniEnv` is there
     * because a binding body cannot reach the JVM without one.
     */
    @OptIn(ExperimentalKotlinPoetApi::class)
    private fun render(expect: JniFunctionContract, functionName: String, registry: TypeRegistry): FunSpec {
        val isCritical = expect.isCritical == true
        val builder = FunSpec.builder(functionName)
        builder.addAnnotation(binding(expect))
        if (isCritical) builder.addAnnotation(AnnotationSpec.builder(JNI_CRITICAL_NATIVE).build())

        if (isCritical) {
            return builder
                .addParameters(criticalParameters(expect))
                .returns(criticalScalarType(expect.returnTypeName))
                .addStatement("return TODO()")
                .build()
        }

        return builder
            .contextParameter("env", JNI_ENV)
            // A `JClass` receiver is what makes the producer see the actual as static; an instance one is the owner's
            // own opaque type, which descends from `_jobject` so the producer still reads it as an instance method.
            .receiver(if (expect.isStatic == true) JNI_CLASS else registry.typeName(expect.className))
            .addParameters(
                expect.parameterTypeNames.mapIndexed { index, jvmType ->
                    ParameterSpec.builder(expect.parameterName(index), registry.typeName(jvmType)).build()
                }
            )
            .returns(registry.typeName(expect.returnTypeName))
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
     * The parameters of a `@CriticalNative` stub: a primitive array arrives as a `(length, pointer)` pair, everything
     * else as the primitive itself.
     */
    private fun criticalParameters(expect: JniFunctionContract): List<ParameterSpec> =
        expect.parameterTypeNames.flatMapIndexed { index, jvmType ->
            val name = expect.parameterName(index)
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
         * The Kotlin type a JVM type is expressed with when no generated typealias covers it.
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
            "java.lang.Object" -> ClassName(TYPES_PACKAGE, "JObject")
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
            else -> if (jvmType.endsWith("[]")) {
                ClassName(TYPES_PACKAGE, "JObjectArray")
            } else {
                ClassName(TYPES_PACKAGE, "JObject")
            }
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
 * The reference types one run of the writer has to declare, and the `@WithJvmType` names they stand for.
 */
private class TypeRegistry(
    /** JVM class name to the generated typealias used for it. */
    val classAliases: Map<String, ClassName>,
    /** JVM class name to the opaque type its typealias points at. */
    val classOpaques: Map<String, ClassName>,
    /** JVM array name to the generated typealias used for it. */
    val arrayAliases: Map<String, ClassName>,
    /** JVM class name to the JVM name of the class it directly extends, or `null` for a root. */
    private val superClasses: Map<String, String?>,
) {
    /**
     * The Kotlin type [jvmType] is expressed with: a generated typealias, a well-known `jni-binding` type, or, for a
     * primitive or an unbounded fallback, the plain native type.
     */
    fun typeName(jvmType: String): TypeName =
        classAliases[jvmType]
            ?: arrayAliases[jvmType]
            ?: WELL_KNOWN_TYPES[jvmType]?.carrier
            ?: JniActualsStubWriter.kotlinTypeOf(jvmType)

    /**
     * The opaque type the opaque of [jvmName] extends: the well-known opaque its superclass maps to, the generated
     * opaque of its superclass, or `_jobject` when the hierarchy is unknown.
     */
    fun parentOpaque(jvmName: String): ClassName {
        val parent = superClasses[jvmName] ?: return JOBJECT_OPAQUE
        return WELL_KNOWN_TYPES[parent]?.opaque ?: classOpaques[parent] ?: JOBJECT_OPAQUE
    }

    companion object {
        fun of(expects: List<JniFunctionContract>): TypeRegistry {
            val aliases = linkedMapOf<String, ClassName>()
            val opaques = linkedMapOf<String, ClassName>()
            val arrays = linkedMapOf<String, ClassName>()

            fun ensureClass(name: String) {
                if (name in WELL_KNOWN_TYPES || aliases.containsKey(name)) return
                aliases[name] = ClassName(GENERATED_TYPES_PACKAGE, typeAliasName(name))
                opaques[name] = ClassName(GENERATED_TYPES_PACKAGE, opaqueName(name))
            }

            val referenced = linkedSetOf<String>()
            for (expect in expects) {
                referenced += expect.className
                referenced += expect.parameterTypeNames
                referenced += expect.returnTypeName
            }
            for (name in referenced) {
                when {
                    name in PRIMITIVE_JVM_TYPES -> {}
                    name.endsWith("[]") -> if (name !in KNOWN_ARRAY_JVM_TYPES) {
                        arrays[name] = ClassName(GENERATED_TYPES_PACKAGE, typeAliasName(name))
                    }
                    else -> ensureClass(name)
                }
            }

            val superClasses = HashMap<String, String?>()
            for (expect in expects) superClasses.putAll(expect.superClasses)

            // The opaque types a stub's receiver is smart-castable to are only half the story: every superclass in the
            // recorded chain has to exist as a type too, down to a well-known opaque.
            val queue = ArrayDeque(aliases.keys)
            while (queue.isNotEmpty()) {
                val name = queue.removeFirst()
                val parent = superClasses[name] ?: continue
                if (parent in WELL_KNOWN_TYPES) continue
                val before = aliases.size
                ensureClass(parent)
                if (aliases.size > before) queue.add(parent)
            }

            return TypeRegistry(aliases, opaques, arrays, superClasses)
        }

        /**
         * The name of the typealias generated for [jvmName].
         *
         * A leading `J` marks it as a JVM type and the rest is the JVM name in PascalCase, with a
         * [well-known package][WELL_KNOWN_PACKAGES] left out, so `java.util.List` is `JList` rather than
         * `JJavaUtilList`. An array appends a suffix to its element's name: `RefArray` for the boxed primitives,
         * where the `Ref` tells `java.lang.Integer[]` (`JIntegerRefArray`) from the primitive `int[]`
         * (`JIntArray`), and `Array` for every other reference type.
         */
        private fun typeAliasName(jvmName: String): String {
            val element = jvmName.removeSuffix("[]")
            val suffix = when {
                element == jvmName -> ""
                element in PRIMITIVE_WRAPPER_JVM_TYPES -> "RefArray"
                else -> "Array"
            }
            return "J" + pascalCase(element.withoutWellKnownPackage()) + suffix
        }

        /** The opaque type a typealias of [jvmName] points at, under a name no JVM type can produce. */
        private fun opaqueName(jvmName: String): String = "_" + snakeCase(typeAliasName(jvmName))

        private fun String.withoutWellKnownPackage(): String =
            if (WELL_KNOWN_PACKAGES.any(::startsWith)) substringAfterLast('.') else this

        /**
         * [name] with a `_` inserted at every word boundary and the result lower-cased, so `JComExampleNative`
         * becomes `j_com_example_native`. A run of capitals is one word, so a `JURL` stays `jurl` rather than
         * `j_u_r_l`.
         */
        private fun snakeCase(name: String): String = buildString {
            for ((index, c) in name.withIndex()) {
                if (c.isUpperCase()) {
                    val previous = name.getOrNull(index - 1)
                    val next = name.getOrNull(index + 1)
                    val startsWord = previous?.isLowerCase() == true || previous?.isDigit() == true || next?.isLowerCase() == true
                    if (index > 0 && startsWord) append('_')
                    append(c.lowercaseChar())
                } else {
                    append(c)
                }
            }
        }

        private fun pascalCase(name: String): String = buildString {
            var capitalize = true
            for (c in name) {
                if (c.isLetterOrDigit()) {
                    append(if (capitalize) c.uppercaseChar() else c)
                    capitalize = false
                } else {
                    capitalize = true
                }
            }
        }
    }
}

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
private val typeDeclaration = Regex("""\b(?:typealias|class)\s+([A-Za-z_]\w*)""")

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

/**
 * The names of every typealias and class declared in [text], so a re-run of the generator adds only what is missing
 * to the shared type file instead of redeclaring it.
 */
private fun declaredTypeNames(text: String): Set<String> =
    typeDeclaration.findAll(text)
        .mapTo(linkedSetOf()) { it.groupValues[1] }
