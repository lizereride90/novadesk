package com.novadesk.core.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.novadesk.core.ui.theme.NovaAccent
import com.novadesk.core.ui.theme.NovaTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
    val openCodeModel: String = "",
    val codexModel: String = "",
    val openCodeApiKey: String = "",
    val codexApiKey: String = "",
)

/**
 * Thin wrapper over DataStore. Plain keys and one data class keep this
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
        val OPENCODE_MODEL = stringPreferencesKey("opencode_model")
        val CODEX_MODEL = stringPreferencesKey("codex_model")
        val OPENCODE_KEY = stringPreferencesKey("opencode_key")
        val CODEX_KEY = stringPreferencesKey("codex_key")
    }

    val flow: Flow<NovaPrefs> = context.prefsStore.data.map { p -> p.toPrefs() }

    /** One-shot snapshot, for callers that just need the current values. */
    suspend fun current(): NovaPrefs = context.prefsStore.data.first().toPrefs()

    private fun androidx.datastore.preferences.core.Preferences.toPrefs() = NovaPrefs(
        theme = runCatching { NovaTheme.valueOf(this[Keys.THEME] ?: "System") }
            .getOrDefault(NovaTheme.System),
        accent = runCatching { NovaAccent.valueOf(this[Keys.ACCENT] ?: "Violet") }
            .getOrDefault(NovaAccent.Violet),
        amoled = this[Keys.AMOLED] == true,
        defaultAgent = this[Keys.DEFAULT_AGENT] ?: "opencode",
        defaultSearch = this[Keys.DEFAULT_SEARCH] ?: "DuckDuckGo",
        agentBrowserControl = this[Keys.AGENT_BROWSER] ?: true,
        confirmSensitiveBrowserActions = this[Keys.CONFIRM_SENSITIVE] ?: true,
        openCodeModel = this[Keys.OPENCODE_MODEL] ?: "",
        codexModel = this[Keys.CODEX_MODEL] ?: "",
        openCodeApiKey = this[Keys.OPENCODE_KEY] ?: "",
        codexApiKey = this[Keys.CODEX_KEY] ?: "",
    )

    suspend fun update(value: NovaPrefs) {
        context.prefsStore.edit { e ->
            e[Keys.THEME] = value.theme.name
            e[Keys.ACCENT] = value.accent.name
            e[Keys.AMOLED] = value.amoled
            e[Keys.DEFAULT_AGENT] = value.defaultAgent
            e[Keys.DEFAULT_SEARCH] = value.defaultSearch
            e[Keys.AGENT_BROWSER] = value.agentBrowserControl
            e[Keys.CONFIRM_SENSITIVE] = value.confirmSensitiveBrowserActions
            e[Keys.OPENCODE_MODEL] = value.openCodeModel
            e[Keys.CODEX_MODEL] = value.codexModel
            e[Keys.OPENCODE_KEY] = value.openCodeApiKey
            e[Keys.CODEX_KEY] = value.codexApiKey
        }
    }
}
