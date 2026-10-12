package com.novadesk.core.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AnsiParserTest {

    @Test
    fun `strip removes colour codes but keeps text`() {
        val raw = "\u001B[1;32mhello\u001B[0m world"
        assertEquals("hello world", AnsiParser.strip(raw))
    }

    @Test
    fun `strip drops cursor control sequences`() {
        val raw = "\u001B[2K\u001B[1Gprompt$ "
        assertEquals("prompt$ ", AnsiParser.strip(raw))
    }

    @Test
    fun `render keeps plain text intact`() {
        val rendered = AnsiParser.render("plain line")
        assertEquals("plain line", rendered.text)
    }

    @Test
    fun `render preserves text inside colour spans`() {
        val rendered = AnsiParser.render("\u001B[32mgreen\u001B[0m and normal")
        assertEquals("green and normal", rendered.text)
        assertFalse(rendered.spanStyles.isEmpty())
    }
}
