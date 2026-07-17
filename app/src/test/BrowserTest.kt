package com.nexus.agent.core.browser

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

class BrowserTest {

    @Mock
    private lateinit var webViewPool: WebViewPool

    @Mock
    private lateinit var pageNavigator: PageNavigator

    @Mock
    private lateinit var contentExtractor: ContentExtractor

    @Mock
    private lateinit var searchEngine: SearchEngine

    @Mock
    private lateinit var screenshotCapture: ScreenshotCapture

    private lateinit var browserAgent: BrowserAgent

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        browserAgent = BrowserAgent(webViewPool, pageNavigator, contentExtractor, searchEngine, screenshotCapture)
    }

    @Test
    fun `navigateTo loads URL and returns page info`() = runTest {
        val url = "https://example.com"
        `when`(pageNavigator.goBack()).thenReturn(PageInfo(title = "Example", url = url, status = 200))

        val result = browserAgent.navigateTo(url)

        assertEquals(url, result.url)
    }

    @Test
    fun `search performs web search`() = runTest {
        val query = "Kotlin coroutines"
        val results = listOf(
            SearchResult("Kotlin Docs", "https://kotlinlang.org", "Official documentation")
        )

        `when`(searchEngine.search(query, "duckduckgo")).thenReturn(results)

        val result = browserAgent.search(query)

        assertEquals(1, result.size)
        assertEquals("Kotlin Docs", result[0].title)
    }

    @Test
    fun `goBack returns to previous page`() = runTest {
        `when`(pageNavigator.goBack()).thenReturn(PageInfo(title = "Previous", url = "https://prev.com", status = 200))

        val result = browserAgent.goBack()

        assertEquals("Previous", result?.title)
    }

    @Test
    fun `getCurrentUrl returns active page URL`() {
        `when`(pageNavigator.getCurrentUrl()).thenReturn("https://current.com")

        assertEquals("https://current.com", browserAgent.getCurrentUrl())
    }

    @Test
    fun `clearCookies removes all cookies`() {
        browserAgent.clearCookies()
        verify(pageNavigator).clearCookies()
    }

    @Test
    fun `getPageSource returns raw HTML`() {
        val html = "<html></html>"
        `when`(pageNavigator.getSource()).thenReturn(html)

        assertEquals(html, browserAgent.getPageSource())
    }
}
