package com.novadesk.feature.files

import org.junit.Assert.assertEquals
import org.junit.Test

class SyntaxLangTest {

    @Test
    fun `extensions map to languages`() {
        assertEquals(Syntax.Lang.Kotlin, Syntax.Lang.forFile("Main.kt"))
        assertEquals(Syntax.Lang.Python, Syntax.Lang.forFile("tool.py"))
        assertEquals(Syntax.Lang.JavaScript, Syntax.Lang.forFile("app.tsx"))
        assertEquals(Syntax.Lang.Markdown, Syntax.Lang.forFile("README.md"))
        assertEquals(Syntax.Lang.Plain, Syntax.Lang.forFile("LICENSE"))
    }
}
