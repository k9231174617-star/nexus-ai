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

    /** Navigate to URL and return PageInfo (test-compatible API) */
    suspend fun navigateTo(url: String): PageInfo {
        val webView = webViewPool.acquire()
        try {
            pageNavigator.navigate(webView, url)
            return PageInfo(
                title = webView.title ?: "",
                url = url,
                status = 200,
            )
        } catch (e: Exception) {
            return PageInfo(title = "", url = url, status = 0)
        } finally {
            webViewPool.release(webView)
        }
    }

    /** Search web using search engine */
    suspend fun search(query: String, engine: String = "duckduckgo"): List<SearchResult> =
        withContext(Dispatchers.IO) {
            searchEngine.search(query, engine)
        }

    /** Extract content from URL (test-compatible API) */
    suspend fun extractContent(url: String): String = withContext(Dispatchers.Main) {
        val webView = webViewPool.acquire()
        pageNavigator.navigate(webView, url)
        val content = contentExtractor.extract(webView)
        webViewPool.release(webView)
        content
    }

    /** Take screenshot and return image bytes (test-compatible API) */
    suspend fun takeScreenshot(): ByteArray = withContext(Dispatchers.Main) {
        val webView = webViewPool.acquire()
        val bytes = screenshotCapture.capture()
        webViewPool.release(webView)
        bytes
    }

    /** Execute JavaScript (test-compatible API) */
    suspend fun executeJavaScript(script: String): String = withContext(Dispatchers.Main) {
        val webView = webViewPool.acquire()
        pageNavigator.executeJs(webView, script)
        webViewPool.release(webView)
        ""
    }

    /** Clear all cookies (test-compatible API) */
    fun clearCookies() = pageNavigator.clearCookies()

    /** Go back in history (test-compatible API) */
    fun goBack(): PageInfo? = pageNavigator.goBack()

    /** Get current URL (test-compatible API) */
    fun getCurrentUrl(): String = pageNavigator.getCurrentUrl()

    /** Get page source (test-compatible API) */
    fun getPageSource(): String = pageNavigator.getSource()

    @Deprecated("Use navigateTo or search instead")
    suspend fun screenshot(url: String): String = withContext(Dispatchers.Main) {
        val webView = webViewPool.acquire()
        pageNavigator.navigate(webView, url)
        val path = screenshotCapture.capture(webView)
        webViewPool.release(webView)
        path
    }

    @Deprecated("Use executeJavaScript instead")
    suspend fun executeJs(script: String): String = withContext(Dispatchers.Main) {
        val webView = webViewPool.acquire()
        val result = pageNavigator.executeJs(webView, script)
        webViewPool.release(webView)
        result
    }

    fun back() = webViewPool.current()?.goBack()
    fun forward() = webViewPool.current()?.goForward()
}
