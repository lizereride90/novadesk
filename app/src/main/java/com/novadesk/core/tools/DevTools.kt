package com.novadesk.core.tools

import java.security.MessageDigest
import org.json.JSONObject

/**
 * Small offline utilities for the Agents/Tools tab. Each one is a pure
 * function with a unit test — no Android APIs, no surprises.
 */
object DevTools {

    fun sha256Hex(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun md5Hex(text: String): String =
        MessageDigest.getInstance("MD5")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /** Pretty-prints JSON or returns the parse error, never throws. */
    fun formatJson(raw: String): Result<String> =
        runCatching {
            val trimmed = raw.trim()
            val pretty = if (trimmed.startsWith("[")) {
                org.json.JSONArray(trimmed).toString(2)
            } else {
                JSONObject(trimmed).toString(2)
            }
            pretty
        }

    fun isValidJson(raw: String): Boolean = formatJson(raw).isSuccess
}
