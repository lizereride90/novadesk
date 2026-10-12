package com.novadesk.core.agents

import com.novadesk.core.linux.DebianLayout
import com.novadesk.core.mcp.McpCatalog
import com.novadesk.core.mcp.McpTransport
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Writes the on-disk configuration both CLIs read, so the MCP servers and
 * the browser bridge are available the first time an agent runs.
 *
 * Files (guest paths are backed by the persistent home):
 * - `~/.config/opencode/opencode.json`
 * - `~/.codex/config.toml`
 * - `~/.novadesk/browser-mcp.mjs`  (Node MCP server the agents call)
 *
 * JSON is built with [JSONObject] so we can never emit an invalid config.
 */
class AgentConfig(
    private val layout: DebianLayout,
    private val bridgeUrl: String,
    private val bridgeToken: String,
) {

    val browserMcpScript: File get() = File(layout.homeDir, ".novadesk/browser-mcp.mjs")
    val browserMcpGuestPath: String get() = "/home/user/.novadesk/browser-mcp.mjs"

    fun writeAll(modelOpenCode: String?, modelCodex: String?) {
        writeBrowserMcpServer()
        writeOpenCodeConfig(modelOpenCode)
        writeCodexConfig(modelCodex)
    }

    private fun writeBrowserMcpServer() {
        File(layout.homeDir, ".novadesk").mkdirs()
        browserMcpScript.writeText(BROWSER_MCP_JS)
    }

    private fun writeOpenCodeConfig(model: String?) {
        val dir = File(layout.homeDir, ".config/opencode").apply { mkdirs() }

        val mcp = JSONObject()
        mcp.put(
            "novadesk-browser",
            JSONObject()
                .put("type", "local")
                .put("command", JSONArray(listOf("node", browserMcpGuestPath)))
                .put(
                    "environment",
                    JSONObject()
                        .put("NOVADESK_BRIDGE_URL", bridgeUrl)
                        .put("NOVADESK_BRIDGE_TOKEN", bridgeToken),
                )
                .put("enabled", true),
        )
        McpCatalog.servers
            .filter { it.transport == McpTransport.Http }
            .forEach { s ->
                mcp.put(
                    s.id,
                    JSONObject().put("type", "remote").put("url", s.packageRef).put("enabled", true),
                )
            }

        val root = JSONObject().put("\$schema", "https://opencode.ai/config.json").put("mcp", mcp)
        if (model != null) root.put("model", model)

        File(dir, "opencode.json").writeText(root.toString(2) + "\n")
    }

    private fun writeCodexConfig(model: String?) {
        val dir = File(layout.homeDir, ".codex").apply { mkdirs() }
        File(dir, "config.toml").writeText(
            buildString {
                if (model != null) append("model = \"$model\"\n\n")
                append("[mcp_servers.novadesk_browser]\n")
                append("command = \"node\"\n")
                append("args = [\"$browserMcpGuestPath\"]\n")
                append(
                    "env = { NOVADESK_BRIDGE_URL = \"$bridgeUrl\", " +
                        "NOVADESK_BRIDGE_TOKEN = \"$bridgeToken\" }\n",
                )
            },
        )
    }

    private companion object {
        /** Dependency-free MCP stdio server that proxies to the app. */
        val BROWSER_MCP_JS = """
            // NovaDesk browser bridge — MCP stdio server that forwards tool
            // calls to the Android app over localhost.
            import readline from "node:readline";

            const URL_ = process.env.NOVADESK_BRIDGE_URL || "http://127.0.0.1:8799/tool";
            const TOKEN = process.env.NOVADESK_BRIDGE_TOKEN || "";

            const TOOLS = [
              { name: "browser_open", description: "Open a URL in the NovaDesk browser.", inputSchema: { type: "object", properties: { url: { type: "string" } }, required: ["url"] } },
              { name: "browser_url", description: "Return the current page URL and title.", inputSchema: { type: "object", properties: {} } },
              { name: "browser_snapshot", description: "Read visible text plus links from the current page.", inputSchema: { type: "object", properties: {} } },
              { name: "browser_click", description: "Click an element by CSS selector.", inputSchema: { type: "object", properties: { selector: { type: "string" } }, required: ["selector"] } },
              { name: "browser_fill", description: "Type into an input by CSS selector.", inputSchema: { type: "object", properties: { selector: { type: "string" }, value: { type: "string" } }, required: ["selector", "value"] } },
              { name: "browser_scroll", description: "Scroll the page by pixels (negative = up).", inputSchema: { type: "object", properties: { dy: { type: "number" } } } },
              { name: "browser_back", description: "Go back one page.", inputSchema: { type: "object", properties: {} } },
            ];

            async function callBridge(tool, args) {
              try {
                const res = await fetch(URL_, {
                  method: "POST",
                  headers: { "content-type": "application/json", "x-nova-token": TOKEN },
                  body: JSON.stringify({ tool, args: args || {} }),
                });
                const data = await res.json();
                const text = typeof data.result === "string" ? data.result : JSON.stringify(data.result ?? data.error ?? "");
                return { content: [{ type: "text", text }] };
              } catch (e) {
                return { content: [{ type: "text", text: "browser bridge error: " + e.message }], isError: true };
              }
            }

            const rl = readline.createInterface({ input: process.stdin });
            function send(msg) { process.stdout.write(JSON.stringify(msg) + "\n"); }

            rl.on("line", async (line) => {
              let req;
              try { req = JSON.parse(line); } catch { return; }
              const { id, method, params } = req;
              if (method === "initialize") {
                send({ jsonrpc: "2.0", id, result: { protocolVersion: "2024-11-05", capabilities: { tools: {} }, serverInfo: { name: "novadesk-browser", version: "0.1.0" } } });
              } else if (method === "notifications/initialized") {
                // no response required
              } else if (method === "tools/list") {
                send({ jsonrpc: "2.0", id, result: { tools: TOOLS } });
              } else if (method === "tools/call") {
                const name = params?.name; const args = params?.arguments || {};
                const tool = name && name.startsWith("browser_") ? name : "browser_" + name;
                send({ jsonrpc: "2.0", id, result: await callBridge(tool, args) });
              } else if (id != null) {
                send({ jsonrpc: "2.0", id, error: { code: -32601, message: "method not found: " + method } });
              }
            });
        """.trimIndent()
    }
}
