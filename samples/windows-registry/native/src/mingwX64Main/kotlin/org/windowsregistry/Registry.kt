@file:OptIn(ExperimentalForeignApi::class)

package org.windowsregistry

import io.github.mimimishkin.jni.binding.JByteArray
import io.github.mimimishkin.jni.binding.JObjectArray
import io.github.mimimishkin.jni.binding.JString
import io.github.mimimishkin.jni.binding.JniEnv
import io.github.mimimishkin.jni.binding.annotation.JniActuals
import io.github.mimimishkin.jni.binding.annotation.WithJvmType
import io.github.mimimishkin.jni.binding.findClass
import io.github.mimimishkin.jni.binding.newObjectArray
import io.github.mimimishkin.jni.binding.set
import io.github.mimimishkin.jni.binding.toJArray
import io.github.mimimishkin.jni.binding.toJString
import io.github.mimimishkin.jni.binding.toKArray
import io.github.mimimishkin.jni.binding.toKString
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toLong
import kotlinx.cinterop.utf8
import kotlinx.cinterop.value
import platform.windows.ERROR_MORE_DATA
import platform.windows.ERROR_NO_MORE_ITEMS
import platform.windows.ERROR_SUCCESS
import platform.windows.HKEY__
import platform.windows.RegCloseKey
import platform.windows.RegCreateKeyExW
import platform.windows.RegDeleteTreeW
import platform.windows.RegDeleteValueW
import platform.windows.RegEnumKeyExW
import platform.windows.RegEnumValueW
import platform.windows.RegFlushKey
import platform.windows.RegOpenKeyExW
import platform.windows.RegQueryValueExW
import platform.windows.RegSetValueExW
import platform.windows.REG_BINARY
import platform.windows.REG_DWORD
import platform.windows.REG_EXPAND_SZ
import platform.windows.REG_QWORD
import platform.windows.REG_SZ
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.toKStringFromUtf16
import kotlinx.cinterop.utf16

typealias JStringArray = @WithJvmType("java.lang.String[]") JObjectArray

private const val KEY_ALL_ACCESS = 0x00F003F
private const val MAX_NAME_LENGTH = 512

@JniActuals(className = "org.windowsregistry.Registry")
object Main {

    context(env: JniEnv)
    fun openKey(rootKey: Long, subKeyPath: JString?, access: Int): Long = memScoped {
        val subKey = subKeyPath?.toKString() ?: return@memScoped 0L
        val phkResult = alloc<CPointerVar<HKEY__>>()
        val status = RegOpenKeyExW(rootKey.toCPointer(), subKey, 0u, access.toUInt(), phkResult.ptr)
        if (status == ERROR_SUCCESS) phkResult.value?.toLong() ?: 0L else 0L
    }

    context(env: JniEnv)
    fun createKey(rootKey: Long, subKeyPath: JString?): Long = memScoped {
        val subKey = subKeyPath?.toKString() ?: return@memScoped 0L
        val phkResult = alloc<CPointerVar<HKEY__>>()
        val disposition = alloc<UIntVar>()
        val status = RegCreateKeyExW(
            rootKey.toCPointer(),
            subKey,
            0u,
            null,
            0u,
            KEY_ALL_ACCESS.toUInt(),
            null,
            phkResult.ptr,
            disposition.ptr,
        )
        if (status == ERROR_SUCCESS) phkResult.value?.toLong() ?: 0L else 0L
    }

    fun closeKey(handle: Long): Boolean = memScoped {
        RegCloseKey(handle.toCPointer()) == ERROR_SUCCESS
    }

    context(env: JniEnv)
    fun deleteKey(rootKey: Long, subKeyPath: JString?): Boolean = memScoped {
        val subKey = subKeyPath?.toKString() ?: return@memScoped false
        RegDeleteTreeW(rootKey.toCPointer(), subKey) == ERROR_SUCCESS
    }

    context(env: JniEnv)
    fun deleteValue(handle: Long, valueName: JString?): Boolean = memScoped {
        val name = valueName?.toKString() ?: return@memScoped false
        RegDeleteValueW(handle.toCPointer(), name) == ERROR_SUCCESS
    }

    fun flushKey(handle: Long): Boolean = memScoped {
        RegFlushKey(handle.toCPointer()) == ERROR_SUCCESS
    }

    context(env: JniEnv)
    fun setStringValue(handle: Long, valueName: JString?, value: JString?, expand: Boolean): Boolean = memScoped {
        val name = valueName?.toKString() ?: return@memScoped false
        val content = value?.toKString() ?: return@memScoped false
        val type = if (expand) REG_EXPAND_SZ else REG_SZ
        val data = content.utf16.ptr.reinterpret<UByteVar>()
        RegSetValueExW(handle.toCPointer(), name, 0u, type.toUInt(), data, (content.length + 1).toUInt() * 2u) == ERROR_SUCCESS
    }

    context(env: JniEnv)
    fun getStringValue(handle: Long, valueName: JString?): JString? = memScoped {
        val name = valueName?.toKString() ?: return@memScoped null
        val key = handle.toCPointer<HKEY__>() ?: return@memScoped null

        val typePtr = alloc<UIntVar>()
        val sizePtr = alloc<UIntVar>()
        val sizeStatus = RegQueryValueExW(key, name, null, typePtr.ptr, null, sizePtr.ptr)
        if (sizeStatus != ERROR_SUCCESS && sizeStatus != ERROR_MORE_DATA) return@memScoped null
        if (typePtr.value.toInt() != REG_SZ && typePtr.value.toInt() != REG_EXPAND_SZ) return@memScoped null

        val byteCount = sizePtr.value.toInt()
        val data = allocArray<UByteVar>(byteCount)
        val readSize = alloc<UIntVar> { value = sizePtr.value }
        val readStatus = RegQueryValueExW(key, name, null, typePtr.ptr, data, readSize.ptr)
        if (readStatus != ERROR_SUCCESS && readStatus != ERROR_MORE_DATA) return@memScoped null

        data.reinterpret<UShortVar>().toKString().toJString()
    }

    context(env: JniEnv)
    fun setIntValue(handle: Long, valueName: JString?, value: Int): Boolean = memScoped {
        val name = valueName?.toKString() ?: return@memScoped false
        val data = allocArray<UByteVar>(4)
        var v = value.toUInt()
        for (i in 0 until 4) {
            data[i] = (v and 0xFFu).toUByte()
            v = v shr 8
        }
        RegSetValueExW(handle.toCPointer(), name, 0u, REG_DWORD.toUInt(), data, 4u) == ERROR_SUCCESS
    }

    context(env: JniEnv)
    fun getIntValue(handle: Long, valueName: JString?): Int = memScoped {
        val name = valueName?.toKString() ?: return@memScoped 0
        val key = handle.toCPointer<HKEY__>() ?: return@memScoped 0

        val typePtr = alloc<UIntVar>()
        val sizePtr = alloc<UIntVar> { value = 4u }
        val data = allocArray<UByteVar>(4)
        val status = RegQueryValueExW(key, name, null, typePtr.ptr, data, sizePtr.ptr)
        if (status != ERROR_SUCCESS || typePtr.value.toInt() != REG_DWORD || sizePtr.value.toInt() < 4) 0
        else (data[0].toInt() or (data[1].toInt() shl 8) or (data[2].toInt() shl 16) or (data[3].toInt() shl 24))
    }

    context(env: JniEnv)
    fun setLongValue(handle: Long, valueName: JString?, value: Long): Boolean = memScoped {
        val name = valueName?.toKString() ?: return@memScoped false
        val data = allocArray<UByteVar>(8)
        var v = value.toULong()
        for (i in 0 until 8) {
            data[i] = (v and 0xFFu).toUByte()
            v = v shr 8
        }
        RegSetValueExW(handle.toCPointer(), name, 0u, REG_QWORD.toUInt(), data, 8u) == ERROR_SUCCESS
    }

    context(env: JniEnv)
    fun getLongValue(handle: Long, valueName: JString?): Long = memScoped {
        val name = valueName?.toKString() ?: return@memScoped 0L
        val key = handle.toCPointer<HKEY__>() ?: return@memScoped 0L

        val typePtr = alloc<UIntVar>()
        val sizePtr = alloc<UIntVar> { value = 8u }
        val data = allocArray<UByteVar>(8)
        val status = RegQueryValueExW(key, name, null, typePtr.ptr, data, sizePtr.ptr)
        if (status != ERROR_SUCCESS || typePtr.value.toInt() != REG_QWORD || sizePtr.value.toInt() < 8) 0L
        else {
            var result = 0L
            for (i in 0 until 8) {
                result = result or (data[i].toLong() shl (8 * i))
            }
            result
        }
    }

    context(env: JniEnv)
    fun setBinaryValue(handle: Long, valueName: JString?, value: JByteArray?): Boolean = memScoped {
        val name = valueName?.toKString() ?: return@memScoped false
        val bytes = value?.toKArray() ?: return@memScoped false
        val data = allocArray<UByteVar>(bytes.size)
        for (i in bytes.indices) data[i] = bytes[i].toUByte()
        RegSetValueExW(handle.toCPointer(), name, 0u, REG_BINARY.toUInt(), data, bytes.size.toUInt()) == ERROR_SUCCESS
    }

    context(env: JniEnv)
    fun getBinaryValue(handle: Long, valueName: JString?): JByteArray? = memScoped {
        val name = valueName?.toKString() ?: return@memScoped null
        val key = handle.toCPointer<HKEY__>() ?: return@memScoped null

        val typePtr = alloc<UIntVar>()
        val sizePtr = alloc<UIntVar>()
        val sizeStatus = RegQueryValueExW(key, name, null, typePtr.ptr, null, sizePtr.ptr)
        if (sizeStatus != ERROR_SUCCESS && sizeStatus != ERROR_MORE_DATA) return@memScoped null
        if (typePtr.value.toInt() != REG_BINARY) return@memScoped null

        val byteCount = sizePtr.value.toInt()
        val data = allocArray<UByteVar>(byteCount)
        val readSize = alloc<UIntVar> { value = sizePtr.value }
        val readStatus = RegQueryValueExW(key, name, null, typePtr.ptr, data, readSize.ptr)
        if (readStatus != ERROR_SUCCESS && readStatus != ERROR_MORE_DATA) return@memScoped null

        val bytes = ByteArray(readSize.value.toInt())
        for (i in bytes.indices) bytes[i] = data[i].toByte()
        bytes.toJArray()
    }

    context(env: JniEnv)
    fun getValueType(handle: Long, valueName: JString?): Int = memScoped {
        val name = valueName?.toKString() ?: return@memScoped 0
        val key = handle.toCPointer<HKEY__>() ?: return@memScoped 0

        val typePtr = alloc<UIntVar>()
        val sizePtr = alloc<UIntVar>()
        val status = RegQueryValueExW(key, name, null, typePtr.ptr, null, sizePtr.ptr)
        if (status != ERROR_SUCCESS && status != ERROR_MORE_DATA) 0 else typePtr.value.toInt()
    }

    context(env: JniEnv)
    fun enumerateSubKeys(handle: Long): JStringArray = memScoped {
        val key = handle.toCPointer<HKEY__>() ?: return@memScoped emptyStringArray()
        val names = mutableListOf<String>()
        val nameBuf = allocArray<UShortVar>(MAX_NAME_LENGTH)
        val nameLen = alloc<UIntVar>()
        var index = 0u
        while (true) {
            nameLen.value = MAX_NAME_LENGTH.toUInt()
            val status = RegEnumKeyExW(key, index, nameBuf, nameLen.ptr, null, null, null, null)
            when (status) {
                ERROR_NO_MORE_ITEMS -> break
                ERROR_SUCCESS -> names.add(nameBuf.toKString())
            }
            index++
        }
        names.toJStringArray()
    }

    context(env: JniEnv)
    fun enumerateValues(handle: Long): JStringArray = memScoped {
        val key = handle.toCPointer<HKEY__>() ?: return@memScoped emptyStringArray()
        val names = mutableListOf<String>()
        val nameBuf = allocArray<UShortVar>(MAX_NAME_LENGTH)
        val nameLen = alloc<UIntVar>()
        val typePtr = alloc<UIntVar>()
        val dataSize = alloc<UIntVar>()
        var index = 0u
        while (true) {
            nameLen.value = MAX_NAME_LENGTH.toUInt()
            val status = RegEnumValueW(key, index, nameBuf, nameLen.ptr, null, typePtr.ptr, null, dataSize.ptr)
            when (status) {
                ERROR_NO_MORE_ITEMS -> break
                ERROR_SUCCESS -> names.add(nameBuf.toKString())
            }
            index++
        }
        names.toJStringArray()
    }

    context(env: JniEnv)
    private fun List<String>.toJStringArray(): JStringArray = memScoped {
        val elementClass = findClass("java/lang/String".utf8) ?: return@memScoped emptyObjectArray()
        val array = newObjectArray(size, elementClass) ?: return@memScoped emptyObjectArray()
        for (i in indices) {
            array[i] = this@toJStringArray[i].toJString()
        }
        array
    }

    context(env: JniEnv)
    private fun emptyStringArray(): JStringArray = memScoped {
        val elementClass = findClass("java/lang/String".utf8) ?: return@memScoped emptyObjectArray()
        newObjectArray(0, elementClass) ?: return@memScoped emptyObjectArray()
    }

    context(env: JniEnv)
    private fun emptyObjectArray(): JStringArray = memScoped {
        val elementClass = findClass("java/lang/String".utf8) ?: error("Cannot find java.lang.String")
        newObjectArray(0, elementClass) ?: error("Cannot allocate a java.lang.String[0] array")
    }
}