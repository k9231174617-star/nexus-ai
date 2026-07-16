package com.nexus.agent.core.browser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class BrowserState(
    val url: String = "",
    val title: String = "",
    val isLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val extractedContent: String = "",
    val error: String? = null,
)

@Singleton
class BrowserAgent @Inject constructor(
    private val webViewPool: WebViewPool,
    private val pageNavigator: PageNavigator,
    private val contentExtractor: ContentExtractor,
    private val searchEngine: SearchEngine,
    private val screenshotCapture: ScreenshotCapture,
) {
    private val _state = MutableStateFlow(BrowserState())
    val state: StateFlow<BrowserState> = _state

    suspend fun navigate(url: String): String = withContext(Dispatchers.Main) {
        _state.value = _state.value.copy(isLoading = true, url = url)
        try {
            val webView = webViewPool.acquire()
            pageNavigator.navigate(webView, url)
            val content = contentExtractor.extract(webView)
            _state.value = _state.value.copy(
                isLoading = false,
                title = webView.title ?: "",
                extractedContent = content,
                canGoBack = webView.canGoBack(),
                canGoForward = webView.canGoForward(),
            )
            webViewPool.release(webView)
            content
        } catch (e: Exception) {
            _state.value = _state.value.copy(isLoading = false, error = e.message)
            ""
        }
    }

    suspend fun search(query: String, engine: String = "duckduckgo"): List<SearchResult> =
        withContext(Dispatchers.IO) {
            searchEngine.search(query, engine)
        }

    suspend fun extractContent(url: String): String = withContext(Dispatchers.Main) {
        val webView = webViewPool.acquire()
        pageNavigator.navigate(webView, url)
        val content = contentExtractor.extract(webView)
        webViewPool.release(webView)
        content
    }

    suspend fun screenshot(url: String): String = withContext(Dispatchers.Main) {
        val webView = webViewPool.acquire()
        pageNavigator.navigate(webView, url)
        val path = screenshotCapture.capture(webView)
        webViewPool.release(webView)
        path
    }

    suspend fun executeJs(script: String): String = withContext(Dispatchers.Main) {
        val webView = webViewPool.acquire()
        val result = pageNavigator.executeJs(webView, script)
        webViewPool.release(webView)
        result
    }

    fun back() = webViewPool.current()?.goBack()
    fun forward() = webViewPool.current()?.goForward()
}