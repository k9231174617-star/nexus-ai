package com.nexus.agent.data.remote

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.IOException
import java.util.concurrent.TimeUnit

// ======================== DATA MODELS ========================

@Serializable
data class BrowserRequest(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val timeoutMs: Long = 30000,
    val followRedirects: Boolean = true,
    val userAgent: String? = null
)

@Serializable
data class BrowserResponse(
    val url: String,
    val statusCode: Int,
    val headers: Map<String, List<String>>,
    val body: String,
    val contentType: String? = null,
    val contentLength: Long = -1,
    val redirectUrl: String? = null,
    val requestTimeMs: Long = 0,
    val isSuccessful: Boolean = false,
    val errorMessage: String? = null
)

@Serializable
data class PageContentRequest(
    val url: String,
    val extractMode: String = "full", // full, article, text, links, images
    val maxLength: Int = 50000,
    val includeHtml: Boolean = false,
    val waitForSelector: String? = null,
    val scrollToBottom: Boolean = false,
    val executeJs: String? = null
)

@Serializable
data class PageContentResponse(
    val url: String,
    val title: String? = null,
    val content: String,
    val html: String? = null,
    val extractedLinks: List<ExtractedLink> = emptyList(),
    val extractedImages: List<ExtractedImage> = emptyList(),
    val metadata: PageMetadata? = null,
    val wordCount: Int = 0,
    val readingTimeMinutes: Int = 0,
    val error: String? = null
)

@Serializable
data class ExtractedLink(
    val url: String,
    val text: String,
    val isExternal: Boolean = false
)

@Serializable
data class ExtractedImage(
    val url: String,
    val alt: String? = null,
    val width: Int? = null,
    val height: Int? = null
)

@Serializable
data class PageMetadata(
    val description: String? = null,
    val keywords: String? = null,
    val author: String? = null,
    val ogTitle: String? = null,
    val ogImage: String? = null,
    val ogDescription: String? = null,
    val canonicalUrl: String? = null,
    val favicon: String? = null,
    val publishedAt: String? = null,
    val modifiedAt: String? = null
)

@Serializable
data class SearchRequest(
    val query: String,
    val engine: String = "duckduckgo", // duckduckgo, google, bing
    val maxResults: Int = 10,
    val safeSearch: Boolean = true,
    val region: String = "en-us",
    val timeRange: String? = null // day, week, month, year
)

@Serializable
data class SearchResponse(
    val query: String,
    val results: List<SearchResult> = emptyList(),
    val totalResults: Int = 0,
    val searchTimeMs: Long = 0,
    val engine: String = "duckduckgo",
    val error: String? = null
)

@Serializable
data class SearchResult(
    val title: String,
    val url: String,
    val snippet: String,
    val source: String? = null,
    val publishedAt: String? = null,
    val rank: Int = 0
)

@Serializable
data class ScreenshotRequest(
    val url: String,
    val width: Int = 1920,
    val height: Int = 1080,
    val fullPage: Boolean = false,
    val format: String = "png", // png, jpeg, webp
    val quality: Int = 90,
    val waitForLoad: Long = 3000,
    val selector: String? = null
)

@Serializable
data class ScreenshotResponse(
    val url: String,
    val imageBase64: String? = null,
    val imageUrl: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val format: String = "png",
    val fileSize: Long = 0,
    val error: String? = null
)

@Serializable
data class FormSubmitRequest(
    val url: String,
    val formData: Map<String, String>,
    val method: String = "POST",
    val headers: Map<String, String> = emptyMap()
)

@Serializable
data class FormSubmitResponse(
    val success: Boolean,
    val responseUrl: String? = null,
    val responseBody: String? = null,
    val statusCode: Int = 0,
    val error: String? = null
)

@Serializable
data class CookieSyncRequest(
    val domain: String,
    val cookies: List<BrowserCookie> = emptyList()
)

@Serializable
data class BrowserCookie(
    val name: String,
    val value: String,
    val domain: String,
    val path: String = "/",
    val expires: Long? = null,
    val secure: Boolean = false,
    val httpOnly: Boolean = false
)

@Serializable
data class ProxyConfig(
    val host: String? = null,
    val port: Int = 8080,
    val username: String? = null,
    val password: String? = null,
    val type: String = "http" // http, socks4, socks5
)

// ======================== API INTERFACE ========================

interface BrowserAPI {
    suspend fun fetch(request: BrowserRequest): BrowserResponse
    suspend fun fetchHtml(url: String, headers: Map<String, String> = emptyMap()): BrowserResponse
    suspend fun fetchJson(url: String, headers: Map<String, String> = emptyMap()): JsonObject?
    suspend fun postJson(url: String, body: JsonObject, headers: Map<String, String> = emptyMap()): BrowserResponse
    
    suspend fun extractPageContent(request: PageContentRequest): PageContentResponse
    suspend fun extractArticle(url: String, maxLength: Int = 10000): PageContentResponse
    
    suspend fun search(request: SearchRequest): SearchResponse
    suspend fun search(query: String, maxResults: Int = 10): SearchResponse
    
    suspend fun takeScreenshot(request: ScreenshotRequest): ScreenshotResponse
    suspend fun takeScreenshot(url: String, fullPage: Boolean = false): ScreenshotResponse
    
    suspend fun submitForm(request: FormSubmitRequest): FormSubmitResponse
    suspend fun submitForm(url: String, data: Map<String, String>): FormSubmitResponse
    
    suspend fun syncCookies(request: CookieSyncRequest): Boolean
    suspend fun getCookies(domain: String): List<BrowserCookie>
    suspend fun clearCookies(domain: String? = null): Boolean
    
    suspend fun checkHealth(): Boolean
    suspend fun getServerInfo(): Map<String, String>
    
    fun setProxy(config: ProxyConfig?)
    fun setDefaultHeaders(headers: Map<String, String>)
    fun setTimeout(timeoutMs: Long)
    fun setRetryPolicy(maxRetries: Int, retryDelayMs: Long)
}

// ======================== IMPLEMENTATION ========================

class BrowserAPIImpl(
    private val baseUrl: String = "https://api.nexus-agent.dev",
    private val apiKey: String? = null,
    private val enableLogging: Boolean = false
) : BrowserAPI {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private var proxyConfig: ProxyConfig? = null
    private var defaultHeaders: Map<String, String> = mapOf(
        "Accept" to "application/json, text/html, */*",
        "Accept-Language" to "en-US,en;q=0.9",
        "Accept-Encoding" to "gzip, deflate, br"
    )
    private var defaultTimeoutMs: Long = 30000
    private var maxRetries: Int = 3
    private var retryDelayMs: Long = 1000

    private val client: OkHttpClient
        get() = buildClient()

    private fun buildClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(defaultTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(defaultTimeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(defaultTimeoutMs, TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)

        proxyConfig?.let { proxy ->
            val proxyAddress = java.net.InetSocketAddress(proxy.host, proxy.port)
            val proxyType = when (proxy.type.lowercase()) {
                "socks4", "socks5" -> java.net.Proxy.Type.SOCKS
                else -> java.net.Proxy.Type.HTTP
            }
            builder.proxy(java.net.Proxy(proxyType, proxyAddress))

            if (proxy.username != null && proxy.password != null) {
                builder.proxyAuthenticator { _, response ->
                    val credential = okhttp3.Credentials.basic(proxy.username, proxy.password)
                    response.request.newBuilder()
                        .header("Proxy-Authorization", credential)
                        .build()
                }
            }
        }

        if (enableLogging) {
            builder.addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
        }

        return builder.build()
    }

    // ----- Core HTTP Methods -----

    override suspend fun fetch(request: BrowserRequest): BrowserResponse = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var lastException: Exception? = null

        repeat(maxRetries) { attempt ->
            try {
                val response = executeRequest(request)
                return@withContext response.copy(
                    requestTimeMs = System.currentTimeMillis() - startTime
                )
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries - 1) {
                    Thread.sleep(retryDelayMs * (attempt + 1))
                }
            }
        }

        BrowserResponse(
            url = request.url,
            statusCode = 0,
            headers = emptyMap(),
            body = "",
            requestTimeMs = System.currentTimeMillis() - startTime,
            isSuccessful = false,
            errorMessage = lastException?.message ?: "Unknown error after $maxRetries retries"
        )
    }

    private fun executeRequest(request: BrowserRequest): BrowserResponse {
        val url = request.url
        val method = request.method.uppercase()

        val requestBuilder = Request.Builder()
            .url(url)
            .apply {
                defaultHeaders.forEach { (key, value) ->
                    header(key, value)
                }
                request.headers.forEach { (key, value) ->
                    header(key, value)
                }
                apiKey?.let { header("X-API-Key", it) }
                request.userAgent?.let { header("User-Agent", it) }
            }

        if (method in listOf("POST", "PUT", "PATCH") && request.body != null) {
            val contentType = request.headers["Content-Type"] ?: "application/json"
            val body = request.body.toRequestBody(contentType.toMediaType())
            when (method) {
                "POST" -> requestBuilder.post(body)
                "PUT" -> requestBuilder.put(body)
                "PATCH" -> requestBuilder.patch(body)
            }
        } else {
            when (method) {
                "GET" -> requestBuilder.get()
                "HEAD" -> requestBuilder.head()
                "DELETE" -> requestBuilder.delete()
            }
        }

        val okRequest = requestBuilder.build()
        val response = client.newCall(okRequest).execute()

        val responseHeaders = response.headers.toMultimap()
        val responseBody = response.body?.string() ?: ""

        return BrowserResponse(
            url = response.request.url.toString(),
            statusCode = response.code,
            headers = responseHeaders,
            body = responseBody,
            contentType = response.header("Content-Type"),
            contentLength = response.body?.contentLength() ?: -1,
            redirectUrl = response.priorResponse?.request?.url?.toString(),
            isSuccessful = response.isSuccessful
        )
    }

    override suspend fun fetchHtml(url: String, headers: Map<String, String>): BrowserResponse {
        return fetch(BrowserRequest(
            url = url,
            method = "GET",
            headers = headers + ("Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        ))
    }

    override suspend fun fetchJson(url: String, headers: Map<String, String>): JsonObject? {
        val response = fetch(BrowserRequest(
            url = url,
            method = "GET",
            headers = headers + ("Accept" to "application/json")
        ))
        return if (response.isSuccessful) {
            try {
                json.parseToJsonElement(response.body).jsonObject
            } catch (e: Exception) {
                null
            }
        } else null
    }

    override suspend fun postJson(
        url: String,
        body: JsonObject,
        headers: Map<String, String>
    ): BrowserResponse {
        return fetch(BrowserRequest(
            url = url,
            method = "POST",
            headers = headers + ("Content-Type" to "application/json"),
            body = json.encodeToString(body)
        ))
    }

    // ----- Page Content Extraction -----

    override suspend fun extractPageContent(request: PageContentRequest): PageContentResponse = withContext(Dispatchers.IO) {
        try {
            val requestBody = json.encodeToString(request)
                .toRequestBody("application/json".toMediaType())

            val httpRequest = Request.Builder()
                .url("$baseUrl/v1/browser/extract")
                .post(requestBody)
                .header("Content-Type", "application/json")
                .apply { apiKey?.let { header("X-API-Key", it) } }
                .build()

            val response = client.newCall(httpRequest).execute()
            val body = response.body?.string() ?: "{}"

            if (response.isSuccessful) {
                json.decodeFromString(PageContentResponse.serializer(), body)
            } else {
                PageContentResponse(
                    url = request.url,
                    content = "",
                    error = "HTTP ${response.code}: $body"
                )
            }
        } catch (e: Exception) {
            PageContentResponse(
                url = request.url,
                content = "",
                error = e.message
            )
        }
    }

    override suspend fun extractArticle(url: String, maxLength: Int): PageContentResponse {
        return extractPageContent(PageContentRequest(
            url = url,
            extractMode = "article",
            maxLength = maxLength
        ))
    }

    // ----- Search -----

    override suspend fun search(request: SearchRequest): SearchResponse = withContext(Dispatchers.IO) {
        try {
            val requestBody = json.encodeToString(request)
                .toRequestBody("application/json".toMediaType())

            val httpRequest = Request.Builder()
                .url("$baseUrl/v1/search")
                .post(requestBody)
                .header("Content-Type", "application/json")
                .apply { apiKey?.let { header("X-API-Key", it) } }
                .build()

            val response = client.newCall(httpRequest).execute()
            val body = response.body?.string() ?: "{}"

            if (response.isSuccessful) {
                json.decodeFromString(SearchResponse.serializer(), body)
            } else {
                SearchResponse(
                    query = request.query,
                    error = "HTTP ${response.code}: $body"
                )
            }
        } catch (e: Exception) {
            SearchResponse(
                query = request.query,
                error = e.message
            )
        }
    }

    override suspend fun search(query: String, maxResults: Int): SearchResponse {
        return search(SearchRequest(query = query, maxResults = maxResults))
    }

    // ----- Screenshots -----

    override suspend fun takeScreenshot(request: ScreenshotRequest): ScreenshotResponse = withContext(Dispatchers.IO) {
        try {
            val requestBody = json.encodeToString(request)
                .toRequestBody("application/json".toMediaType())

            val httpRequest = Request.Builder()
                .url("$baseUrl/v1/browser/screenshot")
                .post(requestBody)
                .header("Content-Type", "application/json")
                .apply { apiKey?.let { header("X-API-Key", it) } }
                .build()

            val response = client.newCall(httpRequest).execute()
            val body = response.body?.string() ?: "{}"

            if (response.isSuccessful) {
                json.decodeFromString(ScreenshotResponse.serializer(), body)
            } else {
                ScreenshotResponse(
                    url = request.url,
                    error = "HTTP ${response.code}: $body"
                )
            }
        } catch (e: Exception) {
            ScreenshotResponse(
                url = request.url,
                error = e.message
            )
        }
    }

    override suspend fun takeScreenshot(url: String, fullPage: Boolean): ScreenshotResponse {
        return takeScreenshot(ScreenshotRequest(url = url, fullPage = fullPage))
    }

    // ----- Form Submission -----

    override suspend fun submitForm(request: FormSubmitRequest): FormSubmitResponse = withContext(Dispatchers.IO) {
        try {
            val requestBody = json.encodeToString(request)
                .toRequestBody("application/json".toMediaType())

            val httpRequest = Request.Builder()
                .url("$baseUrl/v1/browser/form-submit")
                .post(requestBody)
                .header("Content-Type", "application/json")
                .apply { apiKey?.let { header("X-API-Key", it) } }
                .build()

            val response = client.newCall(httpRequest).execute()
            val body = response.body?.string() ?: "{}"

            if (response.isSuccessful) {
                json.decodeFromString(FormSubmitResponse.serializer(), body)
            } else {
                FormSubmitResponse(
                    success = false,
                    error = "HTTP ${response.code}: $body"
                )
            }
        } catch (e: Exception) {
            FormSubmitResponse(
                success = false,
                error = e.message
            )
        }
    }

    override suspend fun submitForm(url: String, data: Map<String, String>): FormSubmitResponse {
        return submitForm(FormSubmitRequest(url = url, formData = data))
    }

// ----- Cookie Management -----

    override suspend fun syncCookies(request: CookieSyncRequest): Boolean = withContext(Dispatchers.IO) {
        try {
            val requestBody = json.encodeToString(request)
                .toRequestBody("application/json".toMediaType())

            val httpRequest = Request.Builder()
                .url("$baseUrl/v1/browser/cookies/sync")
                .post(requestBody)
                .header("Content-Type", "application/json")
                .apply { apiKey?.let { header("X-API-Key", it) } }
                .build()

            val response = client.newCall(httpRequest).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun getCookies(domain: String): List<BrowserCookie> = withContext(Dispatchers.IO) {
        try {
            val httpRequest = Request.Builder()
                .url("$baseUrl/v1/browser/cookies?domain=$domain")
                .get()
                .apply { apiKey?.let { header("X-API-Key", it) } }
                .build()

            val response = client.newCall(httpRequest).execute()
            val body = response.body?.string() ?: "[]"

            if (response.isSuccessful) {
                json.decodeFromString(ListSerializer(BrowserCookie.serializer()), body)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun clearCookies(domain: String?): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = if (domain != null) {
                "$baseUrl/v1/browser/cookies?domain=$domain"
            } else {
                "$baseUrl/v1/browser/cookies"
            }

            val httpRequest = Request.Builder()
                .url(url)
                .delete()
                .apply { apiKey?.let { header("X-API-Key", it) } }
                .build()

            val response = client.newCall(httpRequest).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    // ----- Health & Info -----

    override suspend fun checkHealth(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/health")
                .get()
                .build()

            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun getServerInfo(): Map<String, String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/v1/info")
                .get()
                .apply { apiKey?.let { header("X-API-Key", it) } }
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "{}"

            if (response.isSuccessful) {
                json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), body)
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    // ----- Configuration -----

    override fun setProxy(config: ProxyConfig?) {
        proxyConfig = config
    }

    override fun setDefaultHeaders(headers: Map<String, String>) {
        defaultHeaders = headers
    }

    override fun setTimeout(timeoutMs: Long) {
        defaultTimeoutMs = timeoutMs
    }

    override fun setRetryPolicy(maxRetries: Int, retryDelayMs: Long) {
        this.maxRetries = maxRetries
        this.retryDelayMs = retryDelayMs
    }
}

// ======================== MOCK IMPLEMENTATION ========================

class BrowserAPIMock : BrowserAPI {

    private val mockResponses = mutableMapOf<String, String>()
    private var proxyConfig: ProxyConfig? = null
    private var defaultHeaders: Map<String, String> = emptyMap()
    private var defaultTimeoutMs: Long = 30000
    private var maxRetries: Int = 3
    private var retryDelayMs: Long = 1000

    fun setMockResponse(url: String, response: String) {
        mockResponses[url] = response
    }

    fun clearMocks() {
        mockResponses.clear()
    }

    override suspend fun fetch(request: BrowserRequest): BrowserResponse {
        val mockBody = mockResponses[request.url] ?: "<html><body><h1>Mock Response</h1></body></html>"
        return BrowserResponse(
            url = request.url,
            statusCode = 200,
            headers = mapOf("Content-Type" to listOf("text/html")),
            body = mockBody,
            isSuccessful = true,
            requestTimeMs = 150
        )
    }

    override suspend fun fetchHtml(url: String, headers: Map<String, String>): BrowserResponse {
        return fetch(BrowserRequest(url = url))
    }

    override suspend fun fetchJson(url: String, headers: Map<String, String>): JsonObject? {
        val response = fetch(BrowserRequest(url = url))
        return if (response.isSuccessful) {
            Json.parseToJsonElement(response.body).jsonObject
        } else null
    }

    override suspend fun postJson(url: String, body: JsonObject, headers: Map<String, String>): BrowserResponse {
        return fetch(BrowserRequest(url = url, method = "POST", body = body.toString()))
    }

    override suspend fun extractPageContent(request: PageContentRequest): PageContentResponse {
        return PageContentResponse(
            url = request.url,
            title = "Mock Article Title",
            content = "This is a mock extracted content for testing purposes. It contains enough text to simulate a real article extraction.".repeat(5),
            extractedLinks = listOf(
                ExtractedLink("https://example.com/link1", "Link 1"),
                ExtractedLink("https://example.com/link2", "Link 2")
            ),
            wordCount = 50,
            readingTimeMinutes = 1
        )
    }

    override suspend fun extractArticle(url: String, maxLength: Int): PageContentResponse {
        return extractPageContent(PageContentRequest(url = url, extractMode = "article", maxLength = maxLength))
    }

    override suspend fun search(request: SearchRequest): SearchResponse {
        return SearchResponse(
            query = request.query,
            results = List(request.maxResults.coerceAtMost(3)) { i ->
                SearchResult(
                    title = "Mock Result ${i + 1} for \"${request.query}\"",
                    url = "https://example.com/result$i",
                    snippet = "This is a mock search result snippet for testing the search functionality.",
                    rank = i + 1
                )
            },
            totalResults = request.maxResults,
            searchTimeMs = 250
        )
    }

    override suspend fun search(query: String, maxResults: Int): SearchResponse {
        return search(SearchRequest(query = query, maxResults = maxResults))
    }

    override suspend fun takeScreenshot(request: ScreenshotRequest): ScreenshotResponse {
        return ScreenshotResponse(
            url = request.url,
            imageBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==",
            width = request.width,
            height = request.height,
            format = request.format,
            fileSize = 95
        )
    }

    override suspend fun takeScreenshot(url: String, fullPage: Boolean): ScreenshotResponse {
        return takeScreenshot(ScreenshotRequest(url = url, fullPage = fullPage))
    }

    override suspend fun submitForm(request: FormSubmitRequest): FormSubmitResponse {
        return FormSubmitResponse(
            success = true,
            responseUrl = request.url,
            responseBody = "{\"status\": \"success\", \"message\": \"Form submitted successfully\"}",
            statusCode = 200
        )
    }

    override suspend fun submitForm(url: String, data: Map<String, String>): FormSubmitResponse {
        return submitForm(FormSubmitRequest(url = url, formData = data))
    }

    override suspend fun syncCookies(request: CookieSyncRequest): Boolean = true

    override suspend fun getCookies(domain: String): List<BrowserCookie> {
        return listOf(
            BrowserCookie("session_id", "mock123", domain),
            BrowserCookie("user_pref", "dark_mode", domain)
        )
    }

    override suspend fun clearCookies(domain: String?): Boolean = true

    override suspend fun checkHealth(): Boolean = true

    override suspend fun getServerInfo(): Map<String, String> {
        return mapOf(
            "version" to "1.0.0-mock",
            "status" to "healthy",
            "environment" to "test"
        )
    }

    override fun setProxy(config: ProxyConfig?) {
        proxyConfig = config
    }

    override fun setDefaultHeaders(headers: Map<String, String>) {
        defaultHeaders = headers
    }

    override fun setTimeout(timeoutMs: Long) {
        defaultTimeoutMs = timeoutMs
    }

    override fun setRetryPolicy(maxRetries: Int, retryDelayMs: Long) {
        this.maxRetries = maxRetries
        this.retryDelayMs = retryDelayMs
    }
}

// ======================== FACTORY ========================

object BrowserAPIFactory {
    fun create(
        baseUrl: String = "https://api.nexus-agent.dev",
        apiKey: String? = null,
        useMock: Boolean = false,
        enableLogging: Boolean = false
    ): BrowserAPI {
        return if (useMock) {
            BrowserAPIMock()
        } else {
            BrowserAPIImpl(baseUrl, apiKey, enableLogging)
        }
    }
}