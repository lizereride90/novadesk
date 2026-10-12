package com.novadesk.core.linux

import android.content.Context
import java.io.File

/**
 * Builds the proot command line that enters the Debian tree.
 *
 * The flag set mirrors what proot-distro uses on Android:
 * `--link2symlink` (Termux rootfs expect it), `-0` (fake root so apt and
 * useradd work), `--kill-on-exit` (no orphan processes). We bind the
 * persistent home, /dev, /proc, /sys and /sdcard.
 */
class ProotRunner(
    private val context: Context,
    private val layout: DebianLayout,
) {

    fun isAvailable(): Boolean = layout.prootReady

    /**
     * Full argv for running [command] inside Debian. Environment is applied
     * through `env -i` so host variables never leak into the guest.
     */
    fun argv(
        command: List<String>,
        workDir: String = "/home/user",
        env: Map<String, String> = emptyMap(),
    ): List<String> {
        val baseEnv = linkedMapOf(
            "HOME" to "/home/user",
            "USER" to "user",
            "LOGNAME" to "user",
            "SHELL" to "/bin/bash",
            "TERM" to "xterm-256color",
            "LANG" to "C.UTF-8",
            "LC_ALL" to "C.UTF-8",
            "PATH" to "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
        ).apply { putAll(env) }

        return buildList {
            add(layout.prootBinary.absolutePath)
            add("--link2symlink")
            add("--kill-on-exit")
            add("-0")
            add("--rootfs=${layout.rootfsDir.absolutePath}")
            add("--cwd=$workDir")
            add("--bind=${layout.homeDir.absolutePath}:/home/user")
            bindIfPresent("/dev")
            bindIfPresent("/proc")
            bindIfPresent("/sys")
            bindIfPresent("/sdcard")
            add("/usr/bin/env")
            baseEnv.forEach { (k, v) -> add("$k=$v") }
            addAll(command)
        }
    }

    private fun MutableList<String>.bindIfPresent(path: String) {
        if (File(path).exists()) add("--bind=$path")
    }

    /**
     * Interactive login shell. When proot or Debian is missing we return a
     * plain shell that prints exactly what is wrong instead of a fake prompt.
     */
    fun loginShell(workDir: String = "/home/user"): List<String> {
        if (!layout.prootReady) {
            return listOf(
                "sh", "-c",
                "echo 'NovaDesk: the Linux runtime is not installed yet.';" +
                    "echo 'Open the Terminal tab and run setup, or use More > Settings > Debian.';" +
                    "exec sh",
            )
        }
        if (!layout.looksInstalled) {
            return listOf(
                "sh", "-c",
                "echo 'NovaDesk: proot is ready but the Debian rootfs is missing.';" +
                    "echo 'Finish setup to install Debian.'; exec sh",
            )
        }
        return argv(listOf("/bin/bash", "--login"), workDir)
    }
}
