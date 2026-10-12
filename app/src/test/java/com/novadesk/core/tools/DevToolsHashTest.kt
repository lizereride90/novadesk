package com.novadesk.core.tools

import org.junit.Assert.assertEquals
import org.junit.Test

class DevToolsHashTest {

    @Test
    fun `sha256 matches known vector`() {
        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            DevTools.sha256Hex("hello"),
        )
    }

    @Test
    fun `md5 matches known vector`() {
        assertEquals("5d41402abc4b2a76b9719d911017c592", DevTools.md5Hex("hello"))
    }
}
