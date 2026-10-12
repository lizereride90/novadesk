package com.novadesk.core.terminal

import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * One interactive shell. A reader thread pumps stdout into [output] and the
 * UI writes bytes through [send]. Backed by a real OS process — what you
 * type is what the shell runs, including Debian tools when proot is up.
 *
 * This is deliberately *not* a full VT100 emulator: we stream output lines
 * and honour SGR colours via [AnsiParser]. Full-screen TUIs (vim, htop)
 * degrade to line output — documented in the README rather than faked.
 */
class TerminalSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    private val argv: List<String>,
    private val workDir: File? = null,
    val projectId: String? = null,
    maxLines: Int = 4_000,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val proc: Process
    private val closed = AtomicBoolean(false)
    private val buffer = ArrayDeque<String>()
    private val cap = maxLines.coerceIn(500, 20_000)

    private val _output = MutableStateFlow("")
    val output: StateFlow<String> = _output

    /** Bumped on every flush so the UI only re-renders when there is news. */
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision

    private val _running = MutableStateFlow(true)
    val running: StateFlow<Boolean> = _running

    /** Copy of the last [lines] buffered lines, oldest first. */
    fun snapshot(lines: Int = 1_000): List<String> =
        synchronized(this) { buffer.takeLast(lines) }

    init {
        val builder = ProcessBuilder(argv)
            .redirectErrorStream(true)
        if (workDir != null) builder.directory(workDir)
        // Interactive shells need these or tools like `less` misbehave.
        builder.environment()["TERM"] = "xterm-256color"
        proc = builder.start()
        scope.launch { pump() }
    }

    private fun pump() {
        val reader = proc.inputStream.bufferedReader(Charsets.UTF_8)
        val chunk = CharArray(2_048)
        val pending = StringBuilder()
        try {
            while (!closed.get()) {
                val n = reader.read(chunk)
                if (n < 0) break
                pending.append(chunk, 0, n)
                // Flush complete lines immediately; keep a partial tail.
                val text = pending.toString()
                val lastNl = text.lastIndexOf('\n')
                if (lastNl >= 0) {
                    appendLines(text.substring(0, lastNl + 1))
                    pending.delete(0, lastNl + 1)
                }
                if (pending.length > 8_192) {
                    appendLines(pending.toString())
                    pending.clear()
                }
            }
            if (pending.isNotEmpty()) appendLines(pending.toString())
            try {
                appendLines("\n[process exited: ${proc.waitFor()}]\n")
            } catch (_: Exception) {
            }
        } catch (_: Exception) {
            // Reader dies with the process; nothing useful to report here.
        } finally {
            _running.value = false
        }
    }

    @Synchronized
    private fun appendLines(text: String) {
        for (line in text.split('\n')) {
            buffer.addLast(line)
            while (buffer.size > cap) buffer.removeFirst()
        }
        // Only keep a bounded tail joined — this runs on every read chunk and
        // an unbounded join was the main source of UI jank.
        _output.value = buffer.takeLast(OUTPUT_TAIL_LINES).joinToString("\n")
        _revision.value = _revision.value + 1
    }

    /** Bytes typed by the user, e.g. `"ls\n"` or `"\u0003"` (Ctrl-C). */
    fun send(text: String) {
        if (closed.get()) return
        try {
            proc.outputStream.write(text.toByteArray(Charsets.UTF_8))
            proc.outputStream.flush()
        } catch (_: Exception) {
        }
    }

    /** SIGINT-ish: close stdin so the foreground process sees EOF/Ctrl-C. */
    fun interrupt() {
        send("\u0003")
    }

    fun clear() {
        synchronized(this) {
            buffer.clear()
            buffer.addLast("")
            _output.value = ""
            _revision.value = _revision.value + 1
        }
    }

    fun destroy() {
        if (closed.compareAndSet(false, true)) {
            try {
                proc.outputStream.close()
            } catch (_: Exception) {
            }
            proc.destroy()
            scope.cancel()
        }
    }

    private companion object {
        const val OUTPUT_TAIL_LINES = 1_200
    }
}
