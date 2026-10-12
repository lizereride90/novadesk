package com.novadesk.core.agents

import com.novadesk.core.linux.ProotRunner
import com.novadesk.core.terminal.TerminalManager
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Base class for the two coding agents. Each agent drives its real CLI
 * (`opencode` / `codex`) inside Debian and streams stdout back as chat
 * messages — there is no scripted dialogue anywhere in this file.
 *
 * Subclasses only decide *what* to launch and *how* to detect that setup
 * (login/API key) is still missing.
 */
abstract class AgentRunner(
    protected val terminals: TerminalManager,
    protected val proot: ProotRunner,
    protected val workRoot: File,
) {
    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    protected val _messages = MutableStateFlow<List<AgentMessage>>(emptyList())
    val messages: StateFlow<List<AgentMessage>> = _messages

    protected val _state = MutableStateFlow(RunState.Idle)
    val state: StateFlow<RunState> = _state

    protected val _pending = MutableStateFlow<PendingApproval?>(null)
    val pending: StateFlow<PendingApproval?> = _pending

    private var runJob: Job? = null
    private var sessionLog: File? = null

    abstract val id: AgentId
    /** CLI binary expected inside Debian, e.g. "opencode". */
    abstract val cliName: String
    /** Shown when the CLI or its credentials are missing. */
    abstract val setupHelp: String

    fun historyFile(projectPath: String): File {
        val safe = projectPath.replace("[^A-Za-z0-9_-]".toRegex(), "_")
        return File(workRoot, "sessions/${id.name.lowercase()}-$safe.log")
    }

    /**
     * Sends [prompt] to the agent in [projectPath]. If a risky shell command
     * is proposed by *the user prompt itself*, we pause for approval first —
     * agent-proposed commands are gated at execution time in [executeShell].
     */
    fun send(projectPath: String, prompt: String) {
        if (_state.value == RunState.Working) return
        append(AgentMessage(role = AgentMessage.Role.User, text = prompt))
        runJob = scope.launch {
            _state.value = RunState.Working
            try {
                sessionLog = historyFile(projectPath).apply { parentFile?.mkdirs() }
                runAgent(projectPath, prompt)
                _state.value = RunState.Idle
            } catch (e: AgentNotReadyException) {
                append(AgentMessage(role = AgentMessage.Role.System, text = e.message ?: setupHelp, kind = AgentMessage.Kind.Error))
                _state.value = RunState.Failed
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    append(AgentMessage(role = AgentMessage.Role.System, text = "_Run cancelled._"))
                    _state.value = RunState.Idle
                } else {
                    append(AgentMessage(role = AgentMessage.Role.System, text = "Run failed: ${e.message}", kind = AgentMessage.Kind.Error))
                    _state.value = RunState.Failed
                }
            }
        }
    }

    fun cancel() {
        runJob?.cancel()
        scope.coroutineContext.cancelChildren()
        if (_state.value == RunState.Working) _state.value = RunState.Idle
    }

    fun approvePending(projectPath: String) {
        val pending = _pending.value ?: return
        _pending.value = null
        _state.value = RunState.Working
        runJob = scope.launch {
            try {
                executeShell(projectPath, pending.command)
                _state.value = RunState.Idle
            } catch (e: Exception) {
                append(AgentMessage(role = AgentMessage.Role.System, text = "Command failed: ${e.message}", kind = AgentMessage.Kind.Error))
                _state.value = RunState.Failed
            }
        }
    }

    fun dismissPending() {
        _pending.value = null
        if (_state.value == RunState.NeedsApproval) _state.value = RunState.Idle
    }

    // -- to implement ------------------------------------------------------

    protected abstract suspend fun runAgent(projectPath: String, prompt: String)

    // -- shared helpers ----------------------------------------------------

    protected fun append(msg: AgentMessage) {
        _messages.value = _messages.value + msg
        try {
            sessionLog?.appendText("[${msg.role}] ${msg.text}\n")
        } catch (_: Exception) {
        }
    }

    /**
     * Streams a process's stdout into chat as it arrives. Callers build argv
     * with [ProotRunner.argv] so the CLI runs inside Debian, in the project.
     */
    protected suspend fun streamProcess(
        argv: List<String>,
        emptyHint: String,
    ) {
        val proc = ProcessBuilder(argv).redirectErrorStream(true).start()
        val reader = proc.inputStream.bufferedReader()
        val chunk = StringBuilder()
        var sawOutput = false
        while (true) {
            val line = withContext0 { reader.readLine() } ?: break
            sawOutput = true
            chunk.appendLine(line)
            // Flush paragraph-sized chunks so long runs feel live.
            if (chunk.length > 1_200 || line.isBlank()) {
                append(AgentMessage(role = AgentMessage.Role.Assistant, text = chunk.toString()))
                chunk.clear()
            }
        }
        if (chunk.isNotEmpty()) {
            append(AgentMessage(role = AgentMessage.Role.Assistant, text = chunk.toString()))
        }
        val code = proc.waitFor()
        if (!sawOutput && code != 0) {
            throw AgentNotReadyException(
                "The `$cliName` CLI exited with code $code before printing anything. $emptyHint\n\n$setupHelp",
            )
        }
        if (code != 0) {
            append(
                AgentMessage(
                    role = AgentMessage.Role.System,
                    text = "`$cliName` exited with code $code.",
                    kind = AgentMessage.Kind.Error,
                ),
            )
        }
    }

    /** Runs one shell command through the approval gate. */
    protected suspend fun executeShell(projectPath: String, command: String) {
        ApprovalPolicy.needsApproval(command)?.let {
            _pending.value = it
            _state.value = RunState.NeedsApproval
            append(
                AgentMessage(
                    role = AgentMessage.Role.System,
                    text = "Paused for approval: `${command.take(200)}`",
                    kind = AgentMessage.Kind.ToolCall,
                ),
            )
            return
        }
        append(
            AgentMessage(
                role = AgentMessage.Role.System,
                text = "Running: `$command`",
                kind = AgentMessage.Kind.ToolCall,
            ),
        )
        streamProcess(
            proot.argv(listOf("/bin/bash", "-lc", command), workDir = projectPath),
            emptyHint = "The shell produced no output.",
        )
    }

    private suspend fun <T> withContext0(block: suspend () -> T): T =
        kotlinx.coroutines.withContext(Dispatchers.IO) { block() }

    class AgentNotReadyException(message: String) : Exception(message)
}
