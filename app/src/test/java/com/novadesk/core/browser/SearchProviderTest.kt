package com.novadesk.core.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchProviderTest {

    @Test
    fun `bare words become a search URL`() {
        val url = SearchProvider.resolve("debian proot", SearchProvider.DuckDuckGo)
        assertTrue(url.startsWith("https://duckduckgo.com/?q="))
        assertTrue("debian" in url)
    }

    @Test
    fun `domain without scheme gains https`() {
        assertEquals("https://github.com", SearchProvider.resolve("github.com", SearchProvider.DuckDuckGo))
    }

    @Test
    fun `full URL passes through`() {
        assertEquals(
            "https://example.com/x",
            SearchProvider.resolve("https://example.com/x", SearchProvider.Google),
        )
    }

    @Test
    fun `empty input returns provider home`() {
        assertEquals("https://duckduckgo.com", SearchProvider.resolve("", SearchProvider.DuckDuckGo))
    }
}
