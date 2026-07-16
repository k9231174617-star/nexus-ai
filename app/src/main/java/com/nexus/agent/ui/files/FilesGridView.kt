// app/src/main/java/com/nexus/agent/ui/files/FilesGridView.kt
package com.nexus.agent.ui.files

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.nexus.agent.R
import com.nexus.agent.core.files.model.FileFilterType
import com.nexus.agent.core.files.model.FileItem
import com.nexus.agent.core.files.model.FileViewState
import com.nexus.agent.core.files.model.ViewMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FilesGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SwipeRefreshLayout(context, attrs, defStyleAttr) {

    private val recyclerView: RecyclerView
    private val adapter: FileAdapter
    private var onFileClick: ((FileItem) -> Unit)? = null
    private var onFileLongClick: ((FileItem) -> Unit)? = null
    private var onSelectionChanged: ((Set<FileItem>) -> Unit)? = null
    private var currentViewMode: ViewMode = ViewMode.GRID

    private val selectedItems = mutableSetOf<FileItem>()
    private var isMultiSelectMode = false

    init {
        recyclerView = RecyclerView(context).apply {
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setHasFixedSize(true)
        }
        addView(recyclerView)

        adapter = FileAdapter(
            onClick = { file ->
                if (isMultiSelectMode) {
                    toggleSelection(file)
                } else {
                    onFileClick?.invoke(file)
                }
            },
            onLongClick = { file ->
                if (!isMultiSelectMode) {
                    isMultiSelectMode = true
                    toggleSelection(file)
                    onFileLongClick?.invoke(file)
                } else {
                    toggleSelection(file)
                }
            }
        )
        recyclerView.adapter = adapter
        setGridMode(true)
    }

    fun setState(state: FileViewState) {
        currentViewMode = state.viewMode
        isMultiSelectMode = state.isMultiSelectMode
        
        if (!isMultiSelectMode) {
            selectedItems.clear()
        } else {
            selectedItems.clear()
            selectedItems.addAll(state.selectedItems)
        }
        
        adapter.setSelectedItems(selectedItems)
        adapter.setMultiSelectMode(isMultiSelectMode)
        
        // Обновляем layout
        setGridMode(state.viewMode == ViewMode.GRID)
        
        // Фильтрация и сортировка
        val filtered = filterItems(state.items, state.filterType, state.searchQuery)
        val sorted = sortItems(filtered, state.sortBy, state.sortAscending)
        
        adapter.submitList(sorted)
        
        // Прогресс операций
        adapter.setProgress(state.operationProgress)
        
        isRefreshing = state.isLoading
    }

    fun setOnFileClickListener(listener: (FileItem) -> Unit) {
        onFileClick = listener
    }

    fun setOnFileLongClickListener(listener: (FileItem) -> Unit) {
        onFileLongClick = listener
    }

    fun setOnSelectionChangedListener(listener: (Set<FileItem>) -> Unit) {
        onSelectionChanged = listener
    }

    fun setOnRefreshListener(listener: () -> Unit) {
        setOnRefreshListener { listener() }
    }

    fun exitMultiSelectMode() {
        isMultiSelectMode = false
        selectedItems.clear()
        adapter.setMultiSelectMode(false)
        adapter.setSelectedItems(emptySet())
        onSelectionChanged?.invoke(emptySet())
    }

    fun getSelectedItems(): Set<FileItem> = selectedItems.toSet()

    private fun toggleSelection(file: FileItem) {
        if (file in selectedItems) {
            selectedItems.remove(file)
        } else {
            selectedItems.add(file)
        }
        adapter.setSelectedItems(selectedItems)
        onSelectionChanged?.invoke(selectedItems.toSet())
        
        if (selectedItems.isEmpty()) {
            exitMultiSelectMode()
        }
    }

    private fun setGridMode(isGrid: Boolean) {
        val spanCount = if (isGrid) calculateSpanCount() else 1
        recyclerView.layoutManager = GridLayoutManager(context, spanCount)
        adapter.setGridMode(isGrid)
    }

    private fun calculateSpanCount(): Int {
        val displayMetrics = context.resources.displayMetrics
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
        return when {
            screenWidthDp >= 600 -> 4  // Tablet
            screenWidthDp >= 400 -> 3  // Large phone
            else -> 2                  // Normal phone
        }
    }

    private fun filterItems(
        items: List<FileItem>,
        filter: FileFilterType,
        query: String
    ): List<FileItem> {
        var result = items
        
        // Фильтр по типу
        result = when (filter) {
            FileFilterType.ALL -> result
            FileFilterType.DOCUMENTS -> result.filter { it.isText || it.extension in setOf("pdf", "docx", "xlsx") }
            FileFilterType.IMAGES -> result.filter { it.isImage }
            FileFilterType.VIDEO -> result.filter { it.isVideo }
            FileFilterType.AUDIO -> result.filter { it.isAudio }
            FileFilterType.APK -> result.filter { it.isApk }
            FileFilterType.CODE -> result.filter { it.isCode }
            FileFilterType.ARCHIVES -> result.filter { it.isArchive }
        }
        
        // Поиск по имени
        if (query.isNotBlank()) {
            result = result.filter { it.name.contains(query, ignoreCase = true) }
        }
        
        return result
    }

    private fun sortItems(
        items: List<FileItem>,
        sortBy: com.nexus.agent.core.files.model.SortBy,
        ascending: Boolean
    ): List<FileItem> {
        val sorted = when (sortBy) {
            com.nexus.agent.core.files.model.SortBy.NAME -> items.sortedBy { it.name.lowercase() }
            com.nexus.agent.core.files.model.SortBy.SIZE -> items.sortedBy { it.sizeBytes }
            com.nexus.agent.core.files.model.SortBy.DATE -> items.sortedBy { it.lastModified }
            com.nexus.agent.core.files.model.SortBy.TYPE -> items.sortedBy { it.extension }
        }
        return if (ascending) sorted else sorted.reversed()
    }

    // ==================== ADAPTER ====================

    private inner class FileAdapter(
        private val onClick: (FileItem) -> Unit,
        private val onLongClick: (FileItem) -> Unit
    ) : RecyclerView.Adapter<FileAdapter.ViewHolder>() {

        private var items: List<FileItem> = emptyList()
        private var selected = setOf<FileItem>()
        private var multiSelect = false
        private var isGrid = true
        private var progressMap: Map<String, Float> = emptyMap()

        fun submitList(newItems: List<FileItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        fun setSelectedItems(selection: Set<FileItem>) {
            selected = selection
            notifyDataSetChanged()
        }

        fun setMultiSelectMode(enabled: Boolean) {
            multiSelect = enabled
            notifyDataSetChanged()
        }

        fun setGridMode(grid: Boolean) {
            isGrid = grid
            notifyDataSetChanged()
        }

        fun setProgress(progress: Map<String, Float>) {
            progressMap = progress
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = items.size

        override fun getItemViewType(position: Int): Int {
            return if (isGrid) VIEW_TYPE_GRID else VIEW_TYPE_LIST
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val layoutId = if (viewType == VIEW_TYPE_GRID) {
                R.layout.item_file_grid
            } else {
                R.layout.item_file_list
            }
            val view = LayoutInflater.from(context).inflate(layoutId, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val iconView: ImageView = itemView.findViewById(R.id.iv_file_icon)
            private val nameView: TextView = itemView.findViewById(R.id.tv_file_name)
            private val detailView: TextView? = itemView.findViewById(R.id.tv_file_detail)
            private val checkView: ImageView? = itemView.findViewById(R.id.iv_check)
            private val progressView: View? = itemView.findViewById(R.id.v_progress)

            fun bind(file: FileItem) {
                val isSelected = file in selected
                
                // Имя файла
                nameView.text = file.name
                nameView.isSelected = isSelected
                
                // Детали (размер + дата в list mode)
                detailView?.text = buildString {
                    append(formatSize(file.sizeBytes))
                    append(" • ")
                    append(formatDate(file.lastModified))
                }

                // Иконка
                loadIcon(file)

                // Чекбокс в multiselect mode
                checkView?.isVisible = multiSelect
                checkView?.isSelected = isSelected
                checkView?.setImageResource(
                    if (isSelected) R.drawable.ic_check_circle else R.drawable.ic_check_circle_empty
                )

                // Прогресс операции
                val progress = progressMap[file.absolutePath]
                progressView?.isVisible = progress != null
                progressView?.let { v ->
                    val params = v.layoutParams
                    params.width = if (progress != null) {
                        (itemView.width * progress).toInt()
                    } else 0
                    v.layoutParams = params
                }

                // Фон выделения
                itemView.isSelected = isSelected
                itemView.setBackgroundResource(
                    if (isSelected) R.drawable.bg_file_selected else R.drawable.bg_file_normal
                )

                // Клики
                itemView.setOnClickListener { onClick(file) }
                itemView.setOnLongClickListener { 
                    onLongClick(file)
                    true 
                }
            }

            private fun loadIcon(file: FileItem) {
                when {
                    file.isDirectory -> iconView.setImageResource(R.drawable.ic_folder)
                    file.isImage -> {
                        Glide.with(context)
                            .load(file.absolutePath)
                            .placeholder(R.drawable.ic_image)
                            .centerCrop()
                            .into(iconView)
                    }
                    file.isVideo -> iconView.setImageResource(R.drawable.ic_video)
                    file.isAudio -> iconView.setImageResource(R.drawable.ic_audio)
                    file.isApk -> iconView.setImageResource(R.drawable.ic_apk)
                    file.isArchive -> iconView.setImageResource(R.drawable.ic_archive)
                    file.isCode -> iconView.setImageResource(R.drawable.ic_code)
                    file.isText -> iconView.setImageResource(R.drawable.ic_text)
                    else -> iconView.setImageResource(R.drawable.ic_file_generic)
                }
            }

            private fun formatSize(bytes: Long): String {
                if (bytes < 1024) return "$bytes B"
                val kb = bytes / 1024.0
                if (kb < 1024) return String.format("%.1f KB", kb)
                val mb = kb / 1024.0
                if (mb < 1024) return String.format("%.1f MB", mb)
                val gb = mb / 1024.0
                return String.format("%.1f GB", gb)
            }

            private fun formatDate(timestamp: Long): String {
                val sdf = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
                return sdf.format(Date(timestamp))
            }
        }

        companion object {
            const val VIEW_TYPE_GRID = 0
            const val VIEW_TYPE_LIST = 1
        }
    }
}
