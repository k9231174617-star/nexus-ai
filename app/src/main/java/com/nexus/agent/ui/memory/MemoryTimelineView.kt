package com.nexus.agent.ui.memory

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.animation.doOnEnd
import com.nexus.agent.core.memory.MemoryEntry
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Custom View для визуализации timeline воспоминаний.
 * Отображает воспоминания как точки на временной шкале с цветовой индикацией важности.
 * Поддерживает масштабирование, скролл и выбор диапазона.
 */
class MemoryTimelineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onTimeRangeSelected: ((Long, Long) -> Unit)? = null
    var onPointSelected: ((Long) -> Unit)? = null

    private var memories: List<MemoryEntry> = emptyList()
    private var timePoints: List<TimePoint> = emptyList()
    
    private var minTimestamp = 0L
    private var maxTimestamp = System.currentTimeMillis()
    private var visibleStart = 0L
    private var visibleEnd = System.currentTimeMillis()
    private var scaleX = 1f
    private var offsetX = 0f

    private var selectionStart = -1f
    private var selectionEnd = -1f
    private var selectedPoint: TimePoint? = null
    private var isSelecting = false

    private var animator: ValueAnimator? = null
    private val selectionAnimator = ValueAnimator.ofFloat(0f, 1f)

    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4A5568")
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2D3748")
        strokeWidth = 1f
        alpha = 100
    }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val pointStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4299E1")
        alpha = 60
        style = Paint.Style.FILL
    }

    private val selectionBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4299E1")
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A0AEC0")
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }

    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F6E05E")
        style = Paint.Style.FILL
    }

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            handleTap(e.x, e.y)
            return true
        }

        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            if (!isSelecting) {
                offsetX += distanceX
                constrainOffset()
                invalidate()
            }
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            startSelection(e.x)
        }
    })

    private val scaleGestureDetector = android.view.ScaleGestureDetector(context,
        object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: android.view.ScaleGestureDetector): Boolean {
                val oldScale = scaleX
                scaleX *= detector.scaleFactor
                scaleX = scaleX.coerceIn(0.1f, 50f)
                
                val focusX = detector.focusX
                val focusTime = screenToTime(focusX)
                offsetX = focusX - timeToScreen(focusTime) * (scaleX / oldScale)
                
                constrainOffset()
                invalidate()
                return true
            }
        })

    init {
        setBackgroundColor(Color.parseColor("#1A202C"))
        
        selectionAnimator.apply {
            duration = 300
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                invalidate()
            }
        }
    }

    fun setMemories(newMemories: List<MemoryEntry>) {
        memories = newMemories.sortedBy { it.timestamp }
        
        if (memories.isNotEmpty()) {
            minTimestamp = memories.first().timestamp
            maxTimestamp = memories.last().timestamp
            
            val padding = (maxTimestamp - minTimestamp) / 10
            minTimestamp -= padding
            maxTimestamp += padding
            
            visibleStart = minTimestamp
            visibleEnd = maxTimestamp
            
            timePoints = memories.map { entry ->
                TimePoint(
                    timestamp = entry.timestamp,
                    importance = entry.importanceScore,
                    type = entry.type,
                    y = calculateYPosition(entry.importanceScore),
                    radius = calculateRadius(entry.importanceScore, entry.tokenCount)
                )
            }
        } else {
            timePoints = emptyList()
        }
        
        invalidate()
    }

    fun clearSelection() {
        selectionStart = -1f
        selectionEnd = -1f
        selectedPoint = null
        isSelecting = false
        invalidate()
    }

    fun animateToRange(start: Long, end: Long) {
        val startVisibleStart = visibleStart
        val startVisibleEnd = visibleEnd
        
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 500
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val fraction = anim.animatedValue as Float
                visibleStart = lerp(startVisibleStart, start, fraction)
                visibleEnd = lerp(startVisibleEnd, end, fraction)
                updateScaleFromVisibleRange()
                invalidate()
            }
            doOnEnd {
                onTimeRangeSelected?.invoke(start, end)
            }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateScaleFromVisibleRange()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (timePoints.isEmpty()) {
            drawEmptyState(canvas)
            return
        }

        val paddingTop = 60f
        val paddingBottom = 50f
        val axisY = height - paddingBottom

        drawGrid(canvas, axisY)
        canvas.drawLine(0f, axisY, width.toFloat(), axisY, axisPaint)
        drawTimeLabels(canvas, axisY)
        
        if (selectionStart >= 0 && selectionEnd >= 0) {
            drawSelection(canvas)
        }
        
        drawTrendLine(canvas, paddingTop, axisY)
        drawPoints(canvas, paddingTop, axisY)
        selectedPoint?.let { drawPointHighlight(canvas, it) }
    }

    private fun drawEmptyState(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4A5568")
            textSize = 36f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "Нет данных для отображения",
            width / 2f,
            height / 2f,
            paint
        )
    }

    private fun drawGrid(canvas: Canvas, axisY: Float) {
        val timeSpan = visibleEnd - visibleStart
        val step = calculateGridStep(timeSpan)
        
        var current = (visibleStart / step) * step
        while (current <= visibleEnd) {
            val x = timeToScreen(current)
            if (x in 0f..width.toFloat()) {
                canvas.drawLine(x, 30f, x, axisY, gridPaint)
            }
            current += step
        }
    }

    private fun drawTimeLabels(canvas: Canvas, axisY: Float) {
        val timeSpan = visibleEnd - visibleStart
        val step = calculateGridStep(timeSpan)
        
        var current = (visibleStart / step) * step
        while (current <= visibleEnd) {
            val x = timeToScreen(current)
            if (x in 0f..width.toFloat()) {
                val date = java.util.Date(current)
                val label = when {
                    timeSpan > 30L * 24 * 60 * 60 * 1000 ->
                        android.text.format.DateFormat.format("MMM yyyy", date).toString()
                    timeSpan > 24 * 60 * 60 * 1000 ->
                        android.text.format.DateFormat.format("dd MMM", date).toString()
                    else ->
                        android.text.format.DateFormat.format("HH:mm", date).toString()
                }
                canvas.drawText(label, x, axisY + 35f, labelPaint)
            }
            current += step
        }
    }

    private fun drawTrendLine(canvas: Canvas, paddingTop: Float, axisY: Float) {
        if (timePoints.size < 2) return
        
        val path = android.graphics.Path()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4A5568")
            strokeWidth = 2f
            style = Paint.Style.STROKE
            alpha = 80
        }
        
        var first = true
        timePoints.forEach { point ->
            val x = timeToScreen(point.timestamp)
            val y = paddingTop + point.y * (axisY - paddingTop)
            
            if (first) {
                path.moveTo(x, y)
                first = false
            } else {
                path.lineTo(x, y)
            }
        }
        
        canvas.drawPath(path, paint)
    }

    private fun drawPoints(canvas: Canvas, paddingTop: Float, axisY: Float) {
        timePoints.forEach { point ->
            val x = timeToScreen(point.timestamp)
            if (x < -50 || x > width + 50) return@forEach
            
            val y = paddingTop + point.y * (axisY - paddingTop)
            
            pointPaint.color = getImportanceColor(point.importance)
            
            if (point.importance >= 0.8f) {
                val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = pointPaint.color
                    alpha = 40
                    style = Paint.Style.FILL
                }
                canvas.drawCircle(x, y, point.radius * 2f, glowPaint)
            }
            
            canvas.drawCircle(x, y, point.radius, pointPaint)
            canvas.drawCircle(x, y, point.radius, pointStrokePaint)
        }
    }

    private fun drawPointHighlight(canvas: Canvas, point: TimePoint) {
        val x = timeToScreen(point.timestamp)
        val paddingTop = 60f
        val axisY = height - 50f
        val y = paddingTop + point.y * (axisY - paddingTop)
        
        val pulseRadius = point.radius + 10 + (selectionAnimator.animatedValue as Float) * 15
        highlightPaint.alpha = (100 * (1 - (selectionAnimator.animatedValue as Float))).toInt()
        
        canvas.drawCircle(x, y, pulseRadius, highlightPaint)
        canvas.drawCircle(x, y, point.radius + 5f, pointStrokePaint)
        
        val tooltipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2D3748")
            style = Paint.Style.FILL
        }
        val tooltipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f
        }
        
        val date = java.util.Date(point.timestamp)
        val text = android.text.format.DateFormat.format("dd.MM.yyyy HH:mm", date).toString()
        val textWidth = tooltipTextPaint.measureText(text)
        
        val tooltipLeft = (x - textWidth / 2 - 20).coerceIn(0f, width - textWidth - 40)
        val tooltipTop = y - point.radius - 60
        
        canvas.drawRoundRect(
            tooltipLeft, tooltipTop,
            tooltipLeft + textWidth + 40, tooltipTop + 50,
            10f, 10f, tooltipPaint
        )
        canvas.drawText(text, tooltipLeft + 20 + textWidth / 2, tooltipTop + 35, tooltipTextPaint)
    }

    private fun drawSelection(canvas: Canvas) {
        val left = min(selectionStart, selectionEnd)
        val right = max(selectionStart, selectionEnd)
        
        canvas.drawRect(left, 30f, right, height - 50f, selectionPaint)
        canvas.drawRect(left, 30f, right, height - 50f, selectionBorderPaint)
        
        val startTime = screenToTime(left)
        val endTime = screenToTime(right)
        
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 24f
            textAlign = Paint.Align.CENTER
        }
        
        val startDate = android.text.format.DateFormat.format("dd.MM HH:mm", java.util.Date(startTime))
        val endDate = android.text.format.DateFormat.format("dd.MM HH:mm", java.util.Date(endTime))
        
        canvas.drawText(startDate.toString(), left, height - 15f, labelPaint)
        canvas.drawText(endDate.toString(), right, height - 15f, labelPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (!scaleGestureDetector.isInProgress) {
                    isSelecting = false
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isSelecting) {
                    selectionEnd = event.x
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isSelecting) {
                    finishSelection()
                }
            }
        }
        
        return gestureDetector.onTouchEvent(event) || true
    }

    private fun handleTap(x: Float, y: Float) {
        val paddingTop = 60f
        val axisY = height - 50f
        
        var closest: TimePoint? = null
        var minDistance = Float.MAX_VALUE
        
        timePoints.forEach { point ->
            val px = timeToScreen(point.timestamp)
            val py = paddingTop + point.y * (axisY - paddingTop)
            val dist = abs(x - px) + abs(y - py)
            
            if (dist < minDistance && dist < 100) {
                minDistance = dist
                closest = point
            }
        }
        
        closest?.let {
            selectedPoint = it
            selectionAnimator.start()
            onPointSelected?.invoke(it.timestamp)
            invalidate()
        }
    }

    private fun startSelection(x: Float) {
        isSelecting = true
        selectionStart = x
        selectionEnd = x
        invalidate()
    }

    private fun finishSelection() {
        isSelecting = false
        val left = min(selectionStart, selectionEnd)
        val right = max(selectionStart, selectionEnd)
        
        if (abs(right - left) > 50) {
            val startTime = screenToTime(left)
            val endTime = screenToTime(right)
            onTimeRangeSelected?.invoke(startTime, endTime)
        } else {
            clearSelection()
        }
    }

    private fun timeToScreen(timestamp: Long): Float {
        val timeSpan = visibleEnd - visibleStart
        return if (timeSpan > 0) {
            (timestamp - visibleStart).toFloat() / timeSpan * width * scaleX + offsetX
        } else {
            width / 2f
        }
    }

    private fun screenToTime(x: Float): Long {
        val timeSpan = visibleEnd - visibleStart
        return if (width > 0 && scaleX > 0) {
            ((x - offsetX) / (width * scaleX) * timeSpan + visibleStart).toLong()
        } else {
            visibleStart
        }
    }

    private fun updateScaleFromVisibleRange() {
        val timeSpan = maxTimestamp - minTimestamp
        val visibleSpan = visibleEnd - visibleStart
        scaleX = if (visibleSpan > 0) {
            timeSpan.toFloat() / visibleSpan
        } else {
            1f
        }
        offsetX = -timeToScreen(visibleStart)
    }

    private fun constrainOffset() {
        val maxOffset = width * (scaleX - 1f)
        offsetX = offsetX.coerceIn(-maxOffset, 0f)
    }

    private fun calculateYPosition(importance: Float): Float {
        return 1f - importance
    }

    private fun calculateRadius(importance: Float, tokenCount: Int): Float {
        val baseRadius = 6f
        val importanceBonus = importance * 8f
        val sizeBonus = min(tokenCount / 100f, 6f)
        return baseRadius + importanceBonus + sizeBonus
    }

    private fun getImportanceColor(importance: Float): Int {
        return when {
            importance >= 0.9f -> Color.parseColor("#F56565")
            importance >= 0.7f -> Color.parseColor("#ED8936")
            importance >= 0.5f -> Color.parseColor("#ECC94B")
            importance >= 0.3f -> Color.parseColor("#48BB78")
            else -> Color.parseColor("#4299E1")
        }
    }

    private fun calculateGridStep(timeSpan: Long): Long {
        return when {
            timeSpan > 365L * 24 * 60 * 60 * 1000 -> 30L * 24 * 60 * 60 * 1000
            timeSpan > 30L * 24 * 60 * 60 * 1000 -> 7L * 24 * 60 * 60 * 1000
            timeSpan > 7L * 24 * 60 * 60 * 1000 -> 24L * 60 * 60 * 1000
            timeSpan > 24 * 60 * 60 * 1000 -> 60 * 60 * 1000
            timeSpan > 60 * 60 * 1000 -> 15 * 60 * 1000
            else -> 60 * 1000
        }
    }

    private fun lerp(start: Long, end: Long, fraction: Float): Long {
        return (start + (end - start) * fraction).toLong()
    }

    data class TimePoint(
        val timestamp: Long,
        val importance: Float,
        val type: MemoryEntry.Type,
        val y: Float,
        val radius: Float
    )
}
