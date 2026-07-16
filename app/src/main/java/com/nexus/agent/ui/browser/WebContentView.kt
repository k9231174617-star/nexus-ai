package com.nexus.agent.ui.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.nexus.agent.R
import java.io.ByteArrayInputStream

/**
 * Enhanced WebView wrapper with recording capabilities, content injection,
 * screenshot support, and AI-ready content extraction hooks.
 */
class WebContentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val webView: WebView
    private val loadingView: View
    private val errorView: View

    private var onPageStartedListener: ((String) -> Unit)? = null
    private var onPageFinishedListener: ((String) -> Unit)? = null
    private var onProgressChangedListener: ((Int) -> Unit)? = null
    private var onReceivedTitleListener: ((String) -> Unit)? = null
    private var onReceivedIconListener: ((Bitmap) -> Unit)? = null

    private var isRecording = false
    private val recordedActions = mutableListOf<RecordedAction>()

    private val touchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.touch_indicator)
        alpha = 120
        style = Paint.Style.FILL
    }

    data class RecordedAction(
        val type: ActionType,
        val x: Float,
        val y: Float,
        val timestamp: Long,
        val target: String? = null,
        val value: String? = null
    )

    enum class ActionType {
        CLICK, LONG_CLICK, SCROLL, TEXT_INPUT, NAVIGATE
    }

    init {
        webView = WebView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        // Loading indicator
        loadingView = View(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            setBackgroundColor(ContextCompat.getColor(context, R.color.loading_overlay))
            visibility = View.GONE
        }

        // Error view
        errorView = View(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            setBackgroundColor(ContextCompat.getColor(context, R.color.error_bg))
            visibility = View.GONE
        }

        addView(webView)
        addView(loadingView)
        addView(errorView)

        setupWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun configure(
        javaScriptEnabled: Boolean = true,
        domStorageEnabled: Boolean = true,
        cacheMode: Int = WebSettings.LOAD_DEFAULT,
        userAgent: String? = null
    ) {
        webView.settings.apply {
            this.javaScriptEnabled = javaScriptEnabled
            this.domStorageEnabled = domStorageEnabled
            this.cacheMode = cacheMode
            this.loadWithOverviewMode = true
            this.useWideViewPort = true
            this.setSupportZoom(true)
            this.builtInZoomControls = true
            this.displayZoomControls = false
            this.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            this.allowFileAccess = false
            this.allowContentAccess = false

            userAgent?.let { this.userAgentString = it }
        }

        webView.addJavascriptInterface(WebAppInterface(), "NexusBridge")
    }

    private fun setupWebView() {
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false

                // Block dangerous schemes
                if (url.startsWith("intent://") || url.startsWith("market://")) {
                    return true
                }

                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                loadingView.visibility = View.VISIBLE
                errorView.visibility = View.GONE
                url?.let { onPageStartedListener?.invoke(it) }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                loadingView.visibility = View.GONE
                url?.let { onPageFinishedListener?.invoke(it) }

                // Inject Nexus AI bridge script
                injectBridgeScript()
            }

            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    loadingView.visibility = View.GONE
                    errorView.visibility = View.VISIBLE
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: android.webkit.WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (errorResponse?.statusCode == 404) {
                    // Handle 404
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                onProgressChangedListener?.invoke(newProgress)
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                title?.let { onReceivedTitleListener?.invoke(it) }
            }

            override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                super.onReceivedIcon(view, icon)
                icon?.let { onReceivedIconListener?.invoke(it) }
            }
        }
    }

    private fun injectBridgeScript() {
        val script = """
            (function() {
                window.NexusAI = {
                    extractContent: function() {
                        return {
                            title: document.title,
                            url: window.location.href,
                            text: document.body.innerText,
                            links: Array.from(document.querySelectorAll('a')).map(a => ({
                                text: a.innerText,
                                href: a.href
                            })),
                            headings: Array.from(document.querySelectorAll('h1,h2,h3')).map(h => ({
                                level: h.tagName,
                                text: h.innerText
                            }))
                        };
                    },
                    highlightElement: function(selector) {
                        const el = document.querySelector(selector);
                        if (el) {
                            el.style.outline = '2px solid #00D4FF';
                            el.scrollIntoView({ behavior: 'smooth', block: 'center' });
                        }
                    },
                    fillForm: function(selector, value) {
                        const el = document.querySelector(selector);
                        if (el) {
                            el.value = value;
                            el.dispatchEvent(new Event('input', { bubbles: true }));
                        }
                    },
                    clickElement: function(selector) {
                        const el = document.querySelector(selector);
                        if (el) el.click();
                    }
                };
            })();
        """.trimIndent()

        evaluateJavascript(script) {}
    }

    fun loadUrl(url: String) {
        webView.loadUrl(url)
    }

    fun reload() {
        webView.reload()
    }

    fun canGoBack(): Boolean = webView.canGoBack()

    fun goBack() {
        if (webView.canGoBack()) webView.goBack()
    }

    fun canGoForward(): Boolean = webView.canGoForward()

    fun goForward() {
        if (webView.canGoForward()) webView.goForward()
    }

    fun stopLoading() {
        webView.stopLoading()
    }

    fun evaluateJavascript(script: String, callback: ((String) -> Unit)? = null) {
        webView.evaluateJavascript(script) { result ->
            callback?.invoke(result ?: "null")
        }
    }

    fun getHtmlContent(): String {
        var html = ""
        evaluateJavascript("document.documentElement.outerHTML") { result ->
            html = result?.removePrefix("\"")?.removeSuffix("\"")?.replace("\\\"", "\"")
                ?.replace("\\n", "\n") ?: ""
        }
        // Small delay to allow JS execution
        Thread.sleep(100)
        return html
    }

    fun captureScreenshot(): Bitmap {
        webView.isDrawingCacheEnabled = true
        webView.buildDrawingCache()
        val bitmap = Bitmap.createBitmap(
            webView.drawingCache.copy(Bitmap.Config.ARGB_8888, true)
        )
        webView.isDrawingCacheEnabled = false
        return bitmap
    }

    fun setRecordingEnabled(enabled: Boolean) {
        isRecording = enabled
        if (!enabled) {
            recordedActions.clear()
        }
    }

    fun getRecordedActions(): List<RecordedAction> = recordedActions.toList()

    fun scrollTo(x: Int, y: Int) {
        webView.scrollTo(x, y)
    }

    fun scrollBy(dx: Int, dy: Int) {
        webView.scrollBy(dx, dy)
    }

    fun findAllAsync(find: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            webView.findAllAsync(find)
        }
    }

    fun clearMatches() {
        webView.clearMatches()
    }

    fun setOnPageStartedListener(listener: (String) -> Unit) {
        onPageStartedListener = listener
    }

    fun setOnPageFinishedListener(listener: (String) -> Unit) {
        onPageFinishedListener = listener
    }

    fun setOnProgressChangedListener(listener: (Int) -> Unit) {
        onProgressChangedListener = listener
    }

    fun setOnReceivedTitleListener(listener: (String) -> Unit) {
        onReceivedTitleListener = listener
    }

    fun setOnReceivedIconListener(listener: (Bitmap) -> Unit) {
        onReceivedIconListener = listener
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isRecording) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    recordAction(ActionType.CLICK, event.x, event.y)
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun recordAction(type: ActionType, x: Float, y: Float) {
        recordedActions.add(
            RecordedAction(
                type = type,
                x = x,
                y = y,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw touch indicators when recording
        if (isRecording) {
            recordedActions.lastOrNull()?.let { action ->
                if (System.currentTimeMillis() - action.timestamp < 500) {
                    canvas.drawCircle(action.x, action.y, 20f, touchPaint)
                }
            }
        }
    }

    fun destroy() {
        webView.stopLoading()
        webView.loadUrl("about:blank")
        webView.clearHistory()
        webView.removeAllViews()
        webView.destroy()
    }

    private inner class WebAppInterface {
        @JavascriptInterface
        fun log(message: String) {
            android.util.Log.d("WebBridge", message)
        }

        @JavascriptInterface
        fun onElementClicked(tag: String, id: String, className: String) {
            if (isRecording) {
                recordAction(
                    ActionType.CLICK,
                    0f, 0f // Coordinates captured via touch
                )
            }
        }
    }

    companion object {
        private const val TAG = "WebContentView"
    }
}
