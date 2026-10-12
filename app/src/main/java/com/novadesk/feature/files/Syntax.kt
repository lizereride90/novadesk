package com.novadesk.feature.files

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight

/**
 * Regex-based highlighting for the editor. Good enough to make code
 * scannable; a tree-sitter grade parser would be lovely but is not what
 * ships in v0.1.
 */
object Syntax {

    enum class Lang(val extensions: Set<String>) {
        Kotlin(setOf("kt", "kts")),
        Python(setOf("py")),
        JavaScript(setOf("js", "jsx", "ts", "tsx", "json")),
        Shell(setOf("sh", "bash", "zsh")),
        Markdown(setOf("md", "markdown")),
        Toml(setOf("toml", "gradle.kts")),
        Plain(emptySet()),
        ;

        companion object {
            fun forFile(name: String): Lang {
                val ext = name.substringAfterLast(".", "").lowercase()
                if (name == "build.gradle.kts" || name.endsWith(".gradle.kts")) return Toml
                return values().firstOrNull { ext in it.extensions } ?: Plain
            }
        }
    }

    private val Keyword = Color(0xFFC084FC)
    private val StringLit = Color(0xFF86EFAC)
    private val Comment = Color(0xFF6E6890)
    private val Number = Color(0xFFFBBF24)
    private val Annotation = Color(0xFF67E8F9)

    fun highlight(text: String, lang: Lang): AnnotatedString {
        if (lang == Lang.Plain || lang == Lang.Markdown) return AnnotatedString(text)
        return buildAnnotatedString {
            append(text)
            // Comments first so keywords inside them don't get painted.
            val commentRanges = mutableListOf<IntRange>()
            val commentRe = when (lang) {
                Lang.Python, Lang.Shell -> Regex("#[^\n]*")
                else -> Regex("//[^\n]*|/\\*[\\s\\S]*?\\*/")
            }
            for (m in commentRe.findAll(text)) {
                addStyle(SpanStyle(color = Comment), m.range.first, m.range.last + 1)
                commentRanges += m.range
            }
            fun inComment(i: Int) = commentRanges.any { i in it }

            val stringRe = Regex("\"(\\\\.|[^\"\\\\])*\"|'(\\\\.|[^'\\\\])*'|`[^`]*`")
            val stringRanges = mutableListOf<IntRange>()
            for (m in stringRe.findAll(text)) {
                if (inComment(m.range.first)) continue
                addStyle(SpanStyle(color = StringLit), m.range.first, m.range.last + 1)
                stringRanges += m.range
            }
            fun inString(i: Int) = stringRanges.any { i in it }

            val keywords = when (lang) {
                Lang.Kotlin -> "\\b(fun|val|var|class|object|interface|if|else|when|for|while|return|import|package|data|suspend|override|private|public|internal|companion|sealed|enum|null|true|false|this|it|in|is|as|try|catch|finally|throw)\\b"
                Lang.Python -> "\\b(def|class|if|elif|else|for|while|return|import|from|as|with|lambda|None|True|False|self|try|except|finally|raise|pass|in|is|not|and|or)\\b"
                Lang.JavaScript -> "\\b(function|const|let|var|class|if|else|for|while|return|import|from|export|default|new|this|null|true|false|typeof|async|await)\\b"
                Lang.Shell -> "\\b(if|then|else|fi|for|while|do|done|function|return|exit|export|in|case|esac)\\b"
                else -> "\\b(val|if|else)\\b"
            }
            for (m in Regex(keywords).findAll(text)) {
                if (inComment(m.range.first) || inString(m.range.first)) continue
                addStyle(SpanStyle(color = Keyword, fontWeight = FontWeight.SemiBold), m.range.first, m.range.last + 1)
            }
            for (m in Regex("\\b\\d[\\d_]*\\b").findAll(text)) {
                if (inComment(m.range.first) || inString(m.range.first)) continue
                addStyle(SpanStyle(color = Number), m.range.first, m.range.last + 1)
            }
            for (m in Regex("@\\w+").findAll(text)) {
                if (inComment(m.range.first) || inString(m.range.first)) continue
                addStyle(SpanStyle(color = Annotation), m.range.first, m.range.last + 1)
            }
        }
    }
}
