package com.novadesk.core.browser

import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import kotlin.coroutines.resume

/**
 * Permission-controlled automation surface for agents. Every method maps to
 * one browser tool; the UI layer decides which ones need user confirmation
 * (see Settings → Browser → agent permissions).
 *
 * What this honestly cannot do: logins, CAPTCHAs and canvas-only pages are
 * reported back as errors, not silently pretended through.
 */
class BrowserTools {

    /** Operations the user marked as needing a tap before they run. */
    enum class Sensitive { FormSubmit, Click, FillInput }

    data class PageSnapshot(
        val url: String,
        val title: String,
        val text: String,
        val links: List<Link>,
    )

    data class Link(val text: String, val href: String)

    suspend fun snapshot(web: WebView): PageSnapshot = withContext(Dispatchers.Main) {
        val url = web.url.orEmpty()
        val title = web.title.orEmpty()
        val json = evaluate(
            web,
            """(function(){const t=document.body?document.body.innerText:"";
const ls=[...document.querySelectorAll("a[href]")].slice(0,60).map(a=>({t:(a.innerText||"").trim().slice(0,80),h:a.href}));
return JSON.stringify({text:t.slice(0,8000),links:ls});})()""",
        ) ?: "{}"
        val obj = org.json.JSONObject(json.removeSurrounding("\"").unescapeJs())
        PageSnapshot(
            url = url,
            title = title,
            text = obj.optString("text"),
            links = obj.optJSONArray("links")?.toLinks().orEmpty(),
        )
    }

    suspend fun click(web: WebView, cssSelector: String): String = withContext(Dispatchers.Main) {
        evaluate(
            web,
            """(function(){const el=document.querySelector(${jsString(cssSelector)});
if(!el) return "NOT_FOUND"; el.click(); return "OK";})()""",
        ) ?: "UNKNOWN"
    }

    suspend fun fillInput(web: WebView, cssSelector: String, value: String): String =
        withContext(Dispatchers.Main) {
            evaluate(
                web,
                """(function(){const el=document.querySelector(${jsString(cssSelector)});
if(!el) return "NOT_FOUND";
el.focus(); el.value=${jsString(value)};
el.dispatchEvent(new Event("input",{bubbles:true}));
el.dispatchEvent(new Event("change",{bubbles:true})); return "OK";})()""",
            ) ?: "UNKNOWN"
        }

    suspend fun scrollBy(web: WebView, dy: Int): String = withContext(Dispatchers.Main) {
        evaluate(web, "window.scrollBy(0,$dy);'OK'") ?: "UNKNOWN"
    }

    private suspend fun evaluate(web: WebView, script: String): String? =
        suspendCancellableCoroutine { cont ->
            web.evaluateJavascript(script) { cont.resume(it) }
        }

    private fun jsString(s: String): String =
        "'${s.replace("\\", "\\\\").replace("'", "\\'")}'"

    private fun String.unescapeJs(): String =
        replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")

    private fun JSONArray.toLinks(): List<Link> =
        (0 until length()).mapNotNull {
            val o = optJSONObject(it) ?: return@mapNotNull null
            Link(o.optString("t"), o.optString("h"))
        }
}
