package com.novadesk.core.linux

import android.content.Context
import android.system.Os
import com.novadesk.core.terminal.ShellCommand
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import java.security.MessageDigest

/** Machine-readable install state, persisted so restarts resume cleanly. */
enum class DebianStatus {
    NotInstalled,
    Downloading,
    Verifying,
    Extracting,
    Configuring,
    Ready,
    Failed,
}

/** Progress events surfaced to the setup wizard. */
data class InstallProgress(
    val status: DebianStatus = DebianStatus.NotInstalled,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = -1,
    val message: String = "",
)

/**
 * Downloads, verifies and extracts the Debian rootfs, then writes the
 * initial config (sources.list, user, hostname, resolv.conf).
 *
 * Honest-failure rule: any exception lands the state in [DebianStatus.Failed]
 * with the real message — we never report success we did not earn.
 */
class DebianInstaller(
    private val context: Context,
    private val layout: DebianLayout,
    private val http: OkHttpClient,
) {
    private val _progress = MutableStateFlow(InstallProgress())
    val progress: StateFlow<InstallProgress> = _progress

    fun currentStatus(): DebianStatus =
        if (layout.looksInstalled) DebianStatus.Ready else _progress.value.status

    /**
     * Full install. Downloads the rootfs tarball for [release] with resume
     * support, verifies sha256 when [expectedSha256] is known, extracts via
     * the system tar, and writes default config.
     */
    suspend fun install(
        release: DebianRelease = DebianRelease.default,
        expectedSha256: String? = null,
        tarballUrl: String,
    ) = withContext(Dispatchers.IO) {
        layout.ensureDirs()
        try {
            val arch = DeviceArch.detect()
            require(arch != DeviceArch.Unsupported) {
                "Unsupported CPU architecture (${android.os.Build.SUPPORTED_ABIS.firstOrNull()}). " +
                    "NovaDesk currently supports ARM64, ARMv7 and x86_64."
            }
            requireFreeSpace(minBytes = 1_200_000_000L)

            _progress.value = InstallProgress(DebianStatus.Downloading, message = "Downloading Debian ${release.codename}…")
            val tarball = File(layout.downloadsDir, "debian-${release.codename}.tar.xz")
            downloadResumable(tarballUrl, tarball)

            if (expectedSha256 != null) {
                _progress.value = InstallProgress(DebianStatus.Verifying, message = "Verifying checksum…")
                val actual = sha256(tarball)
                require(actual.equals(expectedSha256, ignoreCase = true)) {
                    "Checksum mismatch: expected $expectedSha256 but got $actual. " +
                        "The download may be corrupt — please retry."
                }
            }

            _progress.value = InstallProgress(DebianStatus.Extracting, message = "Extracting root filesystem…")
            extractTarball(tarball, layout.rootfsDir)

            _progress.value = InstallProgress(DebianStatus.Configuring, message = "Configuring Debian…")
            writeBaseConfig(release)

            _progress.value = InstallProgress(DebianStatus.Ready, message = "Debian ${release.codename} is ready.")
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
        val free = layout.rootDir.freeSpace.coerceAtLeast(context.filesDir.freeSpace)
        require(free > minBytes) {
            "Not enough storage: need ~1.2 GB free, found ${free / 1_000_000} MB."
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
            val total = resp.header("Content-Range")
                ?.substringAfter("/")
                ?.toLongOrNull()
                ?: resp.body.contentLength().let { if (it < 0) -1 else it + already }
            val append = resp.code == 206
            dest.sink(append).buffer().use { sink ->
                val buf = ByteArray(64 * 1024)
                var read: Int
                var done = if (append) already else 0L
                val stream = resp.body.byteStream()
                while (stream.read(buf).also { read = it } != -1) {
                    sink.write(buf, 0, read)
                    done += read
                    _progress.value = InstallProgress(
                        DebianStatus.Downloading,
                        downloadedBytes = done,
                        totalBytes = total,
                        message = "Downloading… ${done / 1_048_576} MB" +
                            if (total > 0) " of ${total / 1_048_576} MB" else "",
                    )
                }
            }
        }
    }

    private fun extractTarball(tarball: File, dest: File) {
        // The device tar understands xz since Android 6; fall back loudly.
        val result = ShellCommand.run("tar", "-xJpf", tarball.absolutePath, "-C", dest.absolutePath)
        require(result.exitCode == 0) {
            "Extraction failed: ${result.stderr.ifBlank { result.stdout }.take(300)}"
        }
    }

    private fun writeBaseConfig(release: DebianRelease) {
        val etc = File(layout.rootfsDir, "etc").apply { mkdirs() }
        File(etc, "hostname").writeText("novadesk\n")
        File(etc, "resolv.conf").writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n")
        File(etc, "os-release-extra").writeText("NOVADESK_RUNTIME=proot\n")
        val aptDir = File(etc, "apt/sources.list.d").apply { mkdirs() }
        File(File(etc, "apt"), "sources.list").writeText(
            "deb ${DebianSources.MIRROR} ${release.codename} main contrib non-free non-free-firmware\n" +
                "deb ${DebianSources.MIRROR}-security ${release.codename}-security main contrib non-free non-free-firmware\n" +
                "deb ${DebianSources.MIRROR} ${release.codename}-updates main contrib non-free non-free-firmware\n",
        )
        aptDir.mkdirs()
        try {
            Os.chmod(layout.homeDir.absolutePath, 493) // 0755
        } catch (_: Exception) {
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            var read: Int
            while (input.read(buf).also { read = it } != -1) digest.update(buf, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
