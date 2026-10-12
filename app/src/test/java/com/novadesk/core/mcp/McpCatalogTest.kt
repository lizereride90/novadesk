package com.novadesk.core.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class McpCatalogTest {

    @Test
    fun `catalog only references real upstream packages`() {
        val refs = McpCatalog.servers.map { it.packageRef }
        assertTrue(refs.contains("@modelcontextprotocol/server-filesystem"))
        assertTrue(refs.contains("@modelcontextprotocol/server-git"))
        assertTrue(refs.contains("@openai/codex").not())
    }

    @Test
    fun `search is case-insensitive and category-aware`() {
        val hits = McpCatalog.search("git", "All")
        assertTrue(hits.any { it.id == "git" })
        val scoped = McpCatalog.search("git", "Databases")
        assertTrue(scoped.none { it.id == "git" })
    }

    @Test
    fun `secret-gated servers fail validation without secrets`() {
        val registry = McpRegistry(java.io.File("/tmp/nonexistent-novadesk-test"))
        val github = McpCatalog.servers.first { it.id == "github" }
        val problem = registry.validate(github, null)
        assertTrue(problem != null && "GITHUB_PERSONAL_ACCESS_TOKEN" in problem)
    }

    @Test
    fun `secret-free servers validate cleanly`() {
        val registry = McpRegistry(java.io.File("/tmp/nonexistent-novadesk-test"))
        val fs = McpCatalog.servers.first { it.id == "filesystem" }
        assertEquals(null, registry.validate(fs, null))
    }
}
