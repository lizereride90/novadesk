package com.novadesk.feature.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.novadesk.core.navigation.Routes
import com.novadesk.core.storage.AppContainer
import com.novadesk.core.terminal.AnsiParser
import com.novadesk.core.terminal.TerminalSession
import com.novadesk.core.ui.theme.NovaMono

/**
 * Interactive terminal. Each tab is a live [TerminalSession]; output is
 * selectable, searchable by eye, and rendered with ANSI colours. The extra
 * key row (Tab / Ctrl / Esc / arrows) exists because Gboard has none of them.
 */
@Composable
fun TerminalScreen(container: AppContainer, nav: NavController) {
    val manager = container.terminals
    val ids by manager.ids.collectAsState()

    // Open the first shell lazily — and offer Debian setup when missing.
    var activeId by remember { mutableStateOf<String?>(null) }
    if (ids.isEmpty()) {
        LaunchedOpen(manager) { activeId = it }
    } else if (activeId == null || manager.get(activeId!!) == null) {
        activeId = ids.first()
    }

    Column(Modifier.fillMaxSize()) {
        if (!container.debianLayout.looksInstalled) {
            DebianNudge { nav.navigate(Routes.DEBIAN_SETUP) }
        }
        ScrollableTabRow(
            selectedTabIndex = ids.indexOf(activeId).coerceAtLeast(0),
            edgePadding = 8.dp,
        ) {
            ids.forEach { id ->
                val session = manager.get(id)
                Tab(
                    selected = id == activeId,
                    onClick = { activeId = id },
                    text = { Text(session?.title ?: "shell", maxLines = 1) },
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { activeId = manager.open().id }) {
                Icon(Icons.Filled.Add, contentDescription = "New tab")
            }
            val current = activeId?.let { manager.get(it) }
            if (current != null) {
                IconButton(onClick = { current.clear() }) {
                    Icon(Icons.Filled.Clear, contentDescription = "Clear screen")
                }
                IconButton(onClick = {
                    manager.close(current.id)
                    activeId = null
                }) {
                    Icon(Icons.Filled.Close, contentDescription = "Close tab")
                }
            }
        }
        val session = activeId?.let { manager.get(it) }
        if (session != null) {
            SessionView(session, Modifier.weight(1f))
        } else {
            Text(
                "Opening shell…",
                modifier = Modifier.padding(20.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LaunchedOpen(manager: com.novadesk.core.terminal.TerminalManager, onOpen: (String) -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) {
        onOpen(manager.open().id)
    }
}

@Composable
private fun DebianNudge(onSetup: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Debian isn't installed — the shell below is limited.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        androidx.compose.material3.TextButton(onClick = onSetup) { Text("Set up") }
    }
}

@Composable
private fun SessionView(session: TerminalSession, modifier: Modifier = Modifier) {
    val raw by session.output.collectAsState()
    var input by remember(session.id) { mutableStateOf("") }
    val rendered = remember(raw) { AnsiParser.render(AnsiParser.strip(raw).takeLast(120_000)) }

    DisposableEffect(session.id) { onDispose { /* session persists; closed via tab X */ } }

    Column(modifier) {
        SelectionContainer(
            modifier = Modifier.weight(1f).fillMaxWidth()
                .padding(horizontal = 12.dp)
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                .padding(10.dp)
                .horizontalScroll(rememberScrollState()),
        ) {
            Text(
                rendered,
                fontFamily = NovaMono,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
            )
        }
        ExtraKeys { key -> session.send(key) }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("$ ") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
            androidx.compose.material3.IconButton(onClick = {
                session.send(input + "\n")
                input = ""
            }) {
                Text("⏎", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

/** Keys a phone keyboard doesn't have but a shell needs. */
@Composable
private fun ExtraKeys(onKey: (String) -> Unit) {
    val keys = remember {
        listOf(
            "Tab" to "\t", "Ctrl-C" to "\u0003", "Ctrl-D" to "\u0004",
            "Esc" to "\u001B", "↑" to "\u001B[A", "↓" to "\u001B[B",
            "←" to "\u001B[D", "→" to "\u001B[C", "|" to "|",
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        keys.forEach { (label, bytes) ->
            AssistChip(onClick = { onKey(bytes) }, label = {
                Text(label, fontFamily = FontFamily.Monospace)
            })
        }
    }
}
