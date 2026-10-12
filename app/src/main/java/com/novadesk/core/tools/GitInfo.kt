package com.novadesk.core.tools

import com.novadesk.core.terminal.ShellCommand
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Read-only git introspection for the workspace header. */
object GitInfo {

    data class Status(val branch: String, val dirty: Boolean, val ahead: Int, val raw: String)

    suspend fun status(repoDir: File): Status? = withContext(Dispatchers.IO) {
        val branch = ShellCommand.run("git", "-C", repoDir.absolutePath, "branch", "--show-current")
        if (!branch.ok) return@withContext null
        val porcelain = ShellCommand.run("git", "-C", repoDir.absolutePath, "status", "--porcelain=v1", "--branch")
        val first = porcelain.stdout.lineSequence().firstOrNull().orEmpty()
        val ahead = Regex("ahead (\\d+)").find(first)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        Status(
            branch = branch.stdout.trim().ifBlank { "(detached)" },
            dirty = porcelain.stdout.lineSequence().drop(1).any { it.isNotBlank() },
            ahead = ahead,
            raw = porcelain.stdout,
        )
    }

    suspend fun diffStat(repoDir: File): String = withContext(Dispatchers.IO) {
        ShellCommand.run("git", "-C", repoDir.absolutePath, "diff", "--stat").stdout
            .ifBlank { "Working tree is clean." }
    }
}
