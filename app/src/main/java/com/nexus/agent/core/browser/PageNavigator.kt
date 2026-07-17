package com.nexus.agent.core.browser

import android.graphics.Bitmap
import android.webkit.WebView
import android.webkit.WebViewClient
import javax.inject.Inject
import javax.inject.Singleton

data class PageInfo(
    val title: String,
    val url: String,
    val status: Int = 200,
)

data class SearchResult(
    val title: String,
    val url: String,
    val snippet: String = "",
)

@Singleton
class PageNavigator @Inject constructor() {

    private var history = mutableListOf<String>()
    private var historyIndex = -1

    fun navigate(webView: WebView, url: String) {
        val normalized = normalizeUrl(url)
        webView.loadUrl(normalized)
        if (historyIndex < 0 || history.lastOrNull() != normalized) {
            history = history.take(historyIndex + 1).toMutableList()
            history.add(normalized)
            historyIndex = history.size - 1
        }
    }

    fun load(url: String): PageInfo {
        // Used for testing/integration
        return PageInfo(title = "", url = url, status = 200)
    }

    fun search(query: String): List<SearchResult> {
        // Web search stub - real implementation uses SearchEngine
        return listOf(
            SearchResult("Search: $query", "https://duckduckgo.com/?q=${query.replace(" ", "+")}")
        )
    }

    fun goBack(): PageInfo? {
        if (historyIndex > 0) {
            historyIndex--
            return PageInfo(title = "", url = history[historyIndex])
        }
        return null
    }

    fun getCurrentUrl(): String = history.getOrElse(historyIndex) { "" }

    fun getSource(): String = ""

    fun clearCookies() {
        android.webkit.CookieManager.getInstance().removeAllCookies(null)
    }

    fun executeJs(webView: WebView, script: String): String {
        webView.evaluateJavascript(script, null)
        return ""
    }

    private fun normalizeUrl(url: String): String {
        if (url.startsWith("http://") || url.startsWith("https://")) return url
        if (url.contains(".")) return "https://$url"
        return "https://duckduckgo.com/?q=${url.replace(" ", "+")}"
    }
}
