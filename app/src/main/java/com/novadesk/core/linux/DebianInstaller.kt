package com.novadesk.core.linux

import android.system.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import java.io.File
import java.security.MessageDigest

/** Machine-readable install state, persisted so restarts resume cleanly. */
enum class DebianStatus {
    NotInstalled,
    FetchingRuntime,
    Downloading,
    Verifying,
    Extracting,
    Configuring,
    Ready,
    Failed,
}

/** Progress events surfaced to the setup UI. */
data class InstallProgress(
    val status: DebianStatus = DebianStatus.NotInstalled,
    val fraction: Float = 0f,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = -1,
    val message: String = "",
)

/**
 * Downloads (with resume), verifies and extracts the Debian rootfs, then
 * writes the base guest config. proot is fetched first because nothing in
 * the guest runs without it.
 *
 * Honest-failure rule: any exception lands in [DebianStatus.Failed] with the
 * real message. We never report success we did not earn.
 */
class DebianInstaller(
    private val layout: DebianLayout,
    private val prootInstaller: ProotInstaller,
    private val http: OkHttpClient,
) {
    private val _progress = MutableStateFlow(InstallProgress())
    val progress: StateFlow<InstallProgress> = _progress

    fun currentStatus(): DebianStatus =
        if (layout.looksInstalled && layout.prootReady) DebianStatus.Ready else _progress.value.status

    suspend fun install(
        release: DebianRelease = DebianRelease.default,
        expectedSha256: String? = null,
        tarballUrl: String? = null,
    ) = withContext(Dispatchers.IO) {
        layout.ensureDirs()
        try {
            val arch = DeviceArch.detect()
            require(arch != DeviceArch.Unsupported) {
                "Unsupported CPU (${android.os.Build.SUPPORTED_ABIS.firstOrNull()}). " +
                    "NovaDesk supports ARM64, ARMv7 and x86_64."
            }
            requireFreeSpace(minBytes = 1_400_000_000L)

            if (!layout.prootReady) {
                _progress.value = InstallProgress(DebianStatus.FetchingRuntime, message = "Fetching the Linux runtime (proot)…")
                prootInstaller.ensureInstalled(arch)
                _progress.value = InstallProgress(DebianStatus.FetchingRuntime, fraction = 1f, message = "Runtime ready.")
            }

            val url = tarballUrl?.takeIf { it.isNotBlank() } ?: DebianSources.rootfsUrl(release, arch)
            require(!url.isNullOrBlank()) { "No built-in rootfs for ${release.codename} on ${arch.label}." }

            _progress.value = InstallProgress(DebianStatus.Downloading, message = "Downloading Debian ${release.codename}…")
            val tarball = File(layout.downloadsDir, "debian-${release.codename}-${arch.label}.tar.xz")
            downloadResumable(url, tarball)

            if (expectedSha256 != null) {
                _progress.value = InstallProgress(DebianStatus.Verifying, message = "Verifying checksum…")
                val actual = sha256(tarball)
                require(actual.equals(expectedSha256, ignoreCase = true)) {
                    "Checksum mismatch: expected $expectedSha256 but got $actual. Retry the download."
                }
            }

            _progress.value = InstallProgress(DebianStatus.Extracting, message = "Extracting root filesystem (this can take a minute)…")
            extractTarball(tarball, layout.rootfsDir)

            _progress.value = InstallProgress(DebianStatus.Configuring, message = "Configuring Debian…")
            writeBaseConfig(release)

            _progress.value = InstallProgress(DebianStatus.Ready, fraction = 1f, message = "Debian ${release.codename} is ready.")
        } catch (e: Exception) {
            _progress.value = InstallProgress(DebianStatus.Failed, message = e.message ?: "Install failed.")
            throw e
        }
    }

    /** Deletes the whole tree. Caller must confirm — this wipes user data. */
    suspend fun reset() = withContext(Dispatchers.IO) {
        layout.rootDir.deleteRecursively()
        layout.ensureDirs()
        _progress.value = InstallProgress(DebianStatus.NotInstalled)
    }

    // -- internals ---------------------------------------------------------

    private fun requireFreeSpace(minBytes: Long) {
        val free = layout.rootDir.freeSpace.coerceAtLeast(0L)
        require(free > minBytes) {
            "Not enough storage: need ~1.4 GB free, found ${free / 1_000_000} MB."
        }
    }

    private fun downloadResumable(url: String, dest: File) {
        val already = if (dest.exists()) dest.length() else 0L
        val request = Request.Builder()
            .url(url)
            .apply { if (already > 0) header("Range", "bytes=$already-") }
            .build()
        http.newCall(request).execute().use { resp ->
            require(resp.isSuccessful || resp.code == 206) {
                "Download failed (HTTP ${resp.code}). Check your connection and retry."
            }
            val body = resp.body ?: error("Empty response body — check your connection and retry.")
            val total = resp.header("Content-Range")
                ?.substringAfter("/")
                ?.toLongOrNull()
                ?: body.contentLength().let { if (it < 0) -1 else it + already }
            val append = resp.code == 206
            dest.sink(append).buffer().use { sink ->
                val buf = ByteArray(128 * 1024)
                var read: Int
                var done = if (append) already else 0L
                val stream = body.byteStream()
                while (stream.read(buf).also { read = it } != -1) {
                    sink.write(buf, 0, read)
                    done += read
                    _progress.value = InstallProgress(
                        DebianStatus.Downloading,
                        fraction = if (total > 0) done.toFloat() / total else 0f,
                        downloadedBytes = done,
                        totalBytes = total,
                        message = "Downloading Debian… ${done / 1_048_576} MB" +
                            if (total > 0) " / ${total / 1_048_576} MB" else "",
                    )
                }
            }
        }
    }

    /**
     * Extracts the tarball into [dest]. proot-distro archives wrap the whole
     * tree in a single top-level directory, so we extract to a staging dir
     * first and move the contents up — otherwise the tree lands one level too
     * deep and no shell is ever found.
     */
    private fun extractTarball(tarball: File, dest: File) {
        val staging = File(dest.parentFile, dest.name + ".staging").apply {
            deleteRecursively()
            mkdirs()
        }
        // `tar -xJ` needs xz support; Android's toybox tar has had it since 6.
        val result = try {
            val proc = ProcessBuilder("tar", "-xJpf", tarball.absolutePath, "-C", staging.absolutePath)
                .redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().readText()
            proc.waitFor() to out
        } catch (e: Exception) {
            -1 to (e.message ?: "")
        }
        // toybox tar cannot mknod device nodes (`dev/console`) without root;
        // it warns and continues, so accept a non-zero exit when the shell
        // tree is nevertheless present. The hard gate below is the tree.
        require(result.first == 0 ||
            File(staging, "bin/bash").exists() ||
            File(staging, "usr/bin/bash").exists()) {
            "Extraction failed: ${result.second.take(300).ifBlank { "unable to run tar -xJ" }}"
        }

        // Unwrap a single top-level directory if the archive has one.
        val children = staging.listFiles().orEmpty()
        val source = if (children.size == 1 && children[0].isDirectory) children[0] else staging
        dest.mkdirs()
        source.listFiles().orEmpty().forEach { f ->
            val target = File(dest, f.name)
            if (!f.renameTo(target)) {
                // Cross-device move isn't possible here, so copy as a fallback.
                f.copyRecursively(target)
                f.deleteRecursively()
            }
        }
        staging.deleteRecursively()

        require(
            File(dest, "bin/bash").exists() || File(dest, "usr/bin/bash").exists(),
        ) { "The archive did not produce a shell — unexpected tarball layout." }
    }

    private fun writeBaseConfig(release: DebianRelease) {
        val etc = File(layout.rootfsDir, "etc").apply { mkdirs() }
        File(etc, "hostname").writeText("novadesk\n")
        File(etc, "resolv.conf").writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n")
        File(etc, "apt/sources.list").apply { parentFile?.mkdirs() }.writeText(
            "deb http://deb.debian.org/debian ${release.codename} main contrib non-free non-free-firmware\n" +
                "deb http://deb.debian.org/debian ${release.codename}-updates main contrib non-free non-free-firmware\n" +
                "deb http://security.debian.org/debian-security ${release.codename}-security main contrib non-free non-free-firmware\n",
        )
        // Nice shell identity without needing a real login session.
        File(etc, "profile.d").apply { mkdirs() }
        File(File(etc, "profile.d"), "novadesk.sh").writeText(
            "export PS1='user@novadesk:\\w\\$ '\n" +
                "export EDITOR=nano\n",
        )
        File(File(etc, "profile.d"), "novadesk.sh").setReadable(true, false)

        try {
            Os.chmod(layout.homeDir.absolutePath, 493) // 0755
        } catch (_: Exception) {
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(128 * 1024)
            var read: Int
            while (input.read(buf).also { read = it } != -1) digest.update(buf, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
