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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.content.ContextCompat
import com.nexus.agent.R
import com.nexus.agent.core.rag.DocumentIngestor
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Visualizes document chunks with syntax-aware highlighting,
 * relevance scoring indicators, and interactive selection.
 */
class ChunkPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val scrollView: ScrollView
    private val chunkContainer: LinearLayout
    private val filterText: androidx.appcompat.widget.AppCompatTextView

    private val chunks = CopyOnWriteArrayList<ChunkData>()
    private val chunkViews = mutableMapOf<String, ChunkItemView>()
    private var filteredDocumentId: String? = null
    private var highlightedChunkIds = setOf<String>()

    private var onChunkClickListener: ((ChunkData) -> Unit)? = null

    data class ChunkData(
        val chunkId: String,
        val documentId: String,
        val documentName: String,
        val content: String,
        val index: Int,
        val totalChunks: Int,
        val tokenCount: Int,
        val embedding: FloatArray? = null
    )

    init {
        setBackgroundColor(ContextCompat.getColor(context, R.color.chunk_preview_bg))
        setPadding(12, 12, 12, 12)

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Header
        filterText = androidx.appcompat.widget.AppCompatTextView(context).apply {
            text = "All Chunks"
            textSize = 12f
            setTextColor(ContextCompat.getColor(context, R.color.neon_gray))
            setPadding(12, 8, 12, 8)
        }
        layout.addView(filterText)

        // Chunk list
        scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        chunkContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        scrollView.addView(chunkContainer)
        layout.addView(scrollView)

        addView(layout)
    }

    fun addChunks(newChunks: List<DocumentIngestor.Chunk>) {
        newChunks.forEach { chunk ->
            val data = ChunkData(
                chunkId = chunk.id,
                documentId = chunk.documentId,
                documentName = chunk.documentName,
                content = chunk.content,
                index = chunk.index,
                totalChunks = chunk.totalChunks,
                tokenCount = chunk.tokenCount
            )
            chunks.add(data)

            val itemView = ChunkItemView(context, data)
            itemView.setOnClickListener {
                onChunkClickListener?.invoke(data)
            }
            chunkViews[chunk.id] = itemView
            chunkContainer.addView(itemView)
        }
        updateFilterDisplay()
    }

    fun removeChunksByDocument(documentId: String) {
        val toRemove = chunks.filter { it.documentId == documentId }
        toRemove.forEach { chunk ->
            chunkViews[chunk.chunkId]?.let { view ->
                chunkContainer.removeView(view)
            }
            chunkViews.remove(chunk.chunkId)
            chunks.remove(chunk)
        }
        updateFilterDisplay()
    }

    fun highlightChunk(chunkId: String) {
        // Remove previous highlight
        chunkViews.values.forEach { it.setHighlighted(false) }

        // Highlight target
        chunkViews[chunkId]?.let { view ->
            view.setHighlighted(true)
            scrollToChunk(chunkId)
        }
    }

    fun highlightRelevantChunks(chunkIds: List<String>) {
        highlightedChunkIds = chunkIds.toSet()
        chunkViews.forEach { (id, view) ->
            view.setRelevant(id in highlightedChunkIds)
        }
    }

    fun filterByDocument(documentId: String?) {
        filteredDocumentId = documentId
        updateFilterDisplay()

        chunkViews.forEach { (id, view) ->
            val chunk = chunks.find { it.chunkId == id }
            view.visibility = if (documentId == null || chunk?.documentId == documentId) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }

    fun clear() {
        chunks.clear()
        chunkViews.clear()
        chunkContainer.removeAllViews()
        filteredDocumentId = null
        highlightedChunkIds = emptySet()
        updateFilterDisplay()
    }

    fun scrollToChunk(chunkId: String) {
        chunkViews[chunkId]?.let { view ->
            scrollView.post {
                scrollView.smoothScrollTo(0, view.top)
            }
        }
    }

    fun setOnChunkClickListener(listener: (ChunkData) -> Unit) {
        onChunkClickListener = listener
    }

    private fun updateFilterDisplay() {
        val visibleCount = if (filteredDocumentId == null) {
            chunks.size
        } else {
            chunks.count { it.documentId == filteredDocumentId }
        }

        filterText.text = when {
            filteredDocumentId != null -> "Filtered: $visibleCount chunks"
            chunks.isEmpty() -> "No chunks"
            else -> "All chunks: $visibleCount"
        }
    }

    /**
     * Individual chunk visualization
     */
    private inner class ChunkItemView @JvmOverloads constructor(
        context: Context,
        private val data: ChunkData,
        attrs: AttributeSet? = null
    ) : FrameLayout(context, attrs) {

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        private val indexPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 20f
            textAlign = Paint.Align.CENTER
        }
        private val contentPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 22f
            color = ContextCompat.getColor(context, R.color.chunk_text)
        }
        private val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 18f
            color = ContextCompat.getColor(context, R.color.neon_gray)
        }

        private var isHighlighted = false
        private var isRelevant = false
        private val cornerRadius = 10f

        init {
            setPadding(16, 16, 16, 16)
            isClickable = true
            isFocusable = true
        }

        fun setHighlighted(highlighted: Boolean) {
            isHighlighted = highlighted
            invalidate()
        }

        fun setRelevant(relevant: Boolean) {
            isRelevant = relevant
            invalidate()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            // Calculate height based on content
            val availableWidth = MeasureSpec.getSize(widthMeasureSpec) - 64
            val staticLayout = StaticLayout.Builder.obtain(
                data.content.take(200),
                0,
                data.content.take(200).length,
                contentPaint,
                availableWidth
            ).setLineSpacing(1.2f, 1f).build()

            val contentHeight = staticLayout.height
            val totalHeight = (contentHeight + 80).coerceIn(
                (100 * resources.displayMetrics.density).toInt(),
                (200 * resources.displayMetrics.density).toInt()
            )

            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), totalHeight)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val rect = RectF(4f, 4f, width - 4f, height - 4f)

            // Background
            bgPaint.color = when {
                isHighlighted -> ContextCompat.getColor(context, R.color.chunk_bg_highlighted)
                isRelevant -> ContextCompat.getColor(context, R.color.chunk_bg_relevant)
                else -> ContextCompat.getColor(context, R.color.chunk_bg)
            }
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)

            // Border
            borderPaint.color = when {
                isHighlighted -> ContextCompat.getColor(context, R.color.neon_cyan)
                isRelevant -> ContextCompat.getColor(context, R.color.neon_green)
                else -> ContextCompat.getColor(context, R.color.chunk_border)
            }
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)

            // Left accent bar
            val accentColor = when {
                isHighlighted -> R.color.neon_cyan
                isRelevant -> R.color.neon_green
                else -> R.color.chunk_accent
            }
            val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ContextCompat.getColor(context, accentColor)
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(
                rect.left, rect.top + 12f,
                rect.left + 4f, rect.bottom - 12f,
                2f, 2f, accentPaint
            )

            // Chunk index badge
            val badgeRect = RectF(16f, 12f, 48f, 36f)
            val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ContextCompat.getColor(context, R.color.chunk_badge_bg)
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(badgeRect, 4f, 4f, badgeBgPaint)

            indexPaint.color = ContextCompat.getColor(context, R.color.neon_white)
            canvas.drawText(
                "${data.index + 1}/${data.totalChunks}",
                badgeRect.centerX(), badgeRect.bottom - 4, indexPaint
            )

            // Document name
            val docPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 18f
                color = ContextCompat.getColor(context, R.color.neon_cyan)
            }
            val docName = if (data.documentName.length > 25) {
                data.documentName.take(22) + "..."
            } else {
                data.documentName
            }
            canvas.drawText(docName, 56f, 30f, docPaint)

            // Content preview
            val content = if (data.content.length > 200) {
                data.content.take(197) + "..."
            } else {
                data.content
            }

            val staticLayout = StaticLayout.Builder.obtain(
                content, 0, content.length, contentPaint, width - 48
            ).setLineSpacing(1.2f, 1f).build()

            canvas.save()
            canvas.translate(16f, 48f)
            staticLayout.draw(canvas)
            canvas.restore()

            // Metadata footer
            val metaText = "${data.tokenCount} tokens • ${data.content.length} chars"
            canvas.drawText(metaText, 16f, height - 16f, metaPaint)

            // Relevance indicator dot
            if (isRelevant) {
                val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = ContextCompat.getColor(context, R.color.neon_green)
                    style = Paint.Style.FILL
                }
                canvas.drawCircle(width - 24f, 24f, 6f, dotPaint)
            }
        }

        override fun dispatchDraw(canvas: Canvas) {
            onDraw(canvas)
            super.dispatchDraw(canvas)
        }
    }

    companion object {
        private const val TAG = "ChunkPreviewView"
    }
}
