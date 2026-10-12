package com.novadesk.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novadesk.core.agents.AgentMessage
import com.novadesk.core.agents.PendingApproval
import com.novadesk.core.agents.RunState

/**
 * Chat surface shared by the OpenCode and Codex screens. One implementation
 * so approvals, cancellation and tool-call styling behave identically.
 */
@Composable
fun AgentChat(
    messages: List<AgentMessage>,
    runState: RunState,
    pending: PendingApproval?,
    onSend: (String) -> Unit,
    onCancel: () -> Unit,
    onApprove: () -> Unit,
    onDismissApproval: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (pending != null) {
            ApprovalCard(pending, onApprove, onDismissApproval)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages, key = { it.id }) { msg -> MessageBubble(msg) }
            if (runState == RunState.Working) {
                item {
                    Text(
                        "Working…",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask for code, a fix, a refactor…") },
                maxLines = 4,
                shape = RoundedCornerShape(16.dp),
            )
            if (runState == RunState.Working) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Filled.Stop, contentDescription = "Stop")
                }
            } else {
                IconButton(onClick = {
                    if (draft.isNotBlank()) {
                        onSend(draft.trim())
                        draft = ""
                    }
                }) {
                    Icon(Icons.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: AgentMessage) {
    val isUser = msg.role == AgentMessage.Role.User
    val container = when {
        isUser -> MaterialTheme.colorScheme.primaryContainer
        msg.kind == AgentMessage.Kind.Error -> MaterialTheme.colorScheme.errorContainer
        msg.kind == AgentMessage.Kind.ToolCall -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            if (msg.kind == AgentMessage.Kind.ToolCall) {
                Text(
                    "tool",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            if (isUser) {
                Text(msg.text, style = MaterialTheme.typography.bodyMedium)
            } else {
                AgentMarkdown(msg.text)
            }
        }
    }
}

@Composable
private fun ApprovalCard(pending: PendingApproval, onApprove: () -> Unit, onDismiss: () -> Unit) {
    NovaCard {
        Text("Approval needed", style = MaterialTheme.typography.titleMedium)
        Text(pending.reason, style = MaterialTheme.typography.bodyMedium)
        Text(
            pending.command,
            fontFamily = com.novadesk.core.ui.theme.NovaMono,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onApprove) { Text("Run it") }
            androidx.compose.material3.OutlinedButton(onClick = onDismiss) { Text("Skip") }
        }
    }
}

/** Branded header so it is always obvious which agent you are talking to. */
@Composable
fun AgentBrandHeader(name: String, tagline: String, accent: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).background(accent, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.take(1),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                tagline,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
