package com.nexus.agent.ui.code

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nexus.agent.R
import java.io.File

/**
 * Адаптер для отображения древовидной структуры файлов.
 * Поддерживает иконки по типам файлов, отступы для вложенности, индикаторы изменений.
 */
class FileTreeAdapter(
    private val onFileClick: (File) -> Unit,
    private val onFileLongClick: (File, View) -> Boolean = { _, _ -> false },
    private val onDirectoryClick: (File) -> Unit,
    private val isExpanded: (File) -> Boolean,
    private val isModified: (File) -> Boolean
) : ListAdapter<FileTreeAdapter.FileItem, FileTreeAdapter.FileViewHolder>(FileDiffCallback()) {

    private var highlightedPosition = -1

    data class FileItem(
        val file: File,
        val depth: Int,
        val isExpanded: Boolean
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file_tree, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, position == highlightedPosition)
    }

    fun setHighlightedPosition(position: Int) {
        val oldPosition = highlightedPosition
        highlightedPosition = position
        if (oldPosition >= 0) notifyItemChanged(oldPosition)
        if (position >= 0) notifyItemChanged(position)
    }

    inner class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivIcon: ImageView = itemView.findViewById(R.id.ivFileIcon)
        private val tvName: TextView = itemView.findViewById(R.id.tvFileName)
        private val ivExpand: ImageView = itemView.findViewById(R.id.ivExpandIndicator)
        private val viewIndent: View = itemView.findViewById(R.id.viewIndent)
        private val ivModified: View = itemView.findViewById(R.id.ivModifiedIndicator)

        fun bind(item: FileItem, isHighlighted: Boolean) {
            val file = item.file
            val context = itemView.context

            // Indentation
            val indentWidth = (item.depth * 24).dpToPx(context)
            viewIndent.layoutParams.width = indentWidth

            // Name
            tvName.text = file.name
            tvName.setTextColor(
                if (isHighlighted) context.getColor(R.color.neon_cyan)
                else context.getColor(R.color.text_primary)
            )

            // Icon based on file type
            ivIcon.setImageResource(getFileIcon(file))

            // Expand indicator for directories
            if (file.isDirectory) {
                ivExpand.visibility = View.VISIBLE
                ivExpand.setImageResource(
                    if (item.isExpanded) R.drawable.ic_expand_less
                    else R.drawable.ic_expand_more
                )
            } else {
                ivExpand.visibility = View.INVISIBLE
            }

            // Modified indicator
            ivModified.visibility = if (isModified(file)) View.VISIBLE else View.INVISIBLE

            // Background for highlighted
            itemView.setBackgroundColor(
                if (isHighlighted) context.getColor(R.color.bg_selected)
                else context.getColor(android.R.color.transparent)
            )

            // Click handlers
            itemView.setOnClickListener {
                if (file.isDirectory) {
                    onDirectoryClick(file)
                } else {
                    onFileClick(file)
                }
            }

            itemView.setOnLongClickListener { view ->
                onFileLongClick(file, view)
            }
        }

        private fun getFileIcon(file: File): Int {
            return when {
                file.isDirectory -> R.drawable.ic_folder
                file.extension.equals("kt", true) -> R.drawable.ic_file_kotlin
                file.extension.equals("java", true) -> R.drawable.ic_file_java
                file.extension.equals("xml", true) -> R.drawable.ic_file_xml
                file.extension.equals("json", true) -> R.drawable.ic_file_json
                file.extension.equals("md", true) -> R.drawable.ic_file_markdown
                file.extension.equals("smali", true) -> R.drawable.ic_file_smali
                file.extension.equals("apk", true) -> R.drawable.ic_file_apk
                file.extension in listOf("png", "jpg", "jpeg", "gif", "webp") -> R.drawable.ic_file_image
                else -> R.drawable.ic_file_generic
            }
        }

        private fun Int.dpToPx(context: android.content.Context): Int {
            return (this * context.resources.displayMetrics.density).toInt()
        }
    }

    class FileDiffCallback : DiffUtil.ItemCallback<FileItem>() {
        override fun areItemsTheSame(oldItem: FileItem, newItem: FileItem): Boolean {
            return oldItem.file.absolutePath == newItem.file.absolutePath
        }

        override fun areContentsTheSame(oldItem: FileItem, newItem: FileItem): Boolean {
            return oldItem == newItem
        }
    }
}
