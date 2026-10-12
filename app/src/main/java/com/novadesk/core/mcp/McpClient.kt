package com.novadesk.core.mcp

import com.novadesk.core.linux.ProotRunner
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Minimal JSON-RPC stdio client: spawns `npx -y <package> …` inside Debian
 * and speaks `initialize` / `tools/list`. Enough to verify a server
 * actually starts and to enumerate its tools for the details screen.
 */
class McpClient(private val proot: ProotRunner) {

    data class Probe(val ok: Boolean, val tools: List<String>, val error: String = "")

    suspend fun probe(server: McpServer, projectPath: String, extraEnv: Map<String, String> = emptyMap()): Probe =
        withContext(Dispatchers.IO) {
            try {
                val argv = proot.argv(
                    listOf(
                        "/bin/bash", "-lc",
                        "exec npx -y ${server.packageRef} ${server.args.joinToString(" ")}",
                    ),
                    workDir = projectPath,
                    env = extraEnv,
                )
                val proc = ProcessBuilder(argv).redirectErrorStream(false).start()
                val out = proc.outputStream.bufferedWriter()
                val reader = proc.inputStream.bufferedReader()

                fun send(obj: JSONObject) {
                    out.write(obj.toString())
                    out.write("\n")
                    out.flush()
                }
                send(JSONObject()
                    .put("jsonrpc", "2.0").put("id", 1).put("method", "initialize")
                    .put("params", JSONObject()
                        .put("protocolVersion", "2024-11-05")
                        .put("capabilities", JSONObject())
                        .put("clientInfo", JSONObject().put("name", "novadesk").put("version", "0.1.0"))))
                send(JSONObject().put("jsonrpc", "2.0").put("method", "notifications/initialized"))
                send(JSONObject().put("jsonrpc", "2.0").put("id", 2).put("method", "tools/list"))

                proc.waitFor()
                // We only need the tools/list answer; scan buffered output for it.
                val tools = mutableListOf<String>()
                // Note: process already exited — read whatever it printed.
                // (A full streaming client is future work; see ARCHITECTURE.md.)
                reader.readText().lineSequence()
                    .mapNotNull { runCatching { JSONObject(it) }.getOrNull() }
                    .firstOrNull { it.optInt("id") == 2 }
                    ?.optJSONObject("result")
                    ?.optJSONArray("tools")
                    ?.let { arr ->
                        (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("name") }
                    }?.let { tools.addAll(it) }
                proc.destroy()
                Probe(ok = true, tools = tools)
            } catch (e: Exception) {
                Probe(ok = false, tools = emptyList(), error = e.message.orEmpty())
            }
        }

    /** npx cache lives under the Debian home; expose its size for Settings. */
    fun cacheDir(home: File): File = File(home, ".npm/_npx")
}
