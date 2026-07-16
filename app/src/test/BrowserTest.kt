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
    private lateinit var jsBridge: JsBridge

    @Mock
    private lateinit var screenshotCapture: ScreenshotCapture

    private lateinit var browserAgent: BrowserAgent

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        browserAgent = BrowserAgent(webViewPool, pageNavigator, contentExtractor, jsBridge, screenshotCapture)
    }

    @Test
    fun `navigateTo loads URL and returns page info`() = runTest {
        val url = "https://example.com"
        val pageInfo = PageInfo(title = "Example", url = url, status = 200)
        
        `when`(pageNavigator.load(url)).thenReturn(pageInfo)
        
        val result = browserAgent.navigateTo(url)
        
        assertEquals("Example", result.title)
        assertEquals(200, result.status)
    }

    @Test
    fun `navigateTo handles invalid URL`() = runTest {
        val url = "not-a-url"
        
        `when`(pageNavigator.load(url)).thenThrow(IllegalArgumentException("Invalid URL"))
        
        try {
            browserAgent.navigateTo(url)
            fail("Should throw exception")
        } catch (e: IllegalArgumentException) {
            assertNotNull(e.message)
        }
    }

    @Test
    fun `extractContent returns readable text`() = runTest {
        val html = "<html><body><p>Hello World</p></body></html>"
        val expectedText = "Hello World"
        
        `when`(contentExtractor.extractText(html)).thenReturn(expectedText)
        
        val result = browserAgent.extractContent(html)
        
        assertEquals(expectedText, result)
    }

    @Test
    fun `executeJavaScript returns evaluation result`() = runTest {
        val script = "document.title"
        val expectedResult = "Page Title"
        
        `when`(jsBridge.evaluate(script)).thenReturn(expectedResult)
        
        val result = browserAgent.executeJavaScript(script)
        
        assertEquals(expectedResult, result)
    }

    @Test
    fun `takeScreenshot returns image bytes`() = runTest {
        val mockBytes = byteArrayOf(0x89, 0x50, 0x4E, 0x47) // PNG header
        
        `when`(screenshotCapture.capture()).thenReturn(mockBytes)
        
        val result = browserAgent.takeScreenshot()
        
        assertArrayEquals(mockBytes, result)
    }

    @Test
    fun `search performs web search`() = runTest {
        val query = "Kotlin coroutines"
        val results = listOf(
            SearchResult("Kotlin Docs", "https://kotlinlang.org", "Official documentation")
        )
        
        `when`(pageNavigator.search(query)).thenReturn(results)
        
        val result = browserAgent.search(query)
        
        assertEquals(1, result.size)
        assertEquals("Kotlin Docs", result[0].title)
    }

    @Test
    fun `goBack returns to previous page`() = runTest {
        val previousPage = PageInfo(title = "Previous", url = "https://prev.com", status = 200)
        
        `when`(pageNavigator.goBack()).thenReturn(previousPage)
        
        val result = browserAgent.goBack()
        
        assertEquals("Previous", result?.title)
    }

    @Test
    fun `getCurrentUrl returns active page URL`() = runTest {
        `when`(pageNavigator.getCurrentUrl()).thenReturn("https://current.com")
        
        assertEquals("https://current.com", browserAgent.getCurrentUrl())
    }

    @Test
    fun `clearCookies removes all cookies`() = runTest {
        browserAgent.clearCookies()
        
        verify(pageNavigator).clearCookies()
    }

    @Test
    fun `getPageSource returns raw HTML`() = runTest {
        val html = "<html></html>"
        
        `when`(pageNavigator.getSource()).thenReturn(html)
        
        assertEquals(html, browserAgent.getPageSource())
    }
}
