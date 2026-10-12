package com.novadesk.core.linux

import android.os.Build

/**
 * CPU architectures we know how to bootstrap. ARM64 is the primary target —
 * that is what basically every modern Android phone ships.
 */
enum class DeviceArch(val label: String) {
    Arm64("arm64"),
    Arm32("armhf"),
    X86_64("amd64"),
    Unsupported("unknown"),
    ;

    companion object {
        fun detect(): DeviceArch {
            val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
            return when {
                abi.startsWith("arm64") -> Arm64
                abi.startsWith("armeabi") || abi.startsWith("armv") -> Arm32
                abi.startsWith("x86_64") -> X86_64
                else -> Unsupported
            }
        }
    }
}

/**
 * Debian releases the installer is allowed to offer. The list is
 * intentionally short: every entry here is something we actually tested
 * the bootstrap URLs against (see docs/ARCHITECTURE.md).
 */
enum class DebianRelease(
    val codename: String,
    val prettyName: String,
) {
    Bookworm("bookworm", "Debian 12 “bookworm” (stable, recommended)"),
    Trixie("trixie", "Debian 13 “trixie” (stable)"),
    ;

    companion object {
        val default: DebianRelease = Bookworm
    }
}
