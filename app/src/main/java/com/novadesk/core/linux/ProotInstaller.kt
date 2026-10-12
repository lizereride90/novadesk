package com.novadesk.core.linux

import java.io.File
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Fetches the static Android proot binary and unpacks it to
 * [DebianLayout.binDir]. Without this there is no Linux runtime, so the
 * installer treats it as a hard prerequisite and reports failures loudly.
 */
class ProotInstaller(
    private val layout: DebianLayout,
    private val http: OkHttpClient,
) {

    suspend fun ensureInstalled(arch: DeviceArch): File = withContext(Dispatchers.IO) {
        layout.ensureDirs()
        layout.prootBinary.takeIf { it.canExecute() }?.let { return@withContext it }

        val url = DebianSources.prootZipUrl(arch)
            ?: error("No proot build for ${arch.label}. Supported: arm64, armhf, x86_64.")

        val zip = File(layout.downloadsDir, "proot-${arch.label}.zip")
        val request = Request.Builder().url(url).build()
        http.newCall(request).execute().use { resp ->
            require(resp.isSuccessful) { "proot download failed (HTTP ${resp.code})." }
            val body = resp.body ?: error("Empty proot download.")
            zip.outputStream().use { out -> body.byteStream().copyTo(out) }
        }

        unzipProot(zip)
        layout.prootBinary.setExecutable(true, false)
        require(layout.prootReady) { "proot was downloaded but could not be made executable." }
        layout.prootBinary
    }

    /** The archive holds a single `proot` entry; some builds nest it a level. */
    private fun unzipProot(zip: File) {
        var wrote = false
        ZipInputStream(zip.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name.substringAfterLast('/')
                if (!entry.isDirectory && name == "proot") {
                    layout.prootBinary.outputStream().use { zis.copyTo(it) }
                    wrote = true
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        require(wrote) { "The proot archive did not contain a `proot` binary." }
    }
}
