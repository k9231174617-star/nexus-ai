package com.nexus.agent.core.browser

import android.webkit.WebView
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class SearchResult(
    val title: String,
    val url: String,
    val snippet: String,
)

@Singleton
class ContentExtractor @Inject constructor() {

    private val extractScript = """
        (function() {
            var content = '';
            var elements = document.querySelectorAll('p, h1, h2, h3, h4, article, main, .content');
            elements.forEach(function(el) {
                var text = el.innerText.trim();
                if (text.length > 30) content += text + '\n\n';
            });
            return content.substring(0, 10000);
        })()
    """.trimIndent()

    private val metaScript = """
        JSON.stringify({
            title: document.title,
            description: document.querySelector('meta[name=description]')?.content || '',
            canonical: document.querySelector('link[rel=canonical]')?.href || window.location.href
        })
    """.trimIndent()

    suspend fun extract(webView: WebView): String = suspendCancellableCoroutine { cont ->
        webView.evaluateJavascript(extractScript) { result ->
            val cleaned = result?.trim('"')?.replace("\\n", "\n")?.replace("\\\"", "\"") ?: ""
            cont.resume(cleaned)
        }
    }

    suspend fun extractMeta(webView: WebView): Map<String, String> =
        suspendCancellableCoroutine { cont ->
            webView.evaluateJavascript(metaScript) { result ->
                try {
                    val json = org.json.JSONObject(result?.trim('"') ?: "{}")
                    cont.resume(mapOf(
                        "title" to json.optString("title"),
                        "description" to json.optString("description"),
                        "canonical" to json.optString("canonical"),
                    ))
                } catch (_: Exception) { cont.resume(emptyMap()) }
            }
        }
}