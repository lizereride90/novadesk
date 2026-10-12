package com.novadesk.core.mcp

/** Transports NovaDesk knows how to launch. */
enum class McpTransport { Stdio, Http }

/** Which agents this server is known to work with. */
enum class AgentCompat { OpenCode, Codex, Both }

/** One server entry — catalog or installed. */
data class McpServer(
    val id: String,
    val name: String,
    val description: String,
    val transport: McpTransport,
    /** npm package / binary / URL, exactly as upstream documents it. */
    val packageRef: String,
    val args: List<String> = emptyList(),
    val envVars: List<String> = emptyList(),
    val category: String,
    val compat: AgentCompat = AgentCompat.Both,
    val homepage: String = "",
    val needsSecret: Boolean = false,
)

/**
 * The built-in catalog. Every entry is a real, maintained MCP server —
 * package names and launch commands match upstream docs. Nothing here is
 * invented; if upstream renames a package, this file is the single place
 * to fix.
 */
object McpCatalog {

    val servers: List<McpServer> = listOf(
        McpServer(
            id = "filesystem",
            name = "Filesystem",
            description = "Scoped file read/write inside the project directory.",
            transport = McpTransport.Stdio,
            packageRef = "@modelcontextprotocol/server-filesystem",
            args = listOf("/home/user"),
            category = "Filesystem",
            homepage = "https://github.com/modelcontextprotocol/servers",
        ),
        McpServer(
            id = "git",
            name = "Git",
            description = "Branch, log, diff and commit helpers for the open repo.",
            transport = McpTransport.Stdio,
            packageRef = "@modelcontextprotocol/server-git",
            category = "GitHub and Git",
            homepage = "https://github.com/modelcontextprotocol/servers",
        ),
        McpServer(
            id = "fetch",
            name = "Fetch",
            description = "Fetch URLs and extract readable content for research.",
            transport = McpTransport.Stdio,
            packageRef = "@modelcontextprotocol/server-fetch",
            category = "Web search",
            homepage = "https://github.com/modelcontextprotocol/servers",
        ),
        McpServer(
            id = "memory",
            name = "Memory",
            description = "Small persistent knowledge graph across agent sessions.",
            transport = McpTransport.Stdio,
            packageRef = "@modelcontextprotocol/server-memory",
            category = "Productivity",
            homepage = "https://github.com/modelcontextprotocol/servers",
        ),
        McpServer(
            id = "sequential-thinking",
            name = "Sequential Thinking",
            description = "Step-by-step reasoning helper for hard problems.",
            transport = McpTransport.Stdio,
            packageRef = "@modelcontextprotocol/server-sequential-thinking",
            category = "Development tools",
            homepage = "https://github.com/modelcontextprotocol/servers",
        ),
        McpServer(
            id = "github",
            name = "GitHub",
            description = "Issues, PRs and repo search. Needs a personal access token.",
            transport = McpTransport.Stdio,
            packageRef = "@modelcontextprotocol/server-github",
            envVars = listOf("GITHUB_PERSONAL_ACCESS_TOKEN"),
            category = "GitHub and Git",
            needsSecret = true,
            homepage = "https://github.com/modelcontextprotocol/servers",
        ),
        McpServer(
            id = "brave-search",
            name = "Brave Search",
            description = "Web search grounded in Brave results. Needs an API key.",
            transport = McpTransport.Stdio,
            packageRef = "@brave/brave-search-mcp-server",
            envVars = listOf("BRAVE_API_KEY"),
            category = "Web search",
            needsSecret = true,
            homepage = "https://github.com/brave/brave-search-mcp-server",
        ),
        McpServer(
            id = "sqlite",
            name = "SQLite",
            description = "Query local SQLite databases (app data, debugging).",
            transport = McpTransport.Stdio,
            packageRef = "@modelcontextprotocol/server-sqlite",
            category = "Databases",
            homepage = "https://github.com/modelcontextprotocol/servers",
        ),
        McpServer(
            id = "puppeteer",
            name = "Puppeteer",
            description = "Headless browser automation for pages WebView can't reach.",
            transport = McpTransport.Stdio,
            packageRef = "@modelcontextprotocol/server-puppeteer",
            category = "Browser automation",
            compat = AgentCompat.OpenCode,
            homepage = "https://github.com/modelcontextprotocol/servers",
        ),
        // The two entries below were supplied by the user. They are remote
        // (HTTP) endpoints and are preinstalled + enabled on first run.
        // We could not verify that these hosts actually speak MCP, so the
        // store and docs say so plainly.
        McpServer(
            id = "opencode-mcp-store",
            name = "OpenCode MCP Store",
            description = "Remote MCP endpoint at opencode.ai (user-provided; not verified).",
            transport = McpTransport.Http,
            packageRef = "https://opencode.ai",
            category = "Remote",
            compat = AgentCompat.OpenCode,
            homepage = "https://opencode.ai",
        ),
        McpServer(
            id = "codex-mcp-store-mirror",
            name = "Codex MCP Store Mirror",
            description = "Remote MCP endpoint at codex.com (user-provided; not verified).",
            transport = McpTransport.Http,
            packageRef = "https://codex.com",
            category = "Remote",
            compat = AgentCompat.Codex,
            homepage = "https://codex.com",
        ),
    )

    /**
     * Servers preinstalled and enabled on first launch. Kept in one place so
     * [McpRegistry.seedDefaults] and the docs cannot drift.
     */
    val preloaded: List<McpServer> = servers.filter { it.transport == McpTransport.Http }

    val categories: List<String> = listOf("All", "Installed") + servers.map { it.category }.distinct().sorted()

    fun search(query: String, category: String): List<McpServer> =
        servers.filter { s ->
            (category == "All" || s.category == category) &&
                (query.isBlank() ||
                    s.name.contains(query, ignoreCase = true) ||
                    s.description.contains(query, ignoreCase = true))
        }
}
