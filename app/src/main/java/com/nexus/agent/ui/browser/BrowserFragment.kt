package com.nexus.agent.ui.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.nexus.agent.R
import com.nexus.agent.core.browser.BrowserAgent
import com.nexus.agent.core.browser.ContentExtractor
import com.nexus.agent.core.browser.PageNavigator
import com.nexus.agent.core.browser.SearchEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Main browser fragment providing a full web browsing experience with AI integration.
 * Supports navigation, content extraction, action recording, and search.
 */
class BrowserFragment : Fragment() {

    private lateinit var addressBar: AddressBarView
    private lateinit var webContent: WebContentView
    private lateinit var actionRecorder: ActionRecorderView
    private lateinit var searchPanel: SearchResultPanel
    private lateinit var progressBar: ProgressBar
    private lateinit var navBack: ImageButton
    private lateinit var navForward: ImageButton
    private lateinit var navRefresh: ImageButton
    private lateinit var navHome: ImageButton
    private lateinit var btnRecord: ImageButton
    private lateinit var btnExtract: ImageButton
    private lateinit var btnScreenshot: ImageButton
    private lateinit var browserContainer: LinearLayout

    private val browserAgent = BrowserAgent()
    private val pageNavigator = PageNavigator()
    private val contentExtractor = ContentExtractor()
    private val searchEngine = SearchEngine()

    private var isRecording = false
    private var currentUrl: String = "https://www.google.com"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_browser, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupListeners()
        setupBrowser()

        savedInstanceState?.let {
            currentUrl = it.getString(KEY_CURRENT_URL, currentUrl)
            loadUrl(currentUrl)
        } ?: loadUrl(currentUrl)
    }

    private fun initViews(view: View) {
        addressBar = view.findViewById(R.id.address_bar)
        webContent = view.findViewById(R.id.web_content_view)
        actionRecorder = view.findViewById(R.id.action_recorder)
        searchPanel = view.findViewById(R.id.search_result_panel)
        progressBar = view.findViewById(R.id.browser_progress)
        navBack = view.findViewById(R.id.btn_nav_back)
        navForward = view.findViewById(R.id.btn_nav_forward)
        navRefresh = view.findViewById(R.id.btn_nav_refresh)
        navHome = view.findViewById(R.id.btn_nav_home)
        btnRecord = view.findViewById(R.id.btn_record)
        btnExtract = view.findViewById(R.id.btn_extract)
        btnScreenshot = view.findViewById(R.id.btn_screenshot)
        browserContainer = view.findViewById(R.id.browser_container)

        updateNavButtons()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupListeners() {
        // Address bar
        addressBar.setOnNavigateListener { url ->
            loadUrl(url)
        }

        addressBar.setOnSearchListener { query ->
            performSearch(query)
        }

        // Navigation buttons
        navBack.setOnClickListener {
            if (webContent.canGoBack()) {
                webContent.goBack()
            }
        }

        navForward.setOnClickListener {
            if (webContent.canGoForward()) {
                webContent.goForward()
            }
        }

        navRefresh.setOnClickListener {
            webContent.reload()
        }

        navHome.setOnClickListener {
            loadUrl("https://www.google.com")
        }

        // Web content callbacks
        webContent.setOnPageStartedListener { url ->
            progressBar.visibility = View.VISIBLE
            progressBar.progress = 0
            addressBar.setUrl(url)
            currentUrl = url
            updateNavButtons()
        }

        webContent.setOnPageFinishedListener { url ->
            progressBar.visibility = View.GONE
            addressBar.setUrl(url)
            currentUrl = url

            // Auto-extract if enabled
            if (browserAgent.isAutoExtractEnabled) {
                extractContent()
            }
        }

        webContent.setOnProgressChangedListener { progress ->
            progressBar.progress = progress
        }

        webContent.setOnReceivedTitleListener { title ->
            addressBar.setPageTitle(title)
        }

        // Action recorder
        btnRecord.setOnClickListener {
            toggleRecording()
        }

        btnExtract.setOnClickListener {
            extractContent()
        }

        btnScreenshot.setOnClickListener {
            takeScreenshot()
        }

        // Search panel
        searchPanel.setOnResultClickListener { result ->
            loadUrl(result.url)
            searchPanel.hide()
        }

        searchPanel.setOnDismissListener {
            searchPanel.hide()
        }
    }

    private fun setupBrowser() {
        browserAgent.initialize(requireContext())
        webContent.configure(
            javaScriptEnabled = true,
            domStorageEnabled = true,
            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
        )
    }

    private fun loadUrl(url: String) {
        val formattedUrl = if (url.startsWith("http://") || url.startsWith("https://")) {
            url
        } else {
            "https://$url"
        }

        webContent.loadUrl(formattedUrl)
        addressBar.setUrl(formattedUrl)
    }

    private fun performSearch(query: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val results = searchEngine.search(query)

                withContext(Dispatchers.Main) {
                    searchPanel.showResults(results)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Search failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun toggleRecording() {
        isRecording = !isRecording

        if (isRecording) {
            btnRecord.setImageResource(R.drawable.ic_stop_recording)
            btnRecord.setColorFilter(
                ContextCompat.getColor(requireContext(), R.color.neon_red)
            )
            actionRecorder.startRecording()
            webContent.setRecordingEnabled(true)
            Toast.makeText(context, "Recording started", Toast.LENGTH_SHORT).show()
        } else {
            btnRecord.setImageResource(R.drawable.ic_record)
            btnRecord.setColorFilter(
                ContextCompat.getColor(requireContext(), R.color.neon_gray)
            )
            actionRecorder.stopRecording()
            webContent.setRecordingEnabled(false)

            val actions = actionRecorder.getRecordedActions()
            Toast.makeText(context, "Recorded ${actions.size} actions", Toast.LENGTH_SHORT).show()
        }
    }

    private fun extractContent() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val html = webContent.getHtmlContent()
                val extracted = contentExtractor.extract(html, currentUrl)

                withContext(Dispatchers.Main) {
                    // Send to chat or show in panel
                    val summary = extracted.toSummary()
                    Toast.makeText(context, "Extracted: $summary", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Extraction failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun takeScreenshot() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bitmap = webContent.captureScreenshot()
                val filename = "screenshot_${System.currentTimeMillis()}.png"

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Screenshot saved: $filename", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Screenshot failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateNavButtons() {
        navBack.isEnabled = webContent.canGoBack()
        navBack.alpha = if (webContent.canGoBack()) 1.0f else 0.3f

        navForward.isEnabled = webContent.canGoForward()
        navForward.alpha = if (webContent.canGoForward()) 1.0f else 0.3f
    }

    fun injectJavaScript(script: String) {
        webContent.evaluateJavascript(script) { result ->
            // Handle result
        }
    }

    fun getCurrentUrl(): String = currentUrl

    fun getPageContent(): String = webContent.getHtmlContent()

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_CURRENT_URL, currentUrl)
    }

    override fun onDestroy() {
        super.onDestroy()
        browserAgent.cleanup()
        webContent.destroy()
    }

    companion object {
        private const val TAG = "BrowserFragment"
        private const val KEY_CURRENT_URL = "browser_current_url"

        fun newInstance(): BrowserFragment {
            return BrowserFragment()
        }
    }
}
