package com.novadesk.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novadesk.core.ui.theme.NovaMono

/**
 * Tiny markdown renderer — just enough for agent output: paragraphs,
 * ```code fences``` with a copy button, `inline code`, and **bold**.
 * A full CommonMark engine would cost an extra dependency for little gain
 * on a phone screen; headings/quotes render as plain bold text.
 */
@Composable
fun AgentMarkdown(text: String, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    SelectionContainer(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            var inCode = false
            val code = StringBuilder()
            var codeLang = ""
            for (rawLine in text.split("\n")) {
                if (rawLine.trimStart().startsWith("```")) {
                    if (inCode) {
                        CodeBlock(code = code.toString(), lang = codeLang,
                            onCopy = { clipboard.setText(AnnotatedString(code.toString())) })
                        code.clear()
                        inCode = false
                    } else {
                        inCode = true
                        codeLang = rawLine.trim().removePrefix("```").trim()
                    }
                    continue
                }
                if (inCode) {
                    code.appendLine(rawLine)
                    continue
                }
                if (rawLine.isBlank()) continue
                Text(
                    text = inlineStyle(rawLine),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (inCode && code.isNotEmpty()) {
                CodeBlock(code = code.toString(), lang = codeLang,
                    onCopy = { clipboard.setText(AnnotatedString(code.toString())) })
            }
        }
    }
}

@Composable
private fun CodeBlock(code: String, lang: String, onCopy: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                lang.ifBlank { "code" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            IconButton(onClick = onCopy) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy code")
            }
        }
        Text(
            code.trimEnd(),
            fontFamily = NovaMono,
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        )
    }
}

/** Handles `code` and **bold** inside a single line. */
private fun inlineStyle(line: String): AnnotatedString {
    val builder = androidx.compose.ui.text.buildAnnotatedString {
        var i = 0
        val bold = Regex("\\*\\*(.+?)\\*\\*")
        val code = Regex("`(.+?)`")
        // Walk matches of either kind in order.
        val marks = (bold.findAll(line).map { it to true } +
            code.findAll(line).map { it to false })
            .sortedBy { it.first.range.first }
        for ((m, isBold) in marks) {
            if (m.range.first > i) append(line.substring(i, m.range.first))
            if (isBold) {
                pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold))
                append(m.groupValues[1])
                pop()
            } else {
                pushStyle(
                    androidx.compose.ui.text.SpanStyle(
                        fontFamily = NovaMono,
                        color = androidx.compose.ui.graphics.Color.Unspecified,
                    ),
                )
                append(m.groupValues[1])
                pop()
            }
            i = m.range.last + 1
        }
        if (i < line.length) append(line.substring(i))
    }
    return builder
}
