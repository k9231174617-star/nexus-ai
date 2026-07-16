package com.nexus.agent.ui.browser

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.nexus.agent.R

/**
 * Custom address bar with URL display, search integration, security indicators,
 * and autocomplete suggestions.
 */
class AddressBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val urlInput: EditText
    private val securityIcon: ImageButton
    private val lockIcon: ImageButton
    private val clearButton: ImageButton
    private val urlContainer: LinearLayout
    private val titleText: TextView

    private var onNavigateListener: ((String) -> Unit)? = null
    private var onSearchListener: ((String) -> Unit)? = null

    private var isSecure = false
    private var currentUrl = ""
    private var pageTitle = ""

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val focusStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }

    private val cornerRadius = 28f
    private val rect = RectF()

    init {
        setPadding(8, 8, 8, 8)

        // Inflate or create layout programmatically
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 12, 16, 12)
        }

        securityIcon = ImageButton(context).apply {
            setImageResource(R.drawable.ic_security)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setPadding(8, 8, 8, 8)
        }

        lockIcon = ImageButton(context).apply {
            setImageResource(R.drawable.ic_lock)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setPadding(8, 8, 8, 8)
            visibility = View.GONE
        }

        urlContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }

        titleText = TextView(context).apply {
            textSize = 10f
            visibility = View.GONE
            setTextColor(ContextCompat.getColor(context, R.color.neon_gray))
            setPadding(4, 0, 4, 0)
        }

        urlInput = EditText(context).apply {
            background = null
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.neon_white))
            setHintTextColor(ContextCompat.getColor(context, R.color.neon_gray))
            hint = "Search or enter address"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setPadding(4, 4, 4, 4)
            isSingleLine = true
        }

        urlContainer.addView(titleText)
        urlContainer.addView(urlInput)

        clearButton = ImageButton(context).apply {
            setImageResource(R.drawable.ic_clear)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setPadding(8, 8, 8, 8)
            visibility = View.GONE
        }

        layout.addView(securityIcon)
        layout.addView(lockIcon)
        layout.addView(urlContainer)
        layout.addView(clearButton)

        addView(layout)

        setupListeners()
        updateThemeColors()
    }

    private fun setupListeners() {
        urlInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                handleInput(urlInput.text.toString())
                true
            } else {
                false
            }
        }

        urlInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                urlInput.setText(currentUrl)
                urlInput.selectAll()
                titleText.visibility = View.GONE
            } else {
                urlInput.setText(formatUrlForDisplay(currentUrl))
                if (pageTitle.isNotEmpty()) {
                    titleText.visibility = View.VISIBLE
                }
            }
            invalidate()
        }

        urlInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                clearButton.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
        })

        clearButton.setOnClickListener {
            urlInput.text?.clear()
        }

        securityIcon.setOnClickListener {
            showSecurityInfo()
        }
    }

    private fun handleInput(input: String) {
        urlInput.clearFocus()

        when {
            input.isBlank() -> return
            isUrl(input) -> onNavigateListener?.invoke(input)
            else -> onSearchListener?.invoke(input)
        }
    }

    private fun isUrl(input: String): Boolean {
        return input.matches(Regex("^(https?://)?([\\w-]+\\.)+[\\w-]+(/.*)?$")) ||
                input.matches(Regex("^(localhost|127\\.0\\.0\\.1|\\[::1\\])(:\\d+)?(/.*)?$")) ||
                input.matches(Regex("^(file|ftp|data|javascript|about):.*$", RegexOption.IGNORE_CASE))
    }

    private fun formatUrlForDisplay(url: String): String {
        return url.removePrefix("https://").removePrefix("http://").removePrefix("www.")
    }

    fun setUrl(url: String) {
        currentUrl = url
        isSecure = url.startsWith("https://")

        if (!urlInput.hasFocus()) {
            urlInput.setText(formatUrlForDisplay(url))
        }

        updateSecurityIndicators()
    }

    fun setPageTitle(title: String) {
        pageTitle = title
        titleText.text = title
        if (!urlInput.hasFocus() && title.isNotEmpty()) {
            titleText.visibility = View.VISIBLE
        }
    }

    fun getCurrentUrl(): String = currentUrl

    fun setOnNavigateListener(listener: (String) -> Unit) {
        onNavigateListener = listener
    }

    fun setOnSearchListener(listener: (String) -> Unit) {
        onSearchListener = listener
    }

    fun showSuggestions(suggestions: List<String>) {
        // Could show dropdown with suggestions
    }

    private fun updateSecurityIndicators() {
        if (isSecure) {
            securityIcon.setImageResource(R.drawable.ic_security_good)
            securityIcon.setColorFilter(ContextCompat.getColor(context, R.color.neon_green))
            lockIcon.visibility = View.VISIBLE
        } else if (currentUrl.startsWith("http://")) {
            securityIcon.setImageResource(R.drawable.ic_security_warning)
            securityIcon.setColorFilter(ContextCompat.getColor(context, R.color.neon_yellow))
            lockIcon.visibility = View.GONE
        } else {
            securityIcon.setImageResource(R.drawable.ic_security)
            securityIcon.setColorFilter(ContextCompat.getColor(context, R.color.neon_gray))
            lockIcon.visibility = View.GONE
        }
    }

    private fun showSecurityInfo() {
        val message = if (isSecure) {
            "Secure connection\n$currentUrl"
        } else {
            "Connection is not secure\n$currentUrl"
        }
        // Show dialog or tooltip
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        rect.set(8f, 8f, width - 8f, height - 8f)

        // Background
        bgPaint.color = ContextCompat.getColor(context, R.color.address_bar_bg)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)

        // Border
        val isFocused = urlInput.hasFocus()
        if (isFocused) {
            focusStrokePaint.color = ContextCompat.getColor(context, R.color.neon_cyan)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, focusStrokePaint)

            // Glow effect
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ContextCompat.getColor(context, R.color.neon_cyan)
                alpha = 20
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(
                rect.left - 1, rect.top - 1,
                rect.right + 1, rect.bottom + 1,
                cornerRadius + 1, cornerRadius + 1, glowPaint
            )
        } else {
            strokePaint.color = ContextCompat.getColor(context, R.color.address_bar_border)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, strokePaint)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        onDraw(canvas)
        super.dispatchDraw(canvas)
    }

    private fun updateThemeColors() {
        // Colors applied in init and draw methods
    }

    companion object {
        private const val TAG = "AddressBarView"
    }
}
