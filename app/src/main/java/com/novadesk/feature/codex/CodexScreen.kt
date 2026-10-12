package com.novadesk.feature.codex

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.novadesk.core.storage.AppContainer
import com.novadesk.core.ui.components.AgentBrandHeader
import com.novadesk.core.ui.components.AgentChat

/**
 * Codex chat. Separate runner, separate history, same chat surface —
 * the two agents never share session state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodexScreen(container: AppContainer, nav: NavController) {
    val runner = container.codex
    val messages by runner.messages.collectAsState()
    val runState by runner.state.collectAsState()
    val pending by runner.pending.collectAsState()
    val projects by container.projects.projects.collectAsState()

    var expanded by remember { mutableStateOf(false) }
    var projectPath by remember(projects) {
        mutableStateOf(projects.firstOrNull()?.rootPath ?: "/home/user")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Codex") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = projectPath,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Working directory") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                        .menuAnchor(),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text("/home/user") },
                        onClick = { projectPath = "/home/user"; expanded = false },
                    )
                    projects.forEach { p ->
                        DropdownMenuItem(
                            text = { Text("${p.name} — ${p.rootPath}") },
                            onClick = { projectPath = p.rootPath; expanded = false },
                        )
                    }
                }
            }
            AgentBrandHeader(
                name = "Codex",
                tagline = "OpenAI coding agent CLI · codex exec",
                accent = MaterialTheme.colorScheme.tertiary,
            )
            AgentChat(
                messages = messages,
                runState = runState,
                pending = pending,
                onSend = { runner.send(projectPath, it) },
                onCancel = { runner.cancel() },
                onApprove = { runner.approvePending(projectPath) },
                onDismissApproval = { runner.dismissPending() },
            )
        }
    }
}
