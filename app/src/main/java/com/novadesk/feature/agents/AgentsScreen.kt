package com.novadesk.feature.agents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.novadesk.core.navigation.Routes
import com.novadesk.core.storage.AppContainer
import com.novadesk.core.tools.DevTools
import com.novadesk.core.ui.components.NovaCard
import com.novadesk.core.ui.components.SectionHeader
import kotlinx.coroutines.launch

/**
 * Agents & tools center. The two coding agents get hero cards up top
 * (they deep-link into their chat screens); below are small offline
 * utilities that always work, Debian or not.
 */
@Composable
fun AgentsScreen(container: AppContainer, nav: NavController) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                "Agents & tools",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(20.dp),
            )
        }
        item {
            SectionHeader("Coding agents")
            AgentHero(
                name = "OpenCode",
                blurb = "Project-aware coding agent running inside Debian.",
                onOpen = { nav.navigate(Routes.OPENCODE) },
            )
            AgentHero(
                name = "Codex",
                blurb = "Separate workspace, own sessions and auth.",
                onOpen = { nav.navigate(Routes.CODEX) },
            )
        }
        item { SectionHeader("Utilities", subtitle = "Offline, instant, no setup") }
        item { JsonTool() }
        item { HashTool() }
    }
}

@Composable
private fun AgentHero(name: String, blurb: String, onOpen: () -> Unit) {
    NovaCard(modifier = Modifier.clickable(onClick = onOpen)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(blurb, style = MaterialTheme.typography.bodyMedium)
            }
            Button(onClick = onOpen) { Text("Open") }
        }
    }
}

@Composable
private fun JsonTool() {
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    NovaCard {
        Text("JSON formatter", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = input, onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            placeholder = { Text("{\"paste\": \"json here\"}") },
            minLines = 2,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                output = DevTools.formatJson(input).fold(
                    onSuccess = { it },
                    onFailure = { "Invalid JSON: ${it.message}" },
                )
            }) { Text("Format") }
        }
        if (output.isNotBlank()) {
            Text(output, fontFamily = com.novadesk.core.ui.theme.NovaMono,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun HashTool() {
    var input by remember { mutableStateOf("") }
    NovaCard {
        Text("Hash calculator", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = input, onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            placeholder = { Text("Text to hash") },
        )
        if (input.isNotEmpty()) {
            Text("SHA-256: ${DevTools.sha256Hex(input)}",
                fontFamily = com.novadesk.core.ui.theme.NovaMono,
                style = MaterialTheme.typography.bodySmall)
            Text("MD5: ${DevTools.md5Hex(input)}",
                fontFamily = com.novadesk.core.ui.theme.NovaMono,
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
