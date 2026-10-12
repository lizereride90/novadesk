package com.novadesk.core.agents

import com.novadesk.core.linux.ProotRunner
import com.novadesk.core.terminal.TerminalManager
import java.io.File

/**
 * Codex integration. Drives the official Muse CLI inside Debian.
 * Authentication is whatever the CLI itself supports (API key / ChatGPT
 * sign-in via `codex`); we surface its own login output instead of
 * inventing an OAuth flow that does not exist.
 *
 * Install: `npm i -g @openai/codex` inside Debian, then `codex` to sign in.
 * Docs: https://developers.openai.com/codex/cli
 */
class CodexRunner(
    terminals: TerminalManager,
    proot: ProotRunner,
    workRoot: File,
    private val modelProvider: () -> String = { "" },
) : AgentRunner(terminals, proot, workRoot) {

    override val id = AgentId.Codex
    override val cliName = "codex"
    override val setupHelp: String =
        "Codex isn't ready inside Debian yet.\n\n" +
            "1. Open the Terminal tab and install the CLI:\n" +
            "   `npm i -g @openai/codex`\n" +
            "2. Run `codex` once and complete its sign-in.\n" +
            "3. Come back here and send your prompt again.\n\n" +
            "Docs: https://developers.openai.com/codex/cli"

    override suspend fun runAgent(projectPath: String, prompt: String) {
        if (!cliAvailable(projectPath)) {
            throw AgentNotReadyException(
                "No `codex` binary found in Debian ($projectPath). $setupHelp",
            )
        }
        append(
            AgentMessage(
                role = AgentMessage.Role.System,
                text = "Running `codex exec` in `$projectPath`…",
                kind = AgentMessage.Kind.ToolCall,
            ),
        )
        val model = modelProvider()
        val execCmd = buildString {
            append("codex exec")
            if (model.isNotBlank()) append(" -m ").append(shellQuote(model))
            append(' ').append(shellQuote(prompt))
        }
        streamProcess(
            argv = proot.argv(
                listOf("/bin/bash", "-lc", execCmd),
                workDir = projectPath,
            ),
            emptyHint = "Codex printed nothing.",
        )
    }

    private fun cliAvailable(projectPath: String): Boolean {
        return try {
            val probe = ProcessBuilder(
                proot.argv(listOf("/bin/bash", "-lc", "command -v $cliName"), workDir = projectPath),
            ).redirectErrorStream(true).start()
            probe.inputStream.bufferedReader().readText()
            probe.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }

    private fun shellQuote(s: String): String = "'${s.replace("'", "'\\''")}'"
}
