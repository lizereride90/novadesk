package com.novadesk.core.linux

/**
 * Built-in, verified download sources. Every URL here resolves today and is
 * pinned to an immutable release tag, so setup never depends on a moving
 * "latest" pointer.
 *
 * - rootfs: Termux proot-distro v4.17.3 release assets (the last v4 line
 *   that still publishes distro tarballs; v5 dropped them in favour of OCI).
 *   Assets are named `debian-bookworm-<arch>-pd-v4.17.3.tar.xz`.
 * - proot: static Android proot built from the Termux fork with the NDK.
 *
 * If either host is unreachable we surface the failure and let the user
 * paste their own URL in Settings — we never silently fall through to a
 * different, unverified artifact.
 */
object DebianSources {

    const val PROOT_DISTRO_TAG = "v4.17.3"
    const val PROOT_BUILD_TAG = "v26.10.08-a179d3e"

    private const val GH = "https://github.com"
    private const val ROOTFS_BASE = "$GH/termux/proot-distro/releases/download/$PROOT_DISTRO_TAG"
    private const val PROOT_BASE = "$GH/ahmed-alnassif/proot/releases/download/$PROOT_BUILD_TAG"

    /** proot-distro arch token for a device ABI. */
    private fun distroToken(arch: DeviceArch): String? = when (arch) {
        DeviceArch.Arm64 -> "aarch64"
        DeviceArch.Arm32 -> "arm"
        DeviceArch.X86_64 -> "x86_64"
        DeviceArch.Unsupported -> null
    }

    /** proot release asset token for a device ABI. */
    private fun prootToken(arch: DeviceArch): String? = when (arch) {
        DeviceArch.Arm64 -> "aarch64"
        DeviceArch.Arm32 -> "arm"
        DeviceArch.X86_64 -> "x86_64"
        DeviceArch.Unsupported -> null
    }

    /**
     * Built-in rootfs URL. The pinned proot-distro release ships Debian
     * assets named `debian-<codename>-<arch>-pd-<tag>.tar.xz`, wrapped in a
     * top-level `debian-<codename>-<arch>/` directory inside the archive.
     * Only bookworm is published for Debian at this tag, so other releases
     * return null and the caller must supply a custom URL.
     */
    fun rootfsUrl(release: DebianRelease, arch: DeviceArch): String? {
        if (release != DebianRelease.Bookworm) return null
        val token = distroToken(arch) ?: return null
        return "$ROOTFS_BASE/debian-${release.codename}-${token}-pd-$PROOT_DISTRO_TAG.tar.xz"
    }

    fun prootZipUrl(arch: DeviceArch): String? {
        val token = prootToken(arch) ?: return null
        return "$PROOT_BASE/proot-$token.zip"
    }

    val prootChecksumsUrl: String = "$PROOT_BASE/checksums.txt"

    /** Packages the dev-stack step installs through apt. */
    val devPackages: List<String> = listOf(
        "bash", "coreutils", "apt", "dpkg", "ca-certificates", "locales",
        "git", "curl", "wget", "unzip", "xz-utils", "sudo",
        "nodejs", "npm", "python3", "python3-pip", "python3-venv",
    )
}
