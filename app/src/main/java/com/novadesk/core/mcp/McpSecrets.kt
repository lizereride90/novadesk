package com.novadesk.core.mcp

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.mcpSecretsStore by preferencesDataStore("mcp_secrets")

/**
 * App-private secret storage for MCP env vars (API keys, tokens).
 * DataStore keeps them out of the exported config and out of git;
 * export/import flows explicitly skip this store.
 */
class McpSecrets(private val context: Context) {

    suspend fun get(varName: String): String? =
        context.mcpSecretsStore.data.map { it[stringPreferencesKey(keyOf(varName))] }.first()

    suspend fun set(varName: String, value: String) {
        context.mcpSecretsStore.edit { it[stringPreferencesKey(keyOf(varName))] = value }
    }

    suspend fun clear(varName: String) {
        context.mcpSecretsStore.edit { it.remove(stringPreferencesKey(keyOf(varName))) }
    }

    private fun keyOf(varName: String) = "mcp_secret_$varName"
}
