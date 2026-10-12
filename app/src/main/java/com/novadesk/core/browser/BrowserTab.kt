package com.novadesk.core.browser

import java.util.UUID

/** One WebView tab. */
data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    var url: String,
    var title: String = url,
    /** True while an agent — not the user — is driving this tab. */
    var agentControlled: Boolean = false,
)

/** Search providers offered in Settings → Browser. */
enum class SearchProvider(val home: String, val query: (String) -> String) {
    DuckDuckGo("https://duckduckgo.com", { "https://duckduckgo.com/?q=${it}" }),
    Google("https://www.google.com", { "https://www.google.com/search?q=${it}" }),
    Brave("https://search.brave.com", { "https://search.brave.com/search?q=${it}" }),
    ;

    companion object {
        fun byName(name: String): SearchProvider =
            values().firstOrNull { it.name == name } ?: DuckDuckGo

        fun resolve(input: String, provider: SearchProvider): String {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) return provider.home
            if (trimmed.contains(".") && !trimmed.contains(" ") &&
                (trimmed.startsWith("http://") || trimmed.startsWith("https://") ||
                    Regex("^[a-z0-9-]+(\\.[a-z0-9-]+)+").containsMatchIn(trimmed))
            ) {
                return if (trimmed.startsWith("http")) trimmed else "https://$trimmed"
            }
            return provider.query(java.net.URLEncoder.encode(trimmed, "UTF-8"))
        }
    }
}
