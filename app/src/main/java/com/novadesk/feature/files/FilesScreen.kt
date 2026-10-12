package com.novadesk.feature.files

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.novadesk.core.storage.FileEntry
import com.novadesk.core.storage.Project
import java.io.File
import kotlinx.coroutines.launch

/**
 * File manager rooted at Debian home. Browsing is sandboxed to
 * [AppContainer.allowedRoots] — the repository layer refuses escapes.
 */
@Composable
fun FilesScreen(container: AppContainer, nav: NavController) {
    val scope = rememberCoroutineScope()
    val home = container.debianLayout.homeDir
    var dir by remember { mutableStateOf(home) }
    var entries by remember { mutableStateOf<List<FileEntry>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var createName by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<FileEntry?>(null) }

    fun roots() = container.allowedRoots(container.debianLayout)
    fun refresh() {
        scope.launch {
            try {
                entries = container.files.list(dir, roots())
                error = null
            } catch (e: Exception) {
                error = e.message
            }
        }
    }
    LaunchedEffect(dir) { refresh() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Row {
                IconButton(
                    enabled = dir != home && dir.parentFile != null,
                    onClick = { dir = dir.parentFile ?: home },
                ) { Icon(Icons.Filled.ArrowBack, contentDescription = "Up") }
                Column(Modifier.padding(start = 4.dp)) {
                    Text("Files", style = MaterialTheme.typography.headlineSmall)
                    Text(dir.absolutePath, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row {
                IconButton(onClick = { showCreate = true }) {
                    Icon(Icons.Filled.CreateNewFolder, contentDescription = "New folder")
                }
                IconButton(onClick = {
                    scope.launch {
                        container.projects.upsert(
                            Project(name = dir.name.ifBlank { "home" }, rootPath = debianPath(dir, container)),
                        )
                    }
                }) { Icon(Icons.Filled.DriveFileMove, contentDescription = "Save as project") }
            }
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }

        LazyColumn(Modifier.weight(1f)) {
            items(entries, key = { it.path }) { entry ->
                ListItem(
                    headlineContent = { Text(entry.name) },
                    supportingContent = {
                        Text(if (entry.isDir) "folder" else "${entry.size} bytes")
                    },
                    leadingContent = {
                        Icon(
                            if (entry.isDir) Icons.Filled.Folder else Icons.Filled.Description,
                            contentDescription = null,
                        )
                    },
                    trailingContent = {
                        if (!entry.isDir) {
                            IconButton(onClick = { pendingDelete = entry }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete")
                            }
                        } else {
                            IconButton(onClick = { pendingDelete = entry }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete")
                            }
                        }
                    },
                    modifier = Modifier.clickable {
                        if (entry.isDir) {
                            dir = File(entry.path)
                        } else {
                            nav.navigate(Routes.editor(entry.path))
                        }
                    },
                )
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("New folder or file") },
            text = {
                OutlinedTextField(value = createName, onValueChange = { createName = it },
                    label = { Text("Name (end with / for folder)") }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            if (createName.endsWith("/")) {
                                container.files.mkdir(dir, roots(), createName.trim('/'))
                            } else if (createName.isNotBlank()) {
                                container.files.writeText(File(dir, createName), roots(), "")
                            }
                            createName = ""
                            showCreate = false
                            refresh()
                        } catch (e: Exception) {
                            error = e.message
                        }
                    }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } },
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete ${target.name}?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            container.files.delete(File(target.path), roots())
                        } catch (e: Exception) {
                            error = e.message
                        }
                        pendingDelete = null
                        refresh()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Keep") } },
        )
    }
}

/** Best-effort mapping of a device path back to its Debian-side path. */
private fun debianPath(file: File, container: AppContainer): String {
    val home = container.debianLayout.homeDir.canonicalPath
    val rootfs = container.debianLayout.rootfsDir.canonicalPath
    val canon = try { file.canonicalPath } catch (_: Exception) { file.absolutePath }
    return when {
        canon.startsWith(home) -> "/home/user" + canon.removePrefix(home)
        canon.startsWith(rootfs) -> canon.removePrefix(rootfs).ifBlank { "/" }
        else -> canon
    }
}
