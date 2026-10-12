package com.novadesk.core.storage

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FilesRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `paths inside the workspace resolve`() {
        val repo = FilesRepository()
        val root = tmp.newFolder("home")
        val child = File(root, "a.txt").apply { writeText("hi") }
        assertEquals(child.canonicalFile, repo.resolve(child, listOf(root)))
    }

    @Test
    fun `escapes outside the workspace are refused`() {
        val repo = FilesRepository()
        val root = tmp.newFolder("home")
        val outside = tmp.newFile("evil.txt")
        try {
            repo.resolve(outside, listOf(root))
            fail("expected refusal")
        } catch (e: IllegalArgumentException) {
            // fail closed, as designed
        }
    }
}
