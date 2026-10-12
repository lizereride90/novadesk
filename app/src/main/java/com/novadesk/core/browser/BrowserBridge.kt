package com.novadesk.core.browser

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom

/**
 * Localhost bridge between an agent's MCP server (running inside Debian) and
 * the live WebView in the browser tab.
 *
 * Security posture:
 * - binds to 127.0.0.1 only, so no other device can reach it;
 * - every request must carry a per-session random token;
 * - it can only reach the WebView the user has open — if the browser tab is
 *   not in the foreground there is no handler and the call fails cleanly.
 */
class BrowserBridge(private val port: Int = 8799) {

    val token: String = buildToken()
    val url: String get() = "http://127.0.0.1:$port/tool"

    /** Signature the browser screen registers to serve calls. */
    fun interface Handler {
        suspend fun handle(tool: String, args: JSONObject): Any?
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var handler: Handler? = null
    @Volatile private var server: ServerSocket? = null

    fun setHandler(h: Handler?) {
        handler = h
    }

    fun start() {
        if (server != null) return
        scope.launch {
            try {
                val socket = ServerSocket(port, 16, InetAddress.getByName("127.0.0.1"))
                server = socket
                while (!socket.isClosed) {
                    val client = socket.accept()
                    launch { serve(client) }
                }
            } catch (_: Exception) {
                // Port busy or socket closed on shutdown — nothing to do.
            }
        }
    }

    fun stop() {
        try {
            server?.close()
        } catch (_: Exception) {
        }
        server = null
    }

    private suspend fun serve(client: Socket) {
        client.use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            val method = parts.getOrNull(0)
            var contentLength = 0
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                val idx = line.indexOf(':')
                if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
            }
            contentLength = headers["content-length"]?.toIntOrNull() ?: 0
            val body = if (contentLength > 0) {
                val buf = CharArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val n = reader.read(buf, read, contentLength - read)
                    if (n < 0) break
                    read += n
                }
                String(buf, 0, read)
            } else {
                ""
            }

            val response = try {
                when {
                    method != "POST" -> JSONObject().put("ok", false).put("error", "POST only")
                    headers["x-nova-token"] != token -> JSONObject().put("ok", false).put("error", "bad token")
                    else -> dispatch(body)
                }
            } catch (e: Exception) {
                JSONObject().put("ok", false).put("error", e.message ?: "bridge error")
            }
            writeResponse(socket.getOutputStream(), response.toString())
        }
    }

    private suspend fun dispatch(body: String): JSONObject {
        val h = handler ?: return JSONObject()
            .put("ok", false)
            .put("error", "Open the Browser tab in NovaDesk first — the agent has no page to control.")
        val req = JSONObject(body)
        val tool = req.optString("tool")
        val args = req.optJSONObject("args") ?: JSONObject()
        val result = try {
            withTimeout(20_000) { h.handle(tool, args) }
        } catch (e: Exception) {
            return JSONObject().put("ok", false).put("error", e.message ?: "tool failed")
        }
        return JSONObject().put("ok", true).put("result", result ?: JSONObject.NULL)
    }

    private fun writeResponse(out: OutputStream, json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\n" +
            "Content-Type: application/json\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n\r\n"
        out.write(header.toByteArray())
        out.write(bytes)
        out.flush()
    }

    private fun buildToken(): String {
        val rnd = ByteArray(24)
        SecureRandom().nextBytes(rnd)
        return rnd.joinToString("") { "%02x".format(it) }
    }
}
