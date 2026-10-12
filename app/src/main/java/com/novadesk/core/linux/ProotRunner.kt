package com.novadesk.core.linux

import android.content.Context
import java.io.File

/**
 * Builds the proot command line that enters the Debian tree.
 *
 * We look for a proot binary in this order:
 * 1. our own files dir (downloaded at setup from a trusted mirror),
 * 2. anything already on PATH (power users who ship their own).
 *
 * If neither exists, [prootOrThrow] explains exactly that instead of
 * crashing with a bare IOException.
 */
class ProotRunner(
    private val context: Context,
    private val layout: DebianLayout,
) {
    private fun prootOrThrow(): String {
        val bundled = File(context.filesDir, "bin/proot")
        if (bundled.canExecute()) return bundled.absolutePath
        return "proot"
    }

    /**
     * argv for `proot -r rootfs -b ... -w HOME env … sh -c <command>`.
     * Callers spawn it with [ProcessBuilder] inside a [TerminalSession].
     */
    fun argv(
        command: List<String>,
        workDir: String = "/home/user",
        env: Map<String, String> = emptyMap(),
    ): List<String> {
        val baseEnv = mapOf(
            "HOME" to "/home/user",
            "USER" to "user",
            "LOGNAME" to "user",
            "SHELL" to "/bin/bash",
            "TERM" to "xterm-256color",
            "LANG" to "C.UTF-8",
            "PATH" to "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
        ) + env

        return buildList {
            add(prootOrThrow())
            add("--rootfs=${layout.rootfsDir.absolutePath}")
            add("--cwd=$workDir")
            add("--bind=${layout.homeDir.absolutePath}:/home/user")
            add("--bind=/sdcard:/sdcard")
            add("--kernel-release=5.15.0")
            add("env")
            baseEnv.forEach { (k, v) -> add("$k=$v") }
            addAll(command)
        }
    }

    /** Interactive login shell: `bash --login`, or plain sh as fallback. */
    fun loginShell(workDir: String = "/home/user"): List<String> =
        if (File(layout.rootfsDir, "bin/bash").exists() ||
            File(layout.rootfsDir, "usr/bin/bash").exists()
        ) {
            argv(listOf("/bin/bash", "--login"), workDir)
        } else {
            // No Debian yet — plain device shell so the terminal still works.
            listOf("sh")
        }
}
