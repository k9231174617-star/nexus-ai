package com.nexus.agent.ui.rag

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.nexus.agent.R

/**
 * Advanced search query input with semantic hints, query history,
 * and real-time suggestion support.
 */
class SearchQueryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val queryInput: EditText
    private val searchButton: ImageButton
    private val clearButton: ImageButton
    private val historyContainer: LinearLayout
    private val suggestionPanel: LinearLayout

    private var onSearchListener: ((String) -> Unit)? = null
    private var onQueryFocusChangeListener: ((Boolean) -> Unit)? = null

    private val queryHistory = mutableListOf<String>()
    private val maxHistorySize = 10

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
        setPadding(12, 12, 12, 12)

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Query input row
        val inputRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 12, 16, 12)
        }

        searchButton = ImageButton(context).apply {
            setImageResource(R.drawable.ic_search)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setColorFilter(ContextCompat.getColor(context, R.color.neon_cyan))
            setPadding(8, 8, 8, 8)
        }

        queryInput = EditText(context).apply {
            background = null
            textSize = 15f
            setTextColor(ContextCompat.getColor(context, R.color.neon_white))
            setHintTextColor(ContextCompat.getColor(context, R.color.neon_gray))
            hint = "Ask about your documents..."
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(12, 8, 12, 8)
            maxLines = 4
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }

        clearButton = ImageButton(context).apply {
            setImageResource(R.drawable.ic_clear)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setColorFilter(ContextCompat.getColor(context, R.color.neon_gray))
            setPadding(8, 8, 8, 8)
            visibility = View.GONE
        }

        inputRow.addView(searchButton)
        inputRow.addView(queryInput)
        inputRow.addView(clearButton)
        layout.addView(inputRow)

        // Suggestion panel (initially hidden)
        suggestionPanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(16, 8, 16, 8)
        }
        layout.addView(suggestionPanel)

        // History
        historyContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12, 8, 12, 8)
        }
        layout.addView(historyContainer)

        addView(layout)
        setupListeners()
    }

    private fun setupListeners() {
        queryInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                        event.action == KeyEvent.ACTION_DOWN)
            ) {
                submitQuery()
                true
            } else {
                false
            }
        }

        queryInput.setOnFocusChangeListener { _, hasFocus ->
            onQueryFocusChangeListener?.invoke(hasFocus)
            if (hasFocus && queryHistory.isNotEmpty()) {
                showHistory()
            } else {
                hideHistory()
            }
            invalidate()
        }

        queryInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                clearButton.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                if (!s.isNullOrEmpty()) {
                    showSuggestions(s.toString())
                } else {
                    hideSuggestions()
                }
            }
        })

        searchButton.setOnClickListener {
            submitQuery()
        }

        clearButton.setOnClickListener {
            queryInput.text?.clear()
            queryInput.requestFocus()
        }
    }

    private fun submitQuery() {
        val query = queryInput.text?.toString()?.trim() ?: return
        if (query.isEmpty()) return

        addToHistory(query)
        onSearchListener?.invoke(query)
        queryInput.clearFocus()
        hideSuggestions()
        hideHistory()
    }

    private fun addToHistory(query: String) {
        queryHistory.remove(query)
        queryHistory.add(0, query)
        if (queryHistory.size > maxHistorySize) {
            queryHistory.removeLast()
        }
    }

    private fun showHistory() {
        historyContainer.removeAllViews()
        queryHistory.take(5).forEach { query ->
            val chip = HistoryChipView(context, query)
            chip.setOnClickListener {
                queryInput.setText(query)
                queryInput.setSelection(query.length)
                submitQuery()
            }
            historyContainer.addView(chip)
        }
        historyContainer.visibility = View.VISIBLE
    }

    private fun hideHistory() {
        historyContainer.visibility = View.GONE
    }

    private fun showSuggestions(query: String) {
        // Could integrate with semantic suggestion engine
        suggestionPanel.removeAllViews()
        suggestionPanel.visibility = View.GONE
    }

    private fun hideSuggestions() {
        suggestionPanel.visibility = View.GONE
    }

    fun setQuery(query: String) {
        queryInput.setText(query)
        queryInput.setSelection(query.length)
    }

    fun getQuery(): String = queryInput.text?.toString() ?: ""

    fun clear() {
        queryInput.text?.clear()
    }

    fun setEnabled(enabled: Boolean) {
        queryInput.isEnabled = enabled
        searchButton.isEnabled = enabled
        searchButton.alpha = if (enabled) 1.0f else 0.5f
    }

    fun setOnSearchListener(listener: (String) -> Unit) {
        onSearchListener = listener
    }

    fun setOnQueryFocusChangeListener(listener: (Boolean) -> Unit) {
        onQueryFocusChangeListener = listener
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val rect = RectF(12f, 12f, width - 12f, (queryInput.height + 48).toFloat())

        // Background
        bgPaint.color = ContextCompat.getColor(context, R.color.query_bg)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)

        // Border
        val isFocused = queryInput.hasFocus()
        if (isFocused) {
            focusStrokePaint.color = ContextCompat.getColor(context, R.color.neon_cyan)

            // Glow
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ContextCompat.getColor(context, R.color.neon_cyan)
                alpha = 25
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(
                rect.left - 2, rect.top - 2,
                rect.right + 2, rect.bottom + 2,
                cornerRadius + 2, cornerRadius + 2, glowPaint
            )

            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, focusStrokePaint)
        } else {
            strokePaint.color = ContextCompat.getColor(context, R.color.query_border)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, strokePaint)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        onDraw(canvas)
        super.dispatchDraw(canvas)
    }

    /**
     * History query chip
     */
    private inner class HistoryChipView @JvmOverloads constructor(
        context: Context,
        private val query: String,
        attrs: AttributeSet? = null
    ) : FrameLayout(context, attrs) {

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = ContextCompat.getColor(context, R.color.history_chip_bg)
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 22f
            color = ContextCompat.getColor(context, R.color.neon_gray)
        }

        init {
            setPadding(16, 8, 16, 8)
            isClickable = true
            isFocusable = true
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val textWidth = textPaint.measureText(query.take(20))
            setMeasuredDimension(
                (textWidth + 32).toInt(),
                (40 * resources.displayMetrics.density).toInt()
            )
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val rect = RectF(4f, 4f, width - 4f, height - 4f)
            canvas.drawRoundRect(rect, 16f, 16f, bgPaint)

            val displayText = if (query.length > 20) query.take(17) + "..." else query
            canvas.drawText(displayText, width / 2f, height / 2f + 8f, textPaint)
        }

        override fun dispatchDraw(canvas: Canvas) {
            onDraw(canvas)
            super.dispatchDraw(canvas)
        }
    }

    companion object {
        private const val TAG = "SearchQueryView"
    }
}
