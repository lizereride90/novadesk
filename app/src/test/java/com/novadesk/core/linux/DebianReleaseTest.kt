package com.novadesk.core.linux

import org.junit.Assert.assertEquals
import org.junit.Test

class DebianReleaseTest {

    @Test
    fun `bookworm is the default release`() {
        assertEquals(DebianRelease.Bookworm, DebianRelease.default)
    }

    @Test
    fun `codenames match official debian names`() {
        assertEquals("bookworm", DebianRelease.Bookworm.codename)
        assertEquals("trixie", DebianRelease.Trixie.codename)
    }
}
