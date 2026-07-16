package com.nexus.agent.ui.common

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import com.nexus.agent.databinding.ViewChatInputBarBinding

/**
 * Custom chat input bar with attachment, voice, and send functionality
 */
class ChatInputBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding = ViewChatInputBarBinding.inflate(
        LayoutInflater.from(context), this, true
    )

    var onSendClick: ((String, List<Attachment>) -> Unit)? = null
    var onAttachmentClick: (() -> Unit)? = null
    var onVoiceClick: (() -> Unit)? = null
    var onTyping: ((String) -> Unit)? = null

    private val attachments = mutableListOf<Attachment>()

    init {
        setupUI()
    }

    private fun setupUI() {
        binding.apply {
            // Send button state
            btnSend.isEnabled = false
            btnSend.alpha = 0.5f

            // Text input
            etMessage.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    onTyping?.invoke(s?.toString() ?: "")
                    updateSendButtonState()
                }
                override fun afterTextChanged(s: Editable?) {}
            })

            etMessage.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendMessage()
                    true
                } else false
            }

            // Buttons
            btnSend.setOnClickListener { sendMessage() }
            btnAttach.setOnClickListener { onAttachmentClick?.invoke() }
            btnVoice.setOnClickListener { onVoiceClick?.invoke() }

            // Attachment chips container
            chipsAttachments.isVisible = false
        }
    }

    private fun updateSendButtonState() {
        val hasText = binding.etMessage.text?.isNotBlank() == true
        val hasAttachments = attachments.isNotEmpty()
        val enabled = hasText || hasAttachments

        binding.btnSend.isEnabled = enabled
        binding.btnSend.alpha = if (enabled) 1.0f else 0.5f
        
        // Switch between send and voice button
        binding.btnVoice.isVisible = !hasText
        binding.btnSend.isVisible = hasText || hasAttachments
    }

    fun sendMessage() {
        val text = binding.etMessage.text?.toString()?.trim() ?: ""
        if (text.isBlank() && attachments.isEmpty()) return

        onSendClick?.invoke(text, attachments.toList())
        
        // Clear input
        binding.etMessage.text?.clear()
        clearAttachments()
    }

    fun addAttachment(attachment: Attachment) {
        attachments.add(attachment)
        updateAttachmentChips()
        updateSendButtonState()
    }

    fun removeAttachment(attachment: Attachment) {
        attachments.remove(attachment)
        updateAttachmentChips()
        updateSendButtonState()
    }

    private fun clearAttachments() {
        attachments.clear()
        updateAttachmentChips()
    }

    private fun updateAttachmentChips() {
        binding.chipsAttachments.apply {
            removeAllViews()
            isVisible = attachments.isNotEmpty()
            
            attachments.forEach { attachment ->
                val chip = com.google.android.material.chip.Chip(context).apply {
                    text = attachment.fileName
                    isCloseIconVisible = true
                    setOnCloseIconClickListener { removeAttachment(attachment) }
                }
                addView(chip)
            }
        }
    }

    fun setLoading(isLoading: Boolean) {
        binding.apply {
            btnSend.isEnabled = !isLoading
            progressBar.isVisible = isLoading
            btnSend.isVisible = !isLoading
            etMessage.isEnabled = !isLoading
        }
    }

    fun setHint(hint: String) {
        binding.etMessage.hint = hint
    }

    fun insertText(text: String) {
        val current = binding.etMessage.text ?: return
        val start = binding.etMessage.selectionStart.coerceAtLeast(0)
        current.insert(start, text)
    }
}

data class Attachment(
    val uri: android.net.Uri,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long
)
