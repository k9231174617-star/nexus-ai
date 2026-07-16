package com.nexus.agent.ui.rag

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.nexus.agent.R
import com.nexus.agent.core.rag.RetrievalResult

/**
 * Displays retrieved sources with relevance scores, content snippets,
 * and interactive attribution for RAG-generated responses.
 */
class SourceAttributionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val headerText: TextView
    private val resultsContainer: LinearLayout
    private val scrollView: ScrollView
    private val emptyView: TextView
    private val confidenceBar: ConfidenceBarView

    private var onSourceClickListener: ((RetrievalResult) -> Unit)? = null

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

    init {
        visibility = View.GONE
        setPadding(16, 16, 16, 16)
        setWillNotDraw(false)

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Header with confidence
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 8, 8, 12)
        }

        headerText = TextView(context).apply {
            text = "Sources"
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.neon_white))
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }

        confidenceBar = ConfidenceBarView(context)
        confidenceBar.layoutParams = LinearLayout.LayoutParams(
            (100 * resources.displayMetrics.density).toInt(),
            (20 * resources.displayMetrics.density).toInt()
        )

        headerRow.addView(headerText)
        headerRow.addView(confidenceBar)
        layout.addView(headerRow)

        // Empty state
        emptyView = TextView(context).apply {
            text = "No sources to display"
            textSize = 13f
            setTextColor(ContextCompat.getColor(context, R.color.neon_gray))
            gravity = android.view.Gravity.CENTER
            setPadding(16, 32, 16, 32)
            visibility = View.GONE
        }
        layout.addView(emptyView)

        // Results
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
    }

    fun showResults(results: List<RetrievalResult>) {
        resultsContainer.removeAllViews()
        visibility = View.VISIBLE

        if (results.isEmpty()) {
            showNoResults()
            return
        }

        emptyView.visibility = View.GONE
        scrollView.visibility = View.VISIBLE

        // Calculate average confidence
        val avgConfidence = results.map { it.score }.average().toFloat()
        confidenceBar.setConfidence(avgConfidence)

        headerText.text = "Sources (${results.size})"

        results.forEachIndexed { index, result ->
            val itemView = SourceItemView(context, result, index + 1)
            itemView.setOnClickListener {
                onSourceClickListener?.invoke(result)
            }
            resultsContainer.addView(itemView)

            // Staggered entrance
            itemView.alpha = 0f
            itemView.translationY = 20f
            itemView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(200)
                .setStartDelay((index * 80).toLong())
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    fun showNoResults() {
        resultsContainer.removeAllViews()
        emptyView.visibility = View.VISIBLE
        scrollView.visibility = View.GONE
        confidenceBar.setConfidence(0f)
        headerText.text = "Sources"
        visibility = View.VISIBLE
    }

    fun hide() {
        if (visibility == View.GONE) return

        animate()
            .alpha(0f)
            .translationY(30f)
            .setDuration(150)
            .withEndAction {
                visibility = View.GONE
                alpha = 1f
                translationY = 0f
            }
            .start()
    }

    fun isShowing(): Boolean = visibility == View.VISIBLE

    fun setOnSourceClickListener(listener: (RetrievalResult) -> Unit) {
        onSourceClickListener = listener
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (visibility != View.VISIBLE) return

        rect.set(8f, 8f, width - 8f, height - 8f)

        // Shadow
        shadowPaint.color = ContextCompat.getColor(context, R.color.source_shadow)
        canvas.drawRoundRect(
            rect.left + 2, rect.top + 4,
            rect.right + 2, rect.bottom + 4,
            cornerRadius, cornerRadius, shadowPaint
        )

        // Background
        bgPaint.color = ContextCompat.getColor(context, R.color.source_panel_bg)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)

        // Border
        strokePaint.color = ContextCompat.getColor(context, R.color.source_panel_border)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, strokePaint)

        // Top accent
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.neon_cyan)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(
            rect.left + 60, rect.top,
            rect.right - 60, rect.top + 2.5f,
            1.25f, 1.25f, accentPaint
        )
    }

    override fun dispatchDraw(canvas: Canvas) {
        onDraw(canvas)
        super.dispatchDraw(canvas)
    }

    /**
     * Confidence score visualization
     */
    private inner class ConfidenceBarView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null
    ) : FrameLayout(context, attrs) {

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = ContextCompat.getColor(context, R.color.confidence_bg)
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 18f
            textAlign = Paint.Align.CENTER
            color = ContextCompat.getColor(context, R.color.neon_white)
        }

        private var confidence = 0f

        fun setConfidence(value: Float) {
            confidence = value.coerceIn(0f, 1f)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val rect = RectF(4f, 4f, width - 4f, height - 4f)
            canvas.drawRoundRect(rect, 6f, 6f, bgPaint)

            // Fill based on confidence
            val fillWidth = rect.width() * confidence
            val fillRect = RectF(rect.left, rect.top, rect.left + fillWidth, rect.bottom)

            fillPaint.color = when {
                confidence >= 0.8f -> ContextCompat.getColor(context, R.color.confidence_high)
                confidence >= 0.5f -> ContextCompat.getColor(context, R.color.confidence_medium)
                else -> ContextCompat.getColor(context, R.color.confidence_low)
            }

            canvas.drawRoundRect(fillRect, 6f, 6f, fillPaint)

            // Percentage text
            canvas.drawText(
                "${(confidence * 100).toInt()}%",
                width / 2f, height / 2f + 6f, textPaint
            )
        }

        override fun dispatchDraw(canvas: Canvas) {
            onDraw(canvas)
            super.dispatchDraw(canvas)
        }
    }

    /**
     * Individual source attribution item
     */
    private inner class SourceItemView @JvmOverloads constructor(
        context: Context,
        private val result: RetrievalResult,
        private val rank: Int,
        attrs: AttributeSet? = null
    ) : FrameLayout(context, attrs) {

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val rankPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 24f
            textAlign = Paint.Align.CENTER
        }
        private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 20f
        }
        private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 24f
        }
        private val snippetPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 22f
            color = ContextCompat.getColor(context, R.color.source_snippet)
        }
        private val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 18f
            color = ContextCompat.getColor(context, R.color.neon_gray)
        }

        private val cornerRadius = 10f

        init {
            setPadding(16, 16, 16, 16)
            isClickable = true
            isFocusable = true

            foreground = ContextCompat.getDrawable(context, android.R.attr.selectableItemBackground)
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val availableWidth = MeasureSpec.getSize(widthMeasureSpec) - 64
            val snippetLayout = StaticLayout.Builder.obtain(
                result.content.take(150),
                0,
                result.content.take(150).length,
                snippetPaint,
                availableWidth
            ).setLineSpacing(1.15f, 1f).build()

            val height = (snippetLayout.height + 100).coerceIn(
                (120 * resources.displayMetrics.density).toInt(),
                (180 * resources.displayMetrics.density).toInt()
            )

            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val rect = RectF(4f, 4f, width - 4f, height - 4f)

            // Background
            bgPaint.color = ContextCompat.getColor(context, R.color.source_item_bg)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)

            // Rank badge
            val badgeRect = RectF(16f, 12f, 44f, 36f)
            val badgeColor = when (rank) {
                1 -> R.color.rank_gold
                2 -> R.color.rank_silver
                3 -> R.color.rank_bronze
                else -> R.color.rank_default
            }
            val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ContextCompat.getColor(context, badgeColor)
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(badgeRect, 4f, 4f, badgePaint)

            rankPaint.color = ContextCompat.getColor(context, R.color.neon_white)
            canvas.drawText(rank.toString(), badgeRect.centerX(), badgeRect.bottom - 4, rankPaint)

            // Score
            val scoreColor = when {
                result.score >= 0.8f -> R.color.confidence_high
                result.score >= 0.5f -> R.color.confidence_medium
                else -> R.color.confidence_low
            }
            scorePaint.color = ContextCompat.getColor(context, scoreColor)
            canvas.drawText(
                String.format("%.2f", result.score),
                width - 60f, 32f, scorePaint
            )

            // Document name
            titlePaint.color = ContextCompat.getColor(context, R.color.source_title)
            val docName = if (result.documentName.length > 35) {
                result.documentName.take(32) + "..."
            } else {
                result.documentName
            }
            canvas.drawText(docName, 56f, 32f, titlePaint)

            // Chunk info
            metaPaint.color = ContextCompat.getColor(context, R.color.neon_gray)
            canvas.drawText(
                "Chunk ${result.chunkIndex + 1}/${result.totalChunks} • ${result.tokenCount} tokens",
                56f, 54f, metaPaint
            )

            // Content snippet
            val snippet = if (result.content.length > 150) {
                result.content.take(147) + "..."
            } else {
                result.content
            }

            val staticLayout = StaticLayout.Builder.obtain(
                snippet, 0, snippet.length, snippetPaint, width - 48
            ).setLineSpacing(1.15f, 1f).build()

            canvas.save()
            canvas.translate(16f, 68f)
            staticLayout.draw(canvas)
            canvas.restore()

            // Bottom border
            val borderPaint = Paint().apply {
                color = ContextCompat.getColor(context, R.color.source_border)
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
        private const val TAG = "SourceAttributionView"
    }
}
