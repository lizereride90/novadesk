package com.novadesk.setup

import com.novadesk.core.agents.AgentConfig
import com.novadesk.core.linux.DebianInstaller
import com.novadesk.core.linux.DebianLayout
import com.novadesk.core.linux.DevEnvInstaller
import com.novadesk.core.linux.DeviceArch
import com.novadesk.core.storage.AppPrefs
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * First-run orchestrator. It owns the "get the machine ready" flow so the
 * UI only renders [SetupState]:
 *
 *   Debian install → all-files access → dev stack → done
 *
 * Heavy lifting is delegated to [DebianInstaller] and [DevEnvInstaller];
 * their progress is folded into a single state stream. Failures stop the
 * flow with a real message and a retry that resumes from where it stopped.
 */
class SetupCoordinator(
    private val installer: DebianInstaller,
    private val devEnv: DevEnvInstaller,
    private val layout: DebianLayout,
    private val prefs: AppPrefs,
    private val agentConfig: AgentConfig,
) {
    enum class Phase { Welcome, Debian, Permission, DevTools, Done, Failed }

    data class SetupState(
        val phase: Phase = Phase.Welcome,
        val fraction: Float = 0f,
        val message: String = "",
        val log: List<String> = emptyList(),
        val error: String? = null,
        val installedTools: List<String> = emptyList(),
        val skippedTools: List<String> = emptyList(),
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(SetupState())
    val state: StateFlow<SetupState> = _state

    private val doneFile = File(layout.rootDir.parentFile, "state/setup.done")
    private var job: Job? = null

    val isComplete: Boolean get() = doneFile.exists() && layout.looksInstalled

    /** Starts (or resumes) setup. Safe to call again after a failure. */
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            try {
                if (!layout.looksInstalled) runDebianStage()
                markDebianReady()
            } catch (e: Exception) {
                _state.value = _state.value.copy(phase = Phase.Failed, error = e.message ?: "Setup failed.")
            }
        }
    }

    private suspend fun runDebianStage() {
        _state.value = SetupState(phase = Phase.Debian, message = "Preparing the Linux runtime…")
        val mirror = scope.launch {
            installer.progress.collect { p ->
                _state.value = _state.value.copy(
                    phase = Phase.Debian,
                    fraction = p.fraction,
                    message = p.message,
                )
            }
        }
        try {
            installer.install()
        } finally {
            mirror.cancel()
        }
    }

    private fun markDebianReady() {
        _state.value = _state.value.copy(
            phase = Phase.Permission,
            fraction = 1f,
            message = "Debian is ready. Grant storage access to continue.",
        )
    }

    /** Called by the UI once storage permission has been granted (or skipped). */
    fun onPermissionResolved() {
        if (_state.value.phase == Phase.Permission) {
            _state.value = _state.value.copy(phase = Phase.DevTools, message = "Installing your dev tools…")
            runDevTools()
        }
    }

    private fun runDevTools() {
        job = scope.launch {
            val logJob = launch {
                devEnv.log.collect { lines ->
                    _state.value = _state.value.copy(phase = Phase.DevTools, log = lines.takeLast(7))
                }
            }
            try {
                val result = devEnv.run()
                writeAgentConfig()
                // Only mark the whole flow done once the guest has its tools;
                // otherwise a crash here would silently skip opencode/codex.
                doneFile.parentFile?.mkdirs()
                doneFile.writeText("ok\n")
                _state.value = _state.value.copy(
                    phase = Phase.Done,
                    fraction = 1f,
                    installedTools = result.installed,
                    skippedTools = result.skipped,
                    message = if (result.ok) "All set." else result.error,
                    error = if (result.ok) null else result.error.ifBlank { null },
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(phase = Phase.Failed, error = e.message ?: "Dev tools failed.")
            } finally {
                logJob.cancel()
            }
        }
    }

    /** Writes opencode/codex/MCP config with the current model choices. */
    suspend fun writeAgentConfig() {
        val p = prefs.current()
        agentConfig.writeAll(p.openCodeModel.ifBlank { null }, p.codexModel.ifBlank { null })
    }

    fun arch(): DeviceArch = DeviceArch.detect()
}
