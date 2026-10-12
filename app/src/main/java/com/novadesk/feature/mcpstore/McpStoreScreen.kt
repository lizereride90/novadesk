package com.novadesk.feature.mcpstore

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.novadesk.core.mcp.AgentCompat
import com.novadesk.core.mcp.McpCatalog
import com.novadesk.core.mcp.McpServer
import com.novadesk.core.storage.AppContainer
import com.novadesk.core.ui.components.NovaCard
import kotlinx.coroutines.launch

/**
 * MCP Store: search, categories, install/enable, per-server secrets and
 * connection probing. Secrets go to the private store; the install list
 * only records ids and flags.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpStoreScreen(container: AppContainer, nav: NavController) {
    val scope = rememberCoroutineScope()
    val installed by container.mcpRegistry.installed.collectAsState()
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All") }
    var selected by remember { mutableStateOf<McpServer?>(null) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("MCP Store") },
            navigationIcon = {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
            },
        )
    }) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Search servers…") },
                singleLine = true,
            )
            LazyRow(modifier = Modifier.padding(vertical = 8.dp)) {
                items(McpCatalog.categories) { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(cat) },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            LazyColumn(Modifier.weight(1f)) {
                val list = if (category == "Installed") {
                    McpCatalog.servers.filter { it.id in installed }
                } else {
                    McpCatalog.search(query, category)
                }
                items(list, key = { it.id }) { server ->
                    val install = installed[server.id]
                    NovaCard(modifier = Modifier.clickable { selected = server }) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(server.name, style = MaterialTheme.typography.titleMedium)
                                Text(server.description, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${server.category} · ${server.transport.name.lowercase()} · " +
                                        compatLabel(server.compat) +
                                        if (install != null) " · ${if (install.enabled) "on" else "off"}" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (install == null) {
                                Button(onClick = { scope.launch { container.mcpRegistry.install(server.id) } }) {
                                    Text("Get")
                                }
                            } else {
                                Switch(
                                    checked = install.enabled,
                                    onCheckedChange = { on ->
                                        scope.launch {
                                            if (on) {
                                                val problem = container.mcpRegistry.validate(server, install)
                                                if (problem == null) container.mcpRegistry.setEnabled(server.id, true)
                                                else container.mcpRegistry.reportError(server.id, problem)
                                            } else {
                                                container.mcpRegistry.setEnabled(server.id, false)
                                            }
                                        }
                                    },
                                )
                            }
                        }
                        install?.lastError?.ifBlank { null }?.let { err ->
                            Text(err, color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    selected?.let { server ->
        ServerSheet(server, container, onClose = { selected = null })
    }
}

private fun compatLabel(compat: AgentCompat): String = when (compat) {
    AgentCompat.OpenCode -> "OpenCode"
    AgentCompat.Codex -> "Codex"
    AgentCompat.Both -> "OpenCode + Codex"
}

@Composable
private fun ServerSheet(server: McpServer, container: AppContainer, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val installed by container.mcpRegistry.installed.collectAsState()
    var secretInput by remember { mutableStateOf("") }
    var probeResult by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(server.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(server.description)
                Text("Package: ${server.packageRef}", fontFamily = com.novadesk.core.ui.theme.NovaMono,
                    style = MaterialTheme.typography.bodySmall)
                if (server.homepage.isNotBlank()) Text(server.homepage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary)
                if (server.needsSecret) {
                    Text("Required secrets: ${server.envVars.joinToString()}")
                    OutlinedTextField(
                        value = secretInput, onValueChange = { secretInput = it },
                        label = { Text(server.envVars.firstOrNull() ?: "secret") },
                        singleLine = true,
                    )
                    Button(onClick = {
                        scope.launch {
                            val key = server.envVars.firstOrNull()
                            if (key != null && secretInput.isNotBlank()) {
                                container.mcpSecrets.set(key, secretInput.trim())
                                container.mcpRegistry.markSecrets(server.id, true)
                                secretInput = ""
                            }
                        }
                    }) { Text("Save secret") }
                }
                probeResult?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            probeResult = "Probing… (spawns the server inside Debian)"
                            val env = mutableMapOf<String, String>()
                            server.envVars.forEach { v ->
                                container.mcpSecrets.get(v)?.let { env[v] = it }
                            }
                            val probe = container.mcpClient.probe(server, "/home/user", env)
                            probeResult = if (probe.ok) {
                                "Server started. Tools: ${probe.tools.take(8).joinToString().ifBlank { "(none listed)" }}"
                            } else {
                                "Probe failed: ${probe.error.ifBlank { "unknown error" }}"
                            }
                            if (!probe.ok) container.mcpRegistry.reportError(server.id, probe.error)
                        }
                    }) { Text("Test connection") }
                    if (server.id in installed) {
                        OutlinedButton(onClick = {
                            scope.launch { container.mcpRegistry.uninstall(server.id) ; onClose() }
                        }) { Text("Uninstall") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Done") } },
    )
}
