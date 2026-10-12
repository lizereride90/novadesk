package com.novadesk.core.mcp

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/** Runtime state of an installed server. */
data class McpInstall(
    val serverId: String,
    val enabled: Boolean = true,
    val secretsConfigured: Boolean = false,
    val lastError: String = "",
)

/**
 * Installed-set registry. Persisted as `id|enabled|secretsConfigured` lines;
 * secrets themselves are *never* written here (see [McpSecrets]).
 */
class McpRegistry(private val dir: File) {

    private val store = File(dir, "mcp_installed.list")
    private val _installed = MutableStateFlow<Map<String, McpInstall>>(emptyMap())
    val installed: StateFlow<Map<String, McpInstall>> = _installed

    suspend fun load() = withContext(Dispatchers.IO) {
        if (!store.exists()) {
            _installed.value = emptyMap()
            return@withContext
        }
        _installed.value = store.readLines()
            .mapNotNull { line ->
                val p = line.split("|")
                if (p.size >= 3) p[0] to McpInstall(p[0], p[1] == "1", p[2] == "1") else null
            }.toMap()
    }

    suspend fun install(serverId: String) = mutate(serverId, McpInstall(serverId))

    suspend fun uninstall(serverId: String) = withContext(Dispatchers.IO) {
        val next = _installed.value - serverId
        persist(next)
        _installed.value = next
    }

    suspend fun setEnabled(serverId: String, enabled: Boolean) =
        mutate(serverId, _installed.value[serverId]?.copy(enabled = enabled) ?: McpInstall(serverId, enabled))

    suspend fun markSecrets(serverId: String, done: Boolean) =
        mutate(serverId, _installed.value[serverId]?.copy(secretsConfigured = done) ?: McpInstall(serverId, secretsConfigured = done))

    suspend fun reportError(serverId: String, error: String) =
        mutate(serverId, _installed.value[serverId]?.copy(lastError = error) ?: McpInstall(serverId, lastError = error))

    /** Refuses to enable servers whose required secrets are missing. */
    fun validate(server: McpServer, install: McpInstall?): String? {
        if (server.needsSecret && install?.secretsConfigured != true) {
            return "${server.name} needs ${server.envVars.joinToString()} before it can run. " +
                "Add it in the server details screen — it is stored in the app-private store, never in git."
        }
        return null
    }

    private suspend fun mutate(id: String, value: McpInstall) = withContext(Dispatchers.IO) {
        val next = _installed.value + (id to value)
        persist(next)
        _installed.value = next
    }

    private fun persist(map: Map<String, McpInstall>) {
        store.parentFile?.mkdirs()
        store.writeText(map.values.joinToString("\n") {
            "${it.serverId}|${if (it.enabled) 1 else 0}|${if (it.secretsConfigured) 1 else 0}"
        })
    }
}
