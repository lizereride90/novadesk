package com.novadesk.core.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.novadesk.core.ui.theme.NovaAccent
import com.novadesk.core.ui.theme.NovaTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.prefsStore by preferencesDataStore("novadesk")

/** Snapshot of user-tunable app settings. */
data class NovaPrefs(
    val theme: NovaTheme = NovaTheme.System,
    val accent: NovaAccent = NovaAccent.Violet,
    val amoled: Boolean = false,
    val defaultAgent: String = "opencode",
    val defaultSearch: String = "DuckDuckGo",
    val agentBrowserControl: Boolean = true,
    val confirmSensitiveBrowserActions: Boolean = true,
)

/**
 * Thin wrapper over DataStore. Plain keys + a single data class keep this
 * boring on purpose — settings must never be the thing that crashes.
 */
class AppPrefs(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val ACCENT = stringPreferencesKey("accent")
        val AMOLED = booleanPreferencesKey("amoled")
        val DEFAULT_AGENT = stringPreferencesKey("default_agent")
        val DEFAULT_SEARCH = stringPreferencesKey("default_search")
        val AGENT_BROWSER = booleanPreferencesKey("agent_browser")
        val CONFIRM_SENSITIVE = booleanPreferencesKey("confirm_sensitive")
    }

    val flow: Flow<NovaPrefs> =
        context.prefsStore.data.map { p ->
            NovaPrefs(
                theme = runCatching { NovaTheme.valueOf(p[Keys.THEME] ?: "System") }
                    .getOrDefault(NovaTheme.System),
                accent = runCatching { NovaAccent.valueOf(p[Keys.ACCENT] ?: "Violet") }
                    .getOrDefault(NovaAccent.Violet),
                amoled = p[Keys.AMOLED] == true,
                defaultAgent = p[Keys.DEFAULT_AGENT] ?: "opencode",
                defaultSearch = p[Keys.DEFAULT_SEARCH] ?: "DuckDuckGo",
                agentBrowserControl = p[Keys.AGENT_BROWSER] ?: true,
                confirmSensitiveBrowserActions = p[Keys.CONFIRM_SENSITIVE] ?: true,
            )
        }

    suspend fun update(value: NovaPrefs) {
        context.prefsStore.edit { e ->
            e[Keys.THEME] = value.theme.name
            e[Keys.ACCENT] = value.accent.name
            e[Keys.AMOLED] = value.amoled
            e[Keys.DEFAULT_AGENT] = value.defaultAgent
            e[Keys.DEFAULT_SEARCH] = value.defaultSearch
            e[Keys.AGENT_BROWSER] = value.agentBrowserControl
            e[Keys.CONFIRM_SENSITIVE] = value.confirmSensitiveBrowserActions
        }
    }
}
