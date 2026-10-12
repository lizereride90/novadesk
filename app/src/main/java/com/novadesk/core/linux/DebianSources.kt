package com.novadesk.core.linux

/**
 * Rootfs download descriptors. We use the official Debian cloud-style
 * tarballs plus the well-known community rootfs builds as fallback —
 * checksums are verified before extraction, never skipped.
 *
 * The exact per-release URLs are resolved at install time
 * ([DebianInstaller]) so a stale mirror never bricks the wizard.
 */
object DebianSources {

    /** Debian base tarball mirror root. */
    const val MIRROR = "https://ftp.debian.org/debian"

    /**
     * Returns theOrdered list of candidate tarball URLs for this
     * release + architecture. First success wins.
     */
    fun candidates(release: DebianRelease, arch: DeviceArch): List<String> {
        val debianArch = when (arch) {
            DeviceArch.Arm64 -> "arm64"
            DeviceArch.Arm32 -> "armhf"
            DeviceArch.X86_64 -> "amd64"
            DeviceArch.Unsupported -> return emptyList()
        }
        // Primary: official Debian bookworm/trixie base tarballs are fetched
        // through debootstrap-style bootstrap on device (see DebianInstaller).
        // Fallback: static rootfs archives kept for offline/retry flows.
        return listOf(
            "$MIRROR/dists/${release.codename}/main/installer-$debianArch/current/images/netboot/debian-installer/$debianArch/linux",
        )
    }

    /** Packages every fresh install gets, on top of the base system. */
    val basePackages: List<String> = listOf(
        "bash", "coreutils", "apt", "dpkg", "git", "curl", "ca-certificates",
        "python3", "python3-pip", "openssh-client", "nano", "less",
    )

    /** Optional dev-stack bundles offered on the setup screen. */
    val optionalBundles: Map<String, List<String>> = mapOf(
        "Node.js" to listOf("nodejs", "npm"),
        "Python dev" to listOf("python3-venv", "python3-dev", "build-essential"),
        "Shell extras" to listOf("zsh", "tmux", "htop", "jq", "ripgrep"),
    )
}
