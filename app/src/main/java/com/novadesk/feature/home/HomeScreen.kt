package com.novadesk.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrowserUpdated
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.novadesk.core.linux.DebianStatus
import com.novadesk.core.navigation.Routes
import com.novadesk.core.storage.AppContainer
import com.novadesk.core.ui.components.NovaCard
import com.novadesk.core.ui.components.SectionHeader

/**
 * Home dashboard: search, quick actions, Debian status, recent projects.
 * Everything here deep-links — the dashboard itself holds no business logic.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(container: AppContainer, nav: NavController) {
    val projects by container.projects.projects.collectAsState()
    val install by container.debianInstaller.progress.collectAsState()
    val installed = container.debianLayout.looksInstalled
    var query by remember { mutableStateOf("") }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(top = 20.dp, bottom = 4.dp)) {
                Text(
                    "NovaDesk",
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Text(
                    "Code. Browse. Build. All in one place.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }

        item {
            SearchBar(
                query = query,
                onQueryChange = { query = it },
                onSearch = {},
                active = false,
                onActiveChange = {},
                placeholder = { Text("Search projects, files, agents, settings…") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {}
        }

        item {
            SectionHeader("Quick actions")
            QuickActions(nav)
        }

        item {
            SectionHeader("Debian", subtitle = "Local Linux runtime")
            NovaCard(
                modifier = Modifier.clickable {
                    if (!installed) nav.navigate(Routes.DEBIAN_SETUP)
                },
            ) {
                val label = when {
                    installed -> "Installed and ready — tap Terminal to open a shell."
                    install.status == DebianStatus.Failed -> "Install failed: ${install.message}. Tap to retry."
                    install.status != DebianStatus.NotInstalled -> install.message.ifBlank { "Working…" }
                    else -> "Not installed yet — tap to set up Debian."
                }
                Text(
                    if (installed) "● Debian ready" else "○ Debian",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (installed) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
        }

        item {
            SectionHeader("Recent projects")
        }
        val filtered = if (query.isBlank()) projects
        else projects.filter { it.name.contains(query, ignoreCase = true) }
        if (filtered.isEmpty()) {
            item {
                NovaCard {
                    Text("No projects yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Create one from the Files tab — pick a folder inside Debian home and it shows up here.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        } else {
            items(filtered.take(8), key = { it.id }) { p ->
                ListItem(
                    headlineContent = { Text(p.name) },
                    supportingContent = { Text(p.rootPath) },
                    leadingContent = { Icon(Icons.Filled.Folder, contentDescription = null) },
                    modifier = Modifier.clickable { nav.navigate(Routes.FILES) },
                )
            }
        }
    }
}

@Composable
private fun QuickActions(nav: NavController) {
    val actions = listOf(
        "OpenCode" to Icons.Filled.SmartToy to Routes.OPENCODE,
        "Terminal" to Icons.Filled.Terminal to Routes.TERMINAL,
        "Files" to Icons.Filled.Folder to Routes.FILES,
        "Browser" to Icons.Filled.BrowserUpdated to Routes.BROWSER,
        "MCP Store" to Icons.Filled.Store to Routes.MCP_STORE,
    )
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for ((pair, route) in actions) {
            val (label, icon) = pair
            ListItem(
                headlineContent = { Text(label) },
                leadingContent = { Icon(icon, contentDescription = null) },
                modifier = Modifier.clickable { nav.navigate(route) },
            )
        }
    }
}
