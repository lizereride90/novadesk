package com.novadesk.core.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.novadesk.core.storage.AppContainer
import com.novadesk.feature.agents.AgentsScreen
import com.novadesk.feature.browser.BrowserScreen
import com.novadesk.feature.codex.CodexScreen
import com.novadesk.feature.files.EditorScreen
import com.novadesk.feature.files.FilesScreen
import com.novadesk.feature.home.HomeScreen
import com.novadesk.feature.mcpstore.McpStoreScreen
import com.novadesk.feature.more.MoreScreen
import com.novadesk.feature.opencode.OpenCodeScreen
import com.novadesk.feature.settings.SettingsScreen
import com.novadesk.feature.terminal.DebianSetupScreen
import com.novadesk.feature.terminal.TerminalScreen

private data class TopLevel(val route: String, val label: String, val icon: ImageVector)

private val TOP_LEVELS = listOf(
    TopLevel(Routes.HOME, "Home", Icons.Filled.Home),
    TopLevel(Routes.AGENTS, "Agents", Icons.Filled.SmartToy),
    TopLevel(Routes.TERMINAL, "Terminal", Icons.Filled.Terminal),
    TopLevel(Routes.FILES, "Files", Icons.Filled.Folder),
    TopLevel(Routes.MORE, "More", Icons.Filled.MoreHoriz),
)

/**
 * App navigation. Bottom-bar tabs own the five top-level destinations;
 * everything else (agent chats, editor, setup wizard, settings) pushes on
 * top and returns with the system back button.
 */
@Composable
fun NovaNavGraph(container: AppContainer) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            // Detail screens keep the bar to maximise reading space.
            val showBar = TOP_LEVELS.any { it.route == current } || current == null
            if (showBar) {
                NavigationBar {
                    TOP_LEVELS.forEach { tab ->
                        NavigationBarItem(
                            selected = current == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(inner),
        ) {
            composable(Routes.HOME) { HomeScreen(container, nav) }
            composable(Routes.AGENTS) { AgentsScreen(container, nav) }
            composable(Routes.TERMINAL) { TerminalScreen(container, nav) }
            composable(Routes.FILES) { FilesScreen(container, nav) }
            composable(Routes.MORE) { MoreScreen(container, nav) }
            composable(Routes.OPENCODE) { OpenCodeScreen(container, nav) }
            composable(Routes.CODEX) { CodexScreen(container, nav) }
            composable(Routes.BROWSER) { BrowserScreen(container, nav) }
            composable(Routes.MCP_STORE) { McpStoreScreen(container, nav) }
            composable(Routes.SETTINGS) { SettingsScreen(container, nav) }
            composable(Routes.DEBIAN_SETUP) { DebianSetupScreen(container, nav) }
            composable(Routes.EDITOR) { EditorScreen(container, nav) }
        }
    }
}
