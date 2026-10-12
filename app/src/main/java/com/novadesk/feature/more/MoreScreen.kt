package com.novadesk.feature.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrowserUpdated
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.novadesk.core.navigation.Routes
import com.novadesk.core.storage.AppContainer

/** Overflow hub for destinations that don't fit the bottom bar. */
@Composable
fun MoreScreen(container: AppContainer, nav: NavController) {
    val rows = listOf(
        Triple("Browser", "Tabs, bookmarks, agent inspection", Routes.BROWSER) to Icons.Filled.BrowserUpdated,
        Triple("MCP Store", "Servers, secrets, connections", Routes.MCP_STORE) to Icons.Filled.Store,
        Triple("OpenCode", "Coding agent chat", Routes.OPENCODE) to Icons.Filled.SmartToy,
        Triple("Settings", "Appearance, Debian, agents, app", Routes.SETTINGS) to Icons.Filled.Settings,
    )
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text("More", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(20.dp))
        }
        rows.forEach { (triple, icon) ->
            val (title, subtitle, route) = triple
            item {
                ListItem(
                    headlineContent = { Text(title) },
                    supportingContent = { Text(subtitle) },
                    leadingContent = { Icon(icon, contentDescription = null) },
                    modifier = Modifier.clickable { nav.navigate(route) },
                )
            }
        }
        item {
            Column(Modifier.padding(20.dp)) {
                Text("NovaDesk 0.1.0", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Debian + agents + browser, on your phone.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
