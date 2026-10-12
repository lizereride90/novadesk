package com.novadesk.core.linux

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/** Outcome summary for the dev-stack step. */
data class DevEnvResult(
    val ok: Boolean,
    val installed: List<String>,
    val skipped: List<String>,
    val error: String = "",
)

/**
 * Installs the language + agent stack inside Debian: git, curl, Node.js,
 * npm and Python via apt, then the Codex and OpenCode CLIs.
 *
 * The individual agent installs are best-effort (they need network and, for
 * Codex, npm) so one failure does not sink the whole step — we report what
 * landed and what did not.
 */
class DevEnvInstaller(private val proot: ProotRunner) {

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log

    suspend fun run(): DevEnvResult = withContext(Dispatchers.IO) {
        if (!proot.isAvailable()) {
            return@withContext DevEnvResult(false, emptyList(), emptyList(), "The Linux runtime isn't available.")
        }
        val installed = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val proc = ProcessBuilder(
            proot.argv(listOf("/bin/bash", "-lc", SCRIPT), workDir = "/home/user"),
        ).redirectErrorStream(true).start()

        proc.inputStream.bufferedReader().use { reader ->
            val buffer = ArrayDeque<String>()
            reader.lineSequence().forEach { line ->
                buffer.addLast(line)
                while (buffer.size > 400) buffer.removeFirst()
                _log.value = buffer.toList()
                when {
                    line.startsWith("[ok]") -> installed += line.removePrefix("[ok]").trim()
                    line.startsWith("[skip]") -> skipped += line.removePrefix("[skip]").trim()
                }
            }
        }
        val code = proc.waitFor()
        DevEnvResult(
            ok = code == 0,
            installed = installed,
            skipped = skipped,
            error = if (code == 0) "" else "The dev-tools script exited with code $code.",
        )
    }

    private companion object {
        val SCRIPT = """
            export DEBIAN_FRONTEND=noninteractive
            export HOME=/home/user
            echo "[*] apt-get update"
            apt-get update -y || echo "[skip] apt update"
            echo "[*] installing base packages"
            if apt-get install -y --no-install-recommends ${DebianSources.devPackages.joinToString(" ")}; then
                echo "[ok] git, curl, nodejs, npm, python3"
            else
                echo "[skip] base packages"
            fi
            echo "[*] ensuring user account"
            id -u user >/dev/null 2>&1 || useradd -m -s /bin/bash user
            if command -v node >/dev/null 2>&1; then echo "[ok] node $(node -v) / npm $(npm -v)"; fi
            if command -v python3 >/dev/null 2>&1; then echo "[ok] $(python3 --version)"; fi
            echo "[*] installing Codex CLI"
            if npm install -g @openai/codex >/dev/null 2>&1; then echo "[ok] codex"; else echo "[skip] codex"; fi
            echo "[*] installing OpenCode CLI"
            if curl -fsSL https://opencode.ai/install | bash >/dev/null 2>&1; then echo "[ok] opencode"; else echo "[skip] opencode"; fi
            echo "[*] dev-tools step complete"
        """.trimIndent()
    }
}
