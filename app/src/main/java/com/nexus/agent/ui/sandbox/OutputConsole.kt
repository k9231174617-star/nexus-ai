package com.nexus.agent.ui.sandbox

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.nexus.agent.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Terminal-like output console for displaying sandbox execution results.
 * Supports color-coded output streams, timestamps, and gesture-based navigation.
 */
class OutputConsole @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val scrollView: ScrollView
    private val outputText: TextView
    private val buffer = SpannableStringBuilder()
    private val lines = CopyOnWriteArrayList<ConsoleLine>()

    private val maxBufferLines = 2000
    private val timestampFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    // Paints for custom drawing (used for cursor/decorations)
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.neon_green)
        style = Paint.Style.FILL
    }
    private val cursorBlinkInterval = 530L
    private var cursorVisible = true
    private var isRunning = false

    // Colors
    private val colorOutput: Int
    private val colorError: Int
    private val colorSystem: Int
    private val colorWarning: Int
    private val colorDebug: Int

    // Gesture detection
    private val gestureDetector: GestureDetector
    private var onLineClickListener: ((String) -> Unit)? = null

    enum class LineType {
        OUTPUT,      // Standard stdout
        ERROR,       // stderr
        SYSTEM,      // System messages
        WARNING,     // Warnings
        DEBUG,       // Debug info
        INPUT        // User input echo
    }

    data class ConsoleLine(
        val text: String,
        val type: LineType,
        val timestamp: Long = System.currentTimeMillis()
    )

    init {
        colorOutput = ContextCompat.getColor(context, R.color.console_output)
        colorError = ContextCompat.getColor(context, R.color.console_error)
        colorSystem = ContextCompat.getColor(context, R.color.console_system)
        colorWarning = ContextCompat.getColor(context, R.color.console_warning)
        colorDebug = ContextCompat.getColor(context, R.color.console_debug)

        scrollView = ScrollView(context).apply {
            isVerticalScrollBarEnabled = true
            overScrollMode = OVER_SCROLL_ALWAYS
            setBackgroundColor(ContextCompat.getColor(context, R.color.console_bg))
        }

        outputText = TextView(context).apply {
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setPadding(16, 16, 16, 16)
            setTextIsSelectable(true)
            setHorizontallyScrolling(true)
        }

        scrollView.addView(outputText)
        addView(scrollView)

        gestureDetector = GestureDetector(context, GestureListener())

        // Start cursor blink animation
        postDelayed(object : Runnable {
            override fun run() {
                if (isRunning) {
                    cursorVisible = !cursorVisible
                    invalidate()
                }
                postDelayed(this, cursorBlinkInterval)
            }
        }, cursorBlinkInterval)
    }

    fun append(text: String, type: LineType = LineType.OUTPUT) {
        val line = ConsoleLine(text, type)
        lines.add(line)

        // Trim buffer if needed
        while (lines.size > maxBufferLines) {
            lines.removeAt(0)
        }

        val spannable = formatLine(line)
        buffer.append(spannable)

        post {
            outputText.text = buffer
            scrollToBottom()
        }
    }

    fun appendOutput(text: String) {
        text.split("\n").forEach { line ->
            if (line.isNotEmpty()) append(line, LineType.OUTPUT)
        }
    }

    fun appendError(text: String) {
        text.split("\n").forEach { line ->
            if (line.isNotEmpty()) append(line, LineType.ERROR)
        }
    }

    fun appendSystem(text: String) {
        text.split("\n").forEach { line ->
            if (line.isNotEmpty()) append(line, LineType.SYSTEM)
        }
    }

    fun appendWarning(text: String) {
        append(text, LineType.WARNING)
    }

    fun appendDebug(text: String) {
        append(text, LineType.DEBUG)
    }

    fun appendInput(text: String) {
        append("> $text", LineType.INPUT)
    }

    private fun formatLine(line: ConsoleLine): SpannableStringBuilder {
        val timestamp = timestampFormat.format(Date(line.timestamp))
        val prefix = when (line.type) {
            LineType.OUTPUT -> ""
            LineType.ERROR -> "[ERR] "
            LineType.SYSTEM -> "[SYS] "
            LineType.WARNING -> "[WRN] "
            LineType.DEBUG -> "[DBG] "
            LineType.INPUT -> ""
        }

        val fullText = "[$timestamp] $prefix${line.text}\n"
        val spannable = SpannableStringBuilder(fullText)

        val color = when (line.type) {
            LineType.OUTPUT -> colorOutput
            LineType.ERROR -> colorError
            LineType.SYSTEM -> colorSystem
            LineType.WARNING -> colorWarning
            LineType.DEBUG -> colorDebug
            LineType.INPUT -> colorOutput
        }

        // Apply color to the entire line except timestamp
        val contentStart = timestamp.length + 3 // "[HH:mm:ss.SSS] ".length
        if (fullText.length > contentStart) {
            spannable.setSpan(
                ForegroundColorSpan(color),
                contentStart,
                fullText.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        // Timestamp in dim color
        spannable.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(context, R.color.console_timestamp)),
            0,
            contentStart,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        return spannable
    }

    fun clear() {
        lines.clear()
        buffer.clear()
        outputText.text = ""
        invalidate()
    }

    fun getText(): String = buffer.toString()

    fun getLines(): List<String> = lines.map { it.text }

    fun scrollToBottom() {
        scrollView.post {
            scrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    fun scrollToTop() {
        scrollView.post {
            scrollView.fullScroll(View.FOCUS_UP)
        }
    }

    fun setRunning(running: Boolean) {
        isRunning = running
        invalidate()
    }

    fun setOnLineClickListener(listener: (String) -> Unit) {
        onLineClickListener = listener
    }

    fun search(query: String): List<Int> {
        val results = mutableListOf<Int>()
        val text = buffer.toString()
        var index = text.indexOf(query, ignoreCase = true)
        while (index >= 0) {
            results.add(index)
            index = text.indexOf(query, index + 1, ignoreCase = true)
        }
        return results
    }

    fun exportToString(): String {
        return lines.joinToString("\n") { line ->
            val prefix = when (line.type) {
                LineType.ERROR -> "[ERROR] "
                LineType.SYSTEM -> "[SYSTEM] "
                LineType.WARNING -> "[WARNING] "
                LineType.DEBUG -> "[DEBUG] "
                else -> ""
            }
            "$prefix${line.text}"
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw running indicator
        if (isRunning && cursorVisible) {
            val indicatorSize = 8f
            val x = width - 32f
            val y = 32f
            canvas.drawCircle(x, y, indicatorSize, cursorPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return super.onTouchEvent(event)
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            // Select word at tap position
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            // Show context menu
        }
    }

    companion object {
        private const val TAG = "OutputConsole"
    }
}
