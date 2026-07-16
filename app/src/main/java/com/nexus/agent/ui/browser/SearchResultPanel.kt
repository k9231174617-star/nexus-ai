package com.nexus.agent.ui.browser

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.nexus.agent.R
import com.nexus.agent.core.browser.SearchEngine

/**
 * Overlay panel for displaying search results with ranking, snippets,
 * and direct navigation capabilities.
 */
class SearchResultPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val scrollView: ScrollView
    private val resultsContainer: LinearLayout
    private val headerText: TextView
    private val dismissButton: TextView
    private val emptyView: TextView

    private var onResultClickListener: ((SearchEngine.SearchResult) -> Unit)? = null
    private var onDismissListener: (() -> Unit)? = null

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val cornerRadius = 16f
    private val rect = RectF()

    private var isVisible = false

    init {
        visibility = View.GONE
        setPadding(16, 16, 16, 16)

        // Shadow/background will be drawn in onDraw
        setWillNotDraw(false)

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }

        // Header
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 16)
        }

        headerText = TextView(context).apply {
            text = "Search Results"
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, R.color.neon_white))
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }

        dismissButton = TextView(context).apply {
            text = "✕"
            textSize = 18f
            setTextColor(ContextCompat.getColor(context, R.color.neon_gray))
            setPadding(16, 8, 16, 8)
            isClickable = true
            isFocusable = true
        }

        headerRow.addView(headerText)
        headerRow.addView(dismissButton)
        layout.addView(headerRow)

        // Empty state
        emptyView = TextView(context).apply {
            text = "No results found"
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.neon_gray))
            visibility = View.GONE
            setPadding(16, 32, 16, 32)
        }
        layout.addView(emptyView)

        // Results scroll
        scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        resultsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        scrollView.addView(resultsContainer)
        layout.addView(scrollView)

        addView(layout)

        dismissButton.setOnClickListener {
            hide()
            onDismissListener?.invoke()
        }

        // Dismiss on outside click
        setOnClickListener {
            hide()
            onDismissListener?.invoke()
        }

        // Prevent click propagation from content
        layout.setOnClickListener { /* consume */ }
    }

    fun showResults(results: List<SearchEngine.SearchResult>) {
        resultsContainer.removeAllViews()

        if (results.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            scrollView.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            scrollView.visibility = View.VISIBLE

            headerText.text = "Search Results (${results.size})"

            results.forEachIndexed { index, result ->
                val itemView = ResultItemView(context, result, index + 1)
                itemView.setOnClickListener {
                    onResultClickListener?.invoke(result)
                }
                resultsContainer.addView(itemView)

                // Staggered animation
                itemView.alpha = 0f
                itemView.translationY = 30f
                itemView.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(250)
                    .setStartDelay((index * 50).toLong())
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
        }

        visibility = View.VISIBLE
        isVisible = true

        // Entrance animation
        alpha = 0f
        scaleX = 0.95f
        scaleY = 0.95f
        animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    fun hide() {
        if (!isVisible) return

        animate()
            .alpha(0f)
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(150)
            .withEndAction {
                visibility = View.GONE
                isVisible = false
            }
            .start()
    }

    fun isShowing(): Boolean = isVisible

    fun setOnResultClickListener(listener: (SearchEngine.SearchResult) -> Unit) {
        onResultClickListener = listener
    }

    fun setOnDismissListener(listener: () -> Unit) {
        onDismissListener = listener
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!isVisible) return

        rect.set(8f, 8f, width - 8f, height - 8f)

        // Shadow
        shadowPaint.color = ContextCompat.getColor(context, R.color.panel_shadow)
        canvas.drawRoundRect(
            rect.left + 4, rect.top + 8,
            rect.right + 4, rect.bottom + 8,
            cornerRadius, cornerRadius, shadowPaint
        )

        // Background
        bgPaint.color = ContextCompat.getColor(context, R.color.panel_bg)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)

        // Border
        strokePaint.color = ContextCompat.getColor(context, R.color.panel_border)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, strokePaint)

        // Top accent line
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.neon_cyan)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(
            rect.left + 40, rect.top,
            rect.right - 40, rect.top + 3f,
            1.5f, 1.5f, accentPaint
        )
    }

    override fun dispatchDraw(canvas: Canvas) {
        onDraw(canvas)
        super.dispatchDraw(canvas)
    }

    /**
     * Individual search result item
     */
    private inner class ResultItemView @JvmOverloads constructor(
        context: Context,
        private val result: SearchEngine.SearchResult,
        private val rank: Int,
        attrs: AttributeSet? = null
    ) : FrameLayout(context, attrs) {

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val rankPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 28f
            textAlign = Paint.Align.CENTER
        }
        private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 26f
        }
        private val urlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 20f
        }
        private val snippetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 22f
        }

        private val cornerRadius = 10f
        private val rect = RectF()

        init {
            setPadding(16, 16, 16, 16)
            isClickable = true
            isFocusable = true

            // Ripple effect on click
            foreground = ContextCompat.getDrawable(context, android.R.attr.selectableItemBackground)
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(
                MeasureSpec.getSize(widthMeasureSpec),
                (120 * resources.displayMetrics.density).toInt()
            )
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            rect.set(4f, 4f, width - 4f, height - 4f)

            // Background
            bgPaint.color = ContextCompat.getColor(context, R.color.result_item_bg)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)

            // Hover/press effect handled by foreground drawable

            // Rank badge
            val badgeSize = 28f
            val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = when (rank) {
                    1 -> ContextCompat.getColor(context, R.color.rank_gold)
                    2 -> ContextCompat.getColor(context, R.color.rank_silver)
                    3 -> ContextCompat.getColor(context, R.color.rank_bronze)
                    else -> ContextCompat.getColor(context, R.color.rank_default)
                }
                style = Paint.Style.FILL
            }
            canvas.drawCircle(40f, 40f, badgeSize, badgePaint)

            rankPaint.color = ContextCompat.getColor(context, R.color.neon_white)
            canvas.drawText(rank.toString(), 40f, 48f, rankPaint)

            // Title
            titlePaint.color = ContextCompat.getColor(context, R.color.result_title)
            val title = if (result.title.length > 45) result.title.take(45) + "…" else result.title
            canvas.drawText(title, 80f, 38f, titlePaint)

            // URL
            urlPaint.color = ContextCompat.getColor(context, R.color.result_url)
            val displayUrl = result.displayUrl.take(50)
            canvas.drawText(displayUrl, 80f, 64f, urlPaint)

            // Snippet
            snippetPaint.color = ContextCompat.getColor(context, R.color.result_snippet)
            val snippet = if (result.snippet.length > 80) result.snippet.take(80) + "…" else result.snippet
            canvas.drawText(snippet, 16f, 94f, snippetPaint)

            // Bottom border
            val borderPaint = Paint().apply {
                color = ContextCompat.getColor(context, R.color.result_border)
                strokeWidth = 0.5f
            }
            canvas.drawLine(16f, height - 1f, width - 16f, height - 1f, borderPaint)
        }

        override fun dispatchDraw(canvas: Canvas) {
            onDraw(canvas)
            super.dispatchDraw(canvas)
        }
    }

    companion object {
        private const val TAG = "SearchResultPanel"
    }
}
