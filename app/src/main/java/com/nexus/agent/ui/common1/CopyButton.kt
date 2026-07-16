package com.nexus.agent.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.content.getSystemService
import com.nexus.agent.R
import com.nexus.agent.databinding.ViewCopyButtonBinding

/**
 * Animated copy button with success feedback
 */
class CopyButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding = ViewCopyButtonBinding.inflate(
        LayoutInflater.from(context), this, true
    )

    private var textToCopy: String = ""
    private var copyLabel: String = "Nexus AI"

    init {
        binding.root.setOnClickListener { performCopy() }
    }

    fun setText(text: String, label: String = "Nexus AI") {
        textToCopy = text
        copyLabel = label
    }

    private fun performCopy() {
        val clipboard = context.getSystemService<ClipboardManager>() ?: return
        
        val clip = ClipData.newPlainText(copyLabel, textToCopy)
        clipboard.setPrimaryClip(clip)

        // Animate to success state
        binding.apply {
            ivIcon.setImageResource(R.drawable.ic_connection_ok)
            tvLabel.text = "Copied!"
            
            // Reset after delay
            postDelayed({
                ivIcon.setImageResource(R.drawable.ic_files) // copy icon
                tvLabel.text = "Copy"
            }, 2000)
        }

        // Haptic feedback
        performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
    }

    fun setCompactMode(compact: Boolean) {
        binding.tvLabel.visibility = if (compact) GONE else VISIBLE
    }
}
