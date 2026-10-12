package com.novadesk.core.navigation

/** Single source of truth for every destination in the app. */
object Routes {
    const val HOME = "home"
    const val AGENTS = "agents"
    const val TERMINAL = "terminal"
    const val FILES = "files"
    const val MORE = "more"

    const val OPENCODE = "opencode"
    const val CODEX = "codex"
    const val EDITOR = "editor?path={path}"
    const val BROWSER = "browser"
    const val MCP_STORE = "mcp"
    const val SETTINGS = "settings"
    const val DEBIAN_SETUP = "debian_setup"

    fun editor(path: String) = "editor?path=$path"
}
