package com.nexus.agent.ui.cli

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import com.nexus.agent.R

/**
 * TerminalView — custom TextView optimized for terminal output rendering.
 * Supports clickable links, color-coded output, cursor simulation, and selection.
 */
class TerminalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private val gestureDetector: GestureDetector
    private var linkClickListener: ((String) -> Unit)? = null
    private var cursorVisible = true
    private var cursorPosition = 0
    private val cursorPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.terminal_cursor)
        style = Paint.Style.FILL
    }
    private val cursorRunnable = Runnable {
        cursorVisible = !cursorVisible
        invalidate()
        postDelayed(cursorRunnable, CURSOR_BLINK_INTERVAL)
    }

    private val lineNumberPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.terminal_linenumber)
        textSize = textSize * 0.8f
        typeface = Typeface.MONOSPACE
    }

    private var showLineNumbers = false
    private var maxLinesBuffer = 1000
    private val textBuffer = StringBuilder()

    init {
        typeface = Typeface.MONOSPACE
        setTextIsSelectable(true)
        isFocusable = true
        isFocusableInTouchMode = true

        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                handleLinkClick(e)
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                super.onLongPress(e)
                // Trigger text selection on long press
                performLongClick()
            }
        })

        startCursorBlink()
    }

    private fun handleLinkClick(event: MotionEvent) {
        val layout = this.layout ?: return
        val x = event.x.toInt()
        val y = event.y.toInt()

        val line = layout.getLineForVertical(y)
        val offset = layout.getOffsetForHorizontal(line, x.toFloat())

        val spannable = text as? Spannable ?: return
        val clickableSpans = spannable.getSpans(offset, offset, ClickableSpan::class.java)

        if (clickableSpans.isNotEmpty()) {
            val span = clickableSpans[0]
            val start = spannable.getSpanStart(span)
            val end = spannable.getSpanEnd(span)
            val linkText = spannable.subSequence(start, end).toString()
            linkClickListener?.invoke(linkText)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        if (showLineNumbers) {
            drawLineNumbers(canvas)
            canvas.save()
            canvas.translate(LINE_NUMBER_WIDTH, 0f)
        }
        super.onDraw(canvas)
        if (showLineNumbers) {
            canvas.restore()
        }
        drawCursor(canvas)
    }

    private fun drawLineNumbers(canvas: Canvas) {
        val layout = this.layout ?: return
        val lineCount = layout.lineCount
        val padding = 8f

        for (i in 0 until lineCount) {
            val lineTop = layout.getLineTop(i).toFloat()
            val lineNumber = (i + 1).toString()
            canvas.drawText(
                lineNumber,
                padding,
                lineTop + layout.getLineBaseline(i),
                lineNumberPaint
            )
        }

        // Draw separator
        val separatorPaint = Paint().apply {
            color = ContextCompat.getColor(context, R.color.terminal_linenumber_separator)
            strokeWidth = 1f
        }
        canvas.drawLine(LINE_NUMBER_WIDTH - 4f, 0f, LINE_NUMBER_WIDTH - 4f, height.toFloat(), separatorPaint)
    }

    private fun drawCursor(canvas: Canvas) {
        if (!cursorVisible || !isFocused) return

        val layout = this.layout ?: return
        val line = layout.getLineForOffset(cursorPosition)
        val baseline = layout.getLineBaseline(line)
        val ascent = layout.getLineAscent(line)
        val x = layout.getPrimaryHorizontal(cursorPosition)
        val y = (baseline + ascent).toFloat()
        val cursorHeight = (layout.getLineDescent(line) - ascent).toFloat()

        canvas.drawRect(x, y, x + CURSOR_WIDTH, y + cursorHeight, cursorPaint)
    }

    fun setCursorPosition(position: Int) {
        cursorPosition = position.coerceIn(0, text?.length ?: 0)
        invalidate()
    }

    fun appendText(text: CharSequence, colorResId: Int = R.color.terminal_stdout) {
        val spannable = text as? Spannable ?: SpannableStringBuilder(text)
        val color = ContextCompat.getColor(context, colorResId)

        if (spannable is SpannableStringBuilder) {
            spannable.setSpan(
                ForegroundColorSpan(color),
                0,
                spannable.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        val builder = SpannableStringBuilder(this.text)
        builder.append(spannable)

        // Trim buffer if too large
        if (builder.length > maxLinesBuffer * 200) {
            val trimStart = builder.length - maxLinesBuffer * 200
            builder.delete(0, trimStart)
        }

        setText(builder)
        cursorPosition = builder.length
        scrollToBottom()
    }

    fun appendLink(text: String, linkAction: String, colorResId: Int = R.color.terminal_link) {
        val spannable = SpannableStringBuilder(text)
        val color = ContextCompat.getColor(context, colorResId)

        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                linkClickListener?.invoke(linkAction)
            }
        }

        spannable.setSpan(
            ForegroundColorSpan(color),
            0,
            spannable.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannable.setSpan(
            clickableSpan,
            0,
            spannable.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        appendText(spannable, colorResId)
    }

    fun clear() {
        text = ""
        textBuffer.clear()
        cursorPosition = 0
    }

    fun setOnLinkClickListener(listener: (String) -> Unit) {
        this.linkClickListener = listener
    }

    fun setShowLineNumbers(show: Boolean) {
        showLineNumbers = show
        setPadding(
            if (show) (LINE_NUMBER_WIDTH + 16).toInt() else 16,
            paddingTop,
            paddingRight,
            paddingBottom
        )
        invalidate()
    }

    fun setMaxBufferLines(maxLines: Int) {
        maxLinesBuffer = maxLines
    }

    private fun scrollToBottom() {
        post {
            val scrollView = parent as? android.widget.ScrollView
            scrollView?.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun startCursorBlink() {
        removeCallbacks(cursorRunnable)
        postDelayed(cursorRunnable, CURSOR_BLINK_INTERVAL)
    }

    private fun stopCursorBlink() {
        removeCallbacks(cursorRunnable)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startCursorBlink()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopCursorBlink()
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (gainFocus) {
            startCursorBlink()
        } else {
            stopCursorBlink()
            cursorVisible = false
            invalidate()
        }
    }

    companion object {
        private const val CURSOR_BLINK_INTERVAL = 530L
        private const val CURSOR_WIDTH = 8f
        private const val LINE_NUMBER_WIDTH = 60f
    }
}
