package com.novadesk.core.agents

/** Which coding agent a message/session belongs to. */
enum class AgentId(val title: String) {
    OpenCode("OpenCode"),
    Codex("Codex"),
}

/** One chat turn. Tool calls ride along as separate [kind] entries. */
data class AgentMessage(
    val id: Long = System.nanoTime(),
    val role: Role,
    val text: String,
    val kind: Kind = Kind.Chat,
) {
    enum class Role { User, Assistant, System }
    enum class Kind { Chat, ToolCall, ToolResult, Error }
}

/** Lifecycle of a single agent run. */
enum class RunState { Idle, Working, NeedsApproval, Failed }

/**
 * Destructive or sensitive operations pause in [RunState.NeedsApproval]
 * until the user explicitly confirms. The pending command is always shown
 * verbatim — no "trust me" runs.
 */
data class PendingApproval(
    val command: String,
    val reason: String,
)

/** Matches shell text against risky patterns (rm -rf /, mkfs, …). */
object ApprovalPolicy {

    private val risky = listOf(
        Regex("\\brm\\s+(-[^\\s]*r[^\\s]*\\s+)?/(\\s|$)"),
        Regex("\\bmkfs\\b"),
        Regex("\\bdd\\b.*\\bof=/dev/"),
        Regex("\\bshutdown\\b"),
        Regex("\\breboot\\b"),
        Regex(":\\(\\)\\s*\\{"),
        Regex("\\bcurl\\b.*\\|\\s*(sudo\\s+)?(bash|sh)"),
        Regex("\\bwget\\b.*\\|\\s*(sudo\\s+)?(bash|sh)"),
    )

    fun needsApproval(command: String): PendingApproval? {
        val hit = risky.firstOrNull { it.containsMatchIn(command) } ?: return null
        return PendingApproval(
            command = command,
            reason = "This looks destructive or downloads-and-executes code (matched `${hit.pattern.take(48)}…`).",
        )
    }
}
