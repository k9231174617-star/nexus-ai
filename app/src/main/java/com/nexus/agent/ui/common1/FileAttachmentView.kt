package com.nexus.agent.ui.common

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import coil.load
import com.nexus.agent.R
import com.nexus.agent.databinding.ViewFileAttachmentBinding
import java.io.File

/**
 * View for displaying file attachments in chat with preview support
 */
class FileAttachmentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding = ViewFileAttachmentBinding.inflate(
        LayoutInflater.from(context), this, true
    )

    var onRemoveClick: (() -> Unit)? = null
    var onClick: (() -> Unit)? = null

    init {
        binding.btnRemove.setOnClickListener { onRemoveClick?.invoke() }
        binding.root.setOnClickListener { onClick?.invoke() }
    }

    fun setAttachment(attachment: Attachment) {
        binding.apply {
            tvFileName.text = attachment.fileName
            tvFileSize.text = formatFileSize(attachment.sizeBytes)

            // Set icon/preview based on mime type
            when {
                attachment.mimeType.startsWith("image/") -> {
                    ivPreview.load(attachment.uri) {
                        crossfade(true)
                        placeholder(R.drawable.ic_files)
                        error(R.drawable.ic_files)
                    }
                    ivPreview.visibility = VISIBLE
                    ivFileIcon.visibility = GONE
                }
                attachment.mimeType.startsWith("video/") -> {
                    ivFileIcon.setImageResource(R.drawable.ic_universal)
                    ivPreview.visibility = GONE
                    ivFileIcon.visibility = VISIBLE
                }
                attachment.mimeType == "application/pdf" -> {
                    ivFileIcon.setImageResource(R.drawable.ic_files)
                    ivPreview.visibility = GONE
                    ivFileIcon.visibility = VISIBLE
                }
                else -> {
                    ivFileIcon.setImageResource(R.drawable.ic_files)
                    ivPreview.visibility = GONE
                    ivFileIcon.visibility = VISIBLE
                }
            }
        }
    }

    fun setUploadProgress(progress: Int) {
        binding.apply {
            progressUpload.visibility = if (progress in 0..99) VISIBLE else GONE
            progressUpload.progress = progress
            tvUploadStatus.visibility = if (progress in 0..99) VISIBLE else GONE
            tvUploadStatus.text = "$progress%"
        }
    }

    fun setError(error: String?) {
        binding.apply {
            tvError.text = error
            tvError.visibility = if (error != null) VISIBLE else GONE
            root.setBackgroundResource(
                if (error != null) R.drawable.bg_attachment_error else R.drawable.bg_attachment
            )
        }
    }

    private fun formatFileSize(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB")
        var size = bytes.toDouble()
        var unitIndex = 0
        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }
        return "%.1f %s".format(size, units[unitIndex])
    }
}
