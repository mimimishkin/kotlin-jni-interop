package io.github.mimimishkin.jni.binding.plugin

import java.io.Serializable

public data class MachineDependent<out T>(
    val forLinuxX64: T,
    val forLinuxArm64: T,
    val forMacX64: T,
    val forMacArm64: T,
    val forWindowsX64: T,
    val forWindowsArm64: T,
) : Serializable {
    public constructor(
        forLinux: T,
        forMac: T,
        forWindows: T,
    ) : this(forLinux, forLinux, forMac, forMac, forWindows, forWindows)

    public operator fun <O> plus(other: MachineDependent<O>): MachineDependent<Pair<T, O>> {
        return MachineDependent(
            forLinuxX64 = this.forLinuxX64 to other.forLinuxX64,
            forLinuxArm64 = this.forLinuxArm64 to other.forLinuxArm64,
            forMacX64 = this.forMacX64 to other.forMacX64,
            forMacArm64 = this.forMacArm64 to other.forMacArm64,
            forWindowsX64 = this.forWindowsX64 to other.forWindowsX64,
            forWindowsArm64 = this.forWindowsArm64 to other.forWindowsArm64,
        )
    }

    public fun <O> map(mapper: (T) -> O): MachineDependent<O> {
        return MachineDependent(
            forLinuxX64 = mapper(forLinuxX64),
            forLinuxArm64 = mapper(forLinuxArm64),
            forMacX64 = mapper(forMacX64),
            forMacArm64 = mapper(forMacArm64),
            forWindowsX64 = mapper(forWindowsX64),
            forWindowsArm64 = mapper(forWindowsArm64),
        )
    }

    override fun toString(): String {
        return  "MachineDependent(\n" +
                "    forLinuxX64=$forLinuxX64,\n" +
                "    forLinuxArm64=$forLinuxArm64,\n" +
                "    forMacX64=$forMacX64,\n" +
                "    forMacArm64=$forMacArm64,\n" +
                "    forWindowsX64=$forWindowsX64,\n" +
                "    forWindowsArm64=$forWindowsArm64,\n" +
                ")"
    }

    public companion object {
        public fun <T> fromString(str: String, parse: (String) -> T): MachineDependent<T> {
            val regex = Regex(  "MachineDependent(?:\\n" +
                                " *forLinuxX64=(.*),\\n" +
                                " *forLinuxArm64=(.*),\\n" +
                                " *forMacX64=(.*),\\n" +
                                " *forMacArm64=(.*),\\n" +
                                " *forWindowsX64=(.*),\\n" +
                                " *forWindowsArm64=(.*),\\n" +
                                ")", RegexOption.MULTILINE)
            val match = regex.matchEntire(str) ?: run {
                runCatching { parse(str) }.onSuccess { return machineIndependent { it } }
                throw IllegalArgumentException("Invalid MachineDependent string format: $str")
            }
            val (linuxX64, linuxArm64, macX64, macArm64, windowsX64, windowsArm64) = match.destructured
            return MachineDependent(
                forLinuxX64 = parse(linuxX64),
                forLinuxArm64 = parse(linuxArm64),
                forMacX64 = parse(macX64),
                forMacArm64 = parse(macArm64),
                forWindowsX64 = parse(windowsX64),
                forWindowsArm64 = parse(windowsArm64),
            )
        }
    }
}

public data class MachineDependentBuilderScope(
    val os: String,
    val arch: String,
    val mapNative: (String) -> String,
) {
    public val isLinux: Boolean = os == "linux"
    public val isMac: Boolean = os == "macos"
    public val isWindows: Boolean = os == "windows"

    public val isX64: Boolean = arch == "x64"
    public val isArm64: Boolean = arch == "aarch64"

    public companion object {
        public val LinuxX64: MachineDependentBuilderScope = MachineDependentBuilderScope("linux", "x64") { "lib$it.so" }
        public val LinuxArm64: MachineDependentBuilderScope = MachineDependentBuilderScope("linux", "aarch64") { "lib$it.so" }
        public val MacX64: MachineDependentBuilderScope = MachineDependentBuilderScope("macos", "x64") { "lib$it.dylib" }
        public val MacArm64: MachineDependentBuilderScope = MachineDependentBuilderScope("macos", "aarch64") { "lib$it.dylib" }
        public val WindowsX64: MachineDependentBuilderScope = MachineDependentBuilderScope("windows", "x64") { "$it.dll" }
        public val WindowsArm64: MachineDependentBuilderScope = MachineDependentBuilderScope("windows", "aarch64") { "$it.dll" }
    }
}

public fun <T> machineDependent(
    build: MachineDependentBuilderScope.() -> T
): MachineDependent<T> {
    return MachineDependent(
        forLinuxX64 = MachineDependentBuilderScope.LinuxX64.build(),
        forLinuxArm64 = MachineDependentBuilderScope.LinuxArm64.build(),
        forMacX64 = MachineDependentBuilderScope.MacX64.build(),
        forMacArm64 = MachineDependentBuilderScope.MacArm64.build(),
        forWindowsX64 = MachineDependentBuilderScope.WindowsX64.build(),
        forWindowsArm64 = MachineDependentBuilderScope.WindowsArm64.build(),
    )
}

public fun <T> machineIndependent(
    value: () -> T
): MachineDependent<T> {
    val value = value.invoke()
    return MachineDependent(
        forLinuxX64 = value,
        forLinuxArm64 = value,
        forMacX64 = value,
        forMacArm64 = value,
        forWindowsX64 = value,
        forWindowsArm64 = value,
    )
}