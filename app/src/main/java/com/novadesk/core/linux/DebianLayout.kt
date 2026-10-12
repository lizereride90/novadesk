package com.novadesk.core.linux

import java.io.File

/**
 * Where everything Debian-related lives on disk.
 *
 * - [rootDir]/rootfs  — the extracted Debian tree, survives app updates.
 * - [rootDir]/downloads — cached rootfs tarballs, safe to delete any time.
 * - [rootDir]/home     — bind-mounted at /home/user inside Debian.
 */
class DebianLayout(appFiles: File) {

    val rootDir: File = File(appFiles, "debian")
    val rootfsDir: File = File(rootDir, "rootfs")
    val downloadsDir: File = File(rootDir, "downloads")
    val homeDir: File = File(rootDir, "home")
    val stateFile: File = File(rootDir, "install_state.json")

    /** True once a bootstrap has completed and /bin/bash exists. */
    val looksInstalled: Boolean
        get() = File(rootfsDir, "bin/bash").exists() ||
            File(rootfsDir, "usr/bin/bash").exists()

    fun ensureDirs() {
        rootfsDir.mkdirs()
        downloadsDir.mkdirs()
        homeDir.mkdirs()
    }

    /** Disk used by the whole Debian tree, for the Settings screen. */
    fun diskUsageBytes(): Long = rootDir.walkTopDown().fold(0L) { acc, f ->
        acc + if (f.isFile) f.length() else 0L
    }
}
