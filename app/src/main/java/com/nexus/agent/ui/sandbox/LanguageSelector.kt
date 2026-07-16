package com.nexus.agent.ui.sandbox

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.nexus.agent.R
import com.nexus.agent.core.sandbox.LanguageRunner

/**
 * Horizontal scrollable language selector with neon-styled chips.
 * Supports single selection with visual feedback and custom language additions.
 */
class LanguageSelector @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val scrollView: HorizontalScrollView
    private val chipContainer: LinearLayout
    private val languageChips = mutableMapOf<LanguageRunner.Language, LanguageChip>()

    private var selectedLanguage: LanguageRunner.Language? = null
    private var onLanguageSelectedListener: ((LanguageRunner.Language) -> Unit)? = null

    // Supported languages
    private val supportedLanguages = listOf(
        LanguageRunner.Language.PYTHON,
        LanguageRunner.Language.JAVASCRIPT,
        LanguageRunner.Language.TYPESCRIPT,
        LanguageRunner.Language.KOTLIN,
        LanguageRunner.Language.JAVA,
        LanguageRunner.Language.RUST,
        LanguageRunner.Language.GO,
        LanguageRunner.Language.CPP,
        LanguageRunner.Language.BASH
    )

    // Paint for custom chip drawing
    private val chipBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val chipStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val chipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 28f
    }

    init {
        scrollView = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
        }

        chipContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 8, 8, 8)
        }

        scrollView.addView(chipContainer)
        addView(scrollView)

        // Set background
        setBackgroundColor(ContextCompat.getColor(context, R.color.selector_bg))

        populateChips()
    }

    private fun populateChips() {
        supportedLanguages.forEach { language ->
            val chip = LanguageChip(context, language)
            chip.setOnClickListener {
                selectLanguage(language)
            }
            languageChips[language] = chip
            chipContainer.addView(chip)
        }
    }

    fun selectLanguage(language: LanguageRunner.Language) {
        if (selectedLanguage == language) return

        // Deselect previous
        selectedLanguage?.let { prev ->
            languageChips[prev]?.setSelected(false)
        }

        // Select new
        selectedLanguage = language
        languageChips[language]?.setSelected(true)

        // Animate scroll to selected chip
        languageChips[language]?.let { chip ->
            val chipLeft = chip.left
            val chipRight = chip.right
            val scrollCenter = scrollView.width / 2
            val chipCenter = (chipLeft + chipRight) / 2
            scrollView.smoothScrollTo(chipCenter - scrollCenter, 0)
        }

        onLanguageSelectedListener?.invoke(language)
    }

    fun getSelectedLanguage(): LanguageRunner.Language? = selectedLanguage

    fun setOnLanguageSelectedListener(listener: (LanguageRunner.Language) -> Unit) {
        onLanguageSelectedListener = listener
    }

    fun addCustomLanguage(language: LanguageRunner.Language) {
        if (language in languageChips) return

        val chip = LanguageChip(context, language)
        chip.setOnClickListener {
            selectLanguage(language)
        }
        languageChips[language] = chip
        chipContainer.addView(chip)
    }

    fun removeLanguage(language: LanguageRunner.Language) {
        languageChips[language]?.let { chip ->
            chipContainer.removeView(chip)
            languageChips.remove(language)
            if (selectedLanguage == language) {
                selectedLanguage = null
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        languageChips.values.forEach { it.isEnabled = enabled }
    }

    /**
     * Custom chip view for each language
     */
    private inner class LanguageChip @JvmOverloads constructor(
        context: Context,
        private val language: LanguageRunner.Language,
        attrs: AttributeSet? = null
    ) : View(context, attrs) {

        private var isChipSelected = false
        private val cornerRadius = 24f
        private val paddingHorizontal = 32f
        private val paddingVertical = 16f

        private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        init {
            setPadding(8, 4, 8, 4)
        }

        fun setSelected(selected: Boolean) {
            isChipSelected = selected
            invalidate()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val textWidth = chipTextPaint.measureText(language.displayName)
            val width = (textWidth + paddingHorizontal * 2 + 40).toInt() // +40 for icon
            val height = (chipTextPaint.textSize + paddingVertical * 2 + 8).toInt()
            setMeasuredDimension(width, height)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val rect = RectF(4f, 4f, width - 4f, height - 4f)

            // Background
            chipBgPaint.color = if (isChipSelected) {
                ContextCompat.getColor(context, R.color.chip_selected_bg)
            } else {
                ContextCompat.getColor(context, R.color.chip_bg)
            }

            // Neon glow for selected
            if (isChipSelected) {
                val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = ContextCompat.getColor(context, R.color.neon_cyan)
                    alpha = 40
                    style = Paint.Style.FILL
                }
                canvas.drawRoundRect(
                    rect.left - 2, rect.top - 2,
                    rect.right + 2, rect.bottom + 2,
                    cornerRadius + 2, cornerRadius + 2, glowPaint
                )
            }

            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, chipBgPaint)

            // Border
            chipStrokePaint.color = if (isChipSelected) {
                ContextCompat.getColor(context, R.color.neon_cyan)
            } else {
                ContextCompat.getColor(context, R.color.chip_border)
            }
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, chipStrokePaint)

            // Language icon (colored circle)
            iconPaint.color = getLanguageColor(language)
            val iconRadius = 10f
            val iconX = paddingHorizontal + 10
            val iconY = height / 2f
            canvas.drawCircle(iconX, iconY, iconRadius, iconPaint)

            // Text
            chipTextPaint.color = if (isChipSelected) {
                ContextCompat.getColor(context, R.color.neon_white)
            } else {
                ContextCompat.getColor(context, R.color.neon_gray)
            }
            val textX = width / 2f + 8 // Offset for icon
            val textY = height / 2f + chipTextPaint.textSize / 3
            canvas.drawText(language.displayName, textX, textY, chipTextPaint)
        }

        private fun getLanguageColor(language: LanguageRunner.Language): Int {
            return ContextCompat.getColor(context, when (language) {
                LanguageRunner.Language.PYTHON -> R.color.lang_python
                LanguageRunner.Language.JAVASCRIPT -> R.color.lang_javascript
                LanguageRunner.Language.TYPESCRIPT -> R.color.lang_typescript
                LanguageRunner.Language.KOTLIN -> R.color.lang_kotlin
                LanguageRunner.Language.JAVA -> R.color.lang_java
                LanguageRunner.Language.RUST -> R.color.lang_rust
                LanguageRunner.Language.GO -> R.color.lang_go
                LanguageRunner.Language.CPP -> R.color.lang_cpp
                LanguageRunner.Language.BASH -> R.color.lang_bash
                else -> R.color.neon_gray
            })
        }
    }

    companion object {
        private const val TAG = "LanguageSelector"
    }
}
