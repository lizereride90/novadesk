package com.novadesk.core.terminal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Result of a short-lived command (apt, git, JSON tools, …). */
data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    val ok: Boolean get() = exitCode == 0
}

/**
 * One-shot process execution. Interactive shells live in [TerminalSession];
 * this is for quick commands with bounded output.
 */
object ShellCommand {

    suspend fun run(vararg argv: String, workDir: java.io.File? = null): CommandResult =
        withContext(Dispatchers.IO) { blocking(argv.toList(), workDir) }

    fun run(vararg argv: String): CommandResult = blocking(argv.toList(), null)

    private fun blocking(argv: List<String>, workDir: java.io.File?): CommandResult {
        return try {
            val proc = ProcessBuilder(argv)
                .apply { if (workDir != null) directory(workDir) }
                .redirectErrorStream(false)
                .start()
            val out = proc.inputStream.readBytes()
            val err = proc.errorStream.readBytes()
            val code = proc.waitFor()
            CommandResult(code, out.toString(Charsets.UTF_8), err.toString(Charsets.UTF_8))
        } catch (e: Exception) {
            CommandResult(127, "", e.message ?: "failed to spawn ${argv.firstOrNull()}")
        }
    }
}
