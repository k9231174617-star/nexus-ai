package com.nexus.agent.core.browser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchEngine @Inject constructor(
    private val client: OkHttpClient,
) {
    private val engines = mapOf(
        "duckduckgo" to "https://api.duckduckgo.com/?q=%s&format=json&no_html=1",
        "brave"      to "https://api.search.brave.com/res/v1/web/search?q=%s",
        "serp"       to "https://serpapi.com/search?q=%s&format=json",
    )

    suspend fun search(query: String, engine: String = "duckduckgo"): List<SearchResult> =
        withContext(Dispatchers.IO) {
            val urlTemplate = engines[engine] ?: engines["duckduckgo"]!!
            val url = urlTemplate.format(java.net.URLEncoder.encode(query, "UTF-8"))

            try {
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext emptyList()
                parseResults(body, engine)
            } catch (e: Exception) {
                emptyList()
            }
        }

    private fun parseResults(json: String, engine: String): List<SearchResult> {
        return try {
            val obj = JSONObject(json)
            when (engine) {
                "duckduckgo" -> {
                    val related = obj.optJSONArray("RelatedTopics") ?: return emptyList()
                    (0 until minOf(related.length(), 10)).mapNotNull { i ->
                        val item = related.getJSONObject(i)
                        val text = item.optString("Text", "")
                        val href = item.optString("FirstURL", "")
                        if (text.isNotBlank() && href.isNotBlank()) {
                            SearchResult(
                                title = text.take(80),
                                url = href,
                                snippet = text,
                            )
                        } else null
                    }
                }
                else -> emptyList()
            }
        } catch (_: Exception) { emptyList() }
    }
}