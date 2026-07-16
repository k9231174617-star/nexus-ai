package com.nexus.agent.ui.sandbox

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.content.ContextCompat
import com.nexus.agent.R
import com.nexus.agent.core.sandbox.LanguageRunner
import java.util.regex.Pattern

/**
 * Advanced code editor panel with syntax highlighting, line numbers, and auto-indentation.
 * Optimized for mobile touch interaction.
 */
class CodeEditorPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val scrollView: ScrollView
    private val horizontalScroll: HorizontalScrollView
    private val contentContainer: LinearLayout
    private val lineNumberView: LineNumberView
    private val editor: EditText

    private var currentLanguage = LanguageRunner.Language.PYTHON
    private var isEditable = true
    private var onCodeChangedListener: ((String) -> Unit)? = null

    // Syntax highlighting paints
    private val keywordPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.syntax_keyword)
        typeface = Typeface.MONOSPACE
    }
    private val stringPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.syntax_string)
        typeface = Typeface.MONOSPACE
    }
    private val commentPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.syntax_comment)
        typeface = Typeface.MONOSPACE
    }
    private val numberPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.syntax_number)
        typeface = Typeface.MONOSPACE
    }
    private val functionPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.syntax_function)
        typeface = Typeface.MONOSPACE
    }
    private val defaultPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.neon_white)
        typeface = Typeface.MONOSPACE
    }

    // Syntax patterns
    private val patterns = mutableMapOf<Pattern, Paint>()

    // Auto-indent tracking
    private var isProcessingTextChange = false

    init {
        // Setup layout
        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        lineNumberView = LineNumberView(context)
        lineNumberView.layoutParams = LinearLayout.LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.MATCH_PARENT
        )

        horizontalScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = true
            overScrollMode = OVER_SCROLL_ALWAYS
        }

        scrollView = ScrollView(context).apply {
            isVerticalScrollBarEnabled = true
            overScrollMode = OVER_SCROLL_ALWAYS
        }

        editor = EditText(context).apply {
            typeface = Typeface.MONOSPACE
            setTextColor(ContextCompat.getColor(context, R.color.neon_white))
            setBackgroundColor(ContextCompat.getColor(context, R.color.editor_bg))
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_FLAG_NO_ENTER_ACTION
            setHorizontallyScrolling(true)
            setPadding(16, 16, 16, 16)
            minHeight = 800
        }

        horizontalScroll.addView(editor)
        scrollView.addView(horizontalScroll)

        mainLayout.addView(lineNumberView)
        mainLayout.addView(scrollView)

        addView(mainLayout)

        setupSyntaxHighlighting()
        setupEditorListeners()
        updatePatternsForLanguage()
    }

    private fun setupSyntaxHighlighting() {
        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isProcessingTextChange) return
                onCodeChangedListener?.invoke(s?.toString() ?: "")
                updateLineNumbers()
            }
        })
    }

    private fun setupEditorListeners() {
        editor.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                handleEnterKey()
                return@setOnKeyListener true
            }
            false
        }

        // Sync scrolling between line numbers and editor
        scrollView.viewTreeObserver.addOnScrollChangedListener {
            lineNumberView.scrollTo(0, scrollView.scrollY)
        }
    }

    private fun handleEnterKey() {
        val text = editor.text ?: return
        val cursorPos = editor.selectionStart
        val lineStart = text.lastIndexOf('\n', cursorPos - 1).let {
            if (it == -1) 0 else it + 1
        }

        val currentLine = text.substring(lineStart, cursorPos)
        val indent = currentLine.takeWhile { it == ' ' || it == '\t' }

        // Check if we need extra indent
        val trimmed = currentLine.trim()
        val extraIndent = when (currentLanguage) {
            LanguageRunner.Language.PYTHON -> {
                if (trimmed.endsWith(":")) "    " else ""
            }
            LanguageRunner.Language.JAVASCRIPT,
            LanguageRunner.Language.TYPESCRIPT -> {
                if (trimmed.endsWith("{")) "    " else ""
            }
            LanguageRunner.Language.KOTLIN,
            LanguageRunner.Language.JAVA -> {
                if (trimmed.endsWith("{")) "    " else ""
            }
            else -> ""
        }

        val insertText = "\n$indent$extraIndent"
        text.insert(cursorPos, insertText)
        editor.setSelection(cursorPos + insertText.length)
    }

    private fun updatePatternsForLanguage() {
        patterns.clear()

        when (currentLanguage) {
            LanguageRunner.Language.PYTHON -> {
                patterns[Pattern.compile("\\b(def|class|if|elif|else|for|while|return|import|from|as|try|except|finally|with|yield|lambda|pass|break|continue|raise|assert|del|global|nonlocal|and|or|not|in|is|True|False|None)\\b")] = keywordPaint
                patterns[Pattern.compile("\"\"\"[\\s\\S]*?\"\"\"")] = stringPaint
                patterns[Pattern.compile("'''[\\s\\S]*?'''")] = stringPaint
                patterns[Pattern.compile("\"[^\"\\\\]*(\\\\.[^\"\\\\]*)*\"")] = stringPaint
                patterns[Pattern.compile("'[^'\\\\]*(\\\\.[^'\\\\]*)*'")] = stringPaint
                patterns[Pattern.compile("#.*$", Pattern.MULTILINE)] = commentPaint
                patterns[Pattern.compile("\\b\\d+\\.?\\d*\\b")] = numberPaint
                patterns[Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*(?=\\()")] = functionPaint
            }
            LanguageRunner.Language.JAVASCRIPT, LanguageRunner.Language.TYPESCRIPT -> {
                patterns[Pattern.compile("\\b(function|const|let|var|if|else|for|while|return|class|extends|import|export|from|async|await|try|catch|finally|throw|new|this|typeof|instanceof|void|delete|yield|true|false|null|undefined)\\b")] = keywordPaint
                patterns[Pattern.compile("`[^`]*`")] = stringPaint
                patterns[Pattern.compile("\"[^\"\\\\]*(\\\\.[^\"\\\\]*)*\"")] = stringPaint
                patterns[Pattern.compile("'[^'\\\\]*(\\\\.[^'\\\\]*)*'")] = stringPaint
                patterns[Pattern.compile("//.*$", Pattern.MULTILINE)] = commentPaint
                patterns[Pattern.compile("/\\*[\\s\\S]*?\\*/")] = commentPaint
                patterns[Pattern.compile("\\b\\d+\\.?\\d*\\b")] = numberPaint
            }
            LanguageRunner.Language.KOTLIN, LanguageRunner.Language.JAVA -> {
                patterns[Pattern.compile("\\b(fun|val|var|if|else|when|for|while|return|class|object|interface|extends|implements|import|package|try|catch|finally|throw|new|this|super|true|false|null|is|as|in|out|where|by|lazy|lateinit|suspend|override|abstract|open|private|protected|public|internal|companion|data|sealed|enum|inline|crossinline|noinline|operator|infix|tailrec)\\b")] = keywordPaint
                patterns[Pattern.compile("\"\"\"[\\s\\S]*?\"\"\"")] = stringPaint
                patterns[Pattern.compile("\"[^\"\\\\]*(\\\\.[^\"\\\\]*)*\"")] = stringPaint
                patterns[Pattern.compile("'[^'\\\\]*(\\\\.[^'\\\\]*)*'")] = stringPaint
                patterns[Pattern.compile("//.*$", Pattern.MULTILINE)] = commentPaint
                patterns[Pattern.compile("/\\*[\\s\\S]*?\\*/")] = commentPaint
                patterns[Pattern.compile("\\b\\d+\\.?\\d*\\b")] = numberPaint
            }
            else -> {
                // Generic patterns
                patterns[Pattern.compile("\"[^\"]*\"")] = stringPaint
                patterns[Pattern.compile("'[^']*'")] = stringPaint
                patterns[Pattern.compile("\\b\\d+\\b")] = numberPaint
            }
        }
    }

    private fun updateLineNumbers() {
        val lineCount = editor.lineCount.coerceAtLeast(1)
        lineNumberView.setLineCount(lineCount)
        lineNumberView.invalidate()
    }

    fun setLanguage(language: LanguageRunner.Language) {
        currentLanguage = language
        updatePatternsForLanguage()
        editor.hint = "Enter ${language.displayName} code..."
        invalidate()
    }

    fun getText(): String = editor.text?.toString() ?: ""

    fun setText(code: String) {
        isProcessingTextChange = true
        editor.setText(code)
        editor.setSelection(code.length)
        isProcessingTextChange = false
        updateLineNumbers()
    }

    fun setEditable(editable: Boolean) {
        isEditable = editable
        editor.isFocusable = editable
        editor.isFocusableInTouchMode = editable
        editor.isEnabled = editable
    }

    fun setOnCodeChangedListener(listener: (String) -> Unit) {
        onCodeChangedListener = listener
    }

    fun insertText(text: String) {
        val cursorPos = editor.selectionStart
        editor.text?.insert(cursorPos, text)
    }

    fun getSelectedText(): String {
        val start = editor.selectionStart
        val end = editor.selectionEnd
        return if (start >= 0 && end > start) {
            editor.text?.substring(start, end) ?: ""
        } else ""
    }

    fun undo() {
        // Could integrate with a command history system
    }

    fun redo() {
        // Could integrate with a command history system
    }

    /**
     * Custom view for displaying line numbers
     */
    private inner class LineNumberView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null
    ) : View(context, attrs) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.line_number)
            textSize = 28f
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.RIGHT
        }

        private val bgPaint = Paint().apply {
            color = ContextCompat.getColor(context, R.color.line_number_bg)
        }

        private var lineCount = 1
        private val lineHeight = 42f // Approximate line height matching editor

        fun setLineCount(count: Int) {
            lineCount = count
            requestLayout()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = (paint.measureText("9999") + 32).toInt()
            val height = (lineCount * lineHeight + 32).toInt()
            setMeasuredDimension(width, height)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            // Background
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            // Line numbers
            val paddingRight = 16f
            for (i in 1..lineCount) {
                val y = 16f + (i - 1) * lineHeight + paint.textSize * 0.75f
                canvas.drawText(i.toString(), width - paddingRight, y, paint)
            }

            // Divider line
            val dividerPaint = Paint().apply {
                color = ContextCompat.getColor(context, R.color.line_number_divider)
                strokeWidth = 1f
            }
            canvas.drawLine(width - 1f, 0f, width - 1f, height.toFloat(), dividerPaint)
        }
    }

    companion object {
        private const val TAG = "CodeEditorPanel"
    }
}
