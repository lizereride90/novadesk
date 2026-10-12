package com.novadesk.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import com.novadesk.core.browser.SearchProvider
import com.novadesk.core.storage.AppContainer
import com.novadesk.core.storage.NovaPrefs
import com.novadesk.core.ui.components.SectionHeader
import com.novadesk.core.ui.theme.NovaAccent
import com.novadesk.core.ui.theme.NovaTheme
import java.io.File
import kotlinx.coroutines.launch

/**
 * Settings. Rule: every toggle here does something real — it either writes
 * to [NovaPrefs] (applied immediately) or runs a Debian/storage action.
 * Nothing is decorative.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, nav: NavController) {
    val scope = rememberCoroutineScope()
    val prefs by container.prefs.flow.collectAsState(initial = NovaPrefs())
    var query by remember { mutableStateOf("") }
    var showReset by remember { mutableStateOf(false) }
    var actionMsg by remember { mutableStateOf("") }

    fun save(next: NovaPrefs) = scope.launch { container.prefs.update(next) }
    val base = prefs ?: NovaPrefs()

    fun matches(vararg hay: String) =
        query.isBlank() || hay.any { it.contains(query, ignoreCase = true) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
            },
        )
    }) { inner ->
        LazyColumn(Modifier.fillMaxSize().padding(inner)) {
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search settings") },
                    singleLine = true,
                )
            }

            if (matches("theme", "dark", "light", "amoled", "appearance", "accent")) {
                item { SectionHeader("Appearance") }
                item {
                    EnumRow("Theme", base.theme.name, NovaTheme.values().map { it.name }) {
                        save(base.copy(theme = NovaTheme.valueOf(it)))
                    }
                }
                item {
                    EnumRow("Accent", base.accent.name, NovaAccent.values().map { it.name }) {
                        save(base.copy(accent = NovaAccent.valueOf(it)))
                    }
                }
                item {
                    SwitchRow("AMOLED black", "Pure-black surfaces.", base.amoled) {
                        save(base.copy(amoled = it))
                    }
                }
            }

            if (matches("debian", "linux", "storage", "release", "shell")) {
                item { SectionHeader("Debian") }
                item {
                    val layout = container.debianLayout
                    val status = if (layout.looksInstalled) "Installed" else "Not installed"
                    val sizeMb = remember { layout.diskUsageBytes() / 1_048_576 }
                    ListItem(
                        headlineContent = { Text("Status: $status") },
                        supportingContent = { Text("On-disk: ~$sizeMb MB · ${layout.rootfsDir.absolutePath}") },
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text("Update packages") },
                        supportingContent = { Text("Runs apt update && apt upgrade inside Debian.") },
                        trailingContent = {
                            OutlinedButton(onClick = {
                                scope.launch {
                                    actionMsg = "Updating… watch the Terminal tab."
                                    val s = container.terminals.openAgentShell("/home/user", "apt upgrade")
                                    s.send("apt update && apt upgrade -y\n")
                                }
                            }) { Text("Run") }
                        },
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text("Reset / reinstall Debian", color = MaterialTheme.colorScheme.error) },
                        supportingContent = { Text("Deletes the rootfs and all Debian data.") },
                        trailingContent = {
                            OutlinedButton(onClick = { showReset = true }) { Text("Reset") }
                        },
                    )
                }
            }

            if (matches("agent", "opencode", "codex", "model", "browser control")) {
                item { SectionHeader("Agents") }
                item {
                    EnumRow("Default agent", base.defaultAgent, listOf("opencode", "codex")) {
                        save(base.copy(defaultAgent = it))
                    }
                }
                item {
                    Text(
                        "Model and provider keys live in each agent's own config inside Debian " +
                            "(opencode.json / codex auth). NovaDesk opens those files for editing " +
                            "rather than keeping a second copy that could drift.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
                item {
                    SwitchRow("Let agents drive browser tabs", "An indicator always shows while they do.", base.agentBrowserControl) {
                        save(base.copy(agentBrowserControl = it))
                    }
                }
                item {
                    SwitchRow("Confirm sensitive browser actions", "Clicks, inputs and form submits pause for approval.", base.confirmSensitiveBrowserActions) {
                        save(base.copy(confirmSensitiveBrowserActions = it))
                    }
                }
            }

            if (matches("browser", "search", "home", "download")) {
                item { SectionHeader("Browser") }
                item {
                    EnumRow("Search engine", base.defaultSearch, SearchProvider.values().map { it.name }) {
                        save(base.copy(defaultSearch = it))
                    }
                }
            }

            if (matches("about", "version", "license", "cache", "export")) {
                item { SectionHeader("App") }
                item {
                    ListItem(
                        headlineContent = { Text("NovaDesk 0.1.0") },
                        supportingContent = { Text("MIT licensed. Clear npx / download caches to free space.") },
                        trailingContent = {
                            OutlinedButton(onClick = {
                                scope.launch {
                                    container.debianLayout.downloadsDir.deleteRecursively()
                                    container.debianLayout.downloadsDir.mkdirs()
                                    actionMsg = "Download cache cleared."
                                }
                            }) { Text("Clear cache") }
                        },
                    )
                }
            }

            if (actionMsg.isNotBlank()) {
                item {
                    Text(actionMsg, modifier = Modifier.padding(20.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }

    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text("Delete Debian?") },
            text = { Text("This wipes the root filesystem, installed packages and /home/user. There is no undo.") },
            confirmButton = {
                Button(onClick = {
                    showReset = false
                    scope.launch {
                        container.terminals.closeAll()
                        container.debianInstaller.reset()
                        actionMsg = "Debian data deleted."
                    }
                }) { Text("Delete everything") }
            },
            dismissButton = { TextButton(onClick = { showReset = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        modifier = Modifier.clickable { onChange(!checked) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnumRow(title: String, current: String, options: List<String>, onPick: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = current, onValueChange = {}, readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.menuAnchor(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { opt ->
                    DropdownMenuItem(text = { Text(opt) }, onClick = { onPick(opt); expanded = false })
                }
            }
        }
    }
}
