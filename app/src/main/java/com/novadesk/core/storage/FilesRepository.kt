package com.novadesk.core.storage

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One entry in a directory listing. */
data class FileEntry(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long,
    val modified: Long,
)

/**
 * File operations shared by the file manager and the editor.
 *
 * Safety rules (enforced here, not in the UI):
 * - every path is normalised and must stay inside an allowed root,
 * - symlinks are resolved and re-checked, so `..` tricks fail closed.
 */
class FilesRepository {

    suspend fun list(dir: File, allowedRoots: List<File>): List<FileEntry> =
        withContext(Dispatchers.IO) {
            val safe = resolve(dir, allowedRoots)
            (safe.listFiles() ?: emptyArray())
                .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                .map {
                    FileEntry(it.name, it.absolutePath, it.isDirectory, it.length(), it.lastModified())
                }
        }

    suspend fun readText(file: File, allowedRoots: List<File>, maxBytes: Long = 1_000_000): String =
        withContext(Dispatchers.IO) {
            val safe = resolve(file, allowedRoots)
            require(safe.length() <= maxBytes) { "File is too large to open safely (${safe.length()} bytes)." }
            safe.readText(Charsets.UTF_8)
        }

    suspend fun writeText(file: File, allowedRoots: List<File>, text: String) =
        withContext(Dispatchers.IO) {
            val safe = resolve(file, allowedRoots)
            safe.parentFile?.mkdirs()
            safe.writeText(text, Charsets.UTF_8)
        }

    suspend fun mkdir(parent: File, allowedRoots: List<File>, name: String): File =
        withContext(Dispatchers.IO) {
            val safe = resolve(File(parent, name), allowedRoots)
            require(safe.mkdirs() || safe.isDirectory) { "Could not create ${safe.name}." }
            safe
        }

    suspend fun rename(file: File, allowedRoots: List<File>, newName: String): File =
        withContext(Dispatchers.IO) {
            val src = resolve(file, allowedRoots)
            val dst = resolve(File(src.parentFile, newName), allowedRoots)
            require(src.renameTo(dst)) { "Rename failed." }
            dst
        }

    suspend fun delete(file: File, allowedRoots: List<File>) =
        withContext(Dispatchers.IO) {
            val safe = resolve(file, allowedRoots)
            require(safe.deleteRecursively()) { "Delete failed." }
        }

    suspend fun copy(src: File, dstDir: File, allowedRoots: List<File>) =
        withContext(Dispatchers.IO) {
            val s = resolve(src, allowedRoots)
            val d = resolve(File(dstDir, s.name), allowedRoots)
            if (s.isDirectory) s.copyRecursively(d) else s.copyTo(d, overwrite = true)
        }

    suspend fun move(src: File, dstDir: File, allowedRoots: List<File>) =
        withContext(Dispatchers.IO) {
            val s = resolve(src, allowedRoots)
            val d = resolve(File(dstDir, s.name), allowedRoots)
            require(s.renameTo(d)) {
                s.copyRecursively(d)
                s.deleteRecursively()
            }
        }

    /** Normalise + resolve symlinks, then require containment in an allowed root. */
    fun resolve(file: File, allowedRoots: List<File>): File {
        val canonical = try {
            File(Files.readSymbolicLink(file.toPath()).toString()).let {
                if (it.isAbsolute) it else File(file.parentFile, it.path)
            }.canonicalFile
        } catch (_: Exception) {
            file.canonicalFile
        }
        val ok = allowedRoots.any { root ->
            val r = root.canonicalFile
            canonical == r || canonical.startsWith(r)
        }
        require(ok) { "Path is outside the allowed workspace: ${file.path}" }
        return canonical
    }
}
