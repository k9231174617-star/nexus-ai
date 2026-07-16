package com.nexus.agent.ui.memory

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import androidx.core.animation.doOnEnd
import androidx.core.view.isVisible
import com.google.android.material.chip.Chip
import com.nexus.agent.R
import com.nexus.agent.databinding.PanelContextInjectBinding

/**
 * Custom View - панель для ручной инжекции контекста в память агента.
 * Позволяет ввести текст, установить важность и теги, затем инжектировать в векторное хранилище.
 */
class ContextInjectPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding = PanelContextInjectBinding.inflate(
        LayoutInflater.from(context), this, true
    )

    var onInjectRequested: ((String, Float, List<String>) -> Unit)? = null
    var onClearRequested: (() -> Unit)? = null

    private val tags = mutableListOf<String>()
    private var currentImportance = 0.7f
    private var isExpanded = false

    init {
        orientation = VERTICAL
        setupViews()
        setupAnimations()
        collapsePanel()
    }

    private fun setupViews() {
        binding.headerContainer.setOnClickListener {
            toggleExpanded()
        }

        binding.importanceSlider.apply {
            valueFrom = 0f
            valueTo = 1f
            value = currentImportance
            stepSize = 0.05f
            addOnChangeListener { _, value, _ ->
                currentImportance = value
                updateImportanceLabel(value)
            }
        }
        updateImportanceLabel(currentImportance)

        binding.tagInput.setOnEditorActionListener { _, _, _ ->
            addTag(binding.tagInput.text?.toString()?.trim() ?: "")
            true
        }

        binding.btnAddTag.setOnClickListener {
            addTag(binding.tagInput.text?.toString()?.trim() ?: "")
        }

        binding.btnInject.setOnClickListener {
            performInject()
        }

        binding.btnClear.setOnClickListener {
            clearPanel()
            onClearRequested?.invoke()
        }

        binding.contextInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateTokenCount(s?.toString() ?: "")
            }
        })

        binding.btnPresetCritical.setOnClickListener {
            binding.importanceSlider.value = 0.95f
            addTag("critical")
        }

        binding.btnPresetSystem.setOnClickListener {
            binding.importanceSlider.value = 0.8f
            addTag("system")
        }

        binding.btnPresetUser.setOnClickListener {
            binding.importanceSlider.value = 0.6f
            addTag("user_preference")
        }

        binding.btnTemplateSummary.setOnClickListener {
            insertTemplate("Сводка предыдущей сессии: ")
        }

        binding.btnTemplateContext.setOnClickListener {
            insertTemplate("Контекст проекта: ")
        }

        binding.btnTemplateReminder.setOnClickListener {
            insertTemplate("Напоминание: ")
        }
    }

    private fun setupAnimations() {
        alpha = 0f
        translationY = -50f
        animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(400)
            .setInterpolator(OvershootInterpolator())
            .start()
    }

    private fun toggleExpanded() {
        isExpanded = !isExpanded
        
        val contentView = binding.contentContainer
        val arrowView = binding.expandArrow
        
        if (isExpanded) {
            contentView.isVisible = true
            contentView.alpha = 0f
            contentView.animate()
                .alpha(1f)
                .setDuration(200)
                .start()
            
            arrowView.animate()
                .rotation(180f)
                .setDuration(200)
                .start()
            
            binding.headerTitle.text = context.getString(R.string.context_inject_title_expanded)
        } else {
            contentView.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    contentView.isVisible = false
                }
                .start()
            
            arrowView.animate()
                .rotation(0f)
                .setDuration(200)
                .start()
            
            binding.headerTitle.text = context.getString(R.string.context_inject_title)
        }
    }

    private fun collapsePanel() {
        isExpanded = false
        binding.contentContainer.isVisible = false
        binding.expandArrow.rotation = 0f
    }

    private fun updateImportanceLabel(value: Float) {
        val (label, color) = when {
            value >= 0.9f -> "Критическая" to R.color.importance_critical
            value >= 0.7f -> "Высокая" to R.color.importance_high
            value >= 0.5f -> "Средняя" to R.color.importance_medium
            value >= 0.3f -> "Низкая" to R.color.importance_low
            else -> "Минимальная" to R.color.importance_minimal
        }
        
        binding.importanceLabel.text = "Важность: $label (${String.format("%.0f", value * 100)}%)"
        binding.importanceLabel.setTextColor(context.getColor(color))
        
        val trackColor = when {
            value >= 0.9f -> R.color.importance_critical
            value >= 0.7f -> R.color.importance_high
            value >= 0.5f -> R.color.importance_medium
            value >= 0.3f -> R.color.importance_low
            else -> R.color.importance_minimal
        }
        binding.importanceSlider.setTrackActiveTintColor(context.getColor(trackColor))
    }

    private fun addTag(tagText: String) {
        if (tagText.isBlank() || tagText in tags) return
        
        tags.add(tagText)
        
        val chip = Chip(context).apply {
            text = tagText
            isCloseIconVisible = true
            setChipBackgroundColorResource(R.color.chip_background)
            setTextColor(context.getColor(R.color.chip_text))
            setCloseIconTintResource(R.color.chip_close_icon)
            
            setOnCloseIconClickListener {
                tags.remove(tagText)
                binding.tagsContainer.removeView(this)
            }
        }
        
        binding.tagsContainer.addView(chip)
        binding.tagInput.text?.clear()
    }

    private fun updateTokenCount(text: String) {
        val estimatedTokens = text.length / 4
        binding.tokenCountLabel.text = "≈ $estimatedTokens токенов"
        
        if (estimatedTokens > 2000) {
            binding.tokenCountLabel.setTextColor(context.getColor(R.color.warning_color))
        } else {
            binding.tokenCountLabel.setTextColor(context.getColor(R.color.text_secondary))
        }
    }

    private fun performInject() {
        val text = binding.contextInput.text?.toString()?.trim() ?: ""
        
        if (text.isBlank()) {
            binding.contextInput.error = "Введите контекст"
            shakeView(binding.contextInput)
            return
        }
        
        binding.btnInject.isEnabled = false
        binding.btnInject.text = "Инжекция..."
        
        val scaleX = ObjectAnimator.ofFloat(binding.btnInject, "scaleX", 1f, 0.95f, 1f)
        val scaleY = ObjectAnimator.ofFloat(binding.btnInject, "scaleY", 1f, 0.95f, 1f)
        AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            duration = 300
            start()
        }
        
        onInjectRequested?.invoke(text, currentImportance, tags.toList())
    }

    fun showSuccessAnimation() {
        binding.btnInject.isEnabled = true
        binding.btnInject.text = "✓ Инжектировано"
        binding.btnInject.setBackgroundColor(context.getColor(R.color.success_color))
        
        val pulse = ObjectAnimator.ofFloat(binding.btnInject, "alpha", 1f, 0.7f, 1f)
        pulse.duration = 500
        pulse.start()
        
        postDelayed({
            binding.btnInject.text = context.getString(R.string.btn_inject)
            binding.btnInject.setBackgroundColor(context.getColor(R.color.primary_color))
            clearPanel()
        }, 1500)
    }

    private fun clearPanel() {
        binding.contextInput.text?.clear()
        tags.clear()
        binding.tagsContainer.removeAllViews()
        binding.importanceSlider.value = 0.7f
        updateTokenCount("")
    }

    private fun insertTemplate(template: String) {
        val current = binding.contextInput.text?.toString() ?: ""
        binding.contextInput.setText(current + template)
        binding.contextInput.setSelection(binding.contextInput.text?.length ?: 0)
    }

    private fun shakeView(view: View) {
        ObjectAnimator.ofFloat(view, "translationX", 0f, -20f, 20f, -15f, 15f, -10f, 10f, 0f).apply {
            duration = 400
            start()
        }
    }

    fun setInjectedContextPreview(text: String, importance: Float, tagList: List<String>) {
        binding.contextInput.setText(text)
        binding.importanceSlider.value = importance
        tags.clear()
        binding.tagsContainer.removeAllViews()
        tagList.forEach { addTag(it) }
        updateTokenCount(text)
    }
}
