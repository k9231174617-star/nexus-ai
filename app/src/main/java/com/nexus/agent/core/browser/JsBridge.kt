package com.nexus.agent.core.browser

import android.webkit.WebView
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JsBridge @Inject constructor() {

    fun evaluate(script: String): String {
        // Simplified: in production use WebView.evaluateJavascript with callback
        return ""
    }

    fun evaluateJs(webView: WebView, script: String, callback: (String) -> Unit) {
        webView.evaluateJavascript(script) { value ->
            callback(value ?: "")
        }
    }
}
