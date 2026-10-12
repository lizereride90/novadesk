package com.novadesk.core.storage

import android.content.Context
import com.novadesk.core.agents.CodexRunner
import com.novadesk.core.agents.OpenCodeRunner
import com.novadesk.core.browser.BrowserTools
import com.novadesk.core.linux.DebianInstaller
import com.novadesk.core.linux.DebianLayout
import com.novadesk.core.linux.ProotRunner
import com.novadesk.core.mcp.McpClient
import com.novadesk.core.mcp.McpRegistry
import com.novadesk.core.mcp.McpSecrets
import com.novadesk.core.terminal.TerminalManager
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Hand-rolled service locator, built once in [NovaDeskApp.onCreate].
 * Construction order mirrors boot order: storage → Debian → shells →
 * agents. Screens receive this through the nav graph — no globals.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // Storage ------------------------------------------------------------
    val prefs = AppPrefs(appContext)
    val files = FilesRepository()
    val projects = ProjectsRepository(File(appContext.filesDir, "state"))

    /** Directories the file manager + agents may touch. */
    fun allowedRoots(layout: DebianLayout): List<File> =
        listOf(layout.homeDir, layout.rootfsDir, appContext.filesDir)

    // Debian -------------------------------------------------------------
    val debianLayout = DebianLayout(appContext.filesDir)
    val debianInstaller = DebianInstaller(appContext, debianLayout, http)
    val proot = ProotRunner(appContext, debianLayout)

    // Terminal -----------------------------------------------------------
    val terminals = TerminalManager(proot)
    val browserTools = BrowserTools()

    // Agents -------------------------------------------------------------
    private val agentWork = File(appContext.filesDir, "state")
    val openCode = OpenCodeRunner(terminals, proot, agentWork)
    val codex = CodexRunner(terminals, proot, agentWork)

    // MCP ----------------------------------------------------------------
    val mcpRegistry = McpRegistry(File(appContext.filesDir, "state"))
    val mcpSecrets = McpSecrets(appContext)
    val mcpClient = McpClient(proot)

    init {
        scope.launch(Dispatchers.IO) {
            projects.load()
            mcpRegistry.load()
        }
    }
}
