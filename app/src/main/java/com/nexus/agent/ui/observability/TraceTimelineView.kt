package com.nexus.agent.ui.observability

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.OverScroller
import androidx.core.view.GestureDetectorCompat
import com.nexus.agent.core.observability.Span
import com.nexus.agent.core.observability.Trace
import kotlin.math.max
import kotlin.math.min

class TraceTimelineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val traces = mutableListOf<Trace>()
    private val spanRects = mutableListOf<SpanRect>()
    private val highlightedTraceIds = mutableSetOf<String>()

    // Rendering
    private val backgroundPaint = Paint().apply {
        color = Color.parseColor("#0A1929")
    }
    private val traceRowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val spanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val spanStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#1E293B")
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 24f
    }
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#90A4AE")
        textSize = 20f
    }
    private val gridPaint = Paint().apply {
        color = Color.parseColor("#1E293B")
        strokeWidth = 1f
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.parseColor("#FF6E40")
    }
    private val errorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FF1744")
    }

    // Layout
    private var rowHeight = 60f
    private var spanHeight = 40f
    private var timeScale = 1.0f // pixels per ms
    private var headerHeight = 50f
    private var timeRangeStart = 0L
    private var timeRangeEnd = 1000L

    // Viewport
    private var scrollX = 0f
    private var scrollY = 0f
    private val scroller = OverScroller(context)

    // Colors by service/operation
    private val spanColors = listOf(
        Color.parseColor("#FF6E40"),
        Color.parseColor("#00E5FF"),
        Color.parseColor("#76FF03"),
        Color.parseColor("#E040FB"),
        Color.parseColor("#448AFF"),
        Color.parseColor("#FFFF00"),
        Color.parseColor("#FF9100"),
        Color.parseColor("#00BFA5")
    )

    private var onSpanClickListener: ((Span) -> Unit)? = null

    private val gestureDetector = GestureDetectorCompat(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            scrollX += distanceX
            scrollY += distanceY
            scrollX = scrollX.coerceIn(0f, max(0f, getContentWidth() - width))
            scrollY = scrollY.coerceIn(0f, max(0f, getContentHeight() - height))
            invalidate()
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            scroller.fling(
                scrollX.toInt(), scrollY.toInt(),
                -velocityX.toInt(), -velocityY.toInt(),
                0, max(0, (getContentWidth() - width).toInt()),
                0, max(0, (getContentHeight() - height).toInt())
            )
            invalidate()
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val x = e.x + scrollX
            val y = e.y + scrollY
            val spanRect = spanRects.find { it.rect.contains(x, y) }
            spanRect?.let {
                onSpanClickListener?.invoke(it.span)
                return true
            }
            return false
        }
    })

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val newScale = timeScale * detector.scaleFactor
            timeScale = newScale.coerceIn(0.01f, 10f)
            invalidate()
            return true
        }
    })

    init {
        isClickable = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        if (traces.isEmpty()) {
            canvas.drawText("No traces available", width / 2f - 100f, height / 2f, textPaint)
            return
        }

        canvas.save()
        canvas.translate(-scrollX, -scrollY)

        // Draw time grid
        drawTimeGrid(canvas)

        // Draw traces
        var currentY = headerHeight
        traces.forEachIndexed { index, trace ->
            drawTraceRow(canvas, trace, index, currentY)
            currentY += rowHeight + getTraceDepth(trace) * (spanHeight + 5f)
        }

        canvas.restore()
    }

    private fun drawTimeGrid(canvas: Canvas) {
        val visibleStart = scrollX
        val visibleEnd = scrollX + width
        val timeStep = calculateTimeStep()

        var currentTime = (timeRangeStart / timeStep) * timeStep
        while (currentTime <= timeRangeEnd) {
            val x = timeToX(currentTime)
            if (x in visibleStart..visibleEnd) {
                canvas.drawLine(x, 0f, x, getContentHeight(), gridPaint)
                val timeLabel = formatTime(currentTime - timeRangeStart)
                canvas.drawText(timeLabel, x + 5f, headerHeight - 10f, timePaint)
            }
            currentTime += timeStep
        }
    }

    private fun drawTraceRow(canvas: Canvas, trace: Trace, index: Int, baseY: Float) {
        val isHighlighted = highlightedTraceIds.contains(trace.id)
        
        // Row background
        val rowColor = if (index % 2 == 0) Color.parseColor("#0F1F2E") else Color.parseColor("#142536")
        traceRowPaint.color = rowColor
        canvas.drawRect(0f, baseY, getContentWidth(), baseY + rowHeight, traceRowPaint)

        // Trace label
        val label = "${trace.name} (${trace.durationMs}ms)"
        canvas.drawText(label, 10f, baseY + rowHeight / 2f + 8f, textPaint)

        // Draw spans
        spanRects.removeAll { it.traceId == trace.id }
        drawSpansRecursive(canvas, trace.rootSpans, baseY + rowHeight, 0, trace.id)

        // Highlight border
        if (isHighlighted) {
            canvas.drawRect(0f, baseY, getContentWidth(), baseY + rowHeight, highlightPaint)
        }
    }

    private fun drawSpansRecursive(
        canvas: Canvas,
        spans: List<Span>,
        baseY: Float,
        depth: Int,
        traceId: String
    ): Float {
        var currentY = baseY + depth * (spanHeight + 5f)
        
        spans.forEach { span ->
            val startX = timeToX(span.startTime)
            val endX = timeToX(span.endTime ?: (span.startTime + 1))
            val width = max(endX - startX, 3f)

            val color = spanColors[span.name.hashCode().absoluteValue % spanColors.size]
            val rect = RectF(startX, currentY, startX + width, currentY + spanHeight)

            // Span background
            spanPaint.color = color
            if (span.hasError) {
                spanPaint.color = Color.parseColor("#FF1744")
            }
            canvas.drawRoundRect(rect, 6f, 6f, spanPaint)
            canvas.drawRoundRect(rect, 6f, 6f, spanStrokePaint)

            // Span label
            val label = if (width > 100) span.name else ""
            canvas.drawText(label, startX + 5f, currentY + spanHeight / 2f + 8f, textPaint)

            // Store for hit testing
            spanRects.add(SpanRect(rect, span, traceId))

            // Draw children
            if (span.childSpans.isNotEmpty()) {
                currentY = drawSpansRecursive(canvas, span.childSpans, baseY, depth + 1, traceId)
            } else {
                currentY += spanHeight + 5f
            }
        }

        return currentY
    }

    private fun drawTimeGrid(canvas: Canvas) {
        val visibleStart = scrollX
        val visibleEnd = scrollX + width
        val timeStep = calculateTimeStep()

        var currentTime = (timeRangeStart / timeStep) * timeStep
        while (currentTime <= timeRangeEnd) {
            val x = timeToX(currentTime)
            if (x >= visibleStart && x <= visibleEnd) {
                canvas.drawLine(x, 0f, x, getContentHeight(), gridPaint)
                val timeLabel = formatTime(currentTime - timeRangeStart)
                canvas.drawText(timeLabel, x + 5f, headerHeight - 10f, timePaint)
            }
            currentTime += timeStep
        }
    }

    private fun timeToX(timeMs: Long): Float {
        return (timeMs - timeRangeStart) * timeScale + 200f // 200px offset for labels
    }

    private fun calculateTimeStep(): Long {
        val visibleDuration = (width / timeScale).toLong()
        return when {
            visibleDuration < 100 -> 10
            visibleDuration < 1000 -> 100
            visibleDuration < 10000 -> 1000
            visibleDuration < 60000 -> 10000
            visibleDuration < 3600000 -> 60000
            else -> 600000
        }
    }

    private fun formatTime(ms: Long): String {
        return when {
            ms < 1000 -> "${ms}ms"
            ms < 60000 -> "${ms / 1000}s"
            ms < 3600000 -> "${ms / 60000}m ${(ms % 60000) / 1000}s"
            else -> "${ms / 3600000}h ${(ms % 3600000) / 60000}m"
        }
    }

    private fun getTraceDepth(trace: Trace): Int {
        return trace.rootSpans.maxOfOrNull { getSpanDepth(it) } ?: 0
    }

    private fun getSpanDepth(span: Span): Int {
        return if (span.childSpans.isEmpty()) 0 else 1 + span.childSpans.maxOf { getSpanDepth(it) }
    }

    private fun getContentWidth(): Float {
        return max(width.toFloat(), (timeRangeEnd - timeRangeStart) * timeScale + 400f)
    }

    private fun getContentHeight(): Float {
        var height = headerHeight
        traces.forEach { trace ->
            height += rowHeight + getTraceDepth(trace) * (spanHeight + 5f)
        }
        return max(height, this.height.toFloat())
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        
        if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
            if (scroller.computeScrollOffset()) {
                scrollX = scroller.currX.toFloat()
                scrollY = scroller.currY.toFloat()
                invalidate()
            }
        }
        
        return true
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollX = scroller.currX.toFloat()
            scrollY = scroller.currY.toFloat()
            invalidate()
            postInvalidateOnAnimation()
        }
    }

    // Public API
    fun setTraces(newTraces: List<Trace>) {
        traces.clear()
        traces.addAll(newTraces)
        
        if (traces.isNotEmpty()) {
            timeRangeStart = traces.minOf { it.startTime }
            timeRangeEnd = traces.maxOf { it.endTime ?: (it.startTime + 1000) }
            val duration = (timeRangeEnd - timeRangeStart).coerceAtLeast(1)
            timeScale = (width - 250f) / duration
        }
        
        spanRects.clear()
        invalidate()
    }

    fun highlightTraces(traceIds: List<String>) {
        highlightedTraceIds.clear()
        highlightedTraceIds.addAll(traceIds)
        invalidate()
    }

    fun focusOnSpan(spanId: String) {
        val spanRect = spanRects.find { it.span.id == spanId } ?: return
        scrollX = spanRect.rect.centerX() - width / 2f
        scrollY = spanRect.rect.centerY() - height / 2f
        invalidate()
    }

    fun setOnSpanClickListener(listener: (Span) -> Unit) {
        onSpanClickListener = listener
    }

    fun clearHighlights() {
        highlightedTraceIds.clear()
        invalidate()
    }

    private data class SpanRect(
        val rect: RectF,
        val span: Span,
        val traceId: String
    )

    private val Int.absoluteValue: Int get() = if (this < 0) -this else this
}
