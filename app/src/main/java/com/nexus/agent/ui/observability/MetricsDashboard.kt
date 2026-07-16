package com.nexus.agent.ui.observability

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

class MetricsDashboard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val metrics = mutableMapOf<String, MetricData>()
    private val history = mutableMapOf<String, MutableList<MetricPoint>>()
    private val maxHistorySize = 100

    // Rendering
    private val backgroundPaint = Paint().apply {
        color = Color.parseColor("#0A1929")
    }
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#142536")
        style = Paint.Style.FILL
    }
    private val cardStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E293B")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#90A4AE")
        textSize = 28f
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 48f
    }
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#607D8B")
        textSize = 24f
    }
    private val chartLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val chartFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val gridPaint = Paint().apply {
        color = Color.parseColor("#1E293B")
        strokeWidth = 1f
    }
    private val alertPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF1744")
        textSize = 20f
    }

    // Layout
    private var cardPadding = 20f
    private var cardsPerRow = 2
    private var cardHeight = 280f
    private var chartHeight = 120f

    // Metric colors
    private val metricColors = mapOf(
        "latency" to Color.parseColor("#FF6E40"),
        "throughput" to Color.parseColor("#00E5FF"),
        "error_rate" to Color.parseColor("#FF1744"),
        "cpu_usage" to Color.parseColor("#76FF03"),
        "memory_usage" to Color.parseColor("#E040FB"),
        "requests_per_second" to Color.parseColor("#448AFF"),
        "active_connections" to Color.parseColor("#FFFF00"),
        "queue_depth" to Color.parseColor("#FF9100")
    )

    private var onMetricClickListener: ((String, Double) -> Unit)? = null
    private var selectedMetric: String? = null

    init {
        isClickable = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        if (metrics.isEmpty()) {
            canvas.drawText("No metrics available", width / 2f - 120f, height / 2f, titlePaint)
            return
        }

        val cardWidth = (width - cardPadding * (cardsPerRow + 1)) / cardsPerRow
        var currentX = cardPadding
        var currentY = cardPadding

        metrics.entries.forEachIndexed { index, (name, data) ->
            drawMetricCard(canvas, name, data, currentX, currentY, cardWidth, cardHeight)
            
            currentX += cardWidth + cardPadding
            if ((index + 1) % cardsPerRow == 0) {
                currentX = cardPadding
                currentY += cardHeight + cardPadding
            }
        }
    }

    private fun drawMetricCard(
        canvas: Canvas,
        name: String,
        data: MetricData,
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {
        val rect = RectF(x, y, x + w, y + h)
        val isSelected = selectedMetric == name

        // Card background
        if (isSelected) {
            cardPaint.color = Color.parseColor("#1A2F45")
            cardStrokePaint.color = Color.parseColor("#00E5FF")
        } else {
            cardPaint.color = Color.parseColor("#142536")
            cardStrokePaint.color = Color.parseColor("#1E293B")
        }
        
        canvas.drawRoundRect(rect, 16f, 16f, cardPaint)
        canvas.drawRoundRect(rect, 16f, 16f, cardStrokePaint)

        // Metric name
        val displayName = name.replace("_", " ").uppercase()
        canvas.drawText(displayName, x + 20f, y + 40f, titlePaint)

        // Current value
        val valueStr = formatValue(data.currentValue, data.unit)
        canvas.drawText(valueStr, x + 20f, y + 90f, valuePaint)

        // Unit
        canvas.drawText(data.unit, x + 20f + valuePaint.measureText(valueStr) + 10f, y + 85f, unitPaint)

        // Trend indicator
        val trend = calculateTrend(name)
        val trendColor = when {
            trend > 0.1 -> Color.parseColor("#FF1744") // Bad: increasing
            trend < -0.1 -> Color.parseColor("#76FF03") // Good: decreasing
            else -> Color.parseColor("#90A4AE")
        }
        val trendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = trendColor
            textSize = 24f
        }
        val trendStr = when {
            trend > 0 -> "▲ ${String.format("%.1f", trend * 100)}%"
            trend < 0 -> "▼ ${String.format("%.1f", -trend * 100)}%"
            else -> "─ 0%"
        }
        canvas.drawText(trendStr, x + w - 120f, y + 40f, trendPaint)

        // Alert indicator
        if (data.isAlert) {
            canvas.drawCircle(x + w - 25f, y + 25f, 8f, alertPaint)
        }

        // Sparkline chart
        drawSparkline(canvas, name, x + 20f, y + 110f, w - 40f, chartHeight)
    }

    private fun drawSparkline(
        canvas: Canvas,
        metricName: String,
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {
        val points = history[metricName] ?: return
        if (points.size < 2) return

        val color = metricColors[metricName] ?: Color.parseColor("#00E5FF")
        chartLinePaint.color = color
        chartFillPaint.color = (color and 0x00FFFFFF) or 0x33000000.toInt()

        val minValue = points.minOf { it.value }
        val maxValue = points.maxOf { it.value }
        val range = maxValue - minValue
        val effectiveRange = if (range == 0.0) 1.0 else range
        val stepX = w / (points.size - 1)

        val path = Path()
        val fillPath = Path()

        points.forEachIndexed { index, point ->
            val px = x + index * stepX
            val py = if (range == 0.0) {
                y + h / 2
            } else {
                y + h - ((point.value - minValue) / range * h).toFloat()
            }

            if (index == 0) {
                path.moveTo(px, py)
                fillPath.moveTo(px, y + h)
                fillPath.lineTo(px, py)
            } else {
                path.lineTo(px, py)
                fillPath.lineTo(px, py)
            }
        }

        fillPath.lineTo(x + w, y + h)
        fillPath.close()

        // Draw fill
        canvas.drawPath(fillPath, chartFillPaint)

        // Draw line
        canvas.drawPath(path, chartLinePaint)

        // Draw grid
        canvas.drawLine(x, y + h, x + w, y + h, gridPaint)
        canvas.drawLine(x, y, x, y + h, gridPaint)

        // Draw min/max labels
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#607D8B")
            textSize = 18f
        }
        canvas.drawText(String.format("%.2f", maxValue), x + w - 60f, y + 20f, labelPaint)
        canvas.drawText(String.format("%.2f", minValue), x + w - 60f, y + h - 5f, labelPaint)
    }

    private fun formatValue(value: Double, unit: String): String {
        return when {
            value >= 1_000_000_000 -> String.format("%.2fB", value / 1_000_000_000)
            value >= 1_000_000 -> String.format("%.2fM", value / 1_000_000)
            value >= 1_000 -> String.format("%.2fK", value / 1_000)
            value >= 100 -> String.format("%.1f", value)
            value >= 1 -> String.format("%.2f", value)
            else -> String.format("%.4f", value)
        }
    }

    private fun calculateTrend(metricName: String): Double {
        val points = history[metricName] ?: return 0.0
        if (points.size < 2) return 0.0

        val recent = points.takeLast(10)
        if (recent.size < 2) return 0.0

        val first = recent.first().value
        val last = recent.last().value
        return if (first == 0.0) 0.0 else (last - first) / first
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val cardWidth = (width - cardPadding * (cardsPerRow + 1)) / cardsPerRow
                var currentX = cardPadding
                var currentY = cardPadding

                metrics.entries.forEachIndexed { index, (name, data) ->
                    val rect = RectF(currentX, currentY, currentX + cardWidth, currentY + cardHeight)
                    if (rect.contains(event.x, event.y)) {
                        selectedMetric = name
                        onMetricClickListener?.invoke(name, data.currentValue)
                        invalidate()
                        return true
                    }

                    currentX += cardWidth + cardPadding
                    if ((index + 1) % cardsPerRow == 0) {
                        currentX = cardPadding
                        currentY += cardHeight + cardPadding
                    }
                }
            }
        }
        return super.onTouchEvent(event)
    }

    // Public API
    fun setMetrics(newMetrics: Map<String, MetricData>) {
        metrics.clear()
        metrics.putAll(newMetrics)

        // Update history
        newMetrics.forEach { (name, data) ->
            val list = history.getOrPut(name) { mutableListOf() }
            list.add(MetricPoint(System.currentTimeMillis(), data.currentValue))
            if (list.size > maxHistorySize) {
                list.removeAt(0)
            }
        }

        invalidate()
    }

    fun addMetric(name: String, value: Double, unit: String, isAlert: Boolean = false) {
        metrics[name] = MetricData(value, unit, isAlert)
        val list = history.getOrPut(name) { mutableListOf() }
        list.add(MetricPoint(System.currentTimeMillis(), value))
        if (list.size > maxHistorySize) {
            list.removeAt(0)
        }
        invalidate()
    }

    fun setOnMetricClickListener(listener: (String, Double) -> Unit) {
        onMetricClickListener = listener
    }

    fun clearSelection() {
        selectedMetric = null
        invalidate()
    }

    fun clearAll() {
        metrics.clear()
        history.clear()
        selectedMetric = null
        invalidate()
    }

    // Data classes
    data class MetricData(
        val currentValue: Double,
        val unit: String,
        val isAlert: Boolean = false
    )

    private data class MetricPoint(
        val timestamp: Long,
        val value: Double
    )
}
