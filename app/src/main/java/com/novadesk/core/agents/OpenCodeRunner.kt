package com.novadesk.core.agents

import com.novadesk.core.linux.ProotRunner
import com.novadesk.core.terminal.ShellCommand
import com.novadesk.core.terminal.TerminalManager
import java.io.File

/**
 * OpenCode integration. Drives the official `opencode` CLI inside Debian
 * (see https://opencode.ai/docs). First run installs/updates it via the
 * documented install script, then every prompt becomes
 * `opencode run "<prompt>"` in the project directory.
 *
 * Model/provider choice is the CLI's own config (`opencode.json` /
 * environment) — we open that file for editing instead of inventing a
 * parallel settings format that would silently drift.
 */
class OpenCodeRunner(
    terminals: TerminalManager,
    proot: ProotRunner,
    workRoot: File,
) : AgentRunner(terminals, proot, workRoot) {

    override val id = AgentId.OpenCode
    override val cliName = "opencode"
    override val setupHelp: String =
        "OpenCode isn't ready inside Debian yet.\n\n" +
            "1. Open the Terminal tab and run the installer:\n" +
            "   `curl -fsSL https://opencode.ai/install | bash`\n" +
            "2. Add a model provider key, e.g. `export ANTHROPIC_API_KEY=…`\n" +
            "   (or configure another provider — `opencode` supports many).\n" +
            "3. Come back here and send your prompt again.\n\n" +
            "Docs: https://opencode.ai/docs"

    override suspend fun runAgent(projectPath: String, prompt: String) {
        if (!cliAvailable()) {
            throw AgentNotReadyException(
                "No `opencode` binary found in Debian ($projectPath). $setupHelp",
            )
        }
        append(
            AgentMessage(
                role = AgentMessage.Role.System,
                text = "Running `opencode` in `$projectPath`…",
                kind = AgentMessage.Kind.ToolCall,
            ),
        )
        streamProcess(
            argv = proot.argv(
                listOf("/bin/bash", "-lc", "opencode run ${shellQuote(prompt)}"),
                workDir = projectPath,
            ),
            emptyHint = "OpenCode printed nothing.",
        )
    }

    private suspend fun cliAvailable(): Boolean =
        ShellCommand.run("sh", "-c", "command -v $cliName").ok ||
            checkDebianBinary()

    private suspend fun checkDebianBinary(): Boolean {
        val probe = ProcessBuilder(
            proot.argv(listOf("/bin/bash", "-lc", "command -v $cliName"), workDir = "/home/user"),
        ).redirectErrorStream(true).start()
        probe.inputStream.bufferedReader().readText()
        return probe.waitFor() == 0
    }

    private fun shellQuote(s: String): String = "'${s.replace("'", "'\\''")}'"
}
