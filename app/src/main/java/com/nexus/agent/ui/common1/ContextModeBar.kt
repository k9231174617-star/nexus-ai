package com.nexus.agent.ui.common

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.nexus.agent.R
import com.nexus.agent.databinding.ViewContextModeBarBinding

/**
 * Context mode selector bar for switching between agent modes
 */
class ContextModeBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding = ViewContextModeBarBinding.inflate(
        LayoutInflater.from(context), this, true
    )

    var onModeChanged: ((ContextMode) -> Unit)? = null
    var onContextPanelClick: (() -> Unit)? = null

    private var currentMode: ContextMode = ContextMode.ASSISTANT

    init {
        setupModeButtons()
        binding.btnContextPanel.setOnClickListener { onContextPanelClick?.invoke() }
    }

    private fun setupModeButtons() {
        binding.apply {
            btnModeAssistant.setOnClickListener { setMode(ContextMode.ASSISTANT) }
            btnModeCoder.setOnClickListener { setMode(ContextMode.CODER) }
            btnModeAgent.setOnClickListener { setMode(ContextMode.AGENT) }
            btnModeResearch.setOnClickListener { setMode(ContextMode.RESEARCH) }
        }
        updateUI()
    }

    fun setMode(mode: ContextMode) {
        if (currentMode == mode) return
        currentMode = mode
        updateUI()
        onModeChanged?.invoke(mode)
    }
    private fun updateUI() {
        binding.apply {
            val buttons = mapOf(
                ContextMode.ASSISTANT to btnModeAssistant,
                ContextMode.CODER to btnModeCoder,
                ContextMode.AGENT to btnModeAgent,
                ContextMode.RESEARCH to btnModeResearch
            )

            buttons.forEach { (mode, button) ->
                val isSelected = mode == currentMode
                button.isSelected = isSelected
                
                // Neon glow effect for selected
                button.alpha = if (isSelected) 1.0f else 0.6f
                
                val colorRes = if (isSelected) {
                    when (mode) {
                        ContextMode.ASSISTANT -> R.color.neon_cyan
                        ContextMode.CODER -> R.color.neon_green
                        ContextMode.AGENT -> R.color.neon_purple
                        ContextMode.RESEARCH -> R.color.neon_orange
                    }
                } else {
                    R.color.text_secondary
                }
                
                button.setTextColor(context.getColor(colorRes))
                
                // Indicator line
                viewModeIndicator.visibility = View.VISIBLE
                viewModeIndicator.setBackgroundColor(context.getColor(colorRes))
            }

            // Update context hint
            tvContextHint.text = when (currentMode) {
                ContextMode.ASSISTANT -> "General AI assistant mode"
                ContextMode.CODER -> "Code-focused with syntax awareness"
                ContextMode.AGENT -> "Autonomous tool execution"
                ContextMode.RESEARCH -> "Deep research with web search"
            }
        }
    }

    fun setContextInfo(activeFiles: Int, memoryEntries: Int) {
        binding.tvContextInfo.text = buildString {
            if (activeFiles > 0) append("$activeFiles files ")
            if (memoryEntries > 0) append("• $memoryEntries memories")
            if (activeFiles == 0 && memoryEntries == 0) append("No context")
        }
        binding.tvContextInfo.isVisible = activeFiles > 0 || memoryEntries > 0
    }

    fun showModeDescription(mode: ContextMode): String {
        return when (mode) {
            ContextMode.ASSISTANT -> "Balanced mode for general questions and conversations"
            ContextMode.CODER -> "Optimized for code generation, review, and debugging"
            ContextMode.AGENT -> "Can use tools, execute commands, and modify files"
            ContextMode.RESEARCH -> "Browses web, analyzes documents, deep research"
        }
    }
}

enum class ContextMode {
    ASSISTANT,   // General chat
    CODER,       // Code-focused
    AGENT,       // Tool-using agent
    RESEARCH     // Research mode
}
