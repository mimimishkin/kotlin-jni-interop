package org.windowsregistry

import io.github.mimimishkin.jni.binding.annotation.JniExpects
import io.github.mimimishkin.jni.binding.annotation.LoadMethod
import java.lang.System.mapLibraryName
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.io.path.absolutePathString

/**
 * High-level JNI bindings for the Windows Registry.
 *
 * The low-level API (raw numeric handles, `HKEY_*`/`KEY_*`/`REG_*` constants and the win32 externals) is kept
 * private; the public surface is the [RootKey], [Access] and [ValueType] enums plus the [Key] wrapper, which
 * exposes a small type-safe DSL (`key["Name"] = "Jon Doe"`, [Key.use] for scoped access, etc.).
 *
 * ```
 * Registry.create(RootKey.CURRENT_USER, "Software\\MyApp").use { key ->
 *     key["Name"] = "Jon Doe"
 *     key["Path"] = "%USERPROFILE%"        // REG_SZ
 *     key["Int"] = -42                     // REG_DWORD
 *     key["Long"] = 9_223_372_036_854_775_807L  // REG_QWORD
 *     key["Blob"] = byteArrayOf(0x00, 0x01, -1) // REG_BINARY
 *     println(key.getString("Name"))
 * }
 * Registry.delete(RootKey.CURRENT_USER, "Software\\MyApp")
 * ```
 *
 * Only 64-bit Windows is supported, so the native library is loaded from a fixed resource path; no `os`/`arch`
 * probing is needed.
 */
@JniExpects(targets = ["mingwX64"])
object Registry {

    /**
     * A predefined root key (`HKEY_*`), sign-extended to the `Long` handle the native side expects.
     */
    enum class RootKey(val handle: Long) {
        CLASSES_ROOT(-2147483648L),
        CURRENT_USER(-2147483647L),
        LOCAL_MACHINE(-2147483646L),
        USERS(-2147483645L),
        CURRENT_CONFIG(-2147483643L),
    }

    /** Access rights for opening a key (`KEY_*`), as understood by [open]. */
    enum class Access(val mask: Int) {
        READ(0x20019),
        WRITE(0x20006),
        ALL(0x00F003F),
    }

    /** Registry value types (`REG_*`). */
    enum class ValueType(val raw: Int) {
        NONE(0),
        SZ(1),
        EXPAND_SZ(2),
        BINARY(3),
        DWORD(4),
        QWORD(11),
        ;

        companion object {
            /** The [ValueType] for a native numeric [raw] type, or [NONE] if it is unknown. */
            fun of(raw: Int): ValueType = entries.find { it.raw == raw } ?: NONE
        }
    }

    @Suppress("UnsafeDynamicallyLoadedCode")
    @LoadMethod
    private fun load() {
        val libPath = "/natives/${mapLibraryName("native")}"
        val libStream = Registry::class.java.getResourceAsStream(libPath)
            ?: error("No native library found at $libPath")
        val outputPath = Files.createTempFile(null, libPath.substringAfterLast('/'))
        libStream.use { input ->
            Files.copy(input, outputPath, StandardCopyOption.REPLACE_EXISTING)
        }
        outputPath.toFile().deleteOnExit()
        System.load(outputPath.absolutePathString())
    }

    /**
     * Opens an existing key with the requested [access]. Returns an open [Key], or `null` if the key is missing
     * or cannot be opened.
     */
    fun open(root: RootKey, subKeyPath: String, access: Access = Access.READ): Key? =
        openKey(root.handle, subKeyPath, access.mask).takeUnless { it == 0L }?.let(::Key)

    /**
     * Creates (or opens) a key, granting it full access. Returns an open [Key].
     *
     * @throws IllegalArgumentException if the key cannot be created.
     */
    fun create(root: RootKey, subKeyPath: String): Key {
        val handle = createKey(root.handle, subKeyPath)
        require(handle != 0L) { "Cannot create registry key: ${root.name}\\$subKeyPath" }
        return Key(handle)
    }

    /**
     * Recursively deletes [subKeyPath] and all of its subkeys and values. Returns `true` on success, `false` if
     * the key is missing or cannot be deleted.
     */
    fun delete(root: RootKey, subKeyPath: String): Boolean = deleteKey(root.handle, subKeyPath)

    /** Runs [block] within [Registry] so the API can be used without a qualifier. */
    inline fun <R> registry(block: Registry.() -> R): R = block()

    /**
     * An open handle to a registry key. Must be [close]d; use [use] for scoped access.
     *
     * @property handle the raw native key handle, retained for low-level reuse.
     */
    class Key internal constructor(@JvmField internal val handle: Long) : AutoCloseable {

        /** Closes the key. No-op on an already-closed key. */
        override fun close() {
            closeKey(handle)
        }

        /** Writes a string value of type [ValueType.SZ], or [ValueType.EXPAND_SZ] when [expand] is `true`. */
        fun setString(name: String, value: String, expand: Boolean = false): Boolean =
            setStringValue(handle, name, value, expand)

        /** Writes an [Int] value as [ValueType.DWORD]. */
        fun setInt(name: String, value: Int): Boolean = setIntValue(handle, name, value)

        /** Writes a [Long] value as [ValueType.QWORD]. */
        fun setLong(name: String, value: Long): Boolean = setLongValue(handle, name, value)

        /** Writes a byte array as [ValueType.BINARY]. */
        fun setBinary(name: String, value: ByteArray): Boolean = setBinaryValue(handle, name, value)

        /** Reads a string value, or `null` if it is missing or not string-typed. */
        fun getString(name: String): String? = getStringValue(handle, name)

        /** Reads a [ValueType.DWORD] value, or `0` if it is missing. */
        fun getInt(name: String): Int = getIntValue(handle, name)

        /** Reads a [ValueType.QWORD] value, or `0` if it is missing. */
        fun getLong(name: String): Long = getLongValue(handle, name)

        /** Reads a [ValueType.BINARY] value, or `null` if it is missing. */
        fun getBinary(name: String): ByteArray? = getBinaryValue(handle, name)

        /** The type of the value, or [ValueType.NONE] if it is missing. */
        fun valueType(name: String): ValueType = ValueType.of(getValueType(handle, name))

        /** The names of all values stored directly in this key. */
        fun values(): List<String> = enumerateValues(handle).toList()

        /** The names of all direct subkeys of this key. */
        fun subKeys(): List<String> = enumerateSubKeys(handle).toList()

        /** Deletes the value with the given [name]. Returns `true` if it existed and was removed. */
        fun deleteValue(name: String): Boolean = deleteValue(handle, name)

        /** Flushes the key's pending changes to disk. */
        fun flush(): Boolean = flushKey(handle)

        /** Writes a string value as [ValueType.SZ] via the DSL: `key["Name"] = "Jon Doe"`. */
        operator fun set(name: String, value: String): Boolean = setStringValue(handle, name, value, false)

        /** Writes an [Int] value as [ValueType.DWORD] via the DSL: `key["Count"] = 7`. */
        operator fun set(name: String, value: Int): Boolean = setIntValue(handle, name, value)

        /** Writes a [Long] value as [ValueType.QWORD] via the DSL: `key["Total"] = 7L`. */
        operator fun set(name: String, value: Long): Boolean = setLongValue(handle, name, value)

        /** Writes a byte array as [ValueType.BINARY] via the DSL: `key["Blob"] = bytes`. */
        operator fun set(name: String, value: ByteArray): Boolean = setBinaryValue(handle, name, value)

        /** Reads a string value via the DSL: `key["Name"]`. */
        operator fun get(name: String): String? = getStringValue(handle, name)
    }

    /**
     * Opens an existing key with the requested [access]. Returns an open [Key], or `null` if the key is missing
     * or cannot be opened.
     */
    private external fun openKey(rootKey: Long, subKeyPath: String, access: Int): Long

    /**
     * Creates (or opens) a key. Returns its handle, or `0` on failure.
     */
    private external fun createKey(rootKey: Long, subKeyPath: String): Long

    private external fun closeKey(handle: Long): Boolean

    /**
     * Recursively deletes [subKeyPath] and all of its subkeys and values.
     */
    private external fun deleteKey(rootKey: Long, subKeyPath: String): Boolean

    private external fun deleteValue(handle: Long, valueName: String): Boolean

    private external fun flushKey(handle: Long): Boolean

    /**
     * Writes a string value of type [ValueType.SZ] (or [ValueType.EXPAND_SZ] when [expand] is true).
     */
    private external fun setStringValue(handle: Long, valueName: String, value: String, expand: Boolean): Boolean

    /**
     * Reads a string value. Returns `null` if it is missing or not string-typed.
     */
    private external fun getStringValue(handle: Long, valueName: String): String?

    private external fun setIntValue(handle: Long, valueName: String, value: Int): Boolean

    /**
     * Reads a [ValueType.DWORD] value. Returns `0` if it is missing.
     */
    private external fun getIntValue(handle: Long, valueName: String): Int

    private external fun setLongValue(handle: Long, valueName: String, value: Long): Boolean

    /**
     * Reads a [ValueType.QWORD] value. Returns `0` if it is missing.
     */
    private external fun getLongValue(handle: Long, valueName: String): Long

    private external fun setBinaryValue(handle: Long, valueName: String, value: ByteArray): Boolean

    /**
     * Reads a [ValueType.BINARY] value. Returns `null` if it is missing.
     */
    private external fun getBinaryValue(handle: Long, valueName: String): ByteArray?

    /**
     * Returns the [ValueType] of the value, or [ValueType.NONE] if it is missing.
     */
    private external fun getValueType(handle: Long, valueName: String): Int

    private external fun enumerateSubKeys(handle: Long): Array<String>

    private external fun enumerateValues(handle: Long): Array<String>
}