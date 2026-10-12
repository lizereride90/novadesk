package com.novadesk.feature.files

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.novadesk.core.storage.AppContainer
import com.novadesk.core.ui.components.AgentMarkdown
import com.novadesk.core.ui.theme.NovaMono
import java.io.File
import kotlinx.coroutines.launch

/**
 * Code editor. Plain [TextField] with regex highlighting, line numbers and
 * a dirty dot — plus markdown preview and JSON validation for those two
 * very common cases.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(container: AppContainer, nav: NavController) {
    val path = nav.currentBackStackEntry?.arguments?.getString("path").orEmpty()
    val scope = rememberCoroutineScope()
    val file = remember(path) { File(path) }
    val lang = remember(path) { Syntax.Lang.forFile(file.name) }

    var plain by remember(path) { mutableStateOf("") }
    var selection by remember(path) { mutableStateOf(TextRange.Zero) }
    var savedText by remember(path) { mutableStateOf<String?>(null) }
    var error by remember(path) { mutableStateOf<String?>(null) }
    var status by remember(path) { mutableStateOf("Loading…") }
    var preview by remember { mutableStateOf(false) }
    val dirty = savedText != null && plain != savedText

    // Highlighted view of the buffer. TextFieldValue carries the spans, so
    // the editable field itself is syntax-coloured while typing stays plain.
    val field = remember(plain, selection) {
        TextFieldValue(Syntax.highlight(plain, lang), selection)
    }

    LaunchedEffect(path) {
        try {
            val text = container.files.readText(file, container.allowedRoots(container.debianLayout))
            plain = text
            selection = TextRange(text.length)
            savedText = text
            status = ""
        } catch (e: Exception) {
            error = e.message
            status = ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text((if (dirty) "● " else "") + file.name) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (lang == Syntax.Lang.Markdown) {
                        IconButton(onClick = { preview = !preview }) {
                            Icon(Icons.Filled.Preview, contentDescription = "Preview")
                        }
                    }
                    IconButton(onClick = {
                        scope.launch {
                            try {
                                container.files.writeText(
                                    file, container.allowedRoots(container.debianLayout), plain,
                                )
                                savedText = plain
                                status = "Saved."
                            } catch (e: Exception) {
                                error = e.message
                            }
                        }
                    }) { Icon(Icons.Filled.Save, contentDescription = "Save") }
                },
            )
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            } else if (status.isNotBlank()) {
                Text(status, modifier = Modifier.padding(16.dp))
            }
            if (lang == Syntax.Lang.Markdown && preview) {
                AgentMarkdown(
                    plain,
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                )
            } else {
                Row(Modifier.weight(1f)) {
                    val lines = plain.count { it == '\n' } + 1
                    Text(
                        (1..lines).joinToString("\n"),
                        fontFamily = NovaMono,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 6.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                    TextField(
                        value = field,
                        onValueChange = {
                            plain = it.text
                            selection = it.selection
                        },
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = NovaMono, fontSize = 13.sp, lineHeight = 20.sp,
                        ),
                    )
                }
                if (file.extension.lowercase() == "json" && savedText != null) {
                    Text(
                        if (com.novadesk.core.tools.DevTools.isValidJson(plain)) "Valid JSON ✓"
                        else "Invalid JSON — fix before saving configs.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Tab key: phones don't have one, code needs it.
                androidx.compose.material3.AssistChip(onClick = {
                    val c = selection.start
                    val next = plain.substring(0, c) + "    " + plain.substring(selection.end)
                    plain = next
                    selection = TextRange(c + 4)
                }, label = { Text("Tab") })
                Text(
                    "${plain.length} chars · ${lang.name.lowercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, top = 12.dp),
                )
            }
        }
    }
}
