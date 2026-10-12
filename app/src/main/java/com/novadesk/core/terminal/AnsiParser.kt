package com.novadesk.core.terminal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.novadesk.core.ui.theme.TermBlack
import com.novadesk.core.ui.theme.TermBlue
import com.novadesk.core.ui.theme.TermCyan
import com.novadesk.core.ui.theme.TermGreen
import com.novadesk.core.ui.theme.TermMagenta
import com.novadesk.core.ui.theme.TermWhite
import com.novadesk.core.ui.theme.TermYellow

/**
 * Minimal ANSI SGR parser. Handles the color/bold/reset sequences real
 * shells actually emit (ls, git, pytest, …) and drops cursor-movement codes
 * we do not model. Anything unrecognised is stripped, never shown raw.
 */
object AnsiParser {

    private val Sequence = Regex("\u001B\\[([0-9;?]*)?([A-Za-z])")
    private val Palette = mapOf(
        30 to TermBlack, 31 to Color(0xFFF87171), 32 to TermGreen,
        33 to TermYellow, 34 to TermBlue, 35 to TermMagenta,
        36 to TermCyan, 37 to TermWhite,
        90 to Color(0xFF9CA3AF), 91 to Color(0xFFFCA5A5),
        92 to Color(0xFF86EFAC), 93 to Color(0xFFFDE047),
        94 to Color(0xFF93C5FD), 95 to Color(0xFFD8B4FE),
        96 to Color(0xFF67E8F9), 97 to Color.White,
    )

    fun render(raw: String): AnnotatedString = buildAnnotatedString {
        var color: Color? = null
        var bold = false
        var cursor = 0
        for (match in Sequence.findAll(raw)) {
            if (match.range.first > cursor) {
                pushEmit(raw.substring(cursor, match.range.first), color, bold)
            }
            val params = match.groupValues[1].split(";").mapNotNull { it.toIntOrNull() }
            val cmd = match.groupValues[2]
            if (cmd == "m") {
                if (params.isEmpty() || params == listOf(0)) {
                    color = null; bold = false
                } else {
                    for (p in params) {
                        when (p) {
                            0 -> { color = null; bold = false }
                            1 -> bold = true
                            22 -> bold = false
                            in 30..37, in 90..97 -> color = Palette[p]
                        }
                    }
                }
            }
            // Other final bytes (K, H, J, …) are terminal control — drop them.
            cursor = match.range.last + 1
        }
        if (cursor < raw.length) pushEmit(raw.substring(cursor), color, bold)
    }

    private fun AnnotatedString.Builder.pushEmit(text: String, color: Color?, bold: Boolean) {
        if (color == null && !bold) {
            append(text)
        } else {
            pushStyle(SpanStyle(color = color ?: Color.Unspecified, fontWeight = if (bold) FontWeight.Bold else null))
            append(text)
            pop()
        }
    }

    /** Plain-text version for copy/search. */
    fun strip(raw: String): String =
        Sequence.replace(raw, "").replace(Regex("[\u0000-\u0008\u000B\u000C\u000E-\u001F]"), "")
}
