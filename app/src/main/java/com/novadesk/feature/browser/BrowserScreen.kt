package com.novadesk.feature.browser

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.novadesk.core.browser.BrowserTab
import com.novadesk.core.browser.SearchProvider
import com.novadesk.core.storage.AppContainer
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Built-in browser: WebView tabs, bookmarks, history, find-in-page and
 * desktop-site toggle. When an agent drives a tab its chip turns cyan so
 * there is never any doubt who is steering.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(container: AppContainer, nav: NavController) {
    val scope = rememberCoroutineScope()
    val prefs by container.prefs.flow.collectAsState(initial = null)
    val provider = SearchProvider.byName(prefs?.defaultSearch ?: "DuckDuckGo")

    val tabs = remember { mutableStateListOf(BrowserTab(url = provider.home)) }
    var activeId by remember { mutableStateOf(tabs.first().id) }
    var address by remember(activeId) { mutableStateOf(tabs.first { it.id == activeId }.url) }
    var progress by remember { mutableStateOf(0) }
    var webRef by remember { mutableStateOf<WebView?>(null) }
    var history by remember { mutableStateOf(listOf<String>()) }
    var bookmarks by remember { mutableStateOf(setOf<String>()) }
    var findQuery by remember { mutableStateOf("") }
    var desktopSite by remember { mutableStateOf(false) }
    var agentBanner by remember { mutableStateOf<String?>(null) }

    val active = tabs.first { it.id == activeId }

    Column(Modifier.fillMaxSize()) {
        LazyRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            items(tabs, key = { it.id }) { tab ->
                AssistChip(
                    onClick = {
                        activeId = tab.id
                        address = tab.url
                    },
                    label = { Text((if (tab.agentControlled) "◉ " else "") + tab.title.take(18)) },
                    trailingIcon = {
                        IconButton(onClick = {
                            if (tabs.size == 1) {
                                tab.url = provider.home; tab.title = provider.home
                                webRef?.loadUrl(provider.home)
                            } else {
                                tabs.remove(tab)
                                if (tab.id == activeId) activeId = tabs.first().id
                            }
                        }) { Icon(Icons.Filled.Close, contentDescription = "Close tab") }
                    },
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
            item {
                IconButton(onClick = {
                    val tab = BrowserTab(url = provider.home)
                    tabs += tab
                    activeId = tab.id
                    address = tab.url
                }) { Icon(Icons.Filled.Add, contentDescription = "New tab") }
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { webRef?.goBack() }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            IconButton(onClick = { webRef?.goForward() }) {
                Icon(Icons.Filled.ArrowForward, contentDescription = "Forward")
            }
            IconButton(onClick = { webRef?.reload() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Reload")
            }
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Search or URL") },
            )
            IconButton(onClick = {
                val target = SearchProvider.resolve(address, provider)
                active.url = target
                webRef?.loadUrl(target)
            }) { Text("Go") }
        }

        if (progress in 1..99) {
            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
        }
        agentBanner?.let {
            Text(it, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }

        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            active.url = request.url.toString()
                            address = active.url
                            return false
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            active.title = view.title ?: url
                            active.url = url
                            address = url
                            history = (listOf(url) + history).distinct().take(100)
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) {
                            progress = newProgress
                        }
                    }
                    loadUrl(active.url)
                    webRef = this
                }
            },
            update = { web ->
                webRef = web
                web.settings.userAgentString = if (desktopSite) {
                    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"
                } else {
                    null
                }
            },
            modifier = Modifier.weight(1f),
        )

        // Let agents drive *this* tab over the localhost bridge, but only
        // while the tab is open and the user has left the toggle on. When
        // the tab goes away the handler is cleared, so a stray agent call
        // gets an honest "no page" error instead of acting on nothing.
        DisposableEffect(webRef, prefs?.agentBrowserControl) {
            val web = webRef
            if (web != null && (prefs?.agentBrowserControl ?: true)) {
                container.browserBridge.setHandler { tool, args ->
                    handleBrowserTool(container, web, active, tool, args)
                }
            } else {
                container.browserBridge.setHandler(null)
            }
            onDispose { container.browserBridge.setHandler(null) }
        }

        LazyRow(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            item {
                AssistChip(
                    onClick = {
                        desktopSite = !desktopSite
                        webRef?.reload()
                    },
                    label = { Text(if (desktopSite) "Desktop: on" else "Desktop: off") },
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
            item {
                AssistChip(
                    onClick = {
                        bookmarks = if (active.url in bookmarks) bookmarks - active.url else bookmarks + active.url
                    },
                    label = { Text(if (active.url in bookmarks) "★ Saved" else "☆ Save") },
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
            item {
                AssistChip(
                    onClick = {
                        scope.launch {
                            agentBanner = "Agent snapshot: ${container.browserTools.snapshot(webRef ?: return@launch).links.size} links found."
                        }
                    },
                    label = { Text("Agent: inspect page") },
                )
            }
        }

        OutlinedTextField(
            value = findQuery,
            onValueChange = { findQuery = it; webRef?.findAllAsync(it) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            placeholder = { Text("Find in page…") },
            singleLine = true,
        )
    }
}

/**
 * Executes one agent tool against the live WebView. Always runs on the main
 * thread because WebView will throw otherwise. Returns a short human-readable
 * result that the agent sees as the tool output.
 */
private suspend fun handleBrowserTool(
    container: AppContainer,
    web: WebView,
    active: BrowserTab,
    tool: String,
    args: JSONObject,
): String = withContext(Dispatchers.Main) {
    active.agentControlled = true
    try {
        when (tool) {
            "browser_open" -> {
                val url = args.optString("url")
                if (url.isBlank()) "No url given." else {
                    web.loadUrl(url)
                    "Opened $url"
                }
            }
            "browser_back" -> {
                web.goBack()
                "Went back."
            }
            "browser_url" -> "${web.url} | ${web.title}"
            "browser_snapshot" -> {
                val snap = container.browserTools.snapshot(web)
                buildString {
                    appendLine(snap.title)
                    appendLine(snap.url)
                    appendLine()
                    appendLine(snap.text.take(6_000))
                    if (snap.links.isNotEmpty()) {
                        appendLine()
                        appendLine("Links:")
                        snap.links.take(40).forEach { appendLine("- ${it.text} -> ${it.href}") }
                    }
                }
            }
            "browser_click" -> container.browserTools.click(web, args.optString("selector"))
            "browser_fill" ->
                container.browserTools.fillInput(web, args.optString("selector"), args.optString("value"))
            "browser_scroll" -> container.browserTools.scrollBy(web, args.optInt("dy", 500))
            else -> "Unknown browser tool: $tool"
        }
    } finally {
        active.agentControlled = false
    }
}
