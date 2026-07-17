package com.nexus.agent.core.browser

import android.content.Context
import android.webkit.WebView
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebViewPool @Inject constructor(
    private val context: Context,
) {
    private val pool = mutableListOf<WebView>()
    private var active: WebView? = null
    private val maxSize = 4

    fun acquire(): WebView {
        val wv = if (pool.isNotEmpty()) {
            pool.removeAt(pool.size - 1)
        } else {
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
            }
        }
        active = wv
        return wv
    }

    fun release(webView: WebView) {
        if (pool.size < maxSize) {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            pool.add(webView)
        } else {
            webView.destroy()
        }
        if (active == webView) active = null
    }

    fun current(): WebView? = active

    fun destroy() {
        pool.forEach { it.destroy() }
        pool.clear()
        active?.destroy()
        active = null
    }
}
