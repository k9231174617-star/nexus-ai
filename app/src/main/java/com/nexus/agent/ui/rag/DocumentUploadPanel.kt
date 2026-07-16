package com.nexus.agent.ui.rag

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
import com.nexus.agent.core.rag.DocumentIngestor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Panel for document upload management with drag-and-drop visuals,
 * progress indicators, and document list management.
 */
class DocumentUploadPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val uploadZone: UploadZoneView
    private val documentList: LinearLayout
    private val scrollView: ScrollView
    private val emptyText: TextView

    private val documents = mutableMapOf<String, DocumentIngestor.Document>()
    private val documentViews = mutableMapOf<String, DocumentItemView>()

    private var onUploadClickListener: (() -> Unit)? = null
    private var onDocumentRemoveListener: ((String) -> Unit)? = null
    private var onDocumentPreviewListener: ((String) -> Unit)? = null

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val dashedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private val cornerRadius = 16f
    private val rect = RectF()

    init {
        setPadding(12, 12, 12, 12)

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Upload zone
        uploadZone = UploadZoneView(context)
        uploadZone.layoutParams = LinearLayout.LayoutParams(
            LayoutParams.MATCH_PARENT,
            (120 * resources.displayMetrics.density).toInt()
        )
        uploadZone.setOnClickListener {
            onUploadClickListener?.invoke()
        }
        layout.addView(uploadZone)

        // Empty state
        emptyText = TextView(context).apply {
            text = "No documents uploaded yet"
            textSize = 13f
            setTextColor(ContextCompat.getColor(context, R.color.neon_gray))
            gravity = android.view.Gravity.CENTER
            setPadding(16, 32, 16, 32)
        }
        layout.addView(emptyText)

        // Document list
        scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        documentList = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        scrollView.addView(documentList)
        layout.addView(scrollView)

        addView(layout)
        updateEmptyState()
    }

    fun addDocument(document: DocumentIngestor.Document) {
        if (document.id in documents) return

        documents[document.id] = document

        val itemView = DocumentItemView(context, document)
        itemView.setOnRemoveClickListener {
            onDocumentRemoveListener?.invoke(document.id)
        }
        itemView.setOnPreviewClickListener {
            onDocumentPreviewListener?.invoke(document.id)
        }

        documentViews[document.id] = itemView
        documentList.addView(itemView, 0)

        // Animate entrance
        itemView.alpha = 0f
        itemView.translationX = -50f
        itemView.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(250)
            .setInterpolator(DecelerateInterpolator())
            .start()

        updateEmptyState()
    }

    fun removeDocument(documentId: String) {
        val view = documentViews[documentId] ?: return

        view.animate()
            .alpha(0f)
            .translationX(50f)
            .setDuration(200)
            .withEndAction {
                documentList.removeView(view)
                documentViews.remove(documentId)
                documents.remove(documentId)
                updateEmptyState()
            }
            .start()
    }

    fun clear() {
        documents.clear()
        documentViews.clear()
        documentList.removeAllViews()
        updateEmptyState()
    }

    fun setEnabled(enabled: Boolean) {
        uploadZone.isEnabled = enabled
        uploadZone.alpha = if (enabled) 1.0f else 0.5f
    }

    fun setOnUploadClickListener(listener: () -> Unit) {
        onUploadClickListener = listener
    }

    fun setOnDocumentRemoveListener(listener: (String) -> Unit) {
        onDocumentRemoveListener = listener
    }

    fun setOnDocumentPreviewListener(listener: (String) -> Unit) {
        onDocumentPreviewListener = listener
    }

    private fun updateEmptyState() {
        val hasDocuments = documents.isNotEmpty()
        emptyText.visibility = if (hasDocuments) View.GONE else View.VISIBLE
        scrollView.visibility = if (hasDocuments) View.VISIBLE else View.GONE
    }

    /**
     * Drag-and-drop upload zone
     */
    private inner class UploadZoneView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null
    ) : FrameLayout(context, attrs) {

        private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            color = ContextCompat.getColor(context, R.color.neon_cyan)
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 28f
            textAlign = Paint.Align.CENTER
            color = ContextCompat.getColor(context, R.color.neon_cyan)
        }
        private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 22f
            textAlign = Paint.Align.CENTER
            color = ContextCompat.getColor(context, R.color.neon_gray)
        }

        private var isDraggingOver = false

        init {
            isClickable = true
            isFocusable = true
            setWillNotDraw(false)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val rect = RectF(8f, 8f, width - 8f, height - 8f)

            // Background
            bgPaint.color = if (isDraggingOver) {
                ContextCompat.getColor(context, R.color.upload_zone_active_bg)
            } else {
                ContextCompat.getColor(context, R.color.upload_zone_bg)
            }
            canvas.drawRoundRect(rect, 12f, 12f, bgPaint)

            // Border
            if (isDraggingOver) {
                strokePaint.color = ContextCompat.getColor(context, R.color.neon_cyan)
                canvas.drawRoundRect(rect, 12f, 12f, strokePaint)
            } else {
                dashedPaint.color = ContextCompat.getColor(context, R.color.upload_zone_border)
                canvas.drawRoundRect(rect, 12f, 12f, dashedPaint)
            }

            // Upload icon (simple arrow up)
            val centerX = width / 2f
            val iconY = height / 2f - 20f
            val arrowSize = 20f

            val path = android.graphics.Path().apply {
                moveTo(centerX, iconY - arrowSize)
                lineTo(centerX - arrowSize * 0.6f, iconY)
                lineTo(centerX + arrowSize * 0.6f, iconY)
                close()
            }
            canvas.drawPath(path, iconPaint)

            // Line under arrow
            canvas.drawLine(
                centerX - arrowSize, iconY + 4,
                centerX + arrowSize, iconY + 4, iconPaint
            )

            // Text
            canvas.drawText("Upload Documents", centerX, iconY + 40, textPaint)
            canvas.drawText("Tap or drop files here", centerX, iconY + 68, subTextPaint)
        }

        override fun dispatchDraw(canvas: Canvas) {
            onDraw(canvas)
            super.dispatchDraw(canvas)
        }
    }

    /**
     * Individual document item view
     */
    private inner class DocumentItemView @JvmOverloads constructor(
        context: Context,
        private val document: DocumentIngestor.Document,
        attrs: AttributeSet? = null
    ) : FrameLayout(context, attrs) {

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 24f
        }
        private val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 20f
            color = ContextCompat.getColor(context, R.color.neon_gray)
        }
        private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = ContextCompat.getColor(context, R.color.neon_cyan)
        }
        private val progressBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = ContextCompat.getColor(context, R.color.progress_bg)
        }

        private var onRemoveClick: (() -> Unit)? = null
        private var onPreviewClick: (() -> Unit)? = null

        private val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

        init {
            setPadding(16, 14, 16, 14)
            isClickable = true
            isFocusable = true

            setOnClickListener {
                onPreviewClick?.invoke()
            }
        }

        fun setOnRemoveClickListener(listener: () -> Unit) {
            onRemoveClick = listener
        }

        fun setOnPreviewClickListener(listener: () -> Unit) {
            onPreviewClick = listener
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(
                MeasureSpec.getSize(widthMeasureSpec),
                (80 * resources.displayMetrics.density).toInt()
            )
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val rect = RectF(4f, 4f, width - 4f, height - 4f)

            // Background
            bgPaint.color = ContextCompat.getColor(context, R.color.document_item_bg)
            canvas.drawRoundRect(rect, 10f, 10f, bgPaint)

            // File type icon
            iconPaint.color = getFileTypeColor(document.fileType)
            canvas.drawRoundRect(20f, 16f, 52f, 48f, 6f, 6f, iconPaint)

            // File type text on icon
            val typeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 16f
                color = ContextCompat.getColor(context, R.color.neon_white)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(
                document.fileType.uppercase().take(3),
                36f, 40f, typeTextPaint
            )

            // Filename
            textPaint.color = ContextCompat.getColor(context, R.color.neon_white)
            val filename = if (document.name.length > 30) {
                document.name.take(27) + "..."
            } else {
                document.name
            }
            canvas.drawText(filename, 68f, 32f, textPaint)

            // Metadata
            val metaText = "${document.chunkCount} chunks • ${formatSize(document.size)} • ${dateFormat.format(Date(document.timestamp))}"
            canvas.drawText(metaText, 68f, 56f, metaPaint)

            // Processing progress bar if not complete
            if (document.processingProgress < 100) {
                val progressRect = RectF(68f, 64f, width - 80f, 70f)
                canvas.drawRoundRect(progressRect, 3f, 3f, progressBgPaint)

                val fillWidth = progressRect.width() * document.processingProgress / 100
                val fillRect = RectF(progressRect.left, progressRect.top,
                    progressRect.left + fillWidth, progressRect.bottom)
                canvas.drawRoundRect(fillRect, 3f, 3f, progressPaint)
            }

            // Remove button (X)
            val removePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ContextCompat.getColor(context, R.color.neon_red)
                strokeWidth = 2.5f
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
            }
            val rx = width - 36f
            val ry = height / 2f
            canvas.drawLine(rx - 8, ry - 8, rx + 8, ry + 8, removePaint)
            canvas.drawLine(rx + 8, ry - 8, rx - 8, ry + 8, removePaint)

            // Bottom border
            val borderPaint = Paint().apply {
                color = ContextCompat.getColor(context, R.color.document_border)
                strokeWidth = 0.5f
            }
            canvas.drawLine(16f, height - 1f, width - 16f, height - 1f, borderPaint)
        }

        override fun dispatchDraw(canvas: Canvas) {
            onDraw(canvas)
            super.dispatchDraw(canvas)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_UP) {
                val x = event.x
                // Check if remove button was clicked
                if (x > width - 60) {
                    onRemoveClick?.invoke()
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        private fun getFileTypeColor(fileType: String): Int {
            return ContextCompat.getColor(context, when (fileType.lowercase()) {
                "pdf" -> R.color.file_pdf
                "txt", "md" -> R.color.file_text
                "doc", "docx" -> R.color.file_doc
                "html", "htm" -> R.color.file_html
                "json" -> R.color.file_json
                "csv" -> R.color.file_csv
                "py", "js", "kt", "java" -> R.color.file_code
                else -> R.color.file_default
            })
        }

        private fun formatSize(bytes: Long): String {
            return when {
                bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
                bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
                else -> "$bytes B"
            }
        }
    }

    companion object {
        private const val TAG = "DocumentUploadPanel"
    }
}
