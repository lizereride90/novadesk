package com.novadesk.core.storage

import android.content.Context
import com.novadesk.core.agents.AgentConfig
import com.novadesk.core.agents.CodexRunner
import com.novadesk.core.agents.OpenCodeRunner
import com.novadesk.core.browser.BrowserBridge
import com.novadesk.core.browser.BrowserTools
import com.novadesk.core.linux.DebianInstaller
import com.novadesk.core.linux.DebianLayout
import com.novadesk.core.linux.DevEnvInstaller
import com.novadesk.core.linux.ProotInstaller
import com.novadesk.core.linux.ProotRunner
import com.novadesk.core.mcp.McpCatalog
import com.novadesk.core.mcp.McpClient
import com.novadesk.core.mcp.McpRegistry
import com.novadesk.core.mcp.McpSecrets
import com.novadesk.core.terminal.TerminalManager
import com.novadesk.setup.SetupCoordinator
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
 * agents → setup. Screens receive this through the nav graph — no globals.
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
    val prootInstaller = ProotInstaller(debianLayout, http)
    val debianInstaller = DebianInstaller(debianLayout, prootInstaller, http)
    val proot = ProotRunner(appContext, debianLayout)
    val devEnv = DevEnvInstaller(proot)

    // Terminal -----------------------------------------------------------
    val terminals = TerminalManager(proot)
    val browserTools = BrowserTools()

    // Browser bridge (agents drive the live WebView over localhost) ------
    val browserBridge = BrowserBridge()

    // Agent configuration + first-run orchestration ----------------------
    val agentConfig = AgentConfig(debianLayout, browserBridge.url, browserBridge.token)
    val setup = SetupCoordinator(
        installer = debianInstaller,
        devEnv = devEnv,
        layout = debianLayout,
        prefs = prefs,
        agentConfig = agentConfig,
    )

    /** Latest model choices, kept hot so runners can build their argv. */
    @Volatile
    var openCodeModel: String = ""
        private set
    @Volatile
    var codexModel: String = ""
        private set

    // Agents -------------------------------------------------------------
    private val agentWork = File(appContext.filesDir, "state")
    val openCode = OpenCodeRunner(terminals, proot, agentWork) { openCodeModel }
    val codex = CodexRunner(terminals, proot, agentWork) { codexModel }

    // MCP ----------------------------------------------------------------
    val mcpRegistry = McpRegistry(File(appContext.filesDir, "state"))
    val mcpSecrets = McpSecrets(appContext)
    val mcpClient = McpClient(proot)

    init {
        browserBridge.start()
        scope.launch(Dispatchers.IO) {
            projects.load()
            mcpRegistry.load()
            mcpRegistry.seedDefaults(McpCatalog.preloaded)
        }
        scope.launch {
            prefs.flow.collect { p ->
                openCodeModel = p.openCodeModel
                codexModel = p.codexModel
            }
        }
    }
}
