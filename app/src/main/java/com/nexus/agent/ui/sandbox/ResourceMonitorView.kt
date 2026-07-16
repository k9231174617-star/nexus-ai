package com.nexus.agent.ui.sandbox

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.nexus.agent.R
import java.util.LinkedList

/**
 * Real-time resource monitor displaying CPU, memory, and execution metrics
 * with animated graphs and progress indicators.
 */
class ResourceMonitorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val cpuText: TextView
    private val memoryText: TextView
    private val timeText: TextView
    private val statusText: TextView
    private val graphView: ResourceGraphView

    private var isMonitoring = false
    private var startTime: Long = 0

    // Stats
    private var currentCpuUsage = 0f
    private var currentMemoryUsage = 0f
    private var currentExecutionTime = 0L

    init {
        setBackgroundColor(ContextCompat.getColor(context, R.color.monitor_bg))
        setPadding(16, 12, 16, 12)

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Stats row
        val statsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        cpuText = createStatText("CPU: --%")
        memoryText = createStatText("MEM: --MB")
        timeText = createStatText("TIME: --ms")
        statusText = createStatText("IDLE").apply {
            setTextColor(ContextCompat.getColor(context, R.color.neon_gray))
        }

        statsRow.addView(cpuText)
        statsRow.addView(memoryText)
        statsRow.addView(timeText)
        statsRow.addView(statusText)

        layout.addView(statsRow)

        // Graph
        graphView = ResourceGraphView(context)
        graphView.layoutParams = LinearLayout.LayoutParams(
            LayoutParams.MATCH_PARENT,
            120
        )
        layout.addView(graphView)

        addView(layout)
    }

    private fun createStatText(initialText: String): TextView {
        return TextView(context).apply {
            text = initialText
            textSize = 11f
            setTextColor(ContextCompat.getColor(context, R.color.neon_cyan))
            typeface = android.graphics.Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            setPadding(4, 4, 4, 4)
        }
    }

    fun startMonitoring() {
        isMonitoring = true
        startTime = System.currentTimeMillis()
        statusText.text = "RUNNING"
        statusText.setTextColor(ContextCompat.getColor(context, R.color.neon_green))
        graphView.clear()
        startUpdateLoop()
    }

    fun stopMonitoring() {
        isMonitoring = false
        statusText.text = "DONE"
        statusText.setTextColor(ContextCompat.getColor(context, R.color.neon_blue))
    }

    fun reset() {
        isMonitoring = false
        currentCpuUsage = 0f
        currentMemoryUsage = 0f
        currentExecutionTime = 0
        cpuText.text = "CPU: --%"
        memoryText.text = "MEM: --MB"
        timeText.text = "TIME: --ms"
        statusText.text = "IDLE"
        statusText.setTextColor(ContextCompat.getColor(context, R.color.neon_gray))
        graphView.clear()
    }

    fun updateStats(cpuUsage: Float, memoryUsage: Float, executionTime: Long) {
        currentCpuUsage = cpuUsage
        currentMemoryUsage = memoryUsage
        currentExecutionTime = executionTime

        cpuText.text = "CPU: ${String.format("%.1f", cpuUsage)}%"
        memoryText.text = "MEM: ${memoryUsage.toInt()}MB"
        timeText.text = "TIME: ${executionTime}ms"

        // Color coding based on thresholds
        cpuText.setTextColor(getColorForValue(cpuUsage, 50f, 80f))
        memoryText.setTextColor(getColorForValue(memoryUsage, 128f, 200f))
    }

    private fun getColorForValue(value: Float, warningThreshold: Float, criticalThreshold: Float): Int {
        return when {
            value >= criticalThreshold -> ContextCompat.getColor(context, R.color.neon_red)
            value >= warningThreshold -> ContextCompat.getColor(context, R.color.neon_yellow)
            else -> ContextCompat.getColor(context, R.color.neon_green)
        }
    }

    private fun startUpdateLoop() {
        postDelayed(object : Runnable {
            override fun run() {
                if (!isMonitoring) return

                // Update execution time
                val elapsed = System.currentTimeMillis() - startTime
                timeText.text = "TIME: ${elapsed}ms"

                // Simulate/poll actual CPU and memory (in real implementation,
                // these would come from the sandbox process)
                graphView.addDataPoint(currentCpuUsage, currentMemoryUsage)

                postDelayed(this, 500)
            }
        }, 500)
    }

    /**
     * Custom graph view for CPU and memory history
     */
    private inner class ResourceGraphView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null
    ) : androidx.appcompat.widget.AppCompatImageView(context, attrs) {

        private val cpuHistory = LinkedList<Float>()
        private val memoryHistory = LinkedList<Float>()
        private val maxHistorySize = 60 // 30 seconds at 500ms intervals

        private val cpuPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.graph_cpu)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        private val memoryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.graph_memory)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        private val gridPaint = Paint().apply {
            color = ContextCompat.getColor(context, R.color.graph_grid)
            strokeWidth = 0.5f
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            alpha = 30
        }

        private val cpuPath = Path()
        private val memoryPath = Path()

        init {
            setBackgroundColor(ContextCompat.getColor(context, R.color.graph_bg))
        }

        fun addDataPoint(cpu: Float, memory: Float) {
            cpuHistory.addLast(cpu.coerceIn(0f, 100f))
            memoryHistory.addLast(memory.coerceIn(0f, 512f)) // Normalize to 512MB max

            while (cpuHistory.size > maxHistorySize) {
                cpuHistory.removeFirst()
                memoryHistory.removeFirst()
            }

            invalidate()
        }

        fun clear() {
            cpuHistory.clear()
            memoryHistory.clear()
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            if (cpuHistory.isEmpty()) return

            val width = width.toFloat()
            val height = height.toFloat()
            val padding = 8f

            // Draw grid
            val gridLines = 4
            for (i in 0..gridLines) {
                val y = padding + (height - 2 * padding) * i / gridLines
                canvas.drawLine(padding, y, width - padding, y, gridPaint)
            }

            // Draw CPU line
            drawLine(canvas, cpuHistory, width, height, padding, cpuPaint, 100f)

            // Draw memory line (scaled)
            drawLine(canvas, memoryHistory, width, height, padding, memoryPaint, 512f)

            // Labels
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ContextCompat.getColor(context, R.color.neon_gray)
                textSize = 20f
            }
            canvas.drawText("CPU", padding, padding + 20, labelPaint)
            canvas.drawText("MEM", padding + 50, padding + 20, labelPaint)
        }

        private fun drawLine(
            canvas: Canvas,
            data: LinkedList<Float>,
            width: Float,
            height: Float,
            padding: Float,
            paint: Paint,
            maxValue: Float
        ) {
            if (data.size < 2) return

            val path = Path()
            val stepX = (width - 2 * padding) / maxHistorySize
            val availableHeight = height - 2 * padding

            data.forEachIndexed { index, value ->
                val x = padding + index * stepX
                val y = padding + availableHeight * (1 - value / maxValue)

                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }

            canvas.drawPath(path, paint)

            // Fill area under line
            val fillPath = Path(path)
            fillPath.lineTo(padding + (data.size - 1) * stepX, height - padding)
            fillPath.lineTo(padding, height - padding)
            fillPath.close()

            fillPaint.color = paint.color
            canvas.drawPath(fillPath, fillPaint)
        }
    }

    companion object {
        private const val TAG = "ResourceMonitorView"
    }
}
