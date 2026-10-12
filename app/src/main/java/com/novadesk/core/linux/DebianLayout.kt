package com.novadesk.core.linux

import java.io.File

/**
 * Where everything Debian-related lives on disk.
 *
 * - [rootDir]/rootfs     — extracted Debian tree, survives app updates.
 * - [rootDir]/downloads  — cached rootfs tarballs, safe to delete.
 * - [rootDir]/home       — bound to /home/user inside Debian.
 * - [binDir]             — bundled binaries we manage (proot, helper CLI).
 */
class DebianLayout(appFiles: File) {

    val rootDir: File = File(appFiles, "debian")
    val rootfsDir: File = File(rootDir, "rootfs")
    val downloadsDir: File = File(rootDir, "downloads")
    val homeDir: File = File(rootDir, "home")
    val binDir: File = File(appFiles, "bin")
    val stateFile: File = File(rootDir, "install_state.json")

    val prootBinary: File = File(binDir, "proot")

    /** True once a bootstrap has completed and /bin/bash exists. */
    val looksInstalled: Boolean
        get() = File(rootfsDir, "bin/bash").exists() ||
            File(rootfsDir, "usr/bin/bash").exists()

    val prootReady: Boolean get() = prootBinary.canExecute()

    fun ensureDirs() {
        rootfsDir.mkdirs()
        downloadsDir.mkdirs()
        homeDir.mkdirs()
        binDir.mkdirs()
    }

    /** Disk used by the whole Debian tree, for the Settings screen. */
    fun diskUsageBytes(): Long = rootDir.walkTopDown().fold(0L) { acc, f ->
        acc + if (f.isFile) f.length() else 0L
    }
}
