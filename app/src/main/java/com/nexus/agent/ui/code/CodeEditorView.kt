package com.nexus.agent.ui.code

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.OverScroller
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.nexus.agent.R
import kotlin.math.max
import kotlin.math.min

/**
 * Кастомный редактор кода с подсветкой синтаксиса, номерами строк,
 * масштабированием, горизонтальной прокруткой и minimap.
 */
class CodeEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HorizontalScrollView(context, attrs, defStyleAttr) {

    var onTextChange: ((String) -> Unit)? = null
    var onSelectionChange: ((Int, Int) -> Unit)? = null
    var onRequestAiAssist: ((String) -> Unit)? = null

    private val editorContainer: LinearLayout
    private val lineNumberView: LineNumberView
    private val editText: CodeEditText
    private val minimapView: MinimapView

    private val syntaxHighlighter: SyntaxHighlighter
    private var currentLanguage: String = "kotlin"
    private var isDirty = false
    private var textSizeSp = 14f
    private val tabSize = 4

    // Gestures
    private val scaleDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetector
    private val scroller = OverScroller(context)

    // Colors
    private val bgColor by lazy { ContextCompat.getColor(context, R.color.bg_editor) }
    private val lineNumberColor by lazy { ContextCompat.getColor(context, R.color.text_disabled) }
    private val lineNumberBg by lazy { ContextCompat.getColor(context, R.color.bg_line_number) }
    private val currentLineColor by lazy { ContextCompat.getColor(context, R.color.bg_current_line) }

    init {
        isFillViewport = true
        isHorizontalScrollBarEnabled = true

        editorContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        lineNumberView = LineNumberView(context)
        editText = CodeEditText(context)
        minimapView = MinimapView(context)

        editorContainer.addView(lineNumberView)
        editorContainer.addView(editText, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))

        addView(editorContainer)

        syntaxHighlighter = SyntaxHighlighter()

        scaleDetector = ScaleGestureDetector(context, ScaleListener())
        gestureDetector = GestureDetector(context, GestureListener())

        setupEditText()
    }

    private fun setupEditText() {
        editText.apply {
            setBackgroundColor(bgColor)
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            typeface = Typeface.MONOSPACE
            setTextSize(textSizeSp)
            inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_FLAG_NO_ENTER_ACTION

            addTextChangedListener(object : TextWatcher {
                private var beforeChange = ""

                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                    beforeChange = s?.toString() ?: ""
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    // Handle auto-indent
                    if (count == 1 && s?.getOrNull(start) == '\n') {
                        applyAutoIndent(start)
                    }
                }

                override fun afterTextChanged(s: Editable?) {
                    s?.let {
                        isDirty = true
                        highlightSyntax(it)
                        lineNumberView.updateLineCount(it.toString().lines().size)
                        onTextChange?.invoke(it.toString())
                    }
                }
            })

            setOnSelectionChangeListener { selStart, selEnd ->
                onSelectionChange?.invoke(selStart, selEnd)
                highlightCurrentLine()
            }
        }
    }

    fun setLanguage(language: String) {
        currentLanguage = language
        syntaxHighlighter.setLanguage(language)
        highlightSyntax(editText.text ?: SpannableStringBuilder(""))
    }

    fun setCode(code: String) {
        editText.setText(code)
        isDirty = false
        highlightSyntax(editText.text ?: SpannableStringBuilder(""))
    }

    fun getCode(): String = editText.text?.toString() ?: ""

    fun isModified(): Boolean = isDirty

    fun markSaved() {
        isDirty = false
    }

    fun insertAtCursor(text: String) {
        val start = editText.selectionStart.coerceAtLeast(0)
        editText.text?.insert(start, text)
    }

    fun replaceSelection(text: String) {
        val start = editText.selectionStart.coerceAtLeast(0)
        val end = editText.selectionEnd.coerceAtLeast(0)
        editText.text?.replace(min(start, end), max(start, end), text)
    }

    fun getSelectedText(): String {
        val start = editText.selectionStart
        val end = editText.selectionEnd
        if (start < 0 || end < 0 || start == end) return ""
        return editText.text?.substring(min(start, end), max(start, end)) ?: ""
    }

    fun undo() {
        // Implement with EditHistory
    }

    fun redo() {
        // Implement with EditHistory
    }

    fun findAndReplace(find: String, replace: String, replaceAll: Boolean = false) {
        val text = editText.text?.toString() ?: return
        if (replaceAll) {
            val newText = text.replace(find, replace)
            editText.setText(newText)
        } else {
            val start = text.indexOf(find, editText.selectionEnd)
            if (start >= 0) {
                editText.text?.replace(start, start + find.length, replace)
                editText.setSelection(start + replace.length)
            }
        }
    }

    fun goToLine(lineNumber: Int) {
        val lines = editText.text?.toString()?.lines() ?: return
        var offset = 0
        for (i in 0 until min(lineNumber - 1, lines.size)) {
            offset += lines[i].length + 1
        }
        editText.setSelection(offset)
        editText.requestFocus()
    }

    private fun applyAutoIndent(newLinePos: Int) {
        val text = editText.text?.toString() ?: return
        val lineStart = text.lastIndexOf('\n', newLinePos - 1).let {
            if (it < 0) 0 else it + 1
        }
        val prevLine = text.substring(lineStart, newLinePos).trimEnd()
        val indent = prevLine.takeWhile { it == ' ' || it == '\t' }
        val extraIndent = if (prevLine.endsWith("{") || prevLine.endsWith(":")) "    " else ""

        editText.text?.insert(newLinePos + 1, indent + extraIndent)
        editText.setSelection(newLinePos + 1 + indent.length + extraIndent.length)
    }

    private fun highlightSyntax(editable: Editable) {
        // Remove old spans
        editable.getSpans(0, editable.length, ForegroundColorSpan::class.java)
            .forEach { editable.removeSpan(it) }
        editable.getSpans(0, editable.length, BackgroundColorSpan::class.java)
            .forEach { editable.removeSpan(it) }

        val spans = syntaxHighlighter.highlight(editable.toString())
        spans.forEach { span ->
            editable.setSpan(
                ForegroundColorSpan(span.color),
                span.start,
                span.end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    private fun highlightCurrentLine() {
        val layout = editText.layout ?: return
        val line = layout.getLineForOffset(editText.selectionStart)
        // Could draw highlight in onDraw
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(ev)
        gestureDetector.onTouchEvent(ev)
        return super.onTouchEvent(ev)
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            textSizeSp = (textSizeSp * detector.scaleFactor).coerceIn(8f, 32f)
            editText.setTextSize(textSizeSp)
            lineNumberView.setTextSize(textSizeSp)
            return true
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            // Select word
            val offset = editText.getOffsetForPosition(e.x, e.y)
            val text = editText.text?.toString() ?: return false
            val wordStart = text.lastIndexOfAny(charArrayOf(' ', '\n', '\t', '(', ')', '{', '}', '[', ']', '.', ','), offset) + 1
            val wordEnd = text.indexOfAny(charArrayOf(' ', '\n', '\t', '(', ')', '{', '}', '[', ']', '.', ','), offset).let {
                if (it < 0) text.length else it
            }
            editText.setSelection(wordStart, wordEnd)
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            val selected = getSelectedText()
            if (selected.isNotBlank()) {
                onRequestAiAssist?.invoke(selected)
            }
        }
    }

    // === Line Number View ===

    private inner class LineNumberView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            color = lineNumberColor
        }
        private var lineCount = 1
        private var lineHeight = 0f

        init {
            setBackgroundColor(lineNumberBg)
        }

        fun updateLineCount(count: Int) {
            lineCount = count
            requestLayout()
            invalidate()
        }

        fun setTextSize(sp: Float) {
            paint.textSize = sp * resources.displayMetrics.scaledDensity
            lineHeight = paint.fontMetrics.run { descent - ascent }
            requestLayout()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val maxDigits = lineCount.toString().length
            val width = (paint.measureText("9") * maxDigits + dpToPx(16)).toInt()
            setMeasuredDimension(width, MeasureSpec.getSize(heightMeasureSpec))
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val lineHeight = editText.lineHeight.toFloat()
            val currentLine = editText.layout?.getLineForOffset(editText.selectionStart) ?: 0

            for (i in 0 until lineCount) {
                val y = (i + 1) * lineHeight - paint.fontMetrics.descent
                val x = width - dpToPx(8).toFloat()

                // Highlight current line number
                if (i == currentLine) {
                    paint.color = ContextCompat.getColor(context, R.color.neon_cyan)
                    paint.typeface = Typeface.DEFAULT_BOLD
                } else {
                    paint.color = lineNumberColor
                    paint.typeface = Typeface.MONOSPACE
                }

                canvas.drawText((i + 1).toString(), x, y, paint)
            }
        }
    }

    // === Minimap View ===

    private inner class MinimapView(context: Context) : View(context) {
        private val minimapPaint = Paint()

        override fun onDraw(canvas: Canvas) {
            // Draw minimap representation of code
        }
    }

    // === Code EditText ===

    private inner class CodeEditText(context: Context) : EditText(context) {
        private var selectionListener: ((Int, Int) -> Unit)? = null

        fun setOnSelectionChangeListener(listener: (Int, Int) -> Unit) {
            selectionListener = listener
        }

        override fun onSelectionChanged(selStart: Int, selEnd: Int) {
            super.onSelectionChanged(selStart, selEnd)
            selectionListener?.invoke(selStart, selEnd)
        }

        override fun onDraw(canvas: Canvas) {
            // Draw current line highlight
            layout?.let { layout ->
                val line = layout.getLineForOffset(selectionStart)
                val top = layout.getLineTop(line).toFloat()
                val bottom = layout.getLineBottom(line).toFloat()
                canvas.drawRect(0f, top, width.toFloat(), bottom, Paint().apply {
                    color = currentLineColor
                })
            }
            super.onDraw(canvas)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
