package com.nexus.agent.ui.universal

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.nexus.agent.R
import com.nexus.agent.databinding.ViewMediaToolbarBinding

/**
 * MediaToolbar — a custom toolbar for universal media operations.
 * Provides quick actions: Import, Camera, AI Generate, Share, Export.
 */
class MediaToolbar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding: ViewMediaToolbarBinding =
        ViewMediaToolbarBinding.inflate(LayoutInflater.from(context), this, true)

    private var actionListener: ((Action) -> Unit)? = null

    enum class Action {
        IMPORT,
        CAMERA,
        AI_GENERATE,
        SHARE,
        EXPORT
    }

    init {
        orientation = HORIZONTAL
        setupClickListeners()
        applyNeonStyling()
    }

    private fun setupClickListeners() {
        binding.btnImport.setOnClickListener {
            animateButton(it)
            actionListener?.invoke(Action.IMPORT)
        }

        binding.btnCamera.setOnClickListener {
            animateButton(it)
            actionListener?.invoke(Action.CAMERA)
        }

        binding.btnAIGenerate.setOnClickListener {
            animateButton(it)
            actionListener?.invoke(Action.AI_GENERATE)
        }

        binding.btnShare.setOnClickListener {
            animateButton(it)
            actionListener?.invoke(Action.SHARE)
        }

        binding.btnExport.setOnClickListener {
            animateButton(it)
            actionListener?.invoke(Action.EXPORT)
        }
    }

    private fun applyNeonStyling() {
        val neonColor = ContextCompat.getColor(context, R.color.neon_cyan)
        binding.btnAIGenerate.setColorFilter(neonColor)
        binding.btnAIGenerate.background = ContextCompat.getDrawable(context, R.drawable.bg_neon_border)
    }

    private fun animateButton(view: View) {
        view.animate()
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(100)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }

    fun setOnActionListener(listener: (Action) -> Unit) {
        this.actionListener = listener
    }

    fun setCameraEnabled(enabled: Boolean) {
        binding.btnCamera.isEnabled = enabled
        binding.btnCamera.alpha = if (enabled) 1.0f else 0.4f
    }

    fun setAIGenerateEnabled(enabled: Boolean) {
        binding.btnAIGenerate.isEnabled = enabled
        binding.btnAIGenerate.alpha = if (enabled) 1.0f else 0.4f
    }

    fun setShareEnabled(enabled: Boolean) {
        binding.btnShare.isEnabled = enabled
        binding.btnShare.alpha = if (enabled) 1.0f else 0.4f
    }

    fun setExportEnabled(enabled: Boolean) {
        binding.btnExport.isEnabled = enabled
        binding.btnExport.alpha = if (enabled) 1.0f else 0.4f
    }

    fun showProgress(message: String) {
        binding.progressBar.visibility = VISIBLE
        binding.tvProgressMessage.text = message
        binding.tvProgressMessage.visibility = VISIBLE
        setAllButtonsEnabled(false)
    }

    fun hideProgress() {
        binding.progressBar.visibility = GONE
        binding.tvProgressMessage.visibility = GONE
        setAllButtonsEnabled(true)
    }

    private fun setAllButtonsEnabled(enabled: Boolean) {
        binding.btnImport.isEnabled = enabled
        binding.btnCamera.isEnabled = enabled
        binding.btnAIGenerate.isEnabled = enabled
        binding.btnShare.isEnabled = enabled
        binding.btnExport.isEnabled = enabled
    }
}
