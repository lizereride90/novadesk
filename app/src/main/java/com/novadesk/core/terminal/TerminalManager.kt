package com.novadesk.core.terminal

import com.novadesk.core.linux.ProotRunner
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Owns every [TerminalSession]. Sessions are keyed by id and grouped
 * per-project so the workspace can restore "the terminal I had open here".
 */
class TerminalManager(private val proot: ProotRunner) {

    private val sessions = ConcurrentHashMap<String, TerminalSession>()
    private val _ids = MutableStateFlow<List<String>>(emptyList())
    val ids: StateFlow<List<String>> = _ids

    fun open(title: String = "shell", workDir: File? = null, projectId: String? = null): TerminalSession {
        val session = TerminalSession(
            title = title,
            argv = proot.loginShell(),
            workDir = workDir,
            projectId = projectId,
        )
        sessions[session.id] = session
        _ids.value = sessions.keys.toList()
        return session
    }

    /** Ad-hoc shell for agents: runs inside Debian with a fixed cwd. */
    fun openAgentShell(workDir: String, title: String = "agent"): TerminalSession {
        val session = TerminalSession(title = title, argv = proot.loginShell(workDir))
        sessions[session.id] = session
        _ids.value = sessions.keys.toList()
        return session
    }

    fun get(id: String): TerminalSession? = sessions[id]

    fun forProject(projectId: String): List<TerminalSession> =
        sessions.values.filter { it.projectId == projectId }

    fun close(id: String) {
        sessions.remove(id)?.destroy()
        _ids.value = sessions.keys.toList()
    }

    fun closeAll() {
        sessions.values.forEach { it.destroy() }
        sessions.clear()
        _ids.value = emptyList()
    }
}
