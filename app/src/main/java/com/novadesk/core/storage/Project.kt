package com.novadesk.core.storage

import java.util.UUID

/** A workspace: one repo/folder plus the agent + sessions attached to it. */
data class Project(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    /** Absolute path inside Debian (e.g. /home/user/my-app). */
    val rootPath: String,
    val preferredAgent: String = "opencode",
    val lastOpenedAt: Long = System.currentTimeMillis(),
)
